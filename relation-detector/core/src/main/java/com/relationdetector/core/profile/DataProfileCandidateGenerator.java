package com.relationdetector.core.profile;

import java.util.ArrayList;
import java.util.Comparator;
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
import com.relationdetector.core.identity.CanonicalEndpointKeyProvider;
import com.relationdetector.core.identity.CanonicalIdentifierResolver;
import com.relationdetector.core.identity.NamespaceContext;
import com.relationdetector.core.metadata.IndexEvidencePolicy;
import com.relationdetector.contracts.spi.IdentifierRules;

/**
 * CN: 从已有 relationship、naming 和 metadata 中选择有界 live profile candidates，不扫描任意列。
 * EN: Selects bounded live-profile candidates from existing relationship, naming, and metadata facts without scanning arbitrary columns.
 */
public final class DataProfileCandidateGenerator {
    private static final Set<EvidenceType> SQL_PREDICATE_EVIDENCE = Set.of(
            EvidenceType.SQL_LOG_JOIN,
            EvidenceType.SQL_LOG_EXISTS,
            EvidenceType.SQL_LOG_SUBQUERY_IN);
    private static final Set<EvidenceType> OBJECT_PREDICATE_EVIDENCE = Set.of(
            EvidenceType.VIEW_JOIN,
            EvidenceType.PROCEDURE_JOIN,
            EvidenceType.TRIGGER_REFERENCE);
    private final IndexEvidencePolicy indexPolicy = new IndexEvidencePolicy();
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
        CanonicalEndpointKeyProvider endpointKeys = new CanonicalEndpointKeyProvider(identifierRules, namespace);
        List<RankedCandidate> eligible = new ArrayList<>();
        for (RelationshipCandidate candidate : candidates == null ? List.<RelationshipCandidate>of() : candidates) {
            if (selectedExistingCandidate(candidate, metadata, effective, resolver, namespace)) {
                eligible.add(rankExisting(candidate, metadata, resolver, namespace, endpointKeys));
            }
        }
        if (effective.discoverFromNamingEvidence()) {
            for (NamingEvidenceCandidate naming : namingEvidence == null ? List.<NamingEvidenceCandidate>of() : namingEvidence) {
                if (namingCandidateAllowed(naming, metadata, resolver, namespace)) {
                    RelationshipCandidate discovered = discoveredCandidate(naming);
                    eligible.add(new RankedCandidate(
                            discovered,
                            ProfileCandidatePriority.NAMING_DISCOVERY,
                            1,
                            endpointKeys.factKey(discovered.source()),
                            endpointKeys.factKey(discovered.target())));
                }
            }
        }
        return applyBudgets(eligible, effective, endpointKeys);
    }

    private boolean selectedExistingCandidate(RelationshipCandidate candidate, MetadataSnapshot metadata,
            DataProfileOptions options, CanonicalIdentifierResolver resolver, NamespaceContext namespace) {
        if (candidate == null || !candidate.source().isColumnLevel() || !candidate.target().isColumnLevel()) {
            return false;
        }
        CanonicalEndpointKey sourceKey = CanonicalEndpointKey.from(candidate.source(), resolver, namespace);
        CanonicalEndpointKey targetKey = CanonicalEndpointKey.from(candidate.target(), resolver, namespace);
        if (!compatible(metadata, sourceKey, targetKey, resolver, namespace)) {
            return false;
        }
        boolean declared = hasEvidence(candidate, EvidenceType.METADATA_FOREIGN_KEY)
                || hasEvidence(candidate, EvidenceType.DDL_FOREIGN_KEY);
        if (declared && !options.verifyDeclaredForeignKeys()) {
            return false;
        }
        if (!declared
                && options.skipUnindexedLargeTargets()
                && metadataHasIndexFacts(metadata)
                && !targetIndexed(metadata, targetKey, resolver, namespace)) {
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
        CanonicalEndpointKey sourceKey = CanonicalEndpointKey.from(naming.source(), resolver, namespace);
        CanonicalEndpointKey targetKey = CanonicalEndpointKey.from(naming.target(), resolver, namespace);
        return targetUnique(metadata, targetKey, resolver, namespace)
                && targetIndexed(metadata, sourceKey, resolver, namespace)
                && compatible(metadata, sourceKey, targetKey, resolver, namespace);
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
                index.columns().size() == 1
                        && indexPolicy.provesSingleColumnUnique(index, index.columns().get(0))
                        && CanonicalEndpointKey.from(index, index.columns().get(0), resolver, namespace).equals(key));
    }

    private boolean targetIndexed(MetadataSnapshot metadata, CanonicalEndpointKey key,
            CanonicalIdentifierResolver resolver, NamespaceContext namespace) {
        if (metadata == null) {
            return false;
        }
        return metadata.indexFacts().stream().anyMatch(index ->
                !index.columns().isEmpty()
                        && indexPolicy.supportsLeadingColumnLookup(index, index.columns().get(0))
                        && CanonicalEndpointKey.from(index, index.columns().get(0), resolver, namespace).equals(key));
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
        return sameNonBlankType(source.dataType(), target.dataType())
                || sameNonBlankType(source.columnType(), target.columnType());
    }

    private MetadataColumnFact column(MetadataSnapshot metadata, CanonicalEndpointKey key,
            CanonicalIdentifierResolver resolver, NamespaceContext namespace) {
        return metadata.columnFacts().stream()
                .filter(fact -> CanonicalEndpointKey.from(fact, resolver, namespace).equals(key))
                .findFirst()
                .orElse(null);
    }

    private RankedCandidate rankExisting(
            RelationshipCandidate candidate,
            MetadataSnapshot metadata,
            CanonicalIdentifierResolver resolver,
            NamespaceContext namespace,
            CanonicalEndpointKeyProvider endpointKeys
    ) {
        CanonicalEndpointKey targetKey = CanonicalEndpointKey.from(candidate.target(), resolver, namespace);
        boolean sqlPredicate = candidate.evidence().stream()
                .anyMatch(evidence -> SQL_PREDICATE_EVIDENCE.contains(evidence.type()));
        boolean declared = hasEvidence(candidate, EvidenceType.METADATA_FOREIGN_KEY)
                || hasEvidence(candidate, EvidenceType.DDL_FOREIGN_KEY);
        boolean naming = hasEvidence(candidate, EvidenceType.NAMING_MATCH);
        boolean objectPredicate = candidate.evidence().stream()
                .anyMatch(evidence -> OBJECT_PREDICATE_EVIDENCE.contains(evidence.type()));
        ProfileCandidatePriority priority;
        if (sqlPredicate && targetUnique(metadata, targetKey, resolver, namespace)) {
            priority = ProfileCandidatePriority.SQL_PREDICATE_UNIQUE_TARGET;
        } else if (declared) {
            priority = ProfileCandidatePriority.DECLARED_FOREIGN_KEY;
        } else if (sqlPredicate && naming) {
            priority = ProfileCandidatePriority.SQL_PREDICATE_WITH_NAMING;
        } else if (objectPredicate) {
            priority = ProfileCandidatePriority.OBJECT_PREDICATE;
        } else {
            priority = ProfileCandidatePriority.OTHER_STRUCTURAL;
        }
        return new RankedCandidate(
                candidate,
                priority,
                observationCount(candidate),
                endpointKeys.factKey(candidate.source()),
                endpointKeys.factKey(candidate.target()));
    }

    private List<RelationshipCandidate> applyBudgets(
            List<RankedCandidate> eligible,
            DataProfileOptions options,
            CanonicalEndpointKeyProvider endpointKeys
    ) {
        eligible.sort(Comparator
                .comparing(RankedCandidate::priority)
                .thenComparing(Comparator.comparingInt(RankedCandidate::observationCount).reversed())
                .thenComparing(RankedCandidate::sourceKey)
                .thenComparing(RankedCandidate::targetKey)
                .thenComparing(candidate -> candidate.candidate().relationSubType().name()));
        Map<String, RankedCandidate> unique = new LinkedHashMap<>();
        for (RankedCandidate ranked : eligible) {
            unique.putIfAbsent(ranked.sourceKey() + "->" + ranked.targetKey(), ranked);
        }

        List<RelationshipCandidate> selected = new ArrayList<>();
        Map<String, Integer> targetsBySource = new HashMap<>();
        for (RankedCandidate ranked : unique.values()) {
            if (selected.size() >= options.maxCandidatePairs()) {
                break;
            }
            String sourceKey = endpointKeys.factKey(ranked.candidate().source());
            if (targetsBySource.getOrDefault(sourceKey, 0) >= options.maxTargetsPerSourceColumn()) {
                continue;
            }
            selected.add(ranked.candidate());
            targetsBySource.merge(sourceKey, 1, Integer::sum);
        }
        return List.copyOf(selected);
    }

    private int observationCount(RelationshipCandidate candidate) {
        int count = candidate.evidence().size() + candidate.rawEvidence().size();
        for (Evidence evidence : candidate.rawEvidence()) {
            Object occurrenceCount = evidence.attributes().get("occurrenceCount");
            if (occurrenceCount instanceof Number number && number.intValue() > 1) {
                count += number.intValue() - 1;
            }
        }
        return count;
    }

    private boolean hasEvidence(RelationshipCandidate candidate, EvidenceType type) {
        return candidate.evidence().stream().anyMatch(evidence -> evidence.type() == type);
    }

    private boolean sameNonBlankType(String left, String right) {
        String normalizedLeft = normalize(left);
        return !normalizedLeft.isBlank() && normalizedLeft.equals(normalize(right));
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private IdentifierRules defaultIdentifierRules() {
        return value -> value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    private enum ProfileCandidatePriority {
        SQL_PREDICATE_UNIQUE_TARGET,
        DECLARED_FOREIGN_KEY,
        SQL_PREDICATE_WITH_NAMING,
        OBJECT_PREDICATE,
        NAMING_DISCOVERY,
        OTHER_STRUCTURAL
    }

    private record RankedCandidate(
            RelationshipCandidate candidate,
            ProfileCandidatePriority priority,
            int observationCount,
            String sourceKey,
            String targetKey
    ) {
    }
}
