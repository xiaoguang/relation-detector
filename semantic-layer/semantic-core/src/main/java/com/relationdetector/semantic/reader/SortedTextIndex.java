package com.relationdetector.semantic.reader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * CN: 将外排后的UTF-8 key文件作为磁盘membership index，构建时检测重复，查询时按文件偏移二分且不加载完整
 * key集合；上游是streaming reader/evidence store，下游是identity与reference closure校验。
 * EN: Uses an externally sorted UTF-8 key file as an on-disk membership index. Construction rejects duplicates and
 * lookup uses file-offset binary search without loading the complete key set.
 */
public final class SortedTextIndex implements AutoCloseable {
    private final Path path;
    private final RandomAccessFile file;

    private SortedTextIndex(Path path) throws IOException {
        this.path = path;
        this.file = new RandomAccessFile(path.toFile(), "r");
    }

    public static SortedTextIndex build(Path rawKeys, Path index, Path workspace, String label) throws IOException {
        Path sorted = workspace.resolve(index.getFileName() + ".sorted");
        new ExternalLineSorter().sort(rawKeys, sorted, workspace.resolve(index.getFileName() + ".sort-work"));
        String previous = null;
        try (BufferedReader reader = Files.newBufferedReader(sorted, StandardCharsets.UTF_8);
             var writer = Files.newBufferedWriter(
                     index, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            String current;
            while ((current = reader.readLine()) != null) {
                if (current.equals(previous)) {
                    throw new ScanResultContractException(label + " contains duplicate identity");
                }
                writer.write(current);
                writer.newLine();
                previous = current;
            }
        } finally {
            Files.deleteIfExists(sorted);
        }
        return new SortedTextIndex(index);
    }

    public static SortedTextIndex openExisting(Path index) throws IOException {
        if (index == null || !Files.isRegularFile(index)) {
            throw new IllegalArgumentException("sorted text index file is required");
        }
        return new SortedTextIndex(index);
    }

    public boolean contains(String key) throws IOException {
        if (key == null || key.isBlank()) {
            return false;
        }
        byte[] expected = key.getBytes(StandardCharsets.UTF_8);
        long low = 0;
        long high = file.length();
        while (low < high) {
            long middle = (low + high) >>> 1;
            long start = lineStart(middle);
            Line line = readLine(start);
            int comparison = compareUtf8(line.value(), expected);
            if (comparison < 0) {
                low = Math.max(start + 1, line.next());
            } else if (comparison > 0) {
                high = start;
            } else {
                return true;
            }
        }
        return false;
    }

    public Path path() {
        return path;
    }

    private long lineStart(long offset) throws IOException {
        if (offset <= 0) {
            return 0;
        }
        long position = Math.min(offset, file.length() - 1);
        file.seek(position);
        while (position > 0) {
            file.seek(position - 1);
            if (file.read() == '\n') {
                return position;
            }
            position--;
        }
        return 0;
    }

    private Line readLine(long start) throws IOException {
        file.seek(start);
        var bytes = new java.io.ByteArrayOutputStream();
        int value;
        while ((value = file.read()) >= 0 && value != '\n') {
            if (value != '\r') {
                bytes.write(value);
            }
        }
        return new Line(bytes.toByteArray(), file.getFilePointer());
    }

    private int compareUtf8(byte[] left, byte[] right) {
        int length = Math.min(left.length, right.length);
        for (int index = 0; index < length; index++) {
            int comparison = Integer.compare(Byte.toUnsignedInt(left[index]), Byte.toUnsignedInt(right[index]));
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(left.length, right.length);
    }

    @Override
    public void close() throws IOException {
        file.close();
    }

    private record Line(byte[] value, long next) {
    }
}
