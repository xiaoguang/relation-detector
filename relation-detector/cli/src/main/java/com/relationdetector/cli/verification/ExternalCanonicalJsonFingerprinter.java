package com.relationdetector.cli.verification;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

/**
 * CN: 对无界 JSON 以 token 流读取，对象字段通过分块外排和多路归并按 Unicode code point 排序，
 * canonical 字节直接进入 SHA-256；不构造完整 JsonNode 或 canonical 字符串。
 * EN: Reads unbounded JSON as tokens, externally sorts object fields by Unicode code point, and feeds canonical
 * bytes directly into SHA-256 without materializing the complete tree or canonical document.
 */
final class ExternalCanonicalJsonFingerprinter {
    private static final Set<String> VOLATILE_KEYS = Set.of(
            "generatedAt", "startedAt", "finishedAt", "durationMillis", "elapsedMillis");
    private static final Set<String> PARSER_IMPLEMENTATION_KEYS = Set.of(
            "backend",
            "detail",
            "fullGrammarNative",
            "fullGrammarProfile",
            "fullGram" + "merNative",
            "fullGram" + "merProfile",
            "parser",
            "parserBackend",
            "parserClass",
            "parserMode",
            "parserName",
            "parserProfile",
            "profile",
            "profileId",
            "resultName",
            "tokenEventNative");
    private static final Comparator<String> CODE_POINT_ORDER =
            ExternalCanonicalJsonFingerprinter::compareCodePoints;
    private static final JsonFactory JSON = JsonFactory.builder().build();
    private static final int VALUE_MEMORY_LIMIT = 1024 * 1024;

    private final Path workspace;
    private final int fieldsPerChunk;

    ExternalCanonicalJsonFingerprinter(Path workspace, int fieldsPerChunk) {
        if (workspace == null || fieldsPerChunk < 1) {
            throw new IllegalArgumentException("fingerprint workspace and positive chunk size are required");
        }
        this.workspace = workspace;
        this.fieldsPerChunk = fieldsPerChunk;
    }

    String fingerprint(Path input, CanonicalFingerprintMode mode) {
        if (input == null || mode == null) {
            throw new IllegalArgumentException("fingerprint input and mode are required");
        }
        deleteRecursively(workspace);
        try {
            Files.createDirectories(workspace);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (JsonParser parser = JSON.createParser(input.toFile());
                    DigestOutputStream output =
                            new DigestOutputStream(OutputStream.nullOutputStream(), digest)) {
                JsonToken token = parser.nextToken();
                if (token == null) {
                    throw new ReleaseVerificationException("JSON fingerprint input is empty");
                }
                writeValue(parser, token, output, mode, 0);
                if (parser.nextToken() != null) {
                    throw new ReleaseVerificationException("JSON fingerprint input has trailing content");
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (ReleaseVerificationException error) {
            throw error;
        } catch (IOException | NoSuchAlgorithmException error) {
            throw new ReleaseVerificationException("failed to fingerprint JSON input", error);
        } finally {
            deleteRecursively(workspace);
        }
    }

    private void writeValue(
            JsonParser parser,
            JsonToken token,
            OutputStream output,
            CanonicalFingerprintMode mode,
            int objectDepth
    ) throws IOException {
        switch (token) {
            case START_OBJECT -> writeObject(parser, output, mode, objectDepth);
            case START_ARRAY -> writeArray(parser, output, mode, objectDepth);
            case VALUE_STRING -> writeQuoted(output, parser.getText());
            case VALUE_NUMBER_INT -> writeAscii(output, new BigInteger(parser.getText()).toString());
            case VALUE_NUMBER_FLOAT -> writeAscii(output, pythonFloat(parser.getText()));
            case VALUE_TRUE -> writeAscii(output, "true");
            case VALUE_FALSE -> writeAscii(output, "false");
            case VALUE_NULL -> writeAscii(output, "null");
            default -> throw new ReleaseVerificationException("unsupported JSON token: " + token);
        }
    }

    private void writeArray(
            JsonParser parser,
            OutputStream output,
            CanonicalFingerprintMode mode,
            int objectDepth
    ) throws IOException {
        output.write('[');
        boolean first = true;
        JsonToken token;
        while ((token = parser.nextToken()) != JsonToken.END_ARRAY) {
            if (token == null) {
                throw new ReleaseVerificationException("unterminated JSON array");
            }
            if (!first) {
                output.write(',');
            }
            writeValue(parser, token, output, mode, objectDepth);
            first = false;
        }
        output.write(']');
    }

    private void writeObject(
            JsonParser parser,
            OutputStream output,
            CanonicalFingerprintMode mode,
            int objectDepth
    ) throws IOException {
        Path level = workspace.resolve("object-" + objectDepth);
        ObjectWorkspace objectWorkspace = new ObjectWorkspace(level);
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
                if (filtered(key, mode)) {
                    parser.skipChildren();
                    continue;
                }
                long offset = spool.position();
                writeValue(parser, valueToken, spool, mode, objectDepth + 1);
                pending.add(new FieldRecord(key, offset, spool.position() - offset));
                if (pending.size() == fieldsPerChunk) {
                    chunks.add(writeChunk(objectWorkspace, chunks.size(), pending));
                    pending.clear();
                }
            }
            if (chunks.isEmpty()) {
                writeInMemoryObject(output, spool, pending);
            } else {
                if (!pending.isEmpty()) {
                    chunks.add(writeChunk(objectWorkspace, chunks.size(), pending));
                }
                writeSortedObject(output, spool, chunks);
            }
        }
    }

    private Path writeChunk(
            ObjectWorkspace workspace,
            int index,
            List<FieldRecord> records
    ) throws IOException {
        records.sort(Comparator.comparing(FieldRecord::key, CODE_POINT_ORDER));
        workspace.prepare();
        Path chunk = workspace.directory().resolve("fields-" + index + ".bin");
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
            List<FieldRecord> records
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
            writeQuoted(output, record.key());
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
            List<Path> chunks
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
                writeQuoted(output, record.key());
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

    private boolean filtered(String key, CanonicalFingerprintMode mode) {
        return VOLATILE_KEYS.contains(key)
                || mode == CanonicalFingerprintMode.SEMANTIC && PARSER_IMPLEMENTATION_KEYS.contains(key);
    }

    private String pythonFloat(String raw) {
        double value;
        try {
            value = Double.parseDouble(raw);
        } catch (NumberFormatException error) {
            throw new ReleaseVerificationException("invalid JSON number", error);
        }
        if (!Double.isFinite(value)) {
            throw new ReleaseVerificationException("non-finite JSON numbers are not supported");
        }
        String java = Double.toString(value);
        int exponentMarker = Math.max(java.indexOf('E'), java.indexOf('e'));
        if (exponentMarker < 0) {
            return java;
        }
        int exponent = Integer.parseInt(java.substring(exponentMarker + 1));
        if (exponent >= -4 && exponent < 16) {
            String plain = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
            return exponent >= 0 && plain.indexOf('.') < 0 ? plain + ".0" : plain;
        }
        String mantissa = java.substring(0, exponentMarker);
        if (mantissa.endsWith(".0")) {
            mantissa = mantissa.substring(0, mantissa.length() - 2);
        }
        String digits = Integer.toString(Math.abs(exponent));
        if (digits.length() < 2) {
            digits = "0" + digits;
        }
        return mantissa + "e" + (exponent >= 0 ? "+" : "-") + digits;
    }

    private void writeQuoted(OutputStream output, String value) throws IOException {
        output.write('"');
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            int width = Character.charCount(codePoint);
            if (width == 1 && Character.isSurrogate(value.charAt(offset))) {
                throw new ReleaseVerificationException("unpaired surrogate in JSON string");
            }
            switch (codePoint) {
                case '"' -> writeAscii(output, "\\\"");
                case '\\' -> writeAscii(output, "\\\\");
                case '\b' -> writeAscii(output, "\\b");
                case '\f' -> writeAscii(output, "\\f");
                case '\n' -> writeAscii(output, "\\n");
                case '\r' -> writeAscii(output, "\\r");
                case '\t' -> writeAscii(output, "\\t");
                default -> {
                    if (codePoint < 0x20) {
                        writeAscii(output, String.format("\\u%04x", codePoint));
                    } else {
                        output.write(new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8));
                    }
                }
            }
            offset += width;
        }
        output.write('"');
    }

    private void writeAscii(OutputStream output, String value) throws IOException {
        output.write(value.getBytes(StandardCharsets.US_ASCII));
    }

    private void deleteRecursively(Path root) {
        if (root == null || Files.notExists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(this::delete);
        } catch (IOException error) {
            throw new ReleaseVerificationException("failed to clean fingerprint workspace", error);
        }
    }

    private void delete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (DirectoryNotEmptyException error) {
            throw new ReleaseVerificationException("fingerprint workspace is not empty", error);
        } catch (IOException error) {
            throw new ReleaseVerificationException("failed to clean fingerprint workspace", error);
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

    private final class ObjectWorkspace {
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
}
