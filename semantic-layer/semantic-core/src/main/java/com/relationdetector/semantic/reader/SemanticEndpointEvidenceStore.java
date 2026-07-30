package com.relationdetector.semantic.reader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.List;
import java.util.TreeSet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * CN: 将endpoint与evidence ref配对记录外排排序、去重，并在每个endpoint只装配一次最终引用列表；
 * 上游是磁盘KG构建器，下游是endpoint node生成，本类不解释evidence内容或保留全图内存索引。
 * EN: Externally sorts and deduplicates endpoint-to-evidence pairs, assembling each endpoint's final reference list
 * exactly once. It serves the disk-backed KG builder without interpreting evidence or retaining a whole-graph index.
 */
final class SemanticEndpointEvidenceStore implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Path workspace;
    private final Path raw;
    private final BufferedWriter writer;
    private ExternalJsonRecordStore records;
    private boolean finished;
    private boolean closed;

    SemanticEndpointEvidenceStore(Path workspace) {
        if (workspace == null) {
            throw new IllegalArgumentException("semantic endpoint evidence workspace is required");
        }
        this.workspace = workspace;
        try {
            Files.createDirectories(workspace);
            raw = workspace.resolve("pairs.raw");
            writer = Files.newBufferedWriter(
                    raw, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        } catch (IOException failure) {
            throw new ScanResultContractException(
                    "failed to create semantic endpoint evidence store", failure);
        }
    }

    void append(String endpoint, List<String> evidenceRefs) {
        ensureWritable();
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("semantic endpoint is required");
        }
        try {
            for (String reference : evidenceRefs == null ? List.<String>of() : evidenceRefs) {
                if (reference == null || reference.isBlank()) {
                    throw new ScanResultContractException(
                            "semantic endpoint evidence reference is invalid");
                }
                writer.write(encode(endpoint));
                writer.write('\t');
                writer.write(encode(reference));
                writer.newLine();
            }
        } catch (IOException failure) {
            throw new ScanResultContractException(
                    "failed to spool semantic endpoint evidence", failure);
        }
    }

    void finish() {
        ensureOpen();
        if (finished) {
            return;
        }
        try {
            writer.close();
            Path sorted = workspace.resolve("pairs.sorted");
            new ExternalLineSorter().sort(raw, sorted, workspace.resolve("sort-work"));
            records = new ExternalJsonRecordStore(workspace.resolve("records"));
            try (BufferedReader reader = Files.newBufferedReader(sorted, StandardCharsets.UTF_8)) {
                String currentEndpoint = null;
                TreeSet<String> references = new TreeSet<>();
                String previous = null;
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.equals(previous)) {
                        continue;
                    }
                    int split = line.indexOf('\t');
                    if (split <= 0 || split == line.length() - 1) {
                        throw new ScanResultContractException(
                                "semantic endpoint evidence pair is malformed");
                    }
                    String endpoint = decode(line.substring(0, split));
                    if (!endpoint.equals(currentEndpoint)) {
                        appendGroup(currentEndpoint, references);
                        currentEndpoint = endpoint;
                        references.clear();
                    }
                    references.add(decode(line.substring(split + 1)));
                    previous = line;
                }
                appendGroup(currentEndpoint, references);
            }
            records.finish();
            finished = true;
        } catch (IOException failure) {
            throw new ScanResultContractException(
                    "failed to finish semantic endpoint evidence store", failure);
        }
    }

    List<String> evidence(String endpoint) {
        finish();
        if (endpoint == null || endpoint.isBlank()) {
            return List.of();
        }
        return records.get(endpoint)
                .map(record -> {
                    java.util.ArrayList<String> result = new java.util.ArrayList<>();
                    record.value().path("refs").forEach(value -> result.add(value.asText()));
                    return List.copyOf(result);
                })
                .orElse(List.of());
    }

    private void appendGroup(String endpoint, TreeSet<String> references) {
        if (endpoint == null) {
            return;
        }
        ObjectNode value = JSON.createObjectNode();
        references.forEach(value.putArray("refs")::add);
        records.append(endpoint, value);
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private void ensureWritable() {
        ensureOpen();
        if (finished) {
            throw new IllegalStateException("semantic endpoint evidence store is finished");
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("semantic endpoint evidence store is closed");
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        RuntimeException failure = null;
        if (!finished) {
            try {
                writer.close();
            } catch (IOException error) {
                failure = new IllegalStateException(
                        "failed to close semantic endpoint evidence spool", error);
            }
        }
        if (records != null) {
            try {
                records.close();
            } catch (RuntimeException error) {
                failure = error;
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
