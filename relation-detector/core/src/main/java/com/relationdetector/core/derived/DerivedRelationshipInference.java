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
import com.relationdetector.core.scan.ScanConfig;

final class DerivedRelationshipInference {
    private final ScanConfig config;
    private final DerivedPathGraphBuilder graphs;
    private final DerivedNamingInference naming;

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
                    || hasTransitiveEvidence(relationship.evidence())) {
                continue;
            }
            directPairs.addAll(graphs.pairKeys(relationship.source(), relationship.target()));
            reverseEdges.add(relationshipEdge(relationship, directNamingRefs).reverse());
            addKeyEndpoint(keyEndpointsByTable, relationship.target());
        }
        if (config.derivedIncludeNamingEdgesInRelationshipPaths) {
            namingEvidence.stream()
                    .filter(NamingEvidenceCandidate::directionHint)
                    .filter(candidate -> !naming.isDerived(candidate.evidence()))
                    .filter(candidate -> candidate.source().isColumnLevel()
                            && candidate.target().isColumnLevel())
                    .map(naming::edge)
                    .map(DerivedEdge::reverse)
                    .forEach(reverseEdges::add);
        }

        List<DerivedPathObservation> paths = graphs.enumerateReferencedBy(
                graphs.build(reverseEdges), immutableEndpoints(keyEndpointsByTable), directPairs);
        List<DerivedPathCandidate> derivedRelationships = new ArrayList<>();
        List<NamingEvidenceCandidate> derivedNaming = new ArrayList<>();
        for (DerivedPathObservation path : paths) {
            NamingEvidenceCandidate namingCandidate = relationshipPathNaming(path);
            derivedRelationships.add(derivedRelationship(path, namingCandidate));
            if (namingCandidate != null) {
                derivedNaming.add(namingCandidate);
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
        candidate.evidence().add(pathEvidence);
        candidate.rawEvidence().add(pathEvidence);
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

    private DerivedEdge relationshipEdge(
            RelationshipCandidate relationship,
            Map<String, List<String>> directNamingRefs
    ) {
        String ref = "relationship:" + relationship.source().normalizedKey()
                + "->" + relationship.target().normalizedKey();
        return new DerivedEdge(
                relationship.source(), relationship.target(), DerivedEdgeKind.RELATIONSHIP,
                relationship.confidence(), ref,
                directNamingRefs.getOrDefault(
                        graphs.pairKey(relationship.source(), relationship.target()), List.of()));
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
            if (endpoints.stream().noneMatch(existing ->
                    existing.normalizedKey().equals(endpoint.normalizedKey()))) {
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

    record Result(
            List<DerivedPathCandidate> derivedRelationships,
            List<NamingEvidenceCandidate> derivedNamingEvidence
    ) {
    }
}
