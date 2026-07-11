package com.relationdetector.semantic.extract.model;

import java.util.List;

public record SemanticGraphEdge(String id, String source, String target, String type, List<String> evidenceRefs) {
}
