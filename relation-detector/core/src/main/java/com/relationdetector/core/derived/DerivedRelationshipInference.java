package com.relationdetector.core.derived;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.relationdetector.contracts.Enums.DerivedPathKind;
import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.RelationType;
import com.relationdetector.contracts.model.DerivedPathCandidate;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.NamingEvidenceCandidate;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.core.evidence.EvidenceObservationAggregator;
import com.relationdetector.core.evidence.EvidenceObservationAggregator.SummaryGroup;
import com.relationdetector.core.scan.ScanConfig;

final class DerivedRelationshipInference {
    private final ScanConfig config;
    private final DerivedPathGraphBuilder graphs;
    private final DerivedNamingInference naming;
    private final EvidenceObservationAggregator<Evidence> observations =
            new EvidenceObservationAggregator<>();

    DerivedRelationshipInference(
            ScanConfig config,
            DerivedPathGraphBuilder graphs,
            DerivedNamingInference naming
    ) {
        this.config = config;
        this.graphs = graphs;
        this.naming = naming;
    }

    Result infer(
            List<RelationshipCandidate> relationships,
            List<NamingEvidenceCandidate> namingEvidence
    ) {
        Map<String, List<String>> directNamingRefs = directNamingRefsByPair(namingEvidence);
        List<DerivedEdge> reverseEdges = new ArrayList<>();
        Set<String> directPairs = new HashSet<>();
        Map<String, List<Endpoint>> keyEndpointsByTable = new LinkedHashMap<>();
        for (RelationshipCandidate relationship : relationships) {
            if (relationship.relationType() != RelationType.FK_LIKE
                    || !relationship.source().isColumnLevel()
                    || !relationship.target().isColumnLevel()
                    || isConditional(relationship)
                    || hasTransitiveEvidence(relationship.evidence())) {
                continue;
            }
            directPairs.addAll(graphs.pairKeys(relationship.source(), relationship.target()));
            relationshipEdges(relationship, directNamingRefs).stream()
                    .map(DerivedEdge::reverse)
                    .forEach(reverseEdges::add);
            addKeyEndpoint(keyEndpointsByTable, relationship.target());
        }
        if (config.derivedIncludeNamingEdgesInRelationshipPaths) {
            namingEvidence.stream()
                    .filter(NamingEvidenceCandidate::directionHint)
                    .filter(candidate -> !naming.isDerived(candidate.evidence()))
                    .filter(candidate -> !naming.isConditional(candidate.evidence()))
                    .filter(candidate -> candidate.source().isColumnLevel()
                            && candidate.target().isColumnLevel())
                    .map(naming::edge)
                    .map(DerivedEdge::reverse)
                    .forEach(reverseEdges::add);
        }

        List<DerivedPathObservation> paths = graphs.enumerateReferencedBy(
                graphs.build(reverseEdges), immutableEndpoints(keyEndpointsByTable), directPairs);
        Map<String, List<DerivedPathObservation>> grouped = new LinkedHashMap<>();
        for (DerivedPathObservation path : paths) {
            grouped.computeIfAbsent(
                    graphs.canonicalPathKey(DerivedPathKind.RELATIONSHIP.name(), path),
                    ignored -> new ArrayList<>()).add(path);
        }
        List<DerivedPathCandidate> derivedRelationships = new ArrayList<>();
        List<NamingEvidenceCandidate> derivedNaming = new ArrayList<>();
        for (List<DerivedPathObservation> variants : grouped.values()) {
            DerivedPathObservation representative = variants.stream()
                    .max(Comparator.comparing(graphs::confidence))
                    .orElseThrow();
            NamingEvidenceCandidate namingCandidate = relationshipPathNaming(representative);
            derivedRelationships.add(derivedRelationship(representative, variants, namingCandidate));
            if (namingCandidate != null) {
                derivedNaming.add(namingCandidate);
            }
            if (graphs.limitReached(derivedRelationships.size())) {
                break;
            }
        }
        return new Result(List.copyOf(derivedRelationships), List.copyOf(derivedNaming));
    }

    private NamingEvidenceCandidate relationshipPathNaming(DerivedPathObservation path) {
        List<String> namingRefs = path.edges().stream()
                .flatMap(edge -> edge.namingRefs().stream()).distinct().sorted().toList();
        if (!config.derivedNamingEvidenceEnabled || namingRefs.isEmpty()) {
            return null;
        }
        List<Endpoint> outputPath = graphs.relationshipOutputPath(path.edges());
        List<Endpoint> traversalPath = graphs.endpoints(path);
        Evidence base = graphs.pathEvidence(
                path, "derived:naming", true, outputPath, traversalPath);
        Map<String, Object> attributes = new LinkedHashMap<>(base.attributes());
        attributes.put("supportingNamingEvidenceRefs", namingRefs);
        Evidence evidence = new Evidence(
                base.type(), base.score(), base.sourceType(),
                base.source(), base.detail(), attributes);
        return new NamingEvidenceCandidate(
                outputPath.get(0), outputPath.get(outputPath.size() - 1), evidence,
                DerivedNamingInference.TRANSITIVE_NAMING_RULE, true, List.of(evidence));
    }

    private DerivedPathCandidate derivedRelationship(
            DerivedPathObservation observation,
            List<DerivedPathObservation> variants,
            NamingEvidenceCandidate namingEvidence
    ) {
        List<Endpoint> outputPath = graphs.relationshipOutputPath(observation.edges());
        List<Endpoint> traversalPath = graphs.endpoints(observation);
        DerivedPathCandidate candidate = new DerivedPathCandidate(
                DerivedPathKind.RELATIONSHIP,
                outputPath.get(0), outputPath.get(outputPath.size() - 1), outputPath);
        candidate.confidence(graphs.confidence(observation));
        candidate.attributes().put("pathLength", observation.edges().size());
        candidate.attributes().put("traversalMode", "REVERSE_REFERENCED_BY");
        candidate.attributes().put("outputDirection", "FK_LIKE_FORWARD");
        candidate.attributes().put("containsNamingEdge",
                observation.edges().stream().anyMatch(edge -> edge.kind() == DerivedEdgeKind.NAMING));
        candidate.attributes().put("containsTableIdentityBridge",
                observation.edges().stream().anyMatch(
                        edge -> edge.kind() == DerivedEdgeKind.TABLE_IDENTITY_BRIDGE));
        candidate.attributes().put("path", graphs.endpointNames(outputPath));
        candidate.attributes().put("traversalPath", graphs.endpointNames(traversalPath));
        Evidence pathEvidence = graphs.pathEvidence(
                observation, "derived:relationship", false, outputPath, traversalPath);
        EvidenceObservationAggregator.Aggregation<Evidence> aggregated = observations.aggregate(
                variants.stream().map(variant -> graphs.pathEvidence(
                        variant, "derived:relationship", false,
                        graphs.relationshipOutputPath(variant.edges()), graphs.endpoints(variant)))
                        .toList(),
                new DerivedPathEvidencePolicy(), true);
        SummaryGroup<Evidence> summary = aggregated.groups().get(0);
        Map<String, Object> summaryAttributes = new LinkedHashMap<>(summary.consensusAttributes());
        summaryAttributes.put("count", summary.count());
        candidate.attributes().put("observationCount", summary.count());
        candidate.evidence().add(new Evidence(
                pathEvidence.type(), candidate.confidence(), pathEvidence.sourceType(),
                pathEvidence.source(), pathEvidence.detail(), summaryAttributes));
        candidate.rawEvidence().addAll(aggregated.rawObservations());
        if (namingEvidence != null) {
            candidate.evidence().add(new Evidence(
                    EvidenceType.NAMING_MATCH,
                    namingEvidence.evidence().score(),
                    EvidenceSourceType.INFERENCE,
                    "derived:naming",
                    namingEvidence.source().displayName() + " -> "
                            + namingEvidence.target().displayName(),
                    Map.of(
                            "evidenceRef", namingEvidence.id(),
                            "namingRule", DerivedNamingInference.TRANSITIVE_NAMING_RULE,
                            "directionHint", true,
                            "suggestedSourceEndpoint", namingEvidence.source().normalizedKey(),
                            "suggestedTargetEndpoint", namingEvidence.target().normalizedKey(),
                            "suggestedSourceEndpointKey", namingEvidence.source().normalizedKey(),
                            "suggestedTargetEndpointKey", namingEvidence.target().normalizedKey())));
        }
        return candidate;
    }

    private List<DerivedEdge> relationshipEdges(
            RelationshipCandidate relationship,
            Map<String, List<String>> directNamingRefs
    ) {
        List<Evidence> raw = relationship.rawEvidence().isEmpty()
                ? relationship.evidence() : relationship.rawEvidence();
        return raw.stream().filter(evidence -> evidence.type() != EvidenceType.NAMING_MATCH)
                .map(evidence -> new DerivedEdge(
                        relationship.source(), relationship.target(), DerivedEdgeKind.RELATIONSHIP,
                        relationship.confidence(), relationshipReference(relationship, evidence),
                        directNamingRefs.getOrDefault(
                                graphs.pairKey(relationship.source(), relationship.target()), List.of())))
                .toList();
    }

    private String relationshipReference(RelationshipCandidate relationship, Evidence evidence) {
        return "relationship:" + relationship.source().normalizedKey()
                + "->" + relationship.target().normalizedKey() + ":"
                + evidence.type() + ":" + evidence.sourceType() + ":" + evidence.source()
                + ":" + evidence.attributes().getOrDefault("sourceStatementId", "")
                + ":" + evidence.attributes().getOrDefault("sourceBlockId", "")
                + ":" + evidence.attributes().getOrDefault("sourceLine", "")
                + ":" + evidence.detail();
    }

    private Map<String, List<String>> directNamingRefsByPair(
            List<NamingEvidenceCandidate> namingEvidence
    ) {
        Map<String, List<String>> refs = new LinkedHashMap<>();
        namingEvidence.stream()
                .filter(NamingEvidenceCandidate::directionHint)
                .filter(candidate -> candidate.source().isColumnLevel()
                        && candidate.target().isColumnLevel())
                .filter(candidate -> !naming.isDerived(candidate.evidence()))
                .filter(candidate -> !naming.isConditional(candidate.evidence()))
                .filter(candidate -> !DerivedNamingInference.TRANSITIVE_NAMING_RULE.equals(candidate.rule()))
                .sorted(Comparator.comparing(NamingEvidenceCandidate::id))
                .forEach(candidate -> refs.computeIfAbsent(
                        graphs.pairKey(candidate.source(), candidate.target()),
                        ignored -> new ArrayList<>()).add(candidate.id()));
        refs.replaceAll((ignored, values) -> values.stream().distinct().sorted().toList());
        return refs;
    }

    private void addKeyEndpoint(Map<String, List<Endpoint>> byTable, Endpoint endpoint) {
        for (String tableKey : graphs.tableGraphKeys(endpoint)) {
            List<Endpoint> endpoints = byTable.computeIfAbsent(tableKey, ignored -> new ArrayList<>());
            if (endpoints.stream().noneMatch(existing -> graphs.sameEndpoint(existing, endpoint))) {
                endpoints.add(endpoint);
            }
        }
    }

    private Map<String, List<Endpoint>> immutableEndpoints(Map<String, List<Endpoint>> source) {
        Map<String, List<Endpoint>> result = new LinkedHashMap<>();
        source.forEach((key, endpoints) -> result.put(key, endpoints.stream()
                .sorted(Comparator.comparing(Endpoint::normalizedKey)).toList()));
        return Map.copyOf(result);
    }

    private boolean hasTransitiveEvidence(List<Evidence> evidence) {
        return evidence.stream().anyMatch(item -> item.type() == EvidenceType.TRANSITIVE_PATH);
    }

    private boolean isConditional(RelationshipCandidate relationship) {
        if (Boolean.TRUE.equals(relationship.attributes().get("conditional"))) {
            return true;
        }
        List<Evidence> structural = relationship.evidence().stream()
                .filter(evidence -> evidence.type() != EvidenceType.NAMING_MATCH)
                .toList();
        return !structural.isEmpty() && structural.stream().allMatch(naming::isConditional);
    }

    record Result(
            List<DerivedPathCandidate> derivedRelationships,
            List<NamingEvidenceCandidate> derivedNamingEvidence
    ) {
    }

    private static final class DerivedPathEvidencePolicy
            implements EvidenceObservationAggregator.ObservationPolicy<Evidence> {
        @Override public Object exactKey(Evidence evidence) {
            Map<String, Object> attributes = new LinkedHashMap<>(evidence.attributes());
            attributes.remove("occurrenceCount");
            return List.of(evidence.type(), evidence.score(), evidence.sourceType(), evidence.source(),
                    evidence.detail(), attributes);
        }
        @Override public Object summaryKey(Evidence evidence) { return "derived-relationship-path"; }
        @Override public int occurrenceCount(Evidence evidence) {
            return EvidenceObservationAggregator.occurrenceCount(evidence.attributes());
        }
        @Override public Map<String, Object> observationAttributes(Evidence evidence) { return evidence.attributes(); }
        @Override public String detail(Evidence evidence) { return evidence.detail(); }
        @Override public Evidence withOccurrenceCount(Evidence evidence, int count) {
            if (count <= 1) return evidence;
            Map<String, Object> attributes = new LinkedHashMap<>(evidence.attributes());
            attributes.put("occurrenceCount", count);
            return new Evidence(evidence.type(), evidence.score(), evidence.sourceType(), evidence.source(),
                    evidence.detail(), attributes);
        }
    }
}
