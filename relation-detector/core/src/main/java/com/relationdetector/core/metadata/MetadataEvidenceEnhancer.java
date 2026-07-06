package com.relationdetector.core.metadata;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.relationdetector.contracts.scoring.DefaultEvidenceScores;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.metadata.MetadataColumnFact;
import com.relationdetector.contracts.metadata.MetadataIndexFact;
import com.relationdetector.contracts.metadata.MetadataSnapshot;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.RelationSubType;
import com.relationdetector.contracts.Enums.RelationType;

/**
 * Adds catalog metadata evidence to already-discovered column relationships.
 *
 * <p>This class is intentionally an enhancer, not a relationship generator.
 * Indexes, unique keys, and compatible types make an existing SQL/DDL/profile
 * candidate more credible, but they are not enough to invent a FK-like edge by
 * themselves.
 */
public final class MetadataEvidenceEnhancer {
    public void enhance(List<RelationshipCandidate> candidates, MetadataSnapshot metadata) {
        if (candidates.isEmpty()) {
            return;
        }
        for (RelationshipCandidate candidate : candidates) {
            if (!candidate.source().isColumnLevel()
                    || !candidate.target().isColumnLevel()) {
                continue;
            }
            String sourceTable = candidate.source().table().tableName();
            String sourceColumn = candidate.source().column().columnName();
            String targetTable = candidate.target().table().tableName();
            String targetColumn = candidate.target().column().columnName();
            boolean sourceUnique = hasIndex(metadata.indexFacts(), sourceTable, sourceColumn, true);
            boolean targetUnique = hasIndex(metadata.indexFacts(), targetTable, targetColumn, true);
            boolean columnCoOccurrence = candidate.relationType() == RelationType.CO_OCCURRENCE
                    && candidate.relationSubType() == RelationSubType.COLUMN_CO_OCCURRENCE;
            if (hasIndex(metadata.indexFacts(), sourceTable, sourceColumn, false) && !sourceUnique) {
                addIfAbsent(candidate, EvidenceType.SOURCE_INDEX, DefaultEvidenceScores.SOURCE_INDEX,
                        "metadata source column is indexed",
                        Map.of("table", sourceTable, "column", sourceColumn,
                                "indexedEndpoint", endpoint(candidate, "source"), "endpointSide", "source"));
            }
            if (hasIndex(metadata.indexFacts(), targetTable, targetColumn, false) && !targetUnique
                    && columnCoOccurrence) {
                addIfAbsent(candidate, EvidenceType.SOURCE_INDEX, DefaultEvidenceScores.SOURCE_INDEX,
                        "metadata target-side non-unique column is indexed",
                        Map.of("table", targetTable, "column", targetColumn,
                                "indexedEndpoint", endpoint(candidate, "target"), "endpointSide", "target"));
            }
            if (targetUnique && (!columnCoOccurrence || !sourceUnique)) {
                addIfAbsent(candidate, EvidenceType.TARGET_UNIQUE, DefaultEvidenceScores.TARGET_UNIQUE,
                        "metadata target column is primary or unique",
                        Map.of("table", targetTable, "column", targetColumn,
                                "uniqueEndpoint", endpoint(candidate, "target"), "endpointSide", "target"));
            } else if (sourceUnique
                    && columnCoOccurrence
                    && !targetUnique) {
                addIfAbsent(candidate, EvidenceType.TARGET_UNIQUE, DefaultEvidenceScores.TARGET_UNIQUE,
                        "metadata source column is primary or unique",
                        Map.of("table", sourceTable, "column", sourceColumn,
                                "uniqueEndpoint", endpoint(candidate, "source"), "endpointSide", "source"));
            }
            if (compatible(column(metadata, sourceTable, sourceColumn), column(metadata, targetTable, targetColumn))) {
                addIfAbsent(candidate, EvidenceType.COLUMN_TYPE_COMPATIBLE, DefaultEvidenceScores.COLUMN_TYPE_COMPATIBLE,
                        "metadata column types are compatible", Map.of("sourceColumn", sourceColumn, "targetColumn", targetColumn));
            }
        }
    }

    private String endpoint(RelationshipCandidate candidate, String side) {
        return switch (side) {
            case "source" -> candidate.source().displayName();
            case "target" -> candidate.target().displayName();
            default -> "";
        };
    }

    private boolean hasIndex(List<MetadataIndexFact> indexes, String table, String column, boolean requireUnique) {
        return indexes.stream().anyMatch(index ->
                equalsIgnoreCase(index.tableName(), table)
                        && (!requireUnique || index.unique() || index.primary())
                        && index.columns().stream().anyMatch(indexColumn -> equalsIgnoreCase(indexColumn, column)));
    }

    private MetadataColumnFact column(MetadataSnapshot metadata, String table, String column) {
        return metadata.columnFacts().stream()
                .filter(fact -> equalsIgnoreCase(fact.tableName(), table) && equalsIgnoreCase(fact.columnName(), column))
                .findFirst()
                .orElse(null);
    }

    private boolean compatible(MetadataColumnFact source, MetadataColumnFact target) {
        if (source == null || target == null) {
            return false;
        }
        return normalize(source.dataType()).equals(normalize(target.dataType()))
                || normalize(source.columnType()).equals(normalize(target.columnType()));
    }

    private void addIfAbsent(
            RelationshipCandidate candidate,
            EvidenceType type,
            double score,
            String detail,
            Map<String, Object> attributes
    ) {
        boolean alreadyPresent = candidate.evidence().stream().anyMatch(evidence -> evidence.type() == type);
        if (alreadyPresent) {
            return;
        }
        candidate.evidence().add(new Evidence(type, java.math.BigDecimal.valueOf(score), EvidenceSourceType.METADATA,
                "metadata catalog facts", detail, new LinkedHashMap<>(attributes)));
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return normalize(left).equals(normalize(right));
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
