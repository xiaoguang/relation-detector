package com.relationdetector.semantic.extraction.artifact;

import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.StableSemanticId;
import com.relationdetector.semantic.extraction.normalization.SemanticExtractionValidationException;
import com.relationdetector.semantic.extraction.prompt.SemanticTokenEstimateBudget;
import com.relationdetector.semantic.internal.store.ExternalJsonRecordStore;

/**
 * CN: 从受限 sidecar 构建 path-backed 可逆 projection 索引，并且每次只还原一个有界记录，避免将完整引用集合物化到内存。
 * EN: Builds a path-backed reversible projection index from a bounded sidecar and restores one bounded record at a
 * time so the complete reference set is never materialized in memory.
 */
final class SemanticProjectionStore implements AutoCloseable {
    private static final String HEADER = "#semantic-external-audit-refs-v2";
    private static final List<Field> FIELDS = List.of(
            new Field("evidenceRefs", "evidenceRefCount"),
            new Field("lineageRefs", "lineageRefCount"),
            new Field("supportingDerivedLineageRefs", "supportingDerivedLineageRefCount"),
            new Field("relationshipRefs", "relationshipRefCount"));
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ExternalJsonRecordStore projections;
    private final ExternalJsonRecordStore externalReferenceRecords;
    private final SemanticExternalAuditReferences.Snapshot externalReferences;

    private SemanticProjectionStore(
            ExternalJsonRecordStore projections,
            ExternalJsonRecordStore externalReferenceRecords,
            SemanticExternalAuditReferences.Snapshot externalReferences
    ) {
        this.projections = projections;
        this.externalReferenceRecords = externalReferenceRecords;
        this.externalReferences = externalReferences;
    }

    static SemanticProjectionStore open(
            Path sidecar,
            Path workspace,
            SemanticRequestPackageLimits limits
    ) {
        if (sidecar == null || workspace == null || limits == null) {
            throw new IllegalArgumentException("semantic projection store inputs are required");
        }
        ExternalJsonRecordStore records = null;
        ExternalJsonRecordStore externalRecords = null;
        try {
            if (!Files.isRegularFile(sidecar, LinkOption.NOFOLLOW_LINKS)
                    || Files.size(sidecar) > limits.maxSidecarBytes()) {
                throw invalid();
            }
            records = new ExternalJsonRecordStore(workspace);
            externalRecords = new ExternalJsonRecordStore(workspace.resolve("external"));
            Builder builder = new Builder(records, externalRecords, limits);
            SemanticBoundedLineReader.forEach(sidecar, limits.maxLineBytes(), builder::accept);
            builder.finish();
            records.finish();
            externalRecords.finish();
            if (records.count() != builder.projectionCount()) {
                throw invalid();
            }
            if (externalRecords.count() != builder.externalCount()) {
                throw invalid();
            }
            return new SemanticProjectionStore(
                    records, externalRecords, builder.externalSnapshot());
        } catch (Exception failure) {
            if (records != null) {
                try {
                    records.close();
                } catch (RuntimeException ignored) {
                    // Preserve one fixed package validation failure.
                }
            }
            if (externalRecords != null) {
                try {
                    externalRecords.close();
                } catch (RuntimeException ignored) {
                    // Preserve one fixed package validation failure.
                }
            }
            throw invalid();
        }
    }

    SemanticExternalAuditReferences.Snapshot externalReferences() {
        return externalReferences;
    }

    void forEachExternalReference(Consumer<String> consumer) {
        if (consumer == null) {
            throw new IllegalArgumentException("semantic external-reference consumer is required");
        }
        externalReferenceRecords.forEach(record -> consumer.accept(record.key()));
    }

    ObjectNode restore(JsonNode projected) {
        if (projected == null || !projected.isObject()) {
            throw invalid();
        }
        ObjectNode result = (ObjectNode) projected.deepCopy();
        String id = result.path("id").asText("");
        if (id.isBlank()) {
            throw invalid();
        }
        for (Field field : FIELDS) {
            JsonNode stored = projections.get(key(id, field.referenceField()))
                    .map(ExternalJsonRecordStore.Record::value)
                    .orElse(null);
            result.remove(field.countField());
            result.remove(field.referenceField() + "Sha256");
            if (stored != null) {
                result.set(field.referenceField(), stored.path("references").deepCopy());
            }
        }
        if (!StableSemanticId.canonicalJson(SemanticExternalAuditReferences.project(result))
                .equals(StableSemanticId.canonicalJson(projected))) {
            throw invalid();
        }
        return result;
    }

    @Override
    public void close() {
        RuntimeException failure = null;
        try {
            projections.close();
        } catch (RuntimeException error) {
            failure = error;
        }
        try {
            externalReferenceRecords.close();
        } catch (RuntimeException error) {
            if (failure == null) {
                failure = error;
            } else {
                failure.addSuppressed(error);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static String key(String id, String field) {
        return id + "\u0000" + field;
    }

    private static final class Builder {
        private final ExternalJsonRecordStore target;
        private final ExternalJsonRecordStore externalTarget;
        private final SemanticRequestPackageLimits limits;
        private final MessageDigest externalDigest = sha256();
        private long lineNumber;
        private long externalCount;
        private long projectionCount;
        private String previousExternal;
        private Pending pending;
        private boolean projectionFormat;
        private boolean projectionRecordsStarted;

        private Builder(
                ExternalJsonRecordStore target,
                ExternalJsonRecordStore externalTarget,
                SemanticRequestPackageLimits limits
        ) {
            this.target = target;
            this.externalTarget = externalTarget;
            this.limits = limits;
        }

        /**
         * CN: 按固定 v2 行状态机接收一行 sidecar，兼容旧 external-only 行；每个字段组在进入下一组前验证顺序、计数和摘要。
         * EN: Accepts one sidecar line through the fixed v2 state machine while retaining legacy external-only lines;
         * each field group validates ordering, count, and digest before the next group begins.
         */
        private void accept(String line) {
            lineNumber++;
            if (lineNumber == 1) {
                if (HEADER.equals(line)) {
                    projectionFormat = true;
                    return;
                }
                appendExternal(decode(line));
                return;
            }
            if (!projectionFormat) {
                appendExternal(decode(line));
                return;
            }
            String[] fields = line.split("\\t", -1);
            if (fields.length == 2 && "E".equals(fields[0]) && !projectionRecordsStarted) {
                appendExternal(decode(fields[1]));
                return;
            }
            projectionRecordsStarted = true;
            if (fields.length == 5 && "F".equals(fields[0])) {
                flushPending();
                String id = decode(fields[1]);
                String field = decode(fields[2]);
                int count = parseCount(fields[3]);
                if (!knownField(field) || !fields[4].matches("[0-9a-f]{64}")) {
                    throw invalid();
                }
                pending = new Pending(id, field, count, fields[4], limits);
                return;
            }
            if (fields.length == 5 && "R".equals(fields[0]) && pending != null) {
                String id = decode(fields[1]);
                String field = decode(fields[2]);
                int ordinal = parseCount(fields[3]);
                if (!pending.id.equals(id) || !pending.field.equals(field)) {
                    throw invalid();
                }
                pending.append(ordinal, decode(fields[4]));
                return;
            }
            throw invalid();
        }

        private void finish() {
            flushPending();
        }

        private void flushPending() {
            if (pending == null) {
                return;
            }
            ObjectNode record = pending.finish();
            target.append(key(pending.id, pending.field), record);
            projectionCount++;
            pending = null;
        }

        private void appendExternal(String reference) {
            if (reference.isBlank()
                    || reference.codePointCount(0, reference.length()) > limits.maxStringCodePoints()
                    || previousExternal != null && previousExternal.compareTo(reference) >= 0) {
                throw invalid();
            }
            byte[] bytes = reference.getBytes(StandardCharsets.UTF_8);
            externalDigest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
            externalDigest.update(bytes);
            previousExternal = reference;
            externalCount++;
            externalTarget.append(reference, JSON.createObjectNode());
        }

        private SemanticExternalAuditReferences.Snapshot externalSnapshot() {
            if (externalCount > Integer.MAX_VALUE) {
                throw invalid();
            }
            return new SemanticExternalAuditReferences.Snapshot(
                    (int) externalCount,
                    HexFormat.of().formatHex(externalDigest.digest()));
        }

        private long projectionCount() {
            return projectionCount;
        }

        private long externalCount() {
            return externalCount;
        }

        private int parseCount(String value) {
            try {
                int count = Integer.parseInt(value);
                if (count < 0 || count > limits.maxEstimatedTokensPerShardOrRecord()) {
                    throw invalid();
                }
                return count;
            } catch (NumberFormatException failure) {
                throw invalid();
            }
        }

        private boolean knownField(String field) {
            return FIELDS.stream().anyMatch(value -> value.referenceField().equals(field));
        }
    }

    private static final class Pending {
        private final String id;
        private final String field;
        private final int count;
        private final String expectedSha256;
        private final ArrayNode references = JSON.createArrayNode();
        private final SemanticTokenEstimateBudget budget;
        private final SemanticRequestPackageLimits limits;

        private Pending(
                String id,
                String field,
                int count,
                String expectedSha256,
                SemanticRequestPackageLimits limits
        ) {
            this.id = id;
            this.field = field;
            this.count = count;
            this.expectedSha256 = expectedSha256;
            this.limits = limits;
            this.budget = new SemanticTokenEstimateBudget(
                    limits.maxEstimatedTokensPerShardOrRecord());
            add(id);
            add(field);
        }

        private void append(int ordinal, String reference) {
            if (ordinal != references.size() || reference.isBlank()) {
                throw invalid();
            }
            add(reference);
            references.add(reference);
        }

        private ObjectNode finish() {
            if (references.size() != count) {
                throw invalid();
            }
            budget.requireCompleteCodePoint("semantic projection record");
            SemanticExternalAuditReferences.Snapshot actual =
                    SemanticExternalAuditReferences.snapshot(textValues(references));
            if (!actual.sha256().equals(expectedSha256)) {
                throw invalid();
            }
            ObjectNode result = JSON.createObjectNode();
            result.set("references", references);
            return result;
        }

        private void add(String value) {
            if (value.codePointCount(0, value.length()) > limits.maxStringCodePoints()) {
                throw invalid();
            }
            budget.addText(value);
        }

        private List<String> textValues(ArrayNode values) {
            java.util.ArrayList<String> result = new java.util.ArrayList<>(values.size());
            values.forEach(value -> result.add(value.asText()));
            return result;
        }
    }

    private static String decode(String value) {
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(value);
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (Exception failure) {
            throw invalid();
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    private static SemanticExtractionValidationException invalid() {
        return new SemanticExtractionValidationException(
                "semantic request bundle package is invalid");
    }

    private record Field(String referenceField, String countField) {
    }
}
