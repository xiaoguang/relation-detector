package com.relationdetector.semantic.extract;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

final class SemanticGraphAssemblerTest {
    @Test
    void rejectsDuplicateNodeIds() {
        SemanticGraphAssembler graph = new SemanticGraphAssembler();
        graph.addNode("duplicate", "Entity", "first", "TABLE", List.of("e1"));

        assertThrows(SemanticExtractionValidationException.class,
                () -> graph.addNode("duplicate", "Metric", "second", "MEASURE", List.of("e2")));
    }

    @Test
    void deduplicatesIdenticalEdgesButRejectsConflictingPayload() {
        SemanticGraphAssembler graph = new SemanticGraphAssembler();
        graph.addEdge("owner", "metric:one", "entity:orders", "OWNER", List.of("e1"));

        assertDoesNotThrow(() -> graph.addEdge(
                "owner", "metric:one", "entity:orders", "OWNER", List.of("e1")));
        assertThrows(SemanticExtractionValidationException.class, () -> graph.addEdge(
                "owner", "metric:one", "entity:orders", "OWNER", List.of("e2")));
    }
}
