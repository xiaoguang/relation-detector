package com.relationdetector.cli.verification;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * CN: 将无界字符串集合分块排序并外排为去重索引，供跨 section 引用闭包做顺序归并；
 * 输入只在固定大小缓冲区中停留，输出位于 verification 临时目录。
 * EN: Externally sorts an unbounded string collection into a deduplicated index for merge-based cross-section
 * reference closure. Only a fixed-size chunk remains in memory and all output stays in verification temp storage.
 */
final class ExternalStringIndex {
    private final Path workspace;
    private final int valuesPerChunk;
    private final List<String> pending;
    private final List<Path> chunks = new ArrayList<>();

    ExternalStringIndex(Path workspace, int valuesPerChunk) {
        if (workspace == null || valuesPerChunk < 1) {
            throw new IllegalArgumentException("string index workspace and positive chunk size are required");
        }
        this.workspace = workspace;
        this.valuesPerChunk = valuesPerChunk;
        this.pending = new ArrayList<>(valuesPerChunk);
    }

    void add(String value) {
        if (value == null || value.isBlank() || value.indexOf('\u0000') >= 0) {
            throw new ReleaseVerificationException("external string index value is invalid");
        }
        pending.add(value);
        if (pending.size() == valuesPerChunk) {
            flush();
        }
    }

    /**
     * CN: 刷新最后一个内存块并归并全部有序块；输入决定是否拒绝重复值，输出是可顺序读取的去重外存索引，
     * 写盘、归并或重复约束失败时不返回不完整索引。
     * EN: Flushes the final memory chunk and merges all sorted chunks. The input controls duplicate rejection and
     * the output is a sequential deduplicated disk index; write, merge, or duplicate failures return no partial index.
     */
    SortedIndex finish(boolean rejectDuplicates) {
        flush();
        Path output = workspace.resolve("sorted.bin");
        try {
            Files.createDirectories(workspace);
            List<Cursor> cursors = new ArrayList<>();
            PriorityQueue<Cursor> queue = new PriorityQueue<>(
                    Comparator.comparing(Cursor::value, ExternalCanonicalJsonFingerprinter::compareCodePoints));
            try (DataOutputStream sink = new DataOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(output)))) {
                for (Path chunk : chunks) {
                    Cursor cursor = new Cursor(chunk);
                    cursors.add(cursor);
                    if (cursor.advance()) {
                        queue.add(cursor);
                    }
                }
                String previous = null;
                long unique = 0;
                while (!queue.isEmpty()) {
                    Cursor cursor = queue.remove();
                    String value = cursor.value();
                    if (value.equals(previous)) {
                        if (rejectDuplicates) {
                            throw new ReleaseVerificationException(
                                    "duplicate value in external string index");
                        }
                    } else {
                        write(sink, value);
                        previous = value;
                        unique++;
                    }
                    if (cursor.advance()) {
                        queue.add(cursor);
                    }
                }
                return new SortedIndex(output, unique);
            } finally {
                for (Cursor cursor : cursors) {
                    cursor.close();
                }
            }
        } catch (ReleaseVerificationException error) {
            throw error;
        } catch (IOException error) {
            throw new ReleaseVerificationException("failed to merge external string index", error);
        }
    }

    static boolean containsAll(SortedIndex superset, SortedIndex subset) {
        try (Cursor available = new Cursor(superset.path());
                Cursor required = new Cursor(subset.path())) {
            boolean hasAvailable = available.advance();
            boolean hasRequired = required.advance();
            while (hasRequired) {
                while (hasAvailable
                        && ExternalCanonicalJsonFingerprinter.compareCodePoints(
                        available.value(), required.value()) < 0) {
                    hasAvailable = available.advance();
                }
                if (!hasAvailable || !available.value().equals(required.value())) {
                    return false;
                }
                hasRequired = required.advance();
            }
            return true;
        } catch (IOException error) {
            throw new ReleaseVerificationException("failed to compare external string indexes", error);
        }
    }

    private void flush() {
        if (pending.isEmpty()) {
            return;
        }
        pending.sort(ExternalCanonicalJsonFingerprinter::compareCodePoints);
        try {
            Files.createDirectories(workspace);
            Path chunk = workspace.resolve("chunk-" + chunks.size() + ".bin");
            try (DataOutputStream output = new DataOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(chunk)))) {
                for (String value : pending) {
                    write(output, value);
                }
            }
            chunks.add(chunk);
            pending.clear();
        } catch (IOException error) {
            throw new ReleaseVerificationException("failed to write external string index chunk", error);
        }
    }

    private static void write(DataOutputStream output, String value) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(encoded.length);
        output.write(encoded);
    }

    record SortedIndex(Path path, long size) {
    }

    private static final class Cursor implements AutoCloseable {
        private final DataInputStream input;
        private String value;

        private Cursor(Path path) throws IOException {
            input = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)));
        }

        private boolean advance() throws IOException {
            try {
                int length = input.readInt();
                if (length < 0) {
                    throw new ReleaseVerificationException("external string index length is invalid");
                }
                byte[] encoded = input.readNBytes(length);
                if (encoded.length != length) {
                    throw new ReleaseVerificationException(
                            "external string index ended unexpectedly");
                }
                value = new String(encoded, StandardCharsets.UTF_8);
                return true;
            } catch (EOFException end) {
                value = null;
                return false;
            }
        }

        private String value() {
            return value;
        }

        @Override
        public void close() throws IOException {
            input.close();
        }
    }
}
