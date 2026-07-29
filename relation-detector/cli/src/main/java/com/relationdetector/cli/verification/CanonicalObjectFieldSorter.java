package com.relationdetector.cli.verification;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

/**
 * CN: 为单个 canonical JSON object 外排字段值和排序索引，并按 Unicode code point 顺序归并写回；
 * 输入是已定位到 START_OBJECT 后的 token 流，输出写入调用方 canonical stream。本类不决定字段过滤、
 * 数字格式或摘要算法，也不把完整 object 保存在堆中。
 * EN: Spools one canonical JSON object's field values and external sort indexes, then merges them in Unicode
 * code-point order into the caller's canonical stream. It neither chooses field filters or number formatting nor
 * owns the digest, and it never retains the complete object on heap.
 */
final class CanonicalObjectFieldSorter {
    private static final int VALUE_MEMORY_LIMIT = 1024 * 1024;
    private static final Comparator<String> CODE_POINT_ORDER =
            CanonicalObjectFieldSorter::compareCodePoints;

    private final Path workspace;
    private final int fieldsPerChunk;

    CanonicalObjectFieldSorter(Path workspace, int fieldsPerChunk) {
        this.workspace = workspace;
        this.fieldsPerChunk = fieldsPerChunk;
    }

    void write(
            JsonParser parser,
            OutputStream output,
            CanonicalFingerprintMode mode,
            int objectDepth,
            FieldFilter filter,
            ValueWriter valueWriter,
            QuotedStringWriter quotedWriter
    ) throws IOException {
        ObjectWorkspace objectWorkspace = new ObjectWorkspace(
                workspace.resolve("object-" + objectDepth));
        List<Path> chunks = new ArrayList<>();
        List<FieldRecord> pending = new ArrayList<>(fieldsPerChunk);
        try (ObjectSpool spool = new ObjectSpool(objectWorkspace)) {
            JsonToken token;
            while ((token = parser.nextToken()) != JsonToken.END_OBJECT) {
                if (token != JsonToken.FIELD_NAME) {
                    throw new ReleaseVerificationException("JSON object field name is required");
                }
                String key = parser.currentName();
                JsonToken valueToken = parser.nextToken();
                if (valueToken == null) {
                    throw new ReleaseVerificationException("JSON object field value is required");
                }
                if (filter.filtered(key, mode)) {
                    parser.skipChildren();
                    continue;
                }
                long offset = spool.position();
                valueWriter.write(parser, valueToken, spool, mode, objectDepth + 1);
                pending.add(new FieldRecord(key, offset, spool.position() - offset));
                if (pending.size() == fieldsPerChunk) {
                    chunks.add(writeChunk(objectWorkspace, chunks.size(), pending));
                    pending.clear();
                }
            }
            if (chunks.isEmpty()) {
                writeInMemoryObject(output, spool, pending, quotedWriter);
            } else {
                if (!pending.isEmpty()) {
                    chunks.add(writeChunk(objectWorkspace, chunks.size(), pending));
                }
                writeSortedObject(output, spool, chunks, quotedWriter);
            }
        }
    }

    private Path writeChunk(
            ObjectWorkspace objectWorkspace,
            int index,
            List<FieldRecord> records
    ) throws IOException {
        records.sort(Comparator.comparing(FieldRecord::key, CODE_POINT_ORDER));
        objectWorkspace.prepare();
        Path chunk = objectWorkspace.directory().resolve("fields-" + index + ".bin");
        try (DataOutputStream output = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(chunk)))) {
            for (FieldRecord record : records) {
                byte[] key = record.key().getBytes(StandardCharsets.UTF_8);
                output.writeInt(key.length);
                output.write(key);
                output.writeLong(record.offset());
                output.writeLong(record.length());
            }
        }
        return chunk;
    }

    private void writeInMemoryObject(
            OutputStream output,
            ObjectSpool spool,
            List<FieldRecord> records,
            QuotedStringWriter quotedWriter
    ) throws IOException {
        records.sort(Comparator.comparing(FieldRecord::key, CODE_POINT_ORDER));
        output.write('{');
        String previous = null;
        boolean first = true;
        for (FieldRecord record : records) {
            if (record.key().equals(previous)) {
                throw new ReleaseVerificationException("duplicate JSON object key");
            }
            if (!first) {
                output.write(',');
            }
            quotedWriter.write(output, record.key());
            output.write(':');
            spool.copy(record.offset(), record.length(), output);
            previous = record.key();
            first = false;
        }
        output.write('}');
    }

    private void writeSortedObject(
            OutputStream output,
            ObjectSpool spool,
            List<Path> chunks,
            QuotedStringWriter quotedWriter
    ) throws IOException {
        PriorityQueue<ChunkCursor> queue = new PriorityQueue<>(
                Comparator.comparing(cursor -> cursor.current().key(), CODE_POINT_ORDER));
        List<ChunkCursor> cursors = new ArrayList<>();
        try {
            for (Path chunk : chunks) {
                ChunkCursor cursor = new ChunkCursor(chunk);
                cursors.add(cursor);
                if (cursor.advance()) {
                    queue.add(cursor);
                }
            }
            output.write('{');
            String previous = null;
            boolean first = true;
            while (!queue.isEmpty()) {
                ChunkCursor cursor = queue.remove();
                FieldRecord record = cursor.current();
                if (record.key().equals(previous)) {
                    throw new ReleaseVerificationException("duplicate JSON object key");
                }
                if (!first) {
                    output.write(',');
                }
                quotedWriter.write(output, record.key());
                output.write(':');
                spool.copy(record.offset(), record.length(), output);
                previous = record.key();
                first = false;
                if (cursor.advance()) {
                    queue.add(cursor);
                }
            }
            output.write('}');
        } finally {
            for (ChunkCursor cursor : cursors) {
                cursor.close();
            }
        }
    }

    static int compareCodePoints(String left, String right) {
        int leftOffset = 0;
        int rightOffset = 0;
        while (leftOffset < left.length() && rightOffset < right.length()) {
            int leftCodePoint = left.codePointAt(leftOffset);
            int rightCodePoint = right.codePointAt(rightOffset);
            if (leftCodePoint != rightCodePoint) {
                return Integer.compare(leftCodePoint, rightCodePoint);
            }
            leftOffset += Character.charCount(leftCodePoint);
            rightOffset += Character.charCount(rightCodePoint);
        }
        return Integer.compare(left.length() - leftOffset, right.length() - rightOffset);
    }

    private record FieldRecord(String key, long offset, long length) {
    }

    @FunctionalInterface
    interface FieldFilter {
        boolean filtered(String key, CanonicalFingerprintMode mode);
    }

    @FunctionalInterface
    interface ValueWriter {
        void write(
                JsonParser parser,
                JsonToken token,
                OutputStream output,
                CanonicalFingerprintMode mode,
                int objectDepth
        ) throws IOException;
    }

    @FunctionalInterface
    interface QuotedStringWriter {
        void write(OutputStream output, String value) throws IOException;
    }

    private static final class ChunkCursor implements AutoCloseable {
        private final DataInputStream input;
        private FieldRecord current;

        private ChunkCursor(Path path) throws IOException {
            input = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)));
        }

        private boolean advance() throws IOException {
            try {
                int keyLength = input.readInt();
                byte[] key = input.readNBytes(keyLength);
                if (key.length != keyLength) {
                    throw new ReleaseVerificationException("canonical field index ended unexpectedly");
                }
                current = new FieldRecord(
                        new String(key, StandardCharsets.UTF_8), input.readLong(), input.readLong());
                return true;
            } catch (java.io.EOFException end) {
                current = null;
                return false;
            }
        }

        private FieldRecord current() {
            return current;
        }

        @Override
        public void close() throws IOException {
            input.close();
        }
    }

    private static final class ObjectWorkspace {
        private final Path directory;
        private boolean prepared;

        private ObjectWorkspace(Path directory) {
            this.directory = directory;
        }

        private void prepare() throws IOException {
            if (!prepared) {
                deleteRecursively(directory);
                Files.createDirectories(directory);
                prepared = true;
            }
        }

        private Path directory() {
            return directory;
        }
    }

    private static final class ObjectSpool extends OutputStream implements AutoCloseable {
        private final ObjectWorkspace workspace;
        private ByteArrayOutputStream memory = new ByteArrayOutputStream();
        private FileChannel file;
        private long position;

        private ObjectSpool(ObjectWorkspace workspace) {
            this.workspace = workspace;
        }

        private long position() {
            return position;
        }

        @Override
        public void write(int value) throws IOException {
            ensureStorage(1);
            if (file == null) {
                memory.write(value);
            } else {
                writeFile(ByteBuffer.wrap(new byte[] {(byte) value}));
            }
            position++;
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            ensureStorage(length);
            if (file == null) {
                memory.write(bytes, offset, length);
            } else {
                writeFile(ByteBuffer.wrap(bytes, offset, length));
            }
            position += length;
        }

        private void ensureStorage(int incoming) throws IOException {
            if (file != null || position + incoming <= VALUE_MEMORY_LIMIT) {
                return;
            }
            workspace.prepare();
            file = FileChannel.open(
                    workspace.directory().resolve("values.spool"),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            writeFile(ByteBuffer.wrap(memory.toByteArray()));
            memory = null;
        }

        private void writeFile(ByteBuffer buffer) throws IOException {
            while (buffer.hasRemaining()) {
                file.write(buffer);
            }
        }

        private void copy(long offset, long length, OutputStream output) throws IOException {
            if (file == null) {
                memory.writeTo(new RangeOutputStream(output, offset, length));
                return;
            }
            ByteBuffer buffer = ByteBuffer.allocate(64 * 1024);
            long source = offset;
            long remaining = length;
            while (remaining > 0) {
                buffer.clear();
                buffer.limit((int) Math.min(buffer.capacity(), remaining));
                int read = file.read(buffer, source);
                if (read < 0) {
                    throw new ReleaseVerificationException(
                            "canonical value spool ended unexpectedly");
                }
                output.write(buffer.array(), 0, read);
                source += read;
                remaining -= read;
            }
        }

        @Override
        public void close() throws IOException {
            if (file != null) {
                file.close();
            }
        }
    }

    private static final class RangeOutputStream extends OutputStream {
        private final OutputStream target;
        private final long offset;
        private final long length;
        private long position;

        private RangeOutputStream(OutputStream target, long offset, long length) {
            this.target = target;
            this.offset = offset;
            this.length = length;
        }

        @Override
        public void write(int value) throws IOException {
            if (position >= offset && position < offset + length) {
                target.write(value);
            }
            position++;
        }

        @Override
        public void write(byte[] bytes, int start, int count) throws IOException {
            long from = Math.max(offset, position);
            long to = Math.min(offset + length, position + count);
            if (from < to) {
                target.write(bytes, start + (int) (from - position), (int) (to - from));
            }
            position += count;
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (root == null || Files.notExists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
