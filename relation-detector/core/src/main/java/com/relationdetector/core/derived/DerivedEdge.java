package com.relationdetector.core.derived;

import java.math.BigDecimal;
import java.util.List;

import com.relationdetector.contracts.Enums.DerivedEvidenceHopKind;
import com.relationdetector.contracts.model.Endpoint;

record DerivedEdge(
        Endpoint source,
        Endpoint target,
        DerivedEvidenceHopKind kind,
        BigDecimal confidence,
        List<String> evidenceRefs,
        List<String> namingRefs
) {
    DerivedEdge {
        evidenceRefs = evidenceRefs.stream().distinct().sorted().toList();
        namingRefs = namingRefs.stream().distinct().sorted().toList();
    }

    DerivedEdge reverse() {
        return new DerivedEdge(target, source, kind, confidence, evidenceRefs, namingRefs);
    }
}

record DerivedPathObservation(
        Endpoint source,
        Endpoint target,
        List<DerivedEdge> edges
) {
    DerivedPathObservation {
        edges = List.copyOf(edges);
    }
}
