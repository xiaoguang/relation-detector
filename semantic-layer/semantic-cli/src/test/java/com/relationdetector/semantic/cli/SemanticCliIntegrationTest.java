package com.relationdetector.semantic.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.StableSemanticId;

final class SemanticCliIntegrationTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void buildAndE2eHandlersShareTheOnlyKgBuildService() throws Exception {
        Path sourceRoot = Path.of("src/main/java/com/relationdetector/semantic/cli");
        String buildHandler = Files.readString(sourceRoot.resolve("SemanticBuildCommandHandler.java"));
        String e2eHandler = Files.readString(sourceRoot.resolve("SemanticE2eCommandHandler.java"));
        String buildService = Files.readString(sourceRoot.resolve("SemanticKgBuildService.java"));

        assertTrue(buildHandler.contains("new SemanticKgBuildService().build("));
        assertTrue(e2eHandler.contains("new SemanticKgBuildService().build("));
        assertFalse(buildHandler.contains("new SemanticEvidenceBuilder"));
        assertFalse(e2eHandler.contains("new SemanticEvidenceBuilder"));
        assertTrue(buildService.contains("new SemanticEvidenceBuilder().build("));
        assertTrue(buildService.contains("new SemanticKgBuilder().build("));
        assertFalse(buildService.contains("SemanticEnricher"));
        assertFalse(buildService.contains(".enrich("));
    }

    @Test
    void semanticCliDoesNotEchoArgumentDetailsOnFailure() {
        PrintStream original = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured));
        try {
            Path sensitiveInput = tempDir.resolve("customer-secret-value.json");
            int exit = Main.run(new String[] {
                    "build",
                    "--input", sensitiveInput.toString(),
                    "--output", tempDir.resolve("output").toString()
            });

            assertEquals(2, exit);
            assertTrue(captured.toString().contains("Semantic command error"));
            assertFalse(captured.toString().contains("customer-secret-value"));
        } finally {
            System.setErr(original);
        }
    }

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
                    "relationSubType": "DECLARED_FK",
                    "confidence": 0.9,
                    "evidence": [{"type": "DDL_FOREIGN_KEY", "sourceType": "DDL_FILE", "score": 0.9, "source": "schema.sql", "detail": "fk", "attributes": {}}],
                    "rawEvidence": [],
                    "warnings": []
                  }],
                  "dataLineages": [{
                    "sources": [{"table": "sales_orders", "column": "id"}],
                    "target": {"table": "sales_fact", "column": "order_id"},
                    "flowKind": "VALUE",
                    "transformType": "DIRECT",
                    "confidence": 0.9,
                    "evidence": [{"type": "DATA_LINEAGE", "transformType": "DIRECT", "sourceType": "DATABASE_OBJECT", "score": 0.9, "source": "routine.sql", "detail": "insert select", "attributes": {"sourceObjectType": "ROUTINE", "sourceObjectName": "shop.sp_rebuild_sales_fact", "sourceStatementId": "shop.sp_rebuild_sales_fact:1-1", "mappingKind": "INSERT_SELECT"}}],
                    "rawEvidence": [],
                    "warnings": [],
                    "attributes": {}
                  }],
                  "derivedRelationships": [],
                  "derivedDataLineages": [],
                  "namingEvidence": [],
                  "derivedNamingEvidence": [],
                  "warnings": []
                }
                """);

        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        int exit;
        System.setOut(new PrintStream(captured));
        try {
            exit = Main.run(new String[] {
                    "extract",
                    "--input", input.toString(),
                    "--output", output.toString(),
                    "--focus", "ROUTINE:shop.sp_rebuild_sales_fact"
            });
        } finally {
            System.setOut(original);
        }

        assertEquals(0, exit);
        Path published = Path.of(captured.toString().trim());
        assertEquals(output.toAbsolutePath().normalize(), published.getParent());
        assertTrue(published.getFileName().toString().startsWith("run-"));
        assertTrue(Files.exists(published.resolve("semantic-extraction-evidence-bundle.json")));
        assertTrue(Files.exists(published.resolve("semantic-extraction-prompt.md")));
        assertTrue(Files.exists(published.resolve("semantic-extraction-codex-session.md")));
        JsonNode evidenceBundle = JSON.readTree(
                published.resolve("semantic-extraction-evidence-bundle.json").toFile());
        assertEquals("ROUTINE:shop.sp_rebuild_sales_fact", evidenceBundle.path("focus").asText());
        assertTrue(evidenceBundle.path("lineage").isArray());
        assertEquals(1, evidenceBundle.path("eventCandidates").size());
        assertEquals(StableSemanticId.of(
                        "event-candidate:routine", "ROUTINE", "shop.sp_rebuild_sales_fact:1-1"),
                evidenceBundle.path("eventCandidates").get(0).path("id").asText());
    }

    @Test
    void semanticExtractDistinguishesUnlimitedInputFromExplicitPreviewLimits() throws Exception {
        Path input = tempDir.resolve("scan-result-zero-limits.json");
        Path output = tempDir.resolve("semantic-extract-zero-limits-output");
        Files.writeString(input, """
                {
                  "database": {"type": "mysql", "schema": "shop"},
                  "generatedAt": "2026-07-05T00:00:00Z",
                  "summary": {"directRelationshipCount": 1, "derivedRelationshipCount": 0, "totalRelationshipCount": 1, "directDataLineageCount": 0, "derivedDataLineageCount": 0, "totalDataLineageCount": 0, "directNamingEvidenceCount": 0, "derivedNamingEvidenceCount": 0, "totalNamingEvidenceCount": 0, "warningCount": 0, "sources": ["object-files"]},
                  "relationships": [{
                    "source": {"table": "sales_fact", "column": "order_id"},
                    "target": {"table": "sales_orders", "column": "id"},
                    "relationType": "FK_LIKE",
                    "relationSubType": "DECLARED_FK",
                    "confidence": 0.9,
                    "evidence": [{"type": "DDL_FOREIGN_KEY", "sourceType": "DDL_FILE", "score": 0.9, "source": "schema.sql", "detail": "fk", "attributes": {}}],
                    "rawEvidence": [],
                    "warnings": []
                  }],
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
                "--input", input.toString(),
                "--output", output.toString(),
                "--max-relationships", "0",
                "--max-lineage", "0",
                "--max-naming", "0"
        });

        assertEquals(0, exit);
        Path published = onlyPublishedRun(output);
        JsonNode evidenceBundle = JSON.readTree(
                published.resolve("semantic-extraction-evidence-bundle.json").toFile());
        assertEquals(1, evidenceBundle.path("relationships").size());

        int rejectedPreview = Main.run(new String[] {
                "extract",
                "--input", input.toString(),
                "--output", tempDir.resolve("semantic-extract-preview-rejected").toString(),
                "--max-relationships", "1"
        });
        int explicitPreview = Main.run(new String[] {
                "extract",
                "--input", input.toString(),
                "--output", tempDir.resolve("semantic-extract-preview").toString(),
                "--max-relationships", "1",
                "--shard-mode", "off"
        });

        assertEquals(2, rejectedPreview);
        assertEquals(0, explicitPreview);
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
                "--request-only",
                "--artifact-retention", "final-only"
        });

        assertEquals(0, exit);
        Path published = onlyPublishedRun(output);
        assertTrue(Files.exists(published.resolve("semantic-extraction-request.json")));
        assertTrue(Files.readString(
                published.resolve("semantic-extraction-request.json")).contains("gpt-5.6-sol"));
        assertTrue(Files.readString(
                published.resolve("semantic-extraction-request.json")).contains("xhigh"));
        JsonNode manifest = JSON.readTree(published.resolve("run-manifest.json").toFile());
        assertEquals("final-only", manifest.path("retention").asText());
        assertEquals("gpt-5.6-sol", manifest.path("model").asText());
        assertEquals("xhigh", manifest.path("reasoningEffort").asText());
    }

    @Test
    void semanticExtractForceModeWritesEvidenceClosedShardRequestsAndManifest() throws Exception {
        Path input = tempDir.resolve("scan-result-sharded.json");
        Path output = tempDir.resolve("semantic-extract-sharded-output");
        ObjectNode scan = JSON.createObjectNode();
        scan.putObject("database").put("type", "mysql").put("catalog", "shop").put("schema", "");
        scan.put("generatedAt", "2026-07-05T00:00:00Z");
        scan.putObject("summary")
                .put("directRelationshipCount", 2)
                .put("derivedRelationshipCount", 0)
                .put("totalRelationshipCount", 2)
                .put("directDataLineageCount", 0)
                .put("derivedDataLineageCount", 0)
                .put("totalDataLineageCount", 0)
                .put("directNamingEvidenceCount", 0)
                .put("derivedNamingEvidenceCount", 0)
                .put("totalNamingEvidenceCount", 0)
                .put("warningCount", 0)
                .putArray("sources").add("logs");
        addRelationship(scan.withArray("relationships"),
                "orders", "customer_id", "customers", "id");
        addRelationship(scan.withArray("relationships"),
                "stock", "supplier_id", "suppliers", "id");
        for (String section : java.util.List.of("dataLineages", "derivedRelationships", "derivedDataLineages",
                "namingEvidence", "derivedNamingEvidence", "warnings")) {
            scan.putArray(section);
        }
        Files.writeString(input, JSON.writeValueAsString(scan));

        int exit = Main.run(new String[] {
                "extract",
                "--provider", "openai-api",
                "--input", input.toString(),
                "--output", output.toString(),
                "--request-only",
                "--shard-mode", "force",
                "--shard-max-output-tokens", "32123",
                "--reconciliation-max-output-tokens", "12345"
        });

        assertEquals(0, exit);
        Path published = onlyPublishedRun(output);
        assertTrue(Files.exists(published.resolve("shards/shard-0001/semantic-extraction-request.json")));
        assertTrue(Files.exists(published.resolve("shards/shard-0002/semantic-extraction-request.json")));
        assertTrue(Files.exists(published.resolve("deterministic-kg/semantic-kg.json")));
        assertTrue(Files.exists(published.resolve("deterministic-kg/semantic-evidence-graph.json")));
        assertTrue(Files.exists(published.resolve("deterministic-kg/semantic-build-run.json")));
        assertTrue(Files.exists(published.resolve(
                "reconciliation/template/semantic-extraction-request.json")));
        assertTrue(Files.readString(published.resolve(
                "reconciliation/template/semantic-extraction-prompt.md")).contains("\"template\" : true"));
        assertEquals(32123, JSON.readTree(published.resolve(
                "shards/shard-0001/semantic-extraction-request.json").toFile())
                .path("max_output_tokens").asInt());
        assertEquals(12345, JSON.readTree(published.resolve(
                "reconciliation/template/semantic-extraction-request.json").toFile())
                .path("max_output_tokens").asInt());
        JsonNode manifest = JSON.readTree(published.resolve("run-manifest.json").toFile());
        assertEquals(2, manifest.path("shardCount").asInt());
        assertEquals(4, manifest.path("ownedCandidateCount").asInt());
        assertEquals("gpt-5.6-sol", manifest.path("model").asText());
        assertEquals("xhigh", manifest.path("reasoningEffort").asText());
        assertTrue(hasArtifact(manifest, "deterministic-kg/semantic-kg.json"));
    }

    @Test
    void semanticE2eWritesKgAndExtractionArtifactsWithCanonicalEvents() throws Exception {
        Path input = tempDir.resolve("scan-result-e2e.json");
        Path output = tempDir.resolve("semantic-e2e");
        Files.writeString(input, """
                {
                  "database": {"type": "mysql", "schema": "shop"},
                  "generatedAt": "2026-07-05T00:00:00Z",
                  "summary": {"directRelationshipCount": 1, "derivedRelationshipCount": 0, "totalRelationshipCount": 1, "directDataLineageCount": 1, "derivedDataLineageCount": 0, "totalDataLineageCount": 1, "directNamingEvidenceCount": 0, "derivedNamingEvidenceCount": 0, "totalNamingEvidenceCount": 0, "warningCount": 0, "sources": ["object-files"]},
                  "relationships": [{
                    "source": {"table": "inventory_transactions", "column": "order_id"},
                    "target": {"table": "sales_orders", "column": "id"},
                    "relationType": "FK_LIKE",
                    "relationSubType": "DECLARED_FK",
                    "confidence": 0.9,
                    "evidence": [{"type": "DDL_FOREIGN_KEY", "sourceType": "DDL_FILE", "score": 0.9, "source": "schema.sql", "detail": "fk", "attributes": {}}],
                    "rawEvidence": [],
                    "warnings": []
                  }],
                  "dataLineages": [{
                    "sources": [{"table": "sales_orders", "column": "id"}],
                    "target": {"table": "inventory_transactions", "column": "order_id"},
                    "flowKind": "VALUE",
                    "transformType": "DIRECT",
                    "confidence": 0.9,
                    "evidence": [{
                      "type": "DATA_LINEAGE",
                      "transformType": "DIRECT",
                      "sourceType": "DATABASE_OBJECT",
                      "score": 0.9,
                      "source": "TRIGGER:trg_sales_order_delivered",
                      "detail": "insert transaction",
                      "attributes": {
                        "sourceObjectType": "TRIGGER",
                        "sourceObjectName": "trg_sales_order_delivered",
                        "sourceFile": "relation-detector/sample-data/mysql/8.0/01-schema/03-triggers.sql",
                        "sourceStatementId": "TRIGGER:trg_sales_order_delivered",
                        "mappingKind": "INSERT_SELECT"
                      }
                    }],
                    "rawEvidence": [],
                    "warnings": [],
                    "attributes": {}
                  }],
                  "derivedRelationships": [],
                  "derivedDataLineages": [],
                  "namingEvidence": [],
                  "derivedNamingEvidence": [],
                  "warnings": []
                }
                """);

        int exit = Main.run(new String[] {
                "e2e",
                "--input", input.toString(),
                "--output", output.toString(),
                "--name", "mysql-v8_0-full-derived"
        });

        assertEquals(0, exit);
        Path kgPath = output.resolve("semantic-kg/mysql-v8_0-full-derived/semantic-kg.json");
        Path bundlePath = output.resolve("semantic-extraction/mysql-v8_0-full-derived/semantic-extraction-evidence-bundle.json");
        assertTrue(Files.exists(kgPath));
        assertTrue(Files.exists(bundlePath));
        JsonNode kg = JSON.readTree(kgPath.toFile());
        JsonNode event = firstNodeOfType(kg, "Event");
        assertEquals("写入库存数据", event.path("label").asText());
        assertTrue(event.path("evidenceRefs").isArray());
        assertTrue(event.path("evidenceRefs").size() > 0);
        assertTrue(hasEdgeType(kg, "EVENT_INPUT"));
        assertTrue(hasEdgeType(kg, "EVENT_OUTPUT"));
        JsonNode bundle = JSON.readTree(bundlePath.toFile());
        assertEquals("TRIGGER", bundle.path("eventCandidates").get(0).path("sourceType").asText());
        assertEquals("trg_sales_order_delivered", bundle.path("eventCandidates").get(0).path("sourceObjectName").asText());
        assertEquals("写入库存数据", bundle.path("eventCandidates").get(0).path("readableNameHint").asText());
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
        assertTrue(Files.exists(onlyPublishedRun(output).resolve("semantic-extraction-codex-session.md")));
    }

    private JsonNode firstNodeOfType(JsonNode kg, String type) {
        for (JsonNode node : kg.path("nodes")) {
            if (type.equals(node.path("type").asText())) {
                return node;
            }
        }
        throw new AssertionError("missing node of type " + type);
    }

    private boolean hasEdgeType(JsonNode kg, String type) {
        for (JsonNode edge : kg.path("edges")) {
            if (type.equals(edge.path("type").asText())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasArtifact(JsonNode manifest, String path) {
        for (JsonNode artifact : manifest.path("artifacts")) {
            if (path.equals(artifact.path("path").asText())) {
                return true;
            }
        }
        return false;
    }

    private Path onlyPublishedRun(Path outputRoot) throws Exception {
        try (java.util.stream.Stream<Path> entries = Files.list(outputRoot)) {
            return entries.filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith("run-"))
                    .findFirst()
                    .orElseThrow();
        }
    }

    private void addRelationship(
            com.fasterxml.jackson.databind.node.ArrayNode relationships,
            String sourceTable,
            String sourceColumn,
            String targetTable,
            String targetColumn
    ) {
        ObjectNode relationship = relationships.addObject();
        relationship.set("source", endpoint(sourceTable, sourceColumn));
        relationship.set("target", endpoint(targetTable, targetColumn));
        relationship.put("relationType", "FK_LIKE");
        relationship.put("relationSubType", "INFERRED_JOIN_FK");
        relationship.put("confidence", 0.8);
        relationship.putArray("evidence").addObject()
                .put("type", "SQL_LOG_JOIN")
                .put("sourceType", "PLAIN_SQL")
                .put("score", 0.8)
                .put("source", "queries.sql")
                .put("detail", "join")
                .putObject("attributes");
        relationship.putArray("rawEvidence");
        relationship.putArray("warnings");
    }

    private ObjectNode endpoint(String table, String column) {
        return JSON.createObjectNode().put("table", table).put("column", column);
    }

    @Test
    void semanticNormalizeExtractionWritesRefClosedDocument() throws Exception {
        Path input = tempDir.resolve("semantic-extraction-result-raw.json");
        Path evidenceBundle = tempDir.resolve("semantic-extraction-evidence-bundle.json");
        Path output = tempDir.resolve("semantic-extraction-result.json");
        Files.writeString(input, """
                {
                  "entities": [
                    {"name": "销售事实表", "physicalName": "sales_fact", "type": "分析事实表", "evidenceRefs": ["sales_orders.id -> sales_fact.order_id"]},
                    {"name": "销售订单", "physicalName": "sales_orders", "type": "业务单据", "evidenceRefs": ["sales_orders.id -> sales_fact.order_id"]}
                  ],
                  "events": [
                    {"name": "重建销售事实表", "physicalName": "erp.sp_rebuild_sales_fact", "type": "数据加工事件", "eventCandidateRef": "event-candidate:routine:erp.sp_rebuild_sales_fact", "inputs": ["销售订单"], "outputs": ["销售事实表"], "evidenceRefs": ["event-candidate:routine:erp.sp_rebuild_sales_fact"]}
                  ],
                  "relations": [],
                  "lineage": [],
                  "metrics": [],
                  "dimensions": [],
                  "triplets": [],
                  "reviewItems": []
                }
                """);
        Files.writeString(evidenceBundle, """
                {
                  "tables": ["sales_fact", "sales_orders"],
                  "evidence": [{"id": "sales_orders.id -> sales_fact.order_id"}],
                  "relationships": [], "lineage": [], "derivedRelationships": [], "derivedLineage": [],
                  "namingEvidence": [], "diagnostics": [],
                  "eventCandidates": [{"id": "event-candidate:routine:erp.sp_rebuild_sales_fact"}],
                  "tripletCandidates": [], "reviewItemCandidates": []
                }
                """);

        int exit = Main.run(new String[] {
                "normalize-extraction",
                "--input", input.toString(),
                "--evidence-bundle", evidenceBundle.toString(),
                "--output", output.toString()
        });

        assertEquals(0, exit);
        JsonNode normalized = JSON.readTree(output.toFile());
        assertEquals("entity:sales_fact", normalized.path("entities").get(0).path("id").asText());
        assertEquals("entity:sales_orders", normalized.path("events").get(0).path("inputEntityRefs").get(0).asText());
        assertTrue(normalized.path("semanticGraph").path("nodes").isArray());
        assertTrue(normalized.path("validation").path("isRefClosed").isBoolean());
    }

    @Test
    void semanticNormalizeExtractionRejectsMissingEvidenceBundle() throws Exception {
        Path input = tempDir.resolve("semantic-extraction-result-raw.json");
        Path output = tempDir.resolve("semantic-extraction-result.json");
        Files.writeString(input, """
                {"entities": [], "events": [], "relations": [], "lineage": [], "metrics": [],
                 "dimensions": [], "triplets": [], "reviewItems": []}
                """);

        int exit = Main.run(new String[] {
                "normalize-extraction",
                "--input", input.toString(),
                "--output", output.toString()
        });

        assertEquals(2, exit);
        assertTrue(Files.notExists(output));
    }
}
