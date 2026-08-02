package com.relationdetector.semantic.internal.store;

import com.relationdetector.semantic.ingest.ScanResultContractException;

import java.io.BufferedReader;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Consumer;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * CN: 以稳定key在磁盘上收集、外排并严格去重JSON记录，并用小型key到byte-offset索引执行有界随机
 * 查找；输入每次仅保留一条记录，输出可逐条迭代或直接写入JsonGenerator，同ID不同内容明确失败，
 * 本类不解释任何semantic section含义。
 * EN: Collects, externally sorts, and strictly deduplicates JSON records by stable key on disk. It retains one
 * record at a time, uses a compact key-to-byte-offset index for bounded random lookup, and can iterate or stream
 * records to a JsonGenerator. Conflicting content for one key fails, while section-specific semantics remain outside
 * this storage primitive.
 */
public final class ExternalJsonRecordStore implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int LINE_SCAN_BUFFER_BYTES = 8 * 1024;
    private final Path workspace;
    private final Path raw;
    private final OutputStream output;
    private final BinaryOperator<JsonNode> conflictMerger;
    private Path sorted;
    private Path offsetIndex;
    private long count;
    private boolean closed;

    public ExternalJsonRecordStore(Path workspace) {
        this(workspace, null);
    }

    public ExternalJsonRecordStore(Path workspace, BinaryOperator<JsonNode> conflictMerger) {
        if (workspace == null) {
            throw new IllegalArgumentException("external JSON record workspace is required");
        }
        this.workspace = workspace;
        this.conflictMerger = conflictMerger;
        try {
            Files.createDirectories(workspace);
            this.raw = workspace.resolve("records.raw");
            this.output = new BufferedOutputStream(Files.newOutputStream(
                    raw, StandardOpenOption.CREATE_NEW));
        } catch (IOException failure) {
            throw new ScanResultContractException("failed to create external JSON record store", failure);
        }
    }

    public void append(String key, JsonNode value) {
        ensureWritable();
        if (key == null || key.isBlank() || value == null) {
            throw new IllegalArgumentException("external JSON record key and value are required");
        }
        try {
            writeAscii(output, encode(key));
            output.write('\t');
            output.write('-');
            output.write('\t');
            try (OutputStream encoded = Base64.getUrlEncoder().withoutPadding()
                    .wrap(new NonClosingOutputStream(output))) {
                JSON.writeValue(encoded, value);
            }
            output.write('\n');
        } catch (IOException failure) {
            throw new ScanResultContractException("failed to append external JSON record", failure);
        }
    }

    public void finish() {
        ensureOpen();
        if (sorted != null) {
            return;
        }
        try {
            output.close();
            Path ordered = workspace.resolve("records.ordered");
            new ExternalLineSorter().sort(raw, ordered, workspace.resolve("sort-work"));
            sorted = workspace.resolve("records.unique");
            offsetIndex = workspace.resolve("records.offsets");
            Files.createFile(sorted);
            long unique = 0;
            try (BufferedReader reader = Files.newBufferedReader(ordered, StandardCharsets.UTF_8);
                 RandomAccessFile output = new RandomAccessFile(sorted.toFile(), "rw");
                 BufferedWriter offsetOutput = Files.newBufferedWriter(
                         offsetIndex, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
                String currentKey = null;
                String currentHash = null;
                String currentPayload = null;
                String line;
                while ((line = reader.readLine()) != null) {
                    int first = line.indexOf('\t');
                    int second = line.indexOf('\t', first + 1);
                    if (first <= 0 || second <= first + 1 || second == line.length() - 1) {
                        throw new ScanResultContractException("external JSON record line is malformed");
                    }
                    String key = line.substring(0, first);
                    String hash = line.substring(first + 1, second);
                    String payload = line.substring(second + 1);
                    if (!key.equals(currentKey)) {
                        if (currentKey != null) {
                            writeUnique(output, offsetOutput, currentKey, currentHash, currentPayload);
                            unique++;
                        }
                        currentKey = key;
                        currentHash = hash;
                        currentPayload = payload;
                        continue;
                    }
                    if (payload.equals(currentPayload)) {
                        continue;
                    }
                    JsonNode currentValue = JSON.readTree(decode(currentPayload));
                    JsonNode candidateValue = JSON.readTree(decode(payload));
                    if (currentValue.equals(candidateValue)) {
                        continue;
                    }
                    if (conflictMerger == null) {
                        throw new ScanResultContractException(
                                "conflicting semantic record id: " + decode(key));
                    }
                    JsonNode merged = conflictMerger.apply(
                            currentValue,
                            candidateValue);
                    if (merged == null) {
                        throw new ScanResultContractException(
                                "semantic record merger returned null for id: " + decode(key));
                    }
                    String mergedJson = JSON.writeValueAsString(merged);
                    currentPayload = encode(mergedJson);
                    currentHash = "-";
                }
                if (currentKey != null) {
                    writeUnique(output, offsetOutput, currentKey, currentHash, currentPayload);
                    unique++;
                }
            }
            count = unique;
        } catch (IOException failure) {
            throw new ScanResultContractException("failed to finish external JSON record store", failure);
        }
    }

    private void writeUnique(
            RandomAccessFile output,
            BufferedWriter offsets,
            String key,
            String hash,
            String payload
    ) throws IOException {
        long offset = output.getFilePointer();
        writeAscii(output, key);
        output.write('\t');
        writeAscii(output, hash);
        output.write('\t');
        writeAscii(output, payload);
        output.write('\n');
        offsets.write(key);
        offsets.write('\t');
        offsets.write(Long.toString(offset));
        offsets.newLine();
    }

    private void writeAscii(RandomAccessFile output, String value) throws IOException {
        output.write(value.getBytes(StandardCharsets.US_ASCII));
    }

    private void writeAscii(OutputStream output, String value) throws IOException {
        output.write(value.getBytes(StandardCharsets.US_ASCII));
    }

    public long count() {
        finish();
        return count;
    }

    public boolean containsKey(String key) {
        finish();
        if (key == null || key.isBlank()) {
            return false;
        }
        return findOffset(encode(key)).isPresent();
    }

    public Optional<Record> get(String key) {
        finish();
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        String encoded = encode(key);
        OptionalLong offset = findOffset(encoded);
        if (offset.isEmpty()) {
            return Optional.empty();
        }
        try (RandomAccessFile file = new RandomAccessFile(sorted.toFile(), "r")) {
            StoredLine line = readStoredLine(
                    file, offset.getAsLong(), new byte[LINE_SCAN_BUFFER_BYTES]);
            if (!line.key().equals(encoded)) {
                throw new ScanResultContractException("external JSON record offset index is inconsistent");
            }
            return Optional.of(new Record(key, JSON.readTree(decode(line.payload()))));
        } catch (IOException failure) {
            throw new ScanResultContractException("failed to read external JSON record", failure);
        }
    }

    public void forEach(Consumer<Record> consumer) {
        if (consumer == null) {
            throw new IllegalArgumentException("external JSON record consumer is required");
        }
        finish();
        try (BufferedReader reader = Files.newBufferedReader(sorted, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                int first = line.indexOf('\t');
                int second = line.indexOf('\t', first + 1);
                consumer.accept(new Record(
                        decode(line.substring(0, first)),
                        JSON.readTree(decode(line.substring(second + 1)))));
            }
        } catch (IOException failure) {
            throw new ScanResultContractException("failed to iterate external JSON records", failure);
        }
    }

    public void forEachDescriptor(BiConsumer<String, Long> consumer) {
        if (consumer == null) {
            throw new IllegalArgumentException("external JSON record descriptor consumer is required");
        }
        finish();
        try (BufferedReader reader = Files.newBufferedReader(sorted, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                int first = line.indexOf('\t');
                int second = line.indexOf('\t', first + 1);
                String payload = line.substring(second + 1);
                consumer.accept(
                        decode(line.substring(0, first)),
                        decodedLength(payload));
            }
        } catch (IOException failure) {
            throw new ScanResultContractException("failed to iterate external JSON record descriptors", failure);
        }
    }

    public void writeArray(JsonGenerator generator, String field) throws IOException {
        if (generator == null || field == null || field.isBlank()) {
            throw new IllegalArgumentException("JSON generator and field are required");
        }
        finish();
        generator.writeArrayFieldStart(field);
        try (BufferedReader reader = Files.newBufferedReader(sorted, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                int second = line.indexOf('\t', line.indexOf('\t') + 1);
                try (var parser = JSON.getFactory().createParser(
                        decode(line.substring(second + 1)))) {
                    parser.nextToken();
                    generator.copyCurrentStructure(parser);
                }
            }
        }
        generator.writeEndArray();
    }

    private void ensureWritable() {
        ensureOpen();
        if (sorted != null) {
            throw new IllegalStateException("external JSON record store is already finished");
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("external JSON record store is closed");
        }
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static long decodedLength(String value) {
        return (long) value.length() * 3L / 4L;
    }

    private long lineStart(RandomAccessFile file, long offset, byte[] buffer) throws IOException {
        if (offset <= 0) {
            return 0;
        }
        long position = Math.min(offset, file.length());
        while (position > 0) {
            long blockStart = Math.max(0, position - buffer.length);
            int length = Math.toIntExact(position - blockStart);
            file.seek(blockStart);
            file.readFully(buffer, 0, length);
            for (int index = length - 1; index >= 0; index--) {
                if (buffer[index] == '\n') {
                    return blockStart + index + 1L;
                }
            }
            position = blockStart;
        }
        return 0;
    }

    private OptionalLong findOffset(String encodedKey) {
        try (RandomAccessFile file = new RandomAccessFile(offsetIndex.toFile(), "r")) {
            long low = 0;
            long high = file.length();
            byte[] lineScanBuffer = new byte[LINE_SCAN_BUFFER_BYTES];
            while (low < high) {
                long middle = (low + high) >>> 1;
                long start = lineStart(file, middle, lineScanBuffer);
                OffsetLine line = readOffsetLine(file, start, lineScanBuffer);
                int comparison = line.key().compareTo(encodedKey);
                if (comparison < 0) {
                    low = Math.max(start + 1, line.next());
                } else if (comparison > 0) {
                    high = start;
                } else {
                    return OptionalLong.of(line.offset());
                }
            }
            return OptionalLong.empty();
        } catch (IOException failure) {
            throw new ScanResultContractException("failed to query external JSON record offset", failure);
        }
    }

    private OffsetLine readOffsetLine(
            RandomAccessFile file,
            long start,
            byte[] buffer
    ) throws IOException {
        AsciiLine asciiLine = readAsciiLine(file, start, buffer);
        String line = asciiLine.value();
        int split = line.indexOf('\t');
        if (split <= 0 || split == line.length() - 1) {
            throw new ScanResultContractException("external JSON record offset line is malformed");
        }
        try {
            long offset = Long.parseLong(line.substring(split + 1));
            if (offset < 0) {
                throw new NumberFormatException("negative offset");
            }
            return new OffsetLine(line.substring(0, split), offset, asciiLine.next());
        } catch (NumberFormatException failure) {
            throw new ScanResultContractException("external JSON record offset is invalid");
        }
    }

    private AsciiLine readAsciiLine(
            RandomAccessFile file,
            long start,
            byte[] buffer
    ) throws IOException {
        file.seek(start);
        ByteArrayOutputStream lineBytes = new ByteArrayOutputStream();
        long next = start;
        boolean found = false;
        int read;
        while ((read = file.read(buffer)) >= 0) {
            int length = read;
            for (int index = 0; index < read; index++) {
                if (buffer[index] == '\n') {
                    length = index;
                    next += index + 1L;
                    found = true;
                    break;
                }
            }
            lineBytes.write(buffer, 0, length);
            if (found) {
                break;
            }
            next += read;
        }
        if (lineBytes.size() == 0 && !found) {
            throw new ScanResultContractException("external JSON record offset index is truncated");
        }
        return new AsciiLine(lineBytes.toString(StandardCharsets.US_ASCII), next);
    }

    private StoredLine readStoredLine(
            RandomAccessFile file,
            long start,
            byte[] buffer
    ) throws IOException {
        String line = readAsciiLine(file, start, buffer).value();
        int first = line.indexOf('\t');
        int second = line.indexOf('\t', first + 1);
        if (first <= 0 || second <= first + 1 || second == line.length() - 1) {
            throw new ScanResultContractException("external JSON record line is malformed");
        }
        return new StoredLine(
                line.substring(0, first),
                line.substring(second + 1));
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            if (sorted == null) {
                output.close();
            }
        } catch (IOException failure) {
            throw new IllegalStateException("failed to close external JSON record store", failure);
        }
    }

    public record Record(String key, JsonNode value) {
        public Record {
            if (key == null || key.isBlank() || value == null) {
                throw new IllegalArgumentException("external JSON record is incomplete");
            }
            value = value.deepCopy();
        }

        @Override
        public JsonNode value() {
            return value.deepCopy();
        }
    }

    private record OffsetLine(String key, long offset, long next) {
    }

    private record AsciiLine(String value, long next) {
    }

    private record StoredLine(String key, String payload) {
    }

    private static final class NonClosingOutputStream extends FilterOutputStream {
        private NonClosingOutputStream(OutputStream delegate) {
            super(delegate);
        }

        @Override
        public void close() {
            // CN: Base64 wrapper已写完尾字节；共享raw buffer只由store按块flush。
            // EN: Base64 has completed its tail bytes; the store alone flushes the shared raw buffer in blocks.
        }
    }
}
