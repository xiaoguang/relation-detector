package com.relationdetector.semantic.extract;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.relationdetector.semantic.extract.model.SemanticGraph;
import com.relationdetector.semantic.extract.model.SemanticGraphEdge;
import com.relationdetector.semantic.extract.model.SemanticGraphNode;
import com.relationdetector.semantic.extract.model.SemanticGraphSummary;

final class SemanticGraphAssembler {
    private final Map<String, SemanticGraphNode> nodes = new LinkedHashMap<>();
    private final Map<String, SemanticGraphEdge> edges = new LinkedHashMap<>();

    void addNode(String id, String kind, String label, String type, List<String> evidenceRefs) {
        SemanticGraphNode node = new SemanticGraphNode(id, kind, text(label), text(type), copy(evidenceRefs));
        if (nodes.putIfAbsent(id, node) != null) {
            throw new SemanticExtractionValidationException("duplicate semantic graph node id: " + id);
        }
    }

    void addEdge(String prefix, String source, String target, String type, List<String> evidenceRefs) {
        if (blank(source) || blank(target)) {
            return;
        }
        String id = prefix + ":" + source + "->" + target + ":" + SemanticNormalizationSupport.slug(type);
        SemanticGraphEdge edge = new SemanticGraphEdge(id, source, target, type, copy(evidenceRefs));
        SemanticGraphEdge previous = edges.putIfAbsent(id, edge);
        if (previous != null && !previous.equals(edge)) {
            throw new SemanticExtractionValidationException("conflicting semantic graph edge id: " + id);
        }
    }

    SemanticGraph build() {
        return new SemanticGraph(
                new ArrayList<>(nodes.values()),
                new ArrayList<>(edges.values()),
                new SemanticGraphSummary(nodes.size(), edges.size()));
    }

    private List<String> copy(List<String> values) {
        return List.copyOf(values == null ? List.of() : values);
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
