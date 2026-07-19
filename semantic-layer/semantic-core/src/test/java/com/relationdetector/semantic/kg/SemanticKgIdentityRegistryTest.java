package com.relationdetector.semantic.kg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

final class SemanticKgIdentityRegistryTest {

    @Test
    void acceptsOnlyIdempotentDuplicateNodes() {
        SemanticKgIdentityRegistry registry = new SemanticKgIdentityRegistry();
        SemanticNode node = node("node:one", "PhysicalTable");

        registry.addNode(node);
        registry.addNode(node);

        assertEquals(List.of(node), registry.nodes());
        assertThrows(IllegalArgumentException.class,
                () -> registry.addNode(node("node:one", "PhysicalColumn")));
    }

    @Test
    void acceptsOnlyIdempotentDuplicateEdges() {
        SemanticKgIdentityRegistry registry = new SemanticKgIdentityRegistry();
        SemanticEdge edge = edge("edge:one", "node:one", "node:two");

        registry.addEdge(edge);
        registry.addEdge(edge);

        assertEquals(List.of(edge), registry.edges());
        assertThrows(IllegalArgumentException.class,
                () -> registry.addEdge(edge("edge:one", "node:one", "node:three")));
    }

    @Test
    void rejectsEdgesWithoutEvidence() {
        SemanticKgIdentityRegistry registry = new SemanticKgIdentityRegistry();

        assertThrows(IllegalArgumentException.class,
                () -> registry.addEdge(new SemanticEdge("edge:empty", "RELATED_TO", "node:one", "node:two",
                        BigDecimal.ONE, List.of(), Map.of())));
    }

    private SemanticNode node(String id, String type) {
        return new SemanticNode(id, type, id, BigDecimal.ONE, "EVIDENCE_SUPPORTED",
                List.of("evidence:one"), Map.of());
    }

    private SemanticEdge edge(String id, String source, String target) {
        return new SemanticEdge(id, "RELATED_TO", source, target, BigDecimal.ONE,
                List.of("evidence:one"), Map.of());
    }
}
