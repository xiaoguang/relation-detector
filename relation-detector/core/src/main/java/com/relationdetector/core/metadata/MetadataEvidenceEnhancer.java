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
import com.relationdetector.core.identity.CanonicalEndpointKey;
import com.relationdetector.core.identity.CanonicalIdentifierResolver;
import com.relationdetector.core.identity.NamespaceContext;
import com.relationdetector.contracts.spi.IdentifierRules;

/**
 * Adds catalog metadata evidence to already-discovered column relationships.
 *
 * <p>This class is intentionally an enhancer, not a relationship generator.
 * Indexes, unique keys, and compatible types make an existing SQL/DDL/profile
 * candidate more credible, but they are not enough to invent a FK-like edge by
 * themselves.
 */
public final class MetadataEvidenceEnhancer {
    private final IndexEvidencePolicy indexPolicy = new IndexEvidencePolicy();
    public void enhance(List<RelationshipCandidate> candidates, MetadataSnapshot metadata) {
        enhance(candidates, metadata, defaultIdentifierRules(), NamespaceContext.empty());
    }

    public void enhance(
            List<RelationshipCandidate> candidates,
            MetadataSnapshot metadata,
            IdentifierRules identifierRules,
            NamespaceContext namespace
    ) {
        if (candidates.isEmpty()) {
            return;
        }
        CanonicalIdentifierResolver resolver = new CanonicalIdentifierResolver(identifierRules);
        for (RelationshipCandidate candidate : candidates) {
            if (!candidate.source().isColumnLevel()
                    || !candidate.target().isColumnLevel()) {
                continue;
            }
            CanonicalEndpointKey sourceKey = CanonicalEndpointKey.from(
                    candidate.source(), resolver, namespace);
            CanonicalEndpointKey targetKey = CanonicalEndpointKey.from(
                    candidate.target(), resolver, namespace);
            String sourceTable = candidate.source().table().tableName();
            String sourceColumn = candidate.source().column().columnName();
            String targetTable = candidate.target().table().tableName();
            String targetColumn = candidate.target().column().columnName();
            boolean sourceUnique = hasIndex(metadata.indexFacts(), sourceKey, true, resolver, namespace);
            boolean targetUnique = hasIndex(metadata.indexFacts(), targetKey, true, resolver, namespace);
            boolean columnCoOccurrence = candidate.relationType() == RelationType.CO_OCCURRENCE
                    && candidate.relationSubType() == RelationSubType.COLUMN_CO_OCCURRENCE;
            if (hasIndex(metadata.indexFacts(), sourceKey, false, resolver, namespace) && !sourceUnique) {
                addIfAbsent(candidate, EvidenceType.SOURCE_INDEX, DefaultEvidenceScores.SOURCE_INDEX,
                        "metadata source column is indexed",
                        Map.of("table", sourceTable, "column", sourceColumn,
                                "indexedEndpoint", endpoint(candidate, "source"), "endpointSide", "source"));
            }
            if (hasIndex(metadata.indexFacts(), targetKey, false, resolver, namespace) && !targetUnique
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
            if (compatible(column(metadata, sourceKey, resolver, namespace),
                    column(metadata, targetKey, resolver, namespace))) {
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

    private boolean hasIndex(
            List<MetadataIndexFact> indexes,
            CanonicalEndpointKey key,
            boolean requireUnique,
            CanonicalIdentifierResolver resolver,
            NamespaceContext namespace
    ) {
        return indexes.stream().anyMatch(index -> {
            if (requireUnique) {
                return index.columns().size() == 1
                        && indexPolicy.provesSingleColumnUnique(index, index.columns().get(0))
                        && CanonicalEndpointKey.from(index, index.columns().get(0), resolver, namespace).equals(key);
            }
            if (index.columns().isEmpty() || !indexPolicy.supportsLeadingColumnLookup(index, index.columns().get(0))) {
                return false;
            }
            return CanonicalEndpointKey.from(index, index.columns().get(0), resolver, namespace).equals(key);
        });
    }

    private MetadataColumnFact column(
            MetadataSnapshot metadata,
            CanonicalEndpointKey key,
            CanonicalIdentifierResolver resolver,
            NamespaceContext namespace
    ) {
        return metadata.columnFacts().stream()
                .filter(fact -> CanonicalEndpointKey.from(fact, resolver, namespace).equals(key))
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
        candidate.evidence().add(new Evidence(type, java.math.BigDecimal.valueOf(score), EvidenceSourceType.METADATA,
                "metadata catalog facts", detail, new LinkedHashMap<>(attributes)));
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private IdentifierRules defaultIdentifierRules() {
        return value -> value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }
}
