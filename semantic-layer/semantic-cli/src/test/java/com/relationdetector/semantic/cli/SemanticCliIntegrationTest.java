package com.relationdetector.semantic.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

final class SemanticCliIntegrationTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void semanticBuildWritesKgBuildRunAndEvidenceGraph() throws Exception {
        Path input = tempDir.resolve("scan-result.json");
        Path output = tempDir.resolve("semantic-output");
        Files.writeString(input, """
                {
                  "database": {"type": "mysql", "schema": "shop"},
                  "generatedAt": "2026-07-05T00:00:00Z",
                  "summary": {"directRelationshipCount": 0, "derivedRelationshipCount": 0, "totalRelationshipCount": 0, "directDataLineageCount": 0, "derivedDataLineageCount": 0, "totalDataLineageCount": 0, "directNamingEvidenceCount": 0, "derivedNamingEvidenceCount": 0, "totalNamingEvidenceCount": 0, "warningCount": 0, "sources": ["logs"]},
                  "relationships": [],
                  "dataLineages": [],
                  "derivedRelationships": [],
                  "derivedDataLineages": [],
                  "namingEvidence": [],
                  "derivedNamingEvidence": [],
                  "warnings": []
                }
                """);

        int exit = Main.run(new String[] {"build", "--input", input.toString(), "--output", output.toString()});

        assertEquals(0, exit);
        assertTrue(Files.exists(output.resolve("semantic-kg.json")));
        assertTrue(Files.exists(output.resolve("semantic-build-run.json")));
        assertTrue(Files.exists(output.resolve("semantic-evidence-graph.json")));
        JsonNode kg = JSON.readTree(output.resolve("semantic-kg.json").toFile());
        assertEquals("mysql", kg.path("buildRun").path("database").path("type").asText());
        assertTrue(kg.path("nodes").isArray());
        assertTrue(kg.path("edges").isArray());
    }

    @Test
    void semanticExtractCodexSessionWritesPromptArtifactsWithoutApiKey() throws Exception {
        Path input = tempDir.resolve("scan-result.json");
        Path output = tempDir.resolve("semantic-extract-output");
        Files.writeString(input, """
                {
                  "database": {"type": "mysql", "schema": "shop"},
                  "generatedAt": "2026-07-05T00:00:00Z",
                  "summary": {"directRelationshipCount": 1, "derivedRelationshipCount": 0, "totalRelationshipCount": 1, "directDataLineageCount": 1, "derivedDataLineageCount": 0, "totalDataLineageCount": 1, "directNamingEvidenceCount": 0, "derivedNamingEvidenceCount": 0, "totalNamingEvidenceCount": 0, "warningCount": 0, "sources": ["object-files"]},
                  "relationships": [{
                    "source": {"table": "sales_fact", "column": "order_id"},
                    "target": {"table": "sales_orders", "column": "id"},
                    "relationType": "FK_LIKE",
                    "confidence": 0.9,
                    "evidence": [{"type": "DDL_FOREIGN_KEY", "source": "schema.sql", "detail": "fk"}],
                    "rawEvidence": []
                  }],
                  "dataLineages": [{
                    "sources": [{"table": "sales_orders", "column": "id"}],
                    "target": {"table": "sales_fact", "column": "order_id"},
                    "flowKind": "VALUE",
                    "transformType": "DIRECT",
                    "confidence": 0.9,
                    "evidence": [{"transformType": "DIRECT", "source": "ROUTINE:shop.sp_rebuild_sales_fact", "detail": "insert select"}],
                    "rawEvidence": []
                  }],
                  "derivedRelationships": [],
                  "derivedDataLineages": [],
                  "namingEvidence": [],
                  "derivedNamingEvidence": [],
                  "warnings": []
                }
                """);

        int exit = Main.run(new String[] {
                "extract",
                "--input", input.toString(),
                "--output", output.toString(),
                "--focus", "ROUTINE:shop.sp_rebuild_sales_fact"
        });

        assertEquals(0, exit);
        assertTrue(Files.exists(output.resolve("semantic-extraction-evidence-bundle.json")));
        assertTrue(Files.exists(output.resolve("semantic-extraction-prompt.md")));
        assertTrue(Files.exists(output.resolve("semantic-extraction-codex-session.md")));
        JsonNode evidenceBundle = JSON.readTree(output.resolve("semantic-extraction-evidence-bundle.json").toFile());
        assertEquals("ROUTINE:shop.sp_rebuild_sales_fact", evidenceBundle.path("focus").asText());
        assertTrue(evidenceBundle.path("lineage").isArray());
    }

    @Test
    void semanticExtractOpenAiRequestOnlyWritesApiRequestWithoutApiKey() throws Exception {
        Path input = tempDir.resolve("scan-result-api.json");
        Path output = tempDir.resolve("semantic-extract-api-output");
        Files.writeString(input, """
                {
                  "database": {"type": "mysql", "schema": "shop"},
                  "generatedAt": "2026-07-05T00:00:00Z",
                  "summary": {"directRelationshipCount": 0, "derivedRelationshipCount": 0, "totalRelationshipCount": 0, "directDataLineageCount": 0, "derivedDataLineageCount": 0, "totalDataLineageCount": 0, "directNamingEvidenceCount": 0, "derivedNamingEvidenceCount": 0, "totalNamingEvidenceCount": 0, "warningCount": 0, "sources": ["object-files"]},
                  "relationships": [],
                  "dataLineages": [],
                  "derivedRelationships": [],
                  "derivedDataLineages": [],
                  "namingEvidence": [],
                  "derivedNamingEvidence": [],
                  "warnings": []
                }
                """);

        int exit = Main.run(new String[] {
                "extract",
                "--provider", "openai-api",
                "--input", input.toString(),
                "--output", output.toString(),
                "--request-only"
        });

        assertEquals(0, exit);
        assertTrue(Files.exists(output.resolve("semantic-extraction-request.json")));
        assertTrue(Files.readString(output.resolve("semantic-extraction-request.json")).contains("gpt-5.5"));
    }

    @Test
    void semanticExtractReadsProviderFromConfig() throws Exception {
        Path input = tempDir.resolve("scan-result-config.json");
        Path output = tempDir.resolve("semantic-extract-config-output");
        Path config = tempDir.resolve("semantic-extraction.yml");
        Files.writeString(input, """
                {
                  "database": {"type": "mysql", "schema": "shop"},
                  "generatedAt": "2026-07-05T00:00:00Z",
                  "summary": {"directRelationshipCount": 0, "derivedRelationshipCount": 0, "totalRelationshipCount": 0, "directDataLineageCount": 0, "derivedDataLineageCount": 0, "totalDataLineageCount": 0, "directNamingEvidenceCount": 0, "derivedNamingEvidenceCount": 0, "totalNamingEvidenceCount": 0, "warningCount": 0, "sources": ["object-files"]},
                  "relationships": [],
                  "dataLineages": [],
                  "derivedRelationships": [],
                  "derivedDataLineages": [],
                  "namingEvidence": [],
                  "derivedNamingEvidence": [],
                  "warnings": []
                }
                """);
        Files.writeString(config, """
                semanticExtraction:
                  provider: codex-session
                  input: %s
                  output: %s
                  focus: ROUTINE:shop.sp_rebuild_sales_fact
                """.formatted(input, output));

        int exit = Main.run(new String[] {"extract", "--config", config.toString()});

        assertEquals(0, exit);
        assertTrue(Files.exists(output.resolve("semantic-extraction-codex-session.md")));
    }

    @Test
    void semanticNormalizeExtractionWritesRefClosedDocument() throws Exception {
        Path input = tempDir.resolve("semantic-extraction-result-raw.json");
        Path output = tempDir.resolve("semantic-extraction-result.json");
        Files.writeString(input, """
                {
                  "entities": [
                    {"name": "销售事实表", "physicalName": "sales_fact", "type": "分析事实表", "evidenceRefs": ["sales_orders.id -> sales_fact.order_id"]},
                    {"name": "销售订单", "physicalName": "sales_orders", "type": "业务单据", "evidenceRefs": ["sales_orders.id -> sales_fact.order_id"]}
                  ],
                  "events": [
                    {"name": "重建销售事实表", "physicalName": "ROUTINE:erp.sp_rebuild_sales_fact", "type": "数据加工事件", "inputs": ["销售订单"], "outputs": ["销售事实表"], "evidenceRefs": ["ROUTINE:erp.sp_rebuild_sales_fact"]}
                  ],
                  "relations": [],
                  "lineage": [],
                  "metrics": [],
                  "dimensions": [],
                  "triplets": [],
                  "reviewItems": []
                }
                """);

        int exit = Main.run(new String[] {
                "normalize-extraction",
                "--input", input.toString(),
                "--output", output.toString()
        });

        assertEquals(0, exit);
        JsonNode normalized = JSON.readTree(output.toFile());
        assertEquals("entity:sales_fact", normalized.path("entities").get(0).path("id").asText());
        assertEquals("entity:sales_orders", normalized.path("events").get(0).path("inputEntityRefs").get(0).asText());
        assertTrue(normalized.path("semanticGraph").path("nodes").isArray());
        assertTrue(normalized.path("validation").path("isRefClosed").isBoolean());
    }
}
