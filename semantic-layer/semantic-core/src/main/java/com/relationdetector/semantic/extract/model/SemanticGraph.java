package com.relationdetector.semantic.extract.model;

import java.util.List;

public record SemanticGraph(
        List<SemanticGraphNode> nodes,
        List<SemanticGraphEdge> edges,
        SemanticGraphSummary summary
) {
}
