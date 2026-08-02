package com.relationdetector.semantic.internal.store;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;

/**
 * CN: 为已按UTF-8字节顺序排序的文本文件提供共享磁盘二分查找；输入是完整key或制表符前缀key，输出是
 * 命中的整行。它不负责外排、去重或解释行内payload。
 *
 * EN: Provides shared on-disk binary lookup for text files sorted by UTF-8 bytes. It accepts either a whole-line key
 * or a tab-delimited prefix key and returns the matching line; sorting, deduplication, and payload interpretation stay
 * with callers.
 */
public final class DiskSortedTextFile implements AutoCloseable {
    private final RandomAccessFile file;

    public DiskSortedTextFile(Path path) throws IOException {
        file = new RandomAccessFile(path.toFile(), "r");
    }

    public Optional<String> find(String key) throws IOException {
        return find(key, false);
    }

    public Optional<String> findTabKey(String key) throws IOException {
        return find(key, true);
    }

    private Optional<String> find(String key, boolean tabDelimited) throws IOException {
        byte[] expected = key.getBytes(StandardCharsets.UTF_8);
        long low = 0;
        long high = file.length();
        while (low < high) {
            long start = lineStart((low + high) >>> 1);
            Line line = readLine(start);
            int comparison = compareKey(line.bytes(), expected, tabDelimited);
            if (comparison < 0) {
                low = Math.max(start + 1, line.next());
            } else if (comparison > 0) {
                high = start;
            } else {
                return Optional.of(new String(line.bytes(), StandardCharsets.UTF_8));
            }
        }
        return Optional.empty();
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
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int value;
        while ((value = file.read()) >= 0 && value != '\n') {
            if (value != '\r') {
                bytes.write(value);
            }
        }
        return new Line(bytes.toByteArray(), file.getFilePointer());
    }

    private int compareKey(byte[] line, byte[] expected, boolean tabDelimited) {
        int keyLength = line.length;
        if (tabDelimited) {
            keyLength = 0;
            while (keyLength < line.length && line[keyLength] != '\t') {
                keyLength++;
            }
        }
        return Utf8ByteOrder.compare(line, keyLength, expected, expected.length);
    }

    @Override
    public void close() throws IOException {
        file.close();
    }

    private record Line(byte[] bytes, long next) {
    }
}
