package com.relationdetector.semantic.reader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.function.Consumer;
import java.util.function.BinaryOperator;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relationdetector.semantic.StableSemanticId;

/**
 * CN: 以稳定key在磁盘上收集、外排并严格去重JSON记录；输入每次仅保留一条记录，输出可逐条迭代或直接
 * 写入JsonGenerator，同ID不同内容明确失败，本类不解释任何semantic section含义。
 * EN: Collects, externally sorts, and strictly deduplicates JSON records by stable key on disk. It retains one
 * record at a time and can iterate or stream records to a JsonGenerator. Conflicting content for one key fails,
 * while section-specific semantics remain outside this storage primitive.
 */
public final class ExternalJsonRecordStore implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Path workspace;
    private final Path raw;
    private final BufferedWriter writer;
    private final BinaryOperator<JsonNode> conflictMerger;
    private Path sorted;
    private SortedTextIndex keyIndex;
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
            this.writer = Files.newBufferedWriter(
                    raw, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
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
            String payload = JSON.writeValueAsString(value);
            writer.write(encode(key));
            writer.write('\t');
            writer.write(StableSemanticId.of("external-json-record", StableSemanticId.canonicalJson(value)));
            writer.write('\t');
            writer.write(encode(payload));
            writer.newLine();
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
            writer.close();
            Path ordered = workspace.resolve("records.ordered");
            new ExternalLineSorter().sort(raw, ordered, workspace.resolve("sort-work"));
            sorted = workspace.resolve("records.unique");
            Path keys = workspace.resolve("records.keys");
            long unique = 0;
            try (BufferedReader reader = Files.newBufferedReader(ordered, StandardCharsets.UTF_8);
                 BufferedWriter output = Files.newBufferedWriter(
                         sorted, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
                 BufferedWriter keyOutput = Files.newBufferedWriter(
                         keys, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
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
                            writeUnique(output, keyOutput, currentKey, currentHash, currentPayload);
                            unique++;
                        }
                        currentKey = key;
                        currentHash = hash;
                        currentPayload = payload;
                        continue;
                    }
                    if (hash.equals(currentHash)) {
                        continue;
                    }
                    if (conflictMerger == null) {
                        throw new ScanResultContractException(
                                "conflicting semantic record id: " + decode(key));
                    }
                    JsonNode merged = conflictMerger.apply(
                            JSON.readTree(decode(currentPayload)),
                            JSON.readTree(decode(payload)));
                    if (merged == null) {
                        throw new ScanResultContractException(
                                "semantic record merger returned null for id: " + decode(key));
                    }
                    String mergedJson = JSON.writeValueAsString(merged);
                    currentPayload = encode(mergedJson);
                    currentHash = StableSemanticId.of(
                            "external-json-record", StableSemanticId.canonicalJson(merged));
                }
                if (currentKey != null) {
                    writeUnique(output, keyOutput, currentKey, currentHash, currentPayload);
                    unique++;
                }
            }
            keyIndex = SortedTextIndex.openExisting(keys);
            count = unique;
        } catch (IOException failure) {
            throw new ScanResultContractException("failed to finish external JSON record store", failure);
        }
    }

    private void writeUnique(
            BufferedWriter output,
            BufferedWriter keys,
            String key,
            String hash,
            String payload
    ) throws IOException {
        output.write(key);
        output.write('\t');
        output.write(hash);
        output.write('\t');
        output.write(payload);
        output.newLine();
        keys.write(key);
        keys.newLine();
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
        try {
            return keyIndex.contains(encode(key));
        } catch (IOException failure) {
            throw new ScanResultContractException("failed to query external JSON record key", failure);
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

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            if (keyIndex != null) {
                keyIndex.close();
            }
            if (sorted == null) {
                writer.close();
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
}
