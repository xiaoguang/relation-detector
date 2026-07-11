package com.relationdetector.core.derived;

import java.util.List;
import java.util.Map;

record DerivedPathGraph(
        List<DerivedEdge> edges,
        Map<String, List<DerivedEdge>> adjacency
) {
    DerivedPathGraph {
        edges = List.copyOf(edges);
        adjacency = Map.copyOf(adjacency);
    }
}
