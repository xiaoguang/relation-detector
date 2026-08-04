package com.relationdetector.semantic.extraction.normalization;

import com.relationdetector.semantic.extraction.normalization.SemanticShardNormalizedResult;


import com.relationdetector.semantic.extraction.prompt.SemanticExtractionPrompt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.graph.EvidenceGraph;
import com.relationdetector.semantic.graph.EvidenceGraphFact;
import com.relationdetector.semantic.model.PhysicalEndpointRef;
import com.relationdetector.semantic.ingest.ScanBundle;
import com.relationdetector.semantic.ingest.ScanDiagnosticFact;
import com.relationdetector.semantic.ingest.ScanFact;
import com.relationdetector.semantic.ingest.ScanLineageFact;
import com.relationdetector.semantic.ingest.ScanMetadataInventoryFixture;
import com.relationdetector.semantic.ingest.ScanNamingEvidenceFact;
import com.relationdetector.semantic.ingest.ScanRelationshipFact;

final class SemanticExtractionRecordImmutabilityTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void semanticExtractionPromptDetachesEvidenceBundle() {
        assertJsonStateIsDetached(
                source -> new SemanticExtractionPrompt("developer", "user", source),
                SemanticExtractionPrompt::evidenceBundle);
    }

    @Test
    void semanticShardNormalizedResultDetachesDocument() {
        assertJsonStateIsDetached(
                source -> new SemanticShardNormalizedResult("shard-0001", source),
                SemanticShardNormalizedResult::document);
    }

    @Test
    void scanFactsDoNotExposeTheirInternalDocuments() {
        PhysicalEndpointRef source = PhysicalEndpointRef.column("shop.orders.customer_id");
        PhysicalEndpointRef target = PhysicalEndpointRef.column("shop.customers.id");
        assertScanFactIsDetached(document -> new ScanRelationshipFact(
                "rel-1", source, target, "FK_LIKE", "INFERRED_JOIN_FK", 0.8d, false, document));
        assertScanFactIsDetached(document -> new ScanLineageFact(
                "lin-1", List.of(source), target, "VALUE", "DIRECT", 0.8d, false, document));
        assertScanFactIsDetached(document -> new ScanNamingEvidenceFact(
                "name-1", source, target, "TABLE_ID", true, 0.8d, document));
        assertScanFactIsDetached(document -> new ScanDiagnosticFact(
                "diag-1", "PARSE_WARNING", "WARN", "message", "source.sql", 1, document));
    }

    @Test
    void evidenceGraphDoesNotExposeFactPayloadOrDiagnostics() {
        ObjectNode payload = nestedDocument();
        EvidenceGraphFact fact = new EvidenceGraphFact(
                "fact-1", "RELATIONSHIP", "fact", List.of(), List.of("evidence-1"),
                BigDecimal.ONE, payload, Map.of());
        payload.withObject("nested").put("value", "constructor mutation");
        assertEquals("original", fact.payload().path("nested").path("value").asText());
        ((ObjectNode) fact.payload().path("nested")).put("value", "accessor mutation");
        assertEquals("original", fact.payload().path("nested").path("value").asText());

        ObjectNode diagnostic = nestedDocument();
        EvidenceGraph graph = new EvidenceGraph(
                new ScanBundle("common", "", "", "", List.of(), List.of(), Map.of(),
                        ScanMetadataInventoryFixture.complete("", ""),
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of()),
                List.of(), List.of(fact), List.of(), List.of(diagnostic), Map.of());
        diagnostic.withObject("nested").put("value", "constructor mutation");
        assertEquals("original", graph.diagnostics().get(0).path("nested").path("value").asText());
        ((ObjectNode) graph.diagnostics().get(0).path("nested")).put("value", "accessor mutation");
        assertEquals("original", graph.diagnostics().get(0).path("nested").path("value").asText());
    }

    private <T> void assertJsonStateIsDetached(
            Function<ObjectNode, T> factory,
            Function<T, ? extends JsonNode> accessor
    ) {
        ObjectNode source = nestedDocument();
        T value = factory.apply(source);

        source.withObject("nested").put("value", "changed through constructor input");
        assertEquals("original", accessor.apply(value).path("nested").path("value").asText());

        JsonNode exposed = accessor.apply(value);
        ((ObjectNode) exposed.path("nested")).put("value", "changed through accessor");
        assertEquals("original", accessor.apply(value).path("nested").path("value").asText());
    }

    private void assertScanFactIsDetached(Function<ObjectNode, ScanFact> factory) {
        ObjectNode source = nestedDocument();
        ScanFact fact = factory.apply(source);

        source.withObject("nested").put("value", "changed through constructor input");
        assertEquals("original", fact.document().path("nested").path("value").asText());

        ((ObjectNode) fact.document().path("nested")).put("value", "changed through accessor");
        assertEquals("original", fact.document().path("nested").path("value").asText());
    }

    private ObjectNode nestedDocument() {
        ObjectNode root = JSON.createObjectNode();
        root.putObject("nested").put("value", "original");
        return root;
    }
}
