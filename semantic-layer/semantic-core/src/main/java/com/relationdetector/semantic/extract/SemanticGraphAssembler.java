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
        nodes.put(id, new SemanticGraphNode(id, kind, text(label), text(type), copy(evidenceRefs)));
    }

    void addEdge(String prefix, String source, String target, String type, List<String> evidenceRefs) {
        if (blank(source) || blank(target)) {
            return;
        }
        String id = prefix + ":" + source + "->" + target + ":" + SemanticNormalizationSupport.slug(type);
        edges.putIfAbsent(id, new SemanticGraphEdge(id, source, target, type, copy(evidenceRefs)));
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
