package com.relationdetector.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relationdetector.semantic.graph.EvidenceGraph;
import com.relationdetector.semantic.graph.SemanticEvidenceBuilder;
import com.relationdetector.semantic.extract.SemanticExtractionBundleBuilder;
import com.relationdetector.semantic.kg.JsonSemanticKgWriter;
import com.relationdetector.semantic.kg.SemanticKgBuilder;
import com.relationdetector.semantic.kg.SemanticKnowledgeGraph;
import com.relationdetector.semantic.reader.ScanBundle;
import com.relationdetector.semantic.reader.ScanResultReader;

final class SemanticKgBuildTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void buildsEvidenceBackedKgFromRelationDetectorJson() throws Exception {
        Path input = tempDir.resolve("scan-result.json");
        Files.writeString(input, sampleScanResult("mysql", "shop"));

        ScanBundle bundle = new ScanResultReader().read(input);
        assertEquals("orders.customer_id", bundle.relationships().get(0).source().displayName());
        assertEquals("payments.amount", bundle.dataLineages().get(0).sources().get(0).displayName());
        assertEquals("TABLE_ID", bundle.namingEvidence().get(0).rule());
        EvidenceGraph evidenceGraph = new SemanticEvidenceBuilder().build(bundle);
        SemanticKnowledgeGraph kg = new SemanticKgBuilder().build(evidenceGraph);
        JsonNode json = JSON.readTree(new JsonSemanticKgWriter().writeKg(kg));

        assertEquals("mysql", json.path("buildRun").path("database").path("type").asText());
        assertEquals("shop", json.path("buildRun").path("database").path("schema").asText());
        assertTrue(json.path("summary").path("nodeCount").asInt() >= 6);
        assertTrue(json.path("summary").path("edgeCount").asInt() >= 7);

        Set<String> nodeIds = JSON.readerForListOf(JsonNode.class)
                .<java.util.List<JsonNode>>readValue(json.path("nodes"))
                .stream()
                .map(node -> node.path("id").asText())
                .collect(Collectors.toSet());
        assertTrue(nodeIds.contains("table:orders"));
        assertTrue(nodeIds.contains("column:orders.customer_id"));
        assertTrue(nodeIds.contains(bundle.relationships().get(0).id()));
        assertTrue(nodeIds.contains(bundle.dataLineages().get(0).id()));
        assertTrue(nodeIds.contains(bundle.namingEvidence().get(0).id()));
        assertTrue(nodeIds.stream().anyMatch(id -> id.startsWith("event-candidate:sql-write:rollup.sql:customer_rollups")));

        Set<String> evidenceIds = JSON.readerForListOf(JsonNode.class)
                .<java.util.List<JsonNode>>readValue(json.path("evidenceRefs"))
                .stream()
                .map(node -> node.path("id").asText())
                .collect(Collectors.toSet());
        assertFalse(evidenceIds.isEmpty());
        for (JsonNode node : json.path("nodes")) {
            for (JsonNode evidenceRef : node.path("evidenceRefs")) {
                assertTrue(evidenceIds.contains(evidenceRef.asText()) || nodeIds.contains(evidenceRef.asText()),
                        () -> evidenceRef + " is unresolved");
            }
        }
        for (JsonNode edge : json.path("edges")) {
            assertFalse(edge.path("evidenceRefs").isEmpty(), () -> edge.path("id").asText() + " lacks evidence");
            for (JsonNode evidenceRef : edge.path("evidenceRefs")) {
                assertTrue(evidenceIds.contains(evidenceRef.asText()) || nodeIds.contains(evidenceRef.asText()),
                        () -> evidenceRef + " is unresolved");
            }
        }
    }

    @Test
    void rejectsMergedScanResultsFromDifferentSchemas() throws Exception {
        Path first = tempDir.resolve("first.json");
        Path second = tempDir.resolve("second.json");
        Files.writeString(first, sampleScanResult("mysql", "shop"));
        Files.writeString(second, sampleScanResult("mysql", "other"));

        IllegalArgumentException error = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new ScanResultReader().readMerged(java.util.List.of(first, second)));

        assertTrue(error.getMessage().contains("same database identity"));
    }

    @Test
    void rejectsMergedScanResultsFromDifferentCatalogs() throws Exception {
        Path first = tempDir.resolve("first.json");
        Path second = tempDir.resolve("second.json");
        Files.writeString(first, sampleScanResult("mysql", "catalog_a", ""));
        Files.writeString(second, sampleScanResult("mysql", "catalog_b", ""));

        IllegalArgumentException error = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new ScanResultReader().readMerged(java.util.List.of(first, second)));

        assertTrue(error.getMessage().contains("same database identity"));
    }

    @Test
    void fixedClockBuildIsByteStableAndDoesNotExposeAbsoluteInputPaths() throws Exception {
        Path input = tempDir.resolve("scan-result.json");
        Files.writeString(input, sampleScanResult("mysql", "shop"));
        EvidenceGraph graph = new SemanticEvidenceBuilder().build(new ScanResultReader().read(input));
        Clock clock = Clock.fixed(Instant.parse("2026-07-11T12:00:00Z"), ZoneOffset.UTC);
        SemanticKgBuilder builder = new SemanticKgBuilder(clock);
        JsonSemanticKgWriter writer = new JsonSemanticKgWriter();

        String first = writer.writeKg(builder.build(graph));
        String second = writer.writeKg(builder.build(graph));

        assertEquals(first, second);
        JsonNode json = JSON.readTree(first);
        assertEquals("2026-07-11T12:00:00Z", json.path("buildRun").path("builtAt").asText());
        for (JsonNode path : json.path("buildRun").path("inputFiles")) {
            assertFalse(Path.of(path.asText()).isAbsolute(), () -> "absolute input path leaked: " + path);
        }
    }

    @Test
    void preservesCatalogAndCanonicalizesPathsInEverySemanticArtifact() throws Exception {
        Path input = tempDir.resolve("scan-result.json").toAbsolutePath();
        Files.writeString(input, sampleScanResult("mysql", "shop_catalog", ""));
        ScanBundle bundle = new ScanResultReader().read(input);
        EvidenceGraph graph = new SemanticEvidenceBuilder().build(bundle);
        SemanticKnowledgeGraph kg = new SemanticKgBuilder().build(graph);
        JsonSemanticKgWriter writer = new JsonSemanticKgWriter();

        JsonNode kgJson = JSON.readTree(writer.writeKg(kg));
        JsonNode evidenceJson = JSON.readTree(writer.writeEvidenceGraph(graph));
        JsonNode extractionJson = new SemanticExtractionBundleBuilder().build(bundle, "", 10, 10, 10);

        assertEquals("shop_catalog", bundle.catalog());
        assertEquals("shop_catalog", kgJson.path("buildRun").path("database").path("catalog").asText());
        assertEquals("shop_catalog", evidenceJson.path("scanBundle").path("catalog").asText());
        assertEquals("shop_catalog", extractionJson.path("database").path("catalog").asText());
        assertFalse(Path.of(evidenceJson.path("scanBundle").path("inputFiles").get(0).asText()).isAbsolute());
        assertFalse(Path.of(extractionJson.path("inputFiles").get(0).asText()).isAbsolute());
    }

    private static String sampleScanResult(String databaseType, String schema) {
        return sampleScanResult(databaseType, "", schema);
    }

    private static String sampleScanResult(String databaseType, String catalog, String schema) {
        return """
                {
                  "database": {"type": "%s", "catalog": "%s", "schema": "%s"},
                  "generatedAt": "2026-07-05T00:00:00Z",
                  "summary": {
                    "directRelationshipCount": 1,
                    "derivedRelationshipCount": 1,
                    "totalRelationshipCount": 2,
                    "directDataLineageCount": 1,
                    "derivedDataLineageCount": 0,
                    "totalDataLineageCount": 1,
                    "directNamingEvidenceCount": 1,
                    "derivedNamingEvidenceCount": 0,
                    "totalNamingEvidenceCount": 1,
                    "warningCount": 1,
                    "sources": ["ddl", "logs"]
                  },
                  "relationships": [{
                    "source": {"table": "orders", "column": "customer_id"},
                    "target": {"table": "customers", "column": "id"},
                    "relationType": "FK_LIKE",
                    "relationSubType": "INFERRED_JOIN_FK",
                    "confidence": 0.82,
                    "evidence": [{"type": "SQL_LOG_JOIN", "sourceType": "PLAIN_SQL", "score": 0.55, "source": "query.sql", "detail": "orders.customer_id = customers.id", "attributes": {}}],
                    "rawEvidence": [{"type": "SQL_LOG_JOIN", "sourceType": "PLAIN_SQL", "score": 0.55, "source": "query.sql", "detail": "orders.customer_id = customers.id", "attributes": {"line": 3}}],
                    "warnings": []
                  }],
                  "dataLineages": [{
                    "sources": [{"table": "payments", "column": "amount"}],
                    "target": {"table": "customer_rollups", "column": "total_paid"},
                    "flowKind": "VALUE",
                    "transformType": "AGGREGATE",
                    "confidence": 0.80,
                    "evidence": [{"type": "DATA_LINEAGE", "transformType": "AGGREGATE", "sourceType": "PLAIN_SQL", "score": 0.80, "source": "rollup.sql", "detail": "SUM(payments.amount)", "attributes": {}}],
                    "rawEvidence": [],
                    "warnings": [],
                    "attributes": {"mappingKind": "INSERT_SELECT"}
                  }],
                  "derivedRelationships": [{
                    "kind": "RELATIONSHIP",
                    "source": {"table": "order_items", "column": "order_id"},
                    "target": {"table": "customers", "column": "id"},
                    "pathLength": 2,
                    "confidence": 0.61,
                    "path": [
                      {"table": "order_items", "column": "order_id"},
                      {"table": "orders", "column": "id"},
                      {"table": "customers", "column": "id"}
                    ],
                    "evidence": [{"type": "TRANSITIVE_PATH", "sourceType": "INFERENCE", "score": 0.61, "source": "derived", "detail": "two hop path", "attributes": {}}],
                    "rawEvidence": [],
                    "attributes": {"pathLength": 2}
                  }],
                  "derivedDataLineages": [],
                  "namingEvidence": [{
                    "id": "naming:orders.customer_id->customers.id:TABLE_ID",
                    "source": {"table": "orders", "column": "customer_id"},
                    "target": {"table": "customers", "column": "id"},
                    "rule": "TABLE_ID",
                    "directionHint": true,
                    "evidence": [{"type": "NAMING_MATCH", "sourceType": "NAMING_HEURISTIC", "score": 0.15, "source": "query.sql", "detail": "customer_id matches customers.id", "attributes": {"namingRule": "TABLE_ID"}}],
                    "rawEvidence": []
                  }],
                  "derivedNamingEvidence": [],
                  "warnings": [{"type": "PARSE_WARNING", "severity": "WARN", "code": "FULL_GRAMMAR_SQL_PARSE_WARNING", "message": "sample warning", "source": "query.sql", "line": 7, "attributes": {}}]
                }
                """.formatted(databaseType, catalog, schema);
    }
}
