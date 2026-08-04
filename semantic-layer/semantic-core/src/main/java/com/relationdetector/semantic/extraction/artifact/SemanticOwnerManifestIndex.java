package com.relationdetector.semantic.extraction.artifact;

import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.extraction.normalization.SemanticExtractionValidationException;
import com.relationdetector.semantic.internal.store.ExternalJsonRecordStore;

/**
 * CN: 为 live 与重建验证提供共享的磁盘型唯一 owner-manifest 查询；输入逐行受限，重复或非法 owner 不能进入索引。
 * EN: Provides a shared disk-backed unique owner-manifest lookup for live and reconstructed validation; bounded
 * line input is required, and duplicate or invalid owners never enter the index.
 */
public final class SemanticOwnerManifestIndex implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final ExternalJsonRecordStore records;
    private final long count;

    private SemanticOwnerManifestIndex(ExternalJsonRecordStore records, long count) {
        this.records = records;
        this.count = count;
    }

    public static SemanticOwnerManifestIndex open(
            Path manifest,
            Path workspace,
            SemanticRequestPackageLimits limits
    ) {
        if (manifest == null || workspace == null || limits == null) {
            throw new IllegalArgumentException("semantic owner manifest inputs are required");
        }
        ExternalJsonRecordStore records = null;
        try {
            if (!Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS)
                    || Files.size(manifest) > limits.maxOwnerManifestBytes()) {
                throw invalid();
            }
            records = new ExternalJsonRecordStore(workspace);
            ExternalJsonRecordStore target = records;
            long lines = SemanticBoundedLineReader.forEach(
                    manifest,
                    limits.maxLineBytes(),
                    line -> append(target, line, limits.maxStringCodePoints()));
            records.finish();
            if (records.count() != lines) {
                throw invalid();
            }
            return new SemanticOwnerManifestIndex(records, lines);
        } catch (Exception failure) {
            if (records != null) {
                try {
                    records.close();
                } catch (RuntimeException ignored) {
                    // The caller receives one fixed package validation failure.
                }
            }
            throw invalid();
        }
    }

    public Optional<Entry> find(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return records.get(id).map(record -> entry(record.value()));
    }

    public long count() {
        return count;
    }

    @Override
    public void close() {
        records.close();
    }

    static boolean isFactSection(String section) {
        return switch (section) {
            case "METADATA_TABLES", "METADATA_COLUMNS", "METADATA_CONSTRAINTS", "METADATA_INDEXES",
                    "RELATIONSHIPS", "LINEAGE", "DERIVED_RELATIONSHIPS", "DERIVED_LINEAGE",
                    "NAMING_EVIDENCE", "DIAGNOSTICS" -> true;
            case "EVENT_CANDIDATES", "REVIEW_ITEM_CANDIDATES", "TRIPLET_CANDIDATES" -> false;
            default -> throw invalid();
        };
    }

    static String manifestSection(String wireName) {
        return switch (wireName) {
            case "metadataTables" -> "METADATA_TABLES";
            case "metadataColumns" -> "METADATA_COLUMNS";
            case "metadataConstraints" -> "METADATA_CONSTRAINTS";
            case "metadataIndexes" -> "METADATA_INDEXES";
            case "relationships" -> "RELATIONSHIPS";
            case "lineage" -> "LINEAGE";
            case "derivedRelationships" -> "DERIVED_RELATIONSHIPS";
            case "derivedLineage" -> "DERIVED_LINEAGE";
            case "namingEvidence" -> "NAMING_EVIDENCE";
            case "diagnostics" -> "DIAGNOSTICS";
            case "eventCandidates" -> "EVENT_CANDIDATES";
            case "reviewItemCandidates" -> "REVIEW_ITEM_CANDIDATES";
            case "tripletCandidates" -> "TRIPLET_CANDIDATES";
            default -> throw invalid();
        };
    }

    private static void append(
            ExternalJsonRecordStore target,
            String line,
            int maximumStringCodePoints
    ) {
        String[] fields = line.split("\\t", -1);
        if (fields.length != 3 || fields[1].isBlank() || fields[2].isBlank()) {
            throw invalid();
        }
        String id = decode(fields[0]);
        if (id.isBlank()
                || id.codePointCount(0, id.length()) > maximumStringCodePoints
                || fields[2].codePointCount(0, fields[2].length()) > maximumStringCodePoints) {
            throw invalid();
        }
        boolean fact = isFactSection(fields[1]);
        ObjectNode value = JSON.createObjectNode()
                .put("section", fields[1])
                .put("owner", fields[2])
                .put("fact", fact);
        target.append(id, value);
    }

    private static Entry entry(JsonNode value) {
        String section = value.path("section").asText("");
        String owner = value.path("owner").asText("");
        if (section.isBlank() || owner.isBlank() || !value.path("fact").isBoolean()) {
            throw invalid();
        }
        return new Entry(section, owner, value.path("fact").booleanValue());
    }

    private static String decode(String value) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(value);
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(decoded))
                    .toString();
        } catch (Exception failure) {
            throw invalid();
        }
    }

    private static SemanticExtractionValidationException invalid() {
        return new SemanticExtractionValidationException(
                "semantic request bundle package is invalid");
    }

    public record Entry(String section, String ownerShardId, boolean fact) {
    }
}
