package com.relationdetector.semantic.reader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.event.SemanticEventCandidate;
import com.relationdetector.semantic.extract.SemanticShardingException;
import com.relationdetector.semantic.model.PhysicalEndpointRef;

/**
 * CN: 将 event typed匹配键与relationship/derived-lineage匹配键分别落盘，经外排排序归并生成稳定、
 * 去重的event关联游标；输入只保留当前记录，输出可按event读取引用，本类不推断SQL结构或持有全图。
 * EN: Spools typed event and relationship/derived-lineage match keys separately, then external-sort joins them into
 * stable deduplicated per-event association cursors. It retains one record at a time and neither infers SQL structure
 * nor holds the complete graph.
 */
final class SemanticEventAssociationStore implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String RELATIONSHIP = "R";
    private static final String DERIVED_LINEAGE = "D";

    private final Path workspace;
    private final Path eventKeys;
    private final Path factKeys;
    private final BufferedWriter eventWriter;
    private final BufferedWriter factWriter;
    private ExternalJsonRecordStore descriptors;
    private Path associations;
    private boolean finished;
    private boolean closed;

    SemanticEventAssociationStore(Path workspace) {
        if (workspace == null) {
            throw new IllegalArgumentException("semantic event association workspace is required");
        }
        this.workspace = workspace;
        try {
            Files.createDirectories(workspace);
            eventKeys = workspace.resolve("event-keys.raw");
            factKeys = workspace.resolve("fact-keys.raw");
            eventWriter = writer(eventKeys);
            factWriter = writer(factKeys);
        } catch (IOException failure) {
            throw new ScanResultContractException("failed to create semantic event association store", failure);
        }
    }

    void appendEvent(SemanticEventCandidate event) {
        ensureWritable();
        if (event == null) {
            throw new IllegalArgumentException("semantic event candidate is required");
        }
        try {
            for (String output : event.outputEndpoints().stream().distinct().toList()) {
                writeEventKey("R:E:" + output, event.id());
            }
            List<String> inputs = event.inputEndpoints().stream()
                    .map(this::tableOf).filter(value -> !value.isBlank()).distinct().toList();
            List<String> outputs = event.outputEndpoints().stream()
                    .map(this::tableOf).filter(value -> !value.isBlank()).distinct().toList();
            for (String input : inputs) {
                for (String output : outputs) {
                    writeEventKey("R:T:" + pair(input, output), event.id());
                }
            }
            java.util.LinkedHashSet<String> endpoints = new java.util.LinkedHashSet<>(event.inputEndpoints());
            endpoints.addAll(event.outputEndpoints());
            for (String endpoint : endpoints) {
                writeEventKey("L:E:" + endpoint, event.id());
                String table = tableOf(endpoint);
                if (!table.isBlank()) {
                    writeEventKey("L:T:" + table, event.id());
                }
            }
        } catch (IOException failure) {
            throw new ScanResultContractException("failed to spool semantic event match keys", failure);
        }
    }

    void appendRelationship(JsonNode relationship) {
        ensureWritable();
        String id = id(relationship, "relationship");
        String source = relationship.path("source").asText("");
        String target = relationship.path("target").asText("");
        try {
            if (!source.isBlank()) {
                writeFactKey("R:E:" + source, RELATIONSHIP, id);
            }
            if (!target.isBlank()) {
                writeFactKey("R:E:" + target, RELATIONSHIP, id);
            }
            String sourceTable = tableOf(source);
            String targetTable = tableOf(target);
            if (!sourceTable.isBlank() && !targetTable.isBlank()) {
                writeFactKey("R:T:" + pair(sourceTable, targetTable), RELATIONSHIP, id);
            }
        } catch (IOException failure) {
            throw new ScanResultContractException("failed to spool relationship match keys", failure);
        }
    }

    void appendDerivedLineage(JsonNode lineage) {
        ensureWritable();
        String id = id(lineage, "derived lineage");
        java.util.LinkedHashSet<String> endpoints = new java.util.LinkedHashSet<>();
        lineage.path("sources").forEach(value -> {
            if (value.isTextual() && !value.asText().isBlank()) {
                endpoints.add(value.asText());
            }
        });
        String target = lineage.path("target").asText("");
        if (!target.isBlank()) {
            endpoints.add(target);
        }
        try {
            for (String endpoint : endpoints) {
                writeFactKey("L:E:" + endpoint, DERIVED_LINEAGE, id);
                String table = tableOf(endpoint);
                if (!table.isBlank()) {
                    writeFactKey("L:T:" + table, DERIVED_LINEAGE, id);
                }
            }
        } catch (IOException failure) {
            throw new ScanResultContractException("failed to spool derived-lineage match keys", failure);
        }
    }

    void finish() {
        ensureOpen();
        if (finished) {
            return;
        }
        try {
            eventWriter.close();
            factWriter.close();
            Path sortedEvents = workspace.resolve("event-keys.sorted");
            Path sortedFacts = workspace.resolve("fact-keys.sorted");
            ExternalLineSorter sorter = new ExternalLineSorter();
            sorter.sort(eventKeys, sortedEvents, workspace.resolve("event-sort"));
            sorter.sort(factKeys, sortedFacts, workspace.resolve("fact-sort"));
            Path rawAssociations = workspace.resolve("associations.raw");
            join(sortedEvents, sortedFacts, rawAssociations);
            Path sortedAssociations = workspace.resolve("associations.sorted");
            sorter.sort(rawAssociations, sortedAssociations, workspace.resolve("association-sort"));
            associations = workspace.resolve("associations.unique");
            descriptors = new ExternalJsonRecordStore(workspace.resolve("descriptors"));
            buildUniqueAssociations(sortedAssociations);
            descriptors.finish();
            finished = true;
        } catch (IOException failure) {
            throw new ScanResultContractException("failed to finish semantic event associations", failure);
        }
    }

    List<String> relationshipRefs(String eventId) {
        return references(eventId, RELATIONSHIP);
    }

    List<String> derivedLineageRefs(String eventId) {
        return references(eventId, DERIVED_LINEAGE);
    }

    long referenceCount(String eventId) {
        return descriptor(eventId).path("referenceCount").asLong(0);
    }

    long estimatedReferenceBytes(String eventId) {
        return descriptor(eventId).path("estimatedReferenceBytes").asLong(0);
    }

    void requireWithinEstimatedBudget(String eventId, long baseSerializedBytes, int maxInputTokens) {
        if (eventId == null || eventId.isBlank() || baseSerializedBytes < 0 || maxInputTokens <= 0) {
            throw new IllegalArgumentException("semantic event id, size and input budget are required");
        }
        long maximumBytes = (long) maxInputTokens * 3L;
        long associationBytes = estimatedReferenceBytes(eventId);
        if (baseSerializedBytes > maximumBytes
                || associationBytes > maximumBytes - baseSerializedBytes) {
            throw new SemanticShardingException(
                    "semantic event association closure exceeds maximum input budget");
        }
    }

    private void join(Path sortedEvents, Path sortedFacts, Path output) throws IOException {
        try (KeyCursor events = new KeyCursor(sortedEvents, 2);
             KeyCursor facts = new KeyCursor(sortedFacts, 3);
             BufferedWriter associationsWriter = writer(output)) {
            while (events.present() && facts.present()) {
                int comparison = events.key().compareTo(facts.key());
                if (comparison < 0) {
                    events.skipGroup();
                    continue;
                }
                if (comparison > 0) {
                    facts.skipGroup();
                    continue;
                }
                String key = events.key();
                Path eventGroup = workspace.resolve("current-event-group");
                try (BufferedWriter groupWriter = writer(eventGroup)) {
                    events.writeGroupValues(groupWriter, key, 1);
                }
                while (facts.present() && key.equals(facts.key())) {
                    String[] fact = facts.fields();
                    try (BufferedReader groupReader = Files.newBufferedReader(eventGroup, StandardCharsets.UTF_8)) {
                        String eventId;
                        while ((eventId = groupReader.readLine()) != null) {
                            associationsWriter.write(eventId);
                            associationsWriter.write('\t');
                            associationsWriter.write(fact[1]);
                            associationsWriter.write('\t');
                            associationsWriter.write(fact[2]);
                            associationsWriter.newLine();
                        }
                    }
                    facts.advance();
                }
                Files.deleteIfExists(eventGroup);
            }
        }
    }

    private void buildUniqueAssociations(Path sorted) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(sorted, StandardCharsets.UTF_8);
             RandomAccessFile output = new RandomAccessFile(associations.toFile(), "rw")) {
            String previousLine = null;
            String currentEvent = null;
            long eventOffset = 0;
            long referenceCount = 0;
            long estimatedBytes = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.equals(previousLine)) {
                    continue;
                }
                String[] fields = split(line, 3);
                if (!fields[0].equals(currentEvent)) {
                    if (currentEvent != null) {
                        appendDescriptor(currentEvent, eventOffset, referenceCount, estimatedBytes);
                    }
                    currentEvent = fields[0];
                    eventOffset = output.getFilePointer();
                    referenceCount = 0;
                    estimatedBytes = 0;
                }
                byte[] bytes = (line + "\n").getBytes(StandardCharsets.US_ASCII);
                output.write(bytes);
                referenceCount++;
                String reference = decode(fields[2]);
                // Each association is emitted in its typed ref list and again in evidenceRefs.
                estimatedBytes += 2L * (JSON.writeValueAsBytes(reference).length + 1L);
                previousLine = line;
            }
            if (currentEvent != null) {
                appendDescriptor(currentEvent, eventOffset, referenceCount, estimatedBytes);
            }
        }
    }

    private void appendDescriptor(String encodedEvent, long offset, long count, long estimatedBytes) {
        ObjectNode value = JSON.createObjectNode();
        value.put("offset", offset);
        value.put("referenceCount", count);
        value.put("estimatedReferenceBytes", estimatedBytes);
        descriptors.append(decode(encodedEvent), value);
    }

    private List<String> references(String eventId, String kind) {
        finish();
        JsonNode descriptor = descriptor(eventId);
        if (descriptor.isMissingNode()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        String encodedEvent = encode(eventId);
        try (RandomAccessFile file = new RandomAccessFile(associations.toFile(), "r")) {
            file.seek(descriptor.path("offset").asLong());
            String line;
            while ((line = file.readLine()) != null) {
                String[] fields = split(line, 3);
                if (!encodedEvent.equals(fields[0])) {
                    break;
                }
                if (kind.equals(fields[1])) {
                    result.add(decode(fields[2]));
                }
            }
            return List.copyOf(result);
        } catch (IOException failure) {
            throw new ScanResultContractException("failed to read semantic event associations", failure);
        }
    }

    private JsonNode descriptor(String eventId) {
        finish();
        return descriptors.get(eventId)
                .map(ExternalJsonRecordStore.Record::value)
                .orElseGet(JSON::missingNode);
    }

    private void writeEventKey(String key, String eventId) throws IOException {
        eventWriter.write(encode(key));
        eventWriter.write('\t');
        eventWriter.write(encode(eventId));
        eventWriter.newLine();
    }

    private void writeFactKey(String key, String kind, String factId) throws IOException {
        factWriter.write(encode(key));
        factWriter.write('\t');
        factWriter.write(kind);
        factWriter.write('\t');
        factWriter.write(encode(factId));
        factWriter.newLine();
    }

    private String pair(String left, String right) {
        return left.compareTo(right) <= 0 ? left + "\u0000" + right : right + "\u0000" + left;
    }

    private String tableOf(String endpoint) {
        return endpoint == null || endpoint.isBlank() ? "" : PhysicalEndpointRef.column(endpoint).table();
    }

    private String id(JsonNode value, String boundary) {
        String id = value == null ? "" : value.path("id").asText("");
        if (id.isBlank()) {
            throw new ScanResultContractException(boundary + " id is required for event association");
        }
        return id;
    }

    private BufferedWriter writer(Path path) throws IOException {
        return Files.newBufferedWriter(
                path, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }

    private String[] split(String line, int expected) {
        String[] fields = line.split("\\t", -1);
        if (fields.length != expected) {
            throw new ScanResultContractException("semantic event association record is malformed");
        }
        return fields;
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
            throw new IllegalStateException("semantic event association store is already finished");
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("semantic event association store is closed");
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            if (!finished) {
                eventWriter.close();
                factWriter.close();
            }
        } catch (IOException ignored) {
            // Cleanup below remains authoritative.
        }
        if (descriptors != null) {
            descriptors.close();
        }
        try {
            deleteRecursively(workspace);
        } catch (IOException failure) {
            throw new IllegalStateException("failed to clean semantic event association store", failure);
        }
    }

    private void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private final class KeyCursor implements AutoCloseable {
        private final BufferedReader reader;
        private final int fieldCount;
        private String line;

        private KeyCursor(Path path, int fieldCount) throws IOException {
            reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
            this.fieldCount = fieldCount;
            advance();
        }

        private boolean present() {
            return line != null;
        }

        private String key() {
            return fields()[0];
        }

        private String[] fields() {
            return split(line, fieldCount);
        }

        private void advance() throws IOException {
            line = reader.readLine();
        }

        private void skipGroup() throws IOException {
            String current = key();
            while (present() && current.equals(key())) {
                advance();
            }
        }

        private void writeGroupValues(BufferedWriter output, String group, int valueIndex) throws IOException {
            String previous = null;
            while (present() && group.equals(key())) {
                String value = fields()[valueIndex];
                if (!value.equals(previous)) {
                    output.write(value);
                    output.newLine();
                    previous = value;
                }
                advance();
            }
        }

        @Override
        public void close() throws IOException {
            reader.close();
        }
    }
}
