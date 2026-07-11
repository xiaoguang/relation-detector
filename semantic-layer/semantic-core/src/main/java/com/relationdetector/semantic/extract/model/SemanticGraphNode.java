package com.relationdetector.semantic.extract.model;

import java.util.List;

public record SemanticGraphNode(String id, String kind, String label, String type, List<String> evidenceRefs) {
}
