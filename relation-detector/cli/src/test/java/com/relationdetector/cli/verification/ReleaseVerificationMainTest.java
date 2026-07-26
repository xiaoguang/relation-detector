package com.relationdetector.cli.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

final class ReleaseVerificationMainTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void fingerprintCommandWritesHistoricalTsvShape() throws Exception {
        Path input = tempDir.resolve("input.json");
        Path output = tempDir.resolve("fingerprints.tsv");
        Files.writeString(input, "{\"generatedAt\":\"now\",\"facts\":[{\"id\":\"a\"}]}");

        assertEquals(0, ReleaseVerificationMain.run(new String[] {
                "fingerprint",
                "--workspace", tempDir.resolve("work").toString(),
                "--output", output.toString(),
                input.toString()
        }));

        String[] fields = Files.readString(output).strip().split("\\t", 2);
        assertEquals("24e6bbe5b9f1e37cdf54f1a4b0d8b56e7f39c13f8044f6f975af3d35c76cf380", fields[0]);
        assertEquals(input.toRealPath().toString(), fields[1]);
    }

    @Test
    void validateResultsWritesSmallClosedReport() throws Exception {
        Path results = tempDir.resolve("results");
        Files.createDirectories(results);
        String result = emptyResult();
        Files.writeString(results.resolve("example.json"), result);
        Files.writeString(results.resolve("example-derived-fresh.json"), result);
        Path output = tempDir.resolve("result-validation.json");

        assertEquals(0, ReleaseVerificationMain.run(new String[] {
                "validate-results",
                "--result-dir", results.toString(),
                "--expected-categories", "1",
                "--output", output.toString()
        }));

        JsonNode report = JSON.readTree(output.toFile());
        assertEquals("PASS", report.path("status").asText());
        assertEquals(1, report.path("categories").asInt());
        assertEquals(2, report.path("jsonFiles").asInt());
        assertEquals(0, report.path("diagnostics").asInt());
        assertEquals("PASS", report.path("integrity").path("evidenceRefs").asText());
        assertEquals("PASS", report.path("integrity").path("derivedCycles").asText());
    }

    @Test
    void invalidLastResultNeverWritesPassReport() throws Exception {
        Path results = tempDir.resolve("results");
        Files.createDirectories(results);
        Files.writeString(results.resolve("example.json"), emptyResult());
        Files.writeString(results.resolve("example-derived-fresh.json"),
                emptyResult().replace("\"warningCount\": 0", "\"warningCount\": 1"));
        Path output = tempDir.resolve("result-validation.json");

        assertThrows(ReleaseVerificationException.class,
                () -> ReleaseVerificationMain.run(new String[] {
                        "validate-results",
                        "--result-dir", results.toString(),
                        "--expected-categories", "1",
                        "--output", output.toString()
                }));
        if (Files.exists(output)) {
            assertEquals("FAIL", JSON.readTree(output.toFile()).path("status").asText());
        }
    }

    @Test
    void aggregateCorrectnessRequiresExactCategoryCoverage() throws Exception {
        Path first = tempDir.resolve("first.json");
        Path second = tempDir.resolve("second.json");
        Files.writeString(first, correctnessSummary(2, "mysql/root"));
        Files.writeString(second, correctnessSummary(3, "postgres/root"));
        Path output = tempDir.resolve("aggregate.json");

        assertEquals(0, ReleaseVerificationMain.run(new String[] {
                "aggregate-correctness",
                "--output", output.toString(),
                "--expected-category", "mysql/root",
                "--expected-category", "postgres/root",
                first.toString(),
                second.toString()
        }));

        JsonNode aggregate = JSON.readTree(output.toFile());
        assertEquals(5, aggregate.path("executed").asInt());
        assertEquals(5, aggregate.path("passed").asInt());
        assertEquals(2, aggregate.path("dialectVersions").size());
    }

    @Test
    void aggregateSampleReportsPreservesExpectedCaseOrder() throws Exception {
        Path firstOutput = tempDir.resolve("first.json");
        Path secondOutput = tempDir.resolve("second.json");
        Files.writeString(firstOutput, "{}");
        Files.writeString(secondOutput, "{}");
        Path first = tempDir.resolve("first-report.json");
        Path second = tempDir.resolve("second-report.json");
        Files.writeString(first, batchReport("second", secondOutput));
        Files.writeString(second, batchReport("first", firstOutput));
        Path output = tempDir.resolve("aggregate.json");

        assertEquals(0, ReleaseVerificationMain.run(new String[] {
                "aggregate-sample",
                "--output", output.toString(),
                "--expected-case", "first",
                "--expected-case", "second",
                first.toString(),
                second.toString()
        }));

        JsonNode aggregate = JSON.readTree(output.toFile());
        assertEquals("first", aggregate.path("cases").get(0).path("id").asText());
        assertEquals("second", aggregate.path("cases").get(1).path("id").asText());
        assertEquals(2, aggregate.path("summary").path("successCount").asInt());
    }

    @Test
    void manifestConsumesTheValidationReportInsteadOfLargeResults() throws Exception {
        Path verification = tempDir.resolve("verification");
        Files.createDirectories(verification);
        Path validation = verification.resolve("result-validation.json");
        Files.writeString(validation, """
                {
                  "status":"PASS",
                  "categories":1,
                  "jsonFiles":2,
                  "diagnostics":0,
                  "integrity":{
                    "evidenceRefs":"PASS",
                    "sourcePaths":"PASS",
                    "sourceLines":"PASS",
                    "rawObservationDuplicates":"PASS",
                    "derivedCycles":"PASS"
                  }
                }
                """);
        Path correctness = verification.resolve("correctness.json");
        Files.writeString(correctness,
                "{\"discovered\":1,\"selected\":1,\"executed\":1,\"passed\":1,\"failed\":0}");
        Path parity = verification.resolve("parity.tsv");
        Files.writeString(parity, """
                Pair\tToken\tFull\tTokenOnly\tFullOnly
                mysql\t1\t1\t0\t0
                postgres\t1\t1\t0\t0
                oracle\t1\t1\t0\t0
                sqlserver\t1\t1\t0\t0
                """);
        Path warnings = verification.resolve("warnings.tsv");
        Files.writeString(warnings, "parser\twarningCode\tcount\nexample\tNONE\t0\n");
        Path fingerprints = verification.resolve("fingerprints.tsv");
        Files.writeString(fingerprints, "aaa\texample.json\nbbb\texample-derived-fresh.json\n");
        Path semantic = verification.resolve("semantic-fingerprints.tsv");
        Files.copy(fingerprints, semantic);
        Path output = verification.resolve("verification-manifest.json");

        assertEquals(0, ReleaseVerificationMain.run(new String[] {
                "manifest",
                "--verification-dir", verification.toString(),
                "--result-validation", validation.toString(),
                "--correctness-summary", correctness.toString(),
                "--observation-parity", parity.toString(),
                "--warning-codes", warnings.toString(),
                "--fingerprints", fingerprints.toString(),
                "--semantic-fingerprints", semantic.toString(),
                "--commit", "test-commit",
                "--branch", "test-branch",
                "--origin-main", "test-commit",
                "--worktree-clean", "true",
                "--maven-status", "0",
                "--expected-fixtures", "1",
                "--expected-categories", "1",
                "--expected-json", "2",
                "--artifact", validation.toString(),
                "--output", output.toString()
        }));

        JsonNode manifest = JSON.readTree(output.toFile());
        assertEquals("PASS", manifest.path("status").asText());
        assertEquals(1, manifest.path("parserMatrix").path("categories").asInt());
        assertEquals("PASS", manifest.path("integrity").path("evidenceRefs").asText());
        assertEquals(1, manifest.path("artifacts").size());
    }

    @Test
    void failureManifestUsesFixedMessageAndHashesTheLog() throws Exception {
        Path log = tempDir.resolve("failure.log");
        Files.writeString(log, "private compiler output");
        Path output = tempDir.resolve("verification-manifest.json");

        assertEquals(0, ReleaseVerificationMain.run(new String[] {
                "failure-manifest",
                "--output", output.toString(),
                "--phase", "noCache",
                "--status", "7",
                "--message", "no-cache acceptance failed",
                "--commit", "head",
                "--branch", "main",
                "--artifact", log.toString()
        }));

        JsonNode manifest = JSON.readTree(output.toFile());
        assertEquals("FAIL", manifest.path("status").asText());
        assertEquals("noCache", manifest.path("failedPhase").asText());
        assertEquals("no-cache acceptance failed", manifest.path("errors").get(0).asText());
        assertEquals(64, manifest.path("artifacts").get(0).path("sha256").asText().length());
        assertEquals(false, manifest.toString().contains("private compiler output"));
    }

    @Test
    void sampleSummaryStreamsDirectAndDerivedCounts() throws Exception {
        Path results = tempDir.resolve("results");
        Path configs = tempDir.resolve("configs");
        Files.createDirectories(results);
        Files.createDirectories(configs);
        Files.writeString(configs.resolve("example.yml"), """
                sources:
                  ddl:
                    files:
                      - ddl.sql
                  objects:
                    files:
                      - object.sql
                  logs:
                    files:
                      - query.sql
                """);
        Files.writeString(results.resolve("example.json"), resultWithCounts(2, 3, 4, 0, 0, 0));
        Files.writeString(results.resolve("example-derived-fresh.json"),
                resultWithCounts(2, 3, 4, 5, 6, 7));
        Path summary = tempDir.resolve("summary.tsv");
        Path derived = tempDir.resolve("summary-with-derived.tsv");
        Path warnings = tempDir.resolve("warning-codes.tsv");

        assertEquals(0, ReleaseVerificationMain.run(new String[] {
                "sample-summary",
                "--result-dir", results.toString(),
                "--config-dir", configs.toString(),
                "--summary", summary.toString(),
                "--derived-summary", derived.toString(),
                "--warnings", warnings.toString(),
                "--requested-case", "example"
        }));

        assertEquals(
                "parser\tfixtures\tSQL / DDL\trelations\tlineage\tnamingEvidence\twarnings\tsources\tjson\n"
                        + "example\t3\t2 / 1\t2\t3\t4\t0\t\t"
                        + results.resolve("example.json") + "\n",
                Files.readString(summary));
        assertEquals(
                "Parser\tFix\tSQL/DDL\tRel\tLin\tName\tDiag\tDerRel\tDerLin\tDerName\n"
                        + "example\t3\t2 / 1\t2\t3\t4\t0\t5\t6\t7\n",
                Files.readString(derived));
        assertEquals("parser\twarningCode\tcount\nexample\tNONE\t0\n", Files.readString(warnings));
    }

    @Test
    void parserSummaryCheckAndUpdateUseTheSameRows() throws Exception {
        Path summary = tempDir.resolve("summary.tsv");
        Files.writeString(summary, completeParserSummary());
        Path document = tempDir.resolve("comparison.md");
        Files.writeString(document, parserComparisonDocument());

        assertThrows(ReleaseVerificationException.class,
                () -> ReleaseVerificationMain.run(new String[] {
                        "parser-summary",
                        "--summary", summary.toString(),
                        "--document", document.toString()
                }));
        assertEquals(0, ReleaseVerificationMain.run(new String[] {
                "parser-summary",
                "--summary", summary.toString(),
                "--document", document.toString(),
                "--update"
        }));
        assertEquals(0, ReleaseVerificationMain.run(new String[] {
                "parser-summary",
                "--summary", summary.toString(),
                "--document", document.toString()
        }));
    }

    @Test
    void environmentReportDoesNotDescribePythonRuntime() throws Exception {
        Path output = tempDir.resolve("environment.json");

        assertEquals(0, ReleaseVerificationMain.run(new String[] {
                "environment",
                "--output", output.toString(),
                "--commit", "head",
                "--branch", "branch",
                "--origin-main", "origin",
                "--maven-bin", "mvn"
        }));

        JsonNode environment = JSON.readTree(output.toFile());
        assertEquals("head", environment.path("commit").asText());
        assertEquals(false, environment.has("python"));
        assertEquals(true, environment.path("java").asText().contains("version"));
        assertEquals(true, environment.path("maven").asText().contains("Apache Maven"));
    }

    @Test
    void performanceReportPreservesCompactReportContract() throws Exception {
        Path surefire = tempDir.resolve("module/target/surefire-reports");
        Files.createDirectories(surefire);
        Files.writeString(surefire.resolve("TEST-example.xml"),
                "<testsuite name=\"example\" tests=\"2\" failures=\"0\" errors=\"0\" skipped=\"1\" time=\"1.5\"/>");
        Path logs = tempDir.resolve("logs");
        Files.createDirectories(logs);
        Files.writeString(logs.resolve("batch.log"),
                "case=example elapsedSeconds=7 status=0\n");
        Path batch = tempDir.resolve("batch.json");
        Files.writeString(batch,
                "{\"summary\":{\"caseCount\":1},\"cases\":[{\"id\":\"example\","
                        + "\"status\":\"SUCCESS\",\"elapsedMillis\":7000}]}");
        Path correctness = tempDir.resolve("correctness.json");
        Files.writeString(correctness, "{\"executed\":1,\"passed\":1}");
        Path fingerprints = tempDir.resolve("fingerprints.tsv");
        Files.writeString(fingerprints, "abc\t/path/example.json\n");
        Path maven = tempDir.resolve("maven.log");
        Files.writeString(maven, """
                [INFO] Processing grammar: Example.g4
                slow correctness fixture manifest.yml 123 ms
                [INFO] module-name ........ SUCCESS [ 1.0 s ]
                """);
        Path output = tempDir.resolve("performance.json");

        assertEquals(0, ReleaseVerificationMain.run(new String[] {
                "performance",
                "--session-start", "0",
                "--surefire-root", tempDir.toString(),
                "--cli-log-root", logs.toString(),
                "--cli-report", batch.toString(),
                "--correctness-summary", correctness.toString(),
                "--fingerprints", fingerprints.toString(),
                "--semantic-fingerprints", fingerprints.toString(),
                "--maven-log", maven.toString(),
                "--output", output.toString()
        }));

        JsonNode report = JSON.readTree(output.toFile());
        assertEquals(2, report.path("tests").path("total").asInt());
        assertEquals(7, report.path("cliCases").get(0).path("elapsedSeconds").asInt());
        assertEquals(1, report.path("maven").path("antlrGrammarProcessCount").asInt());
        assertEquals(1, report.path("canonicalFingerprints").path("count").asInt());
    }

    private String emptyResult() {
        return """
                {
                  "summary": {
                    "directRelationshipCount": 0,
                    "derivedRelationshipCount": 0,
                    "totalRelationshipCount": 0,
                    "directDataLineageCount": 0,
                    "derivedDataLineageCount": 0,
                    "totalDataLineageCount": 0,
                    "directNamingEvidenceCount": 0,
                    "derivedNamingEvidenceCount": 0,
                    "totalNamingEvidenceCount": 0,
                    "warningCount": 0
                  },
                  "relationships": [],
                  "derivedRelationships": [],
                  "dataLineages": [],
                  "derivedDataLineages": [],
                  "namingEvidence": [],
                  "derivedNamingEvidence": [],
                  "warnings": []
                }
                """;
    }

    private String resultWithCounts(
            int relationships,
            int lineage,
            int naming,
            int derivedRelationships,
            int derivedLineage,
            int derivedNaming
    ) {
        return """
                {
                  "summary": {
                    "directRelationshipCount": %d,
                    "derivedRelationshipCount": %d,
                    "totalRelationshipCount": %d,
                    "directDataLineageCount": %d,
                    "derivedDataLineageCount": %d,
                    "totalDataLineageCount": %d,
                    "directNamingEvidenceCount": %d,
                    "derivedNamingEvidenceCount": %d,
                    "totalNamingEvidenceCount": %d,
                    "warningCount": 0
                  },
                  "sources": [],
                  "relationships": [],
                  "derivedRelationships": [],
                  "dataLineages": [],
                  "derivedDataLineages": [],
                  "namingEvidence": [],
                  "derivedNamingEvidence": [],
                  "warnings": []
                }
                """.formatted(
                relationships, derivedRelationships, relationships + derivedRelationships,
                lineage, derivedLineage, lineage + derivedLineage,
                naming, derivedNaming, naming + derivedNaming);
    }

    private String completeParserSummary() {
        StringBuilder result = new StringBuilder(
                "Parser\tFix\tSQL/DDL\tRel\tLin\tName\tDiag\tDerRel\tDerLin\tDerName\n");
        for (String id : parserIds()) {
            result.append(id).append("\t1\t1 / 0\t2\t3\t4\t0\t5\t6\t7\n");
        }
        return result.toString();
    }

    private String parserComparisonDocument() {
        StringBuilder result = new StringBuilder();
        for (String name : parserDisplayNames()) {
            result.append("| ").append(name).append(" | stale | stale | stale | stale | stale | stale |\n");
            result.append("| ").append(name)
                    .append(" | stale | stale | stale | stale | stale | stale | stale | stale | stale |\n");
        }
        return result.toString();
    }

    private String[] parserIds() {
        return new String[] {
                "common-token-event-sample-data",
                "mysql-token-event-root", "mysql-v5_7-full", "mysql-v8_0-full",
                "postgres-token-event-root", "postgres-v16-full", "postgres-v17-full",
                "postgres-v18-full", "oracle-token-event-root", "oracle-v12c-full",
                "oracle-v19c-full", "oracle-v21c-full", "oracle-v26ai-full",
                "sqlserver-token-event-root", "sqlserver-v2016-full", "sqlserver-v2017-full",
                "sqlserver-v2019-full", "sqlserver-v2022-full", "sqlserver-v2025-full"
        };
    }

    private String[] parserDisplayNames() {
        return new String[] {
                "common token-event sample-data",
                "MySQL token-event root sample-data", "MySQL full-grammar v5_7 sample-data",
                "MySQL full-grammar v8_0 sample-data", "PostgreSQL token-event root sample-data",
                "PostgreSQL full-grammar v16 sample-data", "PostgreSQL full-grammar v17 sample-data",
                "PostgreSQL full-grammar v18 sample-data", "Oracle token-event root sample-data",
                "Oracle full-grammar v12c sample-data", "Oracle full-grammar v19c sample-data",
                "Oracle full-grammar v21c sample-data", "Oracle full-grammar v26ai sample-data",
                "SQL Server token-event root sample-data", "SQL Server full-grammar v2016 sample-data",
                "SQL Server full-grammar v2017 sample-data", "SQL Server full-grammar v2019 sample-data",
                "SQL Server full-grammar v2022 sample-data", "SQL Server full-grammar v2025 sample-data"
        };
    }

    private String correctnessSummary(int count, String category) {
        return """
                {
                  "discovered": 5,
                  "selected": %d,
                  "executed": %d,
                  "passed": %d,
                  "failed": 0,
                  "elapsedMillis": 10,
                  "dialectVersions": [{"id": "%s", "executed": %d, "passed": %d}]
                }
                """.formatted(count, count, count, category, count, count);
    }

    private String batchReport(String id, Path output) {
        return """
                {
                  "summary":{"caseCount":1,"successCount":1,"failedCount":0,"skippedCount":0},
                  "cases":[
                    {
                      "id":"%s",
                      "status":"SUCCESS",
                      "elapsedMillis":10,
                      "output":"%s",
                      "directOutput":"%s"
                    }
                  ]
                }
                """.formatted(id, output, output);
    }
}
