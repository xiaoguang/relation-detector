package com.relationdetector.core.profile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.RelationSubType;
import com.relationdetector.contracts.Enums.RelationType;
import com.relationdetector.contracts.metadata.MetadataColumnFact;
import com.relationdetector.contracts.metadata.MetadataIndexFact;
import com.relationdetector.contracts.metadata.MetadataSnapshot;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.NamingEvidenceCandidate;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.scoring.DefaultEvidenceScores;
import com.relationdetector.contracts.spi.DataProfileOptions;
import com.relationdetector.core.identity.CanonicalEndpointKey;
import com.relationdetector.core.identity.CanonicalIdentifierResolver;
import com.relationdetector.core.identity.NamespaceContext;
import com.relationdetector.contracts.spi.IdentifierRules;

/**
 * Selects bounded data-profile candidates without scanning arbitrary columns.
 */
public final class DataProfileCandidateGenerator {
    private static final Set<EvidenceType> STRUCTURAL_PROFILE_EVIDENCE = Set.of(
            EvidenceType.SQL_LOG_JOIN,
            EvidenceType.SQL_LOG_EXISTS,
            EvidenceType.SQL_LOG_SUBQUERY_IN,
            EvidenceType.DDL_FOREIGN_KEY,
            EvidenceType.METADATA_FOREIGN_KEY,
            EvidenceType.VIEW_JOIN,
            EvidenceType.PROCEDURE_JOIN,
            EvidenceType.TRIGGER_REFERENCE,
            EvidenceType.NAMING_MATCH);

    public List<RelationshipCandidate> select(
            List<RelationshipCandidate> candidates,
            MetadataSnapshot metadata,
            List<NamingEvidenceCandidate> namingEvidence,
            DataProfileOptions options
    ) {
        return select(candidates, metadata, namingEvidence, options,
                defaultIdentifierRules(), NamespaceContext.empty());
    }

    public List<RelationshipCandidate> select(
            List<RelationshipCandidate> candidates,
            MetadataSnapshot metadata,
            List<NamingEvidenceCandidate> namingEvidence,
            DataProfileOptions options,
            IdentifierRules identifierRules,
            NamespaceContext namespace
    ) {
        DataProfileOptions effective = options == null ? DataProfileOptions.defaults() : options;
        CanonicalIdentifierResolver resolver = new CanonicalIdentifierResolver(identifierRules);
        List<RelationshipCandidate> result = new ArrayList<>();
        Map<String, RelationshipCandidate> seen = new LinkedHashMap<>();
        Map<String, Integer> targetsBySource = new HashMap<>();
        int limit = effective.maxCandidatePairs();
        for (RelationshipCandidate candidate : candidates == null ? List.<RelationshipCandidate>of() : candidates) {
            if (selectedExistingCandidate(candidate, metadata, effective, resolver, namespace)) {
                add(seen, result, targetsBySource, candidate, limit, effective.maxTargetsPerSourceColumn());
            }
        }
        if (effective.discoverFromNamingEvidence()) {
            for (NamingEvidenceCandidate naming : namingEvidence == null ? List.<NamingEvidenceCandidate>of() : namingEvidence) {
                if (namingCandidateAllowed(naming, metadata, resolver, namespace)) {
                    add(seen, result, targetsBySource, discoveredCandidate(naming), limit,
                            effective.maxTargetsPerSourceColumn());
                }
            }
        }
        return List.copyOf(result);
    }

    private boolean selectedExistingCandidate(RelationshipCandidate candidate, MetadataSnapshot metadata,
            DataProfileOptions options, CanonicalIdentifierResolver resolver, NamespaceContext namespace) {
        if (candidate == null || !candidate.source().isColumnLevel() || !candidate.target().isColumnLevel()) {
            return false;
        }
        if (options.skipUnindexedLargeTargets()
                && metadataHasIndexFacts(metadata)
                && !targetIndexed(metadata,
                CanonicalEndpointKey.from(candidate.target(), resolver, namespace), resolver, namespace)) {
            return false;
        }
        boolean declared = candidate.evidence().stream().anyMatch(evidence ->
                evidence.type() == EvidenceType.METADATA_FOREIGN_KEY
                        || evidence.type() == EvidenceType.DDL_FOREIGN_KEY);
        if (declared && !options.verifyDeclaredForeignKeys()) {
            return false;
        }
        return candidate.evidence().stream().anyMatch(evidence -> STRUCTURAL_PROFILE_EVIDENCE.contains(evidence.type()));
    }

    private boolean namingCandidateAllowed(
            NamingEvidenceCandidate naming,
            MetadataSnapshot metadata,
            CanonicalIdentifierResolver resolver,
            NamespaceContext namespace
    ) {
        if (naming == null || !naming.directionHint()
                || !naming.source().isColumnLevel() || !naming.target().isColumnLevel()) {
            return false;
        }
        return targetUnique(metadata,
                CanonicalEndpointKey.from(naming.target(), resolver, namespace), resolver, namespace)
                && compatible(metadata,
                CanonicalEndpointKey.from(naming.source(), resolver, namespace),
                CanonicalEndpointKey.from(naming.target(), resolver, namespace), resolver, namespace);
    }

    private RelationshipCandidate discoveredCandidate(NamingEvidenceCandidate naming) {
        RelationshipCandidate candidate = new RelationshipCandidate(
                naming.source(),
                naming.target(),
                RelationType.FK_LIKE,
                RelationSubType.PROFILE_SUPPORTED_FK);
        candidate.evidence().add(new Evidence(EvidenceType.NAMING_MATCH,
                java.math.BigDecimal.valueOf(DefaultEvidenceScores.NAMING_MATCH),
                EvidenceSourceType.NAMING_HEURISTIC,
                naming.evidence().source(),
                "profile candidate from top-level naming evidence",
                Map.of("evidenceRef", naming.id(), "namingRule", naming.rule())));
        return candidate;
    }

    private boolean targetUnique(MetadataSnapshot metadata, CanonicalEndpointKey key,
            CanonicalIdentifierResolver resolver, NamespaceContext namespace) {
        if (metadata == null) {
            return false;
        }
        return metadata.indexFacts().stream().anyMatch(index ->
                (index.unique() || index.primary())
                        && index.columns().stream().anyMatch(indexColumn ->
                        CanonicalEndpointKey.from(index, indexColumn, resolver, namespace).equals(key)));
    }

    private boolean targetIndexed(MetadataSnapshot metadata, CanonicalEndpointKey key,
            CanonicalIdentifierResolver resolver, NamespaceContext namespace) {
        if (metadata == null) {
            return false;
        }
        return metadata.indexFacts().stream().anyMatch(index ->
                index.visible()
                        && index.columns().stream().anyMatch(indexColumn ->
                        CanonicalEndpointKey.from(index, indexColumn, resolver, namespace).equals(key)));
    }

    private boolean metadataHasIndexFacts(MetadataSnapshot metadata) {
        return metadata != null && !metadata.indexFacts().isEmpty();
    }

    private boolean compatible(MetadataSnapshot metadata, CanonicalEndpointKey sourceKey,
            CanonicalEndpointKey targetKey, CanonicalIdentifierResolver resolver, NamespaceContext namespace) {
        if (metadata == null) {
            return false;
        }
        MetadataColumnFact source = column(metadata, sourceKey, resolver, namespace);
        MetadataColumnFact target = column(metadata, targetKey, resolver, namespace);
        if (source == null || target == null) {
            return false;
        }
        return normalize(source.dataType()).equals(normalize(target.dataType()))
                || normalize(source.columnType()).equals(normalize(target.columnType()));
    }

    private MetadataColumnFact column(MetadataSnapshot metadata, CanonicalEndpointKey key,
            CanonicalIdentifierResolver resolver, NamespaceContext namespace) {
        return metadata.columnFacts().stream()
                .filter(fact -> CanonicalEndpointKey.from(fact, resolver, namespace).equals(key))
                .findFirst()
                .orElse(null);
    }

    private void add(Map<String, RelationshipCandidate> seen, List<RelationshipCandidate> result,
            Map<String, Integer> targetsBySource, RelationshipCandidate candidate, int limit, int maxTargetsPerSource) {
        if (result.size() >= limit) {
            return;
        }
        String sourceKey = candidate.source().normalizedKey();
        if (targetsBySource.getOrDefault(sourceKey, 0) >= maxTargetsPerSource) {
            return;
        }
        String key = candidate.source().normalizedKey() + "->" + candidate.target().normalizedKey();
        if (!seen.containsKey(key)) {
            seen.put(key, candidate);
            result.add(candidate);
            targetsBySource.merge(sourceKey, 1, Integer::sum);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private IdentifierRules defaultIdentifierRules() {
        return value -> value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }
}
