package com.relationdetector.core.derived;

import java.util.List;
import java.util.Set;

import com.relationdetector.contracts.Enums.DerivedEvidenceHopKind;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.NamingEvidenceCandidate;
import com.relationdetector.core.scan.ScanConfig;

final class DerivedNamingInference {
    static final String TRANSITIVE_NAMING_RULE = "TRANSITIVE_NAMING_PATH";

    private final ScanConfig config;
    private final DerivedPathGraphBuilder graphs;

    DerivedNamingInference(ScanConfig config, DerivedPathGraphBuilder graphs) {
        this.config = config;
        this.graphs = graphs;
    }

    List<NamingEvidenceCandidate> derive(List<NamingEvidenceCandidate> namingEvidence) {
        if (!config.derivedNamingEvidenceEnabled) {
            return List.of();
        }
        List<DerivedEdge> edges = namingEvidence.stream()
                .filter(NamingEvidenceCandidate::directionHint)
                .filter(candidate -> !isDerived(candidate.evidence()))
                .filter(candidate -> !isConditional(candidate.evidence()))
                .map(this::edge)
                .toList();
        return graphs.enumerate(graphs.build(edges), Set.of(), true).stream()
                .map(this::candidate)
                .toList();
    }

    private NamingEvidenceCandidate candidate(DerivedPathObservation path) {
        List<com.relationdetector.contracts.model.Endpoint> endpoints = graphs.endpoints(path);
        Evidence evidence = graphs.pathEvidence(path, "derived:naming", true, endpoints, endpoints);
        java.util.Map<String, Object> attributes = new java.util.LinkedHashMap<>(evidence.attributes());
        attributes.put("evidenceSets", List.of(graphs.evidenceSet(path, endpoints, false)));
        Evidence summarized = new Evidence(
                evidence.type(), evidence.score(), evidence.sourceType(),
                evidence.source(), evidence.detail(), attributes);
        return new NamingEvidenceCandidate(
                path.source(), path.target(), summarized,
                TRANSITIVE_NAMING_RULE, true, List.of(summarized));
    }

    DerivedEdge edge(NamingEvidenceCandidate naming) {
        return new DerivedEdge(
                naming.source(), naming.target(), DerivedEvidenceHopKind.NAMING,
                naming.evidence().score(), List.of(naming.id()), List.of(naming.id()));
    }

    boolean isDerived(Evidence evidence) {
        return Boolean.TRUE.equals(evidence.attributes().get("derived"));
    }

    boolean isConditional(Evidence evidence) {
        return Boolean.TRUE.equals(evidence.attributes().get("conditional"));
    }
}
