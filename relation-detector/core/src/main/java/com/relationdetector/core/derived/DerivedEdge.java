package com.relationdetector.core.derived;

import java.math.BigDecimal;
import java.util.List;

import com.relationdetector.contracts.model.Endpoint;

enum DerivedEdgeKind {
    RELATIONSHIP,
    LINEAGE,
    NAMING,
    TABLE_IDENTITY_BRIDGE
}

record DerivedEdge(
        Endpoint source,
        Endpoint target,
        DerivedEdgeKind kind,
        BigDecimal confidence,
        String ref,
        List<String> namingRefs
) {
    DerivedEdge {
        namingRefs = List.copyOf(namingRefs);
    }

    DerivedEdge reverse() {
        return new DerivedEdge(target, source, kind, confidence, ref, namingRefs);
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
