package com.relationdetector.core.ddl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.scoring.DefaultEvidenceScores;
import com.relationdetector.contracts.spi.IdentifierRules;
import com.relationdetector.core.identity.CanonicalEndpointKey;
import com.relationdetector.core.identity.CanonicalIdentifierResolver;
import com.relationdetector.core.identity.NamespaceContext;

/**
 *
 * Scan-level DDL index, uniqueness and column observations keyed by exact endpoint identity.
 */
public final class DdlEvidenceInventory {
    private final Map<CanonicalEndpointKey, List<Observation>> sourceIndexes = new LinkedHashMap<>();
    private final Map<CanonicalEndpointKey, List<Observation>> targetUnique = new LinkedHashMap<>();
    private final Map<CanonicalEndpointKey, List<Observation>> columns = new LinkedHashMap<>();
    private final CanonicalIdentifierResolver resolver;
    private final NamespaceContext namespace;

    public DdlEvidenceInventory() {
        this(value -> value == null ? "" : value.strip().toLowerCase(java.util.Locale.ROOT),
                NamespaceContext.empty());
    }

    public DdlEvidenceInventory(IdentifierRules identifierRules, NamespaceContext namespace) {
        this.resolver = new CanonicalIdentifierResolver(identifierRules);
        this.namespace = namespace == null ? NamespaceContext.empty() : namespace;
    }

    public void addSourceIndex(CanonicalEndpointKey endpoint, Observation observation) {
        add(sourceIndexes, endpoint, observation);
    }

    public void addTargetUnique(CanonicalEndpointKey endpoint, Observation observation) {
        add(targetUnique, endpoint, observation);
    }

    public void addColumn(CanonicalEndpointKey endpoint, Observation observation) {
        add(columns, endpoint, observation);
    }

    public void merge(DdlEvidenceInventory other) {
        if (other == null) {
            return;
        }
        merge(sourceIndexes, other.sourceIndexes);
        merge(targetUnique, other.targetUnique);
        merge(columns, other.columns);
    }

    public void enhance(List<RelationshipCandidate> candidates) {
        if (candidates == null) {
            return;
        }
        java.util.Set<RelationshipFactKey> enhancedFacts = new java.util.LinkedHashSet<>();
        for (RelationshipCandidate candidate : candidates) {
            if (!candidate.source().isColumnLevel() || !candidate.target().isColumnLevel()) {
                continue;
            }
            CanonicalEndpointKey sourceKey = key(candidate.source());
            CanonicalEndpointKey targetKey = key(candidate.target());
            if (!enhancedFacts.add(new RelationshipFactKey(sourceKey, targetKey))) {
                continue;
            }
            addEvidence(candidate, sourceIndexes.get(sourceKey), EvidenceType.SOURCE_INDEX,
                    DefaultEvidenceScores.SOURCE_INDEX, "DDL indexed endpoint",
                    "indexEndpoint", candidate.source().displayName(), "source");
            addEvidence(candidate, sourceIndexes.get(targetKey), EvidenceType.SOURCE_INDEX,
                    DefaultEvidenceScores.SOURCE_INDEX, "DDL indexed endpoint",
                    "indexEndpoint", candidate.target().displayName(), "target");
            addEvidence(candidate, targetUnique.get(sourceKey), EvidenceType.TARGET_UNIQUE,
                    DefaultEvidenceScores.TARGET_UNIQUE, "DDL primary/unique endpoint",
                    "uniqueEndpoint", candidate.source().displayName(), "source");
            addEvidence(candidate, targetUnique.get(targetKey), EvidenceType.TARGET_UNIQUE,
                    DefaultEvidenceScores.TARGET_UNIQUE, "DDL primary/unique endpoint",
                    "uniqueEndpoint", candidate.target().displayName(), "target");
        }
    }

    public boolean isEmpty() {
        return sourceIndexes.isEmpty() && targetUnique.isEmpty() && columns.isEmpty();
    }

    private void addEvidence(
            RelationshipCandidate candidate,
            List<Observation> observations,
            EvidenceType type,
            double score,
            String detail,
            String endpointAttribute,
            String endpoint,
            String endpointSide
    ) {
        if (observations == null) {
            return;
        }
        for (Observation observation : observations) {
            Map<String, Object> attributes = new LinkedHashMap<>(observation.attributes());
            attributes.put("ddlRole", observation.role());
            attributes.put("ddlKind", observation.kind());
            attributes.put(endpointAttribute, endpoint);
            attributes.put("endpointSide", endpointSide);
            if (observation.line() > 0) {
                attributes.put("sourceLine", observation.line());
            }
            Evidence evidence = new Evidence(type, BigDecimal.valueOf(score), observation.sourceType(),
                    observation.source(), detail, attributes);
            if (!candidate.evidence().contains(evidence)) {
                candidate.evidence().add(evidence);
            }
        }
    }

    private CanonicalEndpointKey key(com.relationdetector.contracts.model.Endpoint endpoint) {
        return CanonicalEndpointKey.from(endpoint, resolver, namespace);
    }

    private record RelationshipFactKey(CanonicalEndpointKey source, CanonicalEndpointKey target) {
    }

    private void add(
            Map<CanonicalEndpointKey, List<Observation>> inventory,
            CanonicalEndpointKey endpoint,
            Observation observation
    ) {
        if (endpoint == null || observation == null) {
            return;
        }
        List<Observation> observations = inventory.computeIfAbsent(endpoint, ignored -> new ArrayList<>());
        if (!observations.contains(observation)) {
            observations.add(observation);
        }
    }

    private void merge(
            Map<CanonicalEndpointKey, List<Observation>> target,
            Map<CanonicalEndpointKey, List<Observation>> source
    ) {
        source.forEach((endpoint, observations) -> observations.forEach(observation ->
                add(target, endpoint, observation)));
    }

    public record Observation(
            String role,
            String kind,
            EvidenceSourceType sourceType,
            String source,
            long line,
            Map<String, Object> attributes
    ) {
        public Observation {
            role = role == null ? "" : role;
            kind = kind == null ? "" : kind;
            source = source == null ? "" : source;
            attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        }
    }
}
