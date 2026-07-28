package com.relationdetector.semantic.reader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.OptionalInt;
import java.util.function.Consumer;

/**
 * CN: 将外排去重后的字符串key映射为稳定dense integer id，lookup使用磁盘二分且不会把dictionary加载进堆；
 * 供file-backed union-find使用，不解释key内容。
 * EN: Maps externally sorted unique string keys to stable dense integer ids. Lookup uses on-disk binary search and
 * never loads the dictionary into heap; the dictionary does not interpret key content.
 */
final class DiskStringDictionary implements AutoCloseable {
    private final Path path;
    private final RandomAccessFile file;
    private final int size;

    private DiskStringDictionary(Path path, int size) throws IOException {
        this.path = path;
        this.file = new RandomAccessFile(path.toFile(), "r");
        this.size = size;
    }

    static DiskStringDictionary build(Path rawKeys, Path dictionary, Path workspace) throws IOException {
        Path sorted = workspace.resolve("keys.sorted");
        new ExternalLineSorter().sort(rawKeys, sorted, workspace.resolve("sort"));
        String previous = null;
        int id = 0;
        try (BufferedReader reader = Files.newBufferedReader(sorted, StandardCharsets.UTF_8);
             BufferedWriter writer = Files.newBufferedWriter(
                     dictionary, StandardCharsets.UTF_8,
                     StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            String key;
            while ((key = reader.readLine()) != null) {
                if (key.equals(previous)) {
                    continue;
                }
                writer.write(key);
                writer.write('\t');
                writer.write("%010d".formatted(id++));
                writer.newLine();
                previous = key;
            }
        } finally {
            Files.deleteIfExists(sorted);
        }
        return new DiskStringDictionary(dictionary, id);
    }

    int size() {
        return size;
    }

    OptionalInt id(String key) throws IOException {
        String prefix = key + "\t";
        byte[] expected = prefix.getBytes(StandardCharsets.UTF_8);
        long low = 0;
        long high = file.length();
        while (low < high) {
            long middle = (low + high) >>> 1;
            long start = lineStart(middle);
            Line line = readLine(start);
            int comparison = comparePrefix(line.value(), expected);
            if (comparison < 0) {
                low = Math.max(start + 1, line.next());
            } else if (comparison > 0) {
                high = start;
            } else {
                String value = new String(line.value(), StandardCharsets.UTF_8);
                return OptionalInt.of(Integer.parseInt(value.substring(prefix.length())));
            }
        }
        return OptionalInt.empty();
    }

    void forEach(Consumer<Entry> consumer) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                int split = line.lastIndexOf('\t');
                consumer.accept(new Entry(line.substring(0, split), Integer.parseInt(line.substring(split + 1))));
            }
        }
    }

    private long lineStart(long offset) throws IOException {
        if (offset <= 0 || file.length() == 0) {
            return 0;
        }
        long position = Math.min(offset, file.length() - 1);
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

    private int comparePrefix(byte[] line, byte[] expected) {
        int keyLength = 0;
        while (keyLength < line.length && line[keyLength] != '\t') {
            keyLength++;
        }
        int length = Math.min(keyLength + 1, expected.length);
        for (int index = 0; index < length; index++) {
            int comparison = Integer.compare(Byte.toUnsignedInt(line[index]), Byte.toUnsignedInt(expected[index]));
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(keyLength + 1, expected.length);
    }

    @Override
    public void close() throws IOException {
        file.close();
    }

    record Entry(String key, int id) {
    }

    private record Line(byte[] value, long next) {
    }
}
