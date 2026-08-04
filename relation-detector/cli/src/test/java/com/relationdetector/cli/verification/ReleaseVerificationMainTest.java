package com.relationdetector.cli.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

final class ReleaseVerificationMainTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void fingerprintCommandUsesTheCompleteBundleRelativePathAsLogicalIdentity() throws Exception {
        Path results = tempDir.resolve("results");
        Path input = results.resolve("example/result.json");
        Files.createDirectories(input.getParent());
        Path output = tempDir.resolve("fingerprints.tsv");
        Files.writeString(input, "{\"generatedAt\":\"now\",\"facts\":[{\"id\":\"a\"}]}");

        ReleaseVerificationMain.run(new String[] {
                "fingerprint",
                "--workspace", tempDir.resolve("work").toString(),
                "--output", output.toString(),
                results.toString()
        });

        String[] fields = Files.readString(output).strip().split("\\t", 2);
        assertEquals("24e6bbe5b9f1e37cdf54f1a4b0d8b56e7f39c13f8044f6f975af3d35c76cf380", fields[0]);
        assertEquals("example/result.json", fields[1]);
    }

    @Test
    void fingerprintCommandPreservesExplicitFileRealPathIdentity() throws Exception {
        Path input = tempDir.resolve("input.json");
        Path output = tempDir.resolve("explicit-fingerprints.tsv");
        Files.writeString(input, "{\"facts\":[]}");

        ReleaseVerificationMain.run(new String[] {
                "fingerprint",
                "--workspace", tempDir.resolve("explicit-work").toString(),
                "--output", output.toString(),
                input.toString()
        });

        String[] fields = Files.readString(output).strip().split("\\t", 2);
        assertEquals(input.toRealPath().toString(), fields[1]);
    }

    @Test
    void validateResultsWritesSmallClosedReport() throws Exception {
        Path results = tempDir.resolve("results");
        Files.createDirectories(results);
        String result = emptyResult();
        writeBundle(results, "example", result, result);
        Path output = tempDir.resolve("result-validation.json");

        ReleaseVerificationMain.run(new String[] {
                "validate-results",
                "--result-dir", results.toString(),
                "--expected-categories", "1",
                "--output", output.toString()
        });

        JsonNode report = JSON.readTree(output.toFile());
        assertEquals("PASS", report.path("status").asText());
        assertEquals(1, report.path("categories").asInt());
        assertEquals(2, report.path("jsonFiles").asInt());
        assertEquals(0, report.path("diagnostics").asInt());
        assertEquals(0, report.path("validatedSourceLocationCount").asInt());
        assertEquals("PASS", report.path("integrity").path("evidenceRefs").asText());
        assertEquals("PASS",
                report.path("integrity").path("providedSourceLocationsValid").asText());
        assertEquals(true, report.path("integrity").path("sourceLines").isMissingNode());
        assertEquals("PASS", report.path("integrity").path("derivedCycles").asText());
    }

    @Test
    void validateResultsRequiresEveryWriterSummaryCount() throws Exception {
        Path results = tempDir.resolve("missing-summary-field-results");
        Files.createDirectories(results);
        String missing = emptyResult().replace("\"warningCount\": 0", "\"omittedWarningCount\": 0");
        writeBundle(results, "example", missing, missing);

        assertThrows(ReleaseVerificationException.class,
                () -> ReleaseVerificationMain.run(new String[] {
                        "validate-results",
                        "--result-dir", results.toString(),
                        "--expected-categories", "1",
                        "--output", tempDir.resolve("missing-summary-field-report.json").toString()
                }));
    }

    @Test
    void validateResultsRejectsLegacyFlatJsonOutsideBundles() throws Exception {
        Path results = tempDir.resolve("legacy-flat-results");
        Files.createDirectories(results);
        writeBundle(results, "example", emptyResult(), emptyResult());
        Files.writeString(results.resolve("example-derived-fresh.json"), emptyResult());

        assertThrows(ReleaseVerificationException.class,
                () -> ReleaseVerificationMain.run(new String[] {
                        "validate-results",
                        "--result-dir", results.toString(),
                        "--expected-categories", "1",
                        "--output", tempDir.resolve("legacy-flat-validation.json").toString()
                }));
    }

    @Test
    void validateResultsRejectsAliasDirectorySymlinksBeforeSelectingJsonLeaves() throws Exception {
        Path results = tempDir.resolve("alias-directory-results");
        Files.createDirectories(results);
        writeBundle(results, "example", emptyResult(), emptyResult());
        Path external = tempDir.resolve("external-alias-bundle");
        writeBundleFiles(external, emptyResult(), emptyResult());
        Files.createSymbolicLink(results.resolve("alias"), external);

        assertThrows(ReleaseVerificationException.class,
                () -> ReleaseVerificationMain.run(new String[] {
                        "validate-results",
                        "--result-dir", results.toString(),
                        "--expected-categories", "1",
                        "--output", tempDir.resolve("alias-directory-validation.json").toString()
                }));
    }

    @Test
    void validateResultsRejectsUnexpectedSymlinksInsideBundles() throws Exception {
        Path results = tempDir.resolve("bundle-symlink-results");
        Files.createDirectories(results);
        writeBundle(results, "example", emptyResult(), emptyResult());
        Path external = tempDir.resolve("external-link.txt");
        Files.writeString(external, "outside");
        Files.createSymbolicLink(results.resolve("example/link.txt"), external);

        assertThrows(ReleaseVerificationException.class,
                () -> ReleaseVerificationMain.run(new String[] {
                        "validate-results",
                        "--result-dir", results.toString(),
                        "--expected-categories", "1",
                        "--output", tempDir.resolve("bundle-symlink-validation.json").toString()
                }));
    }

    @Test
    void validateResultsAcceptsLinearDerivedEvidenceSets() throws Exception {
        Path results = tempDir.resolve("derived-evidence-set-results");
        Files.createDirectories(results);
        String direct = resultWithExpandedInventory();
        String derived = resultWithDerivedEvidenceSet(false);
        writeBundle(results, "example", direct, derived);

        ReleaseVerificationMain.run(new String[] {
                "validate-results",
                "--result-dir", results.toString(),
                "--expected-categories", "1",
                "--output", tempDir.resolve("derived-evidence-set-report.json").toString()
        });
    }

    @Test
    void validateResultsRejectsLegacyDerivedRawEvidence() throws Exception {
        Path results = tempDir.resolve("legacy-derived-evidence-results");
        Files.createDirectories(results);
        writeBundle(results, "example", resultWithExpandedInventory(),
                resultWithDerivedEvidenceSet(true));

        assertThrows(ReleaseVerificationException.class,
                () -> ReleaseVerificationMain.run(new String[] {
                        "validate-results",
                        "--result-dir", results.toString(),
                        "--expected-categories", "1",
                        "--output", tempDir.resolve("legacy-derived-report.json").toString()
                }));
    }

    @Test
    void validateResultsRejectsInventoryThatWasNotBuiltFromDdl() throws Exception {
        Path results = tempDir.resolve("not-requested-inventory-results");
        Files.createDirectories(results);
        String missing = emptyResult()
                .replace("\"status\": \"COMPLETE\"", "\"status\": \"NOT_REQUESTED\"")
                .replace("\"basis\": \"DDL_DECLARATIONS\"", "\"basis\": \"NONE\"");
        writeBundle(results, "example", missing, missing);

        assertThrows(ReleaseVerificationException.class,
                () -> ReleaseVerificationMain.run(new String[] {
                        "validate-results",
                        "--result-dir", results.toString(),
                        "--expected-categories", "1",
                        "--output", tempDir.resolve("not-requested-inventory-report.json").toString()
                }));
    }

    @Test
    void validateResultsRejectsDifferentDirectAndDerivedInventories() throws Exception {
        Path results = tempDir.resolve("inventory-mismatch-results");
        Files.createDirectories(results);
        writeBundle(results, "example", emptyResult(),
                emptyResult().replace("\"schema\": \"sample_data\"", "\"schema\": \"other\""));

        assertThrows(ReleaseVerificationException.class,
                () -> ReleaseVerificationMain.run(new String[] {
                        "validate-results",
                        "--result-dir", results.toString(),
                        "--expected-categories", "1",
                        "--output", tempDir.resolve("inventory-mismatch-report.json").toString()
                }));
    }

    @Test
    void invalidLastResultNeverWritesPassReport() throws Exception {
        Path results = tempDir.resolve("results");
        Files.createDirectories(results);
        writeBundle(results, "example", emptyResult(),
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

        ReleaseVerificationMain.run(new String[] {
                "aggregate-correctness",
                "--output", output.toString(),
                "--expected-category", "mysql/root",
                "--expected-category", "postgres/root",
                first.toString(),
                second.toString()
        });

        JsonNode aggregate = JSON.readTree(output.toFile());
        assertEquals(5, aggregate.path("executed").asInt());
        assertEquals(5, aggregate.path("passed").asInt());
        assertEquals(2, aggregate.path("dialectVersions").size());
    }

    @Test
    void aggregateSampleReportsPreservesExpectedCaseOrder() throws Exception {
        Path firstOutput = tempDir.resolve("results/first");
        Path secondOutput = tempDir.resolve("results/second");
        writeBundleFiles(firstOutput, "{}", "{}");
        writeBundleFiles(secondOutput, "{}", "{}");
        Path first = tempDir.resolve("first-report.json");
        Path second = tempDir.resolve("second-report.json");
        Files.writeString(first, batchReport("second", secondOutput));
        Files.writeString(second, batchReport("first", firstOutput));
        Path output = tempDir.resolve("aggregate.json");

        ReleaseVerificationMain.run(new String[] {
                "aggregate-sample",
                "--output", output.toString(),
                "--expected-case", "first",
                "--expected-case", "second",
                first.toString(),
                second.toString()
        });

        JsonNode aggregate = JSON.readTree(output.toFile());
        assertEquals(2, aggregate.path("artifactSchemaVersion").asInt());
        assertEquals("first", aggregate.path("cases").get(0).path("id").asText());
        assertEquals("second", aggregate.path("cases").get(1).path("id").asText());
        assertEquals(2, aggregate.path("summary").path("successCount").asInt());
    }

    @Test
    void aggregateSampleReportsRejectsAnIncompleteOutputBundle() throws Exception {
        Path bundle = tempDir.resolve("results/incomplete");
        Files.createDirectories(bundle);
        Files.writeString(bundle.resolve("result.json"), "{}");
        Path report = tempDir.resolve("incomplete-report.json");
        Files.writeString(report, batchReport("incomplete", bundle));

        assertThrows(ReleaseVerificationException.class,
                () -> ReleaseVerificationMain.run(new String[] {
                        "aggregate-sample",
                        "--output", tempDir.resolve("aggregate-incomplete.json").toString(),
                        "--expected-case", "incomplete",
                        report.toString()
                }));
    }

    @Test
    void aggregateSampleReportsRejectsSuccessCountThatDoesNotMatchSuccessfulCases() throws Exception {
        Path bundle = tempDir.resolve("results/example");
        writeBundleFiles(bundle, "{}", "{}");
        Path report = tempDir.resolve("wrong-success-count-report.json");
        Files.writeString(report,
                batchReport("example", bundle).replace("\"successCount\":1", "\"successCount\":0"));

        assertThrows(ReleaseVerificationException.class,
                () -> ReleaseVerificationMain.run(new String[] {
                        "aggregate-sample",
                        "--output", tempDir.resolve("wrong-success-count-aggregate.json").toString(),
                        "--expected-case", "example",
                        report.toString()
                }));
    }

    @Test
    void aggregateSampleReportsRejectsOutputBundleBoundToAnotherId() throws Exception {
        Path bundle = tempDir.resolve("results/other");
        writeBundleFiles(bundle, "{}", "{}");
        Path report = tempDir.resolve("wrong-bound-bundle-report.json");
        Files.writeString(report, batchReport("expected", bundle));

        assertThrows(ReleaseVerificationException.class,
                () -> ReleaseVerificationMain.run(new String[] {
                        "aggregate-sample",
                        "--output", tempDir.resolve("wrong-bound-bundle-aggregate.json").toString(),
                        "--expected-case", "expected",
                        report.toString()
                }));
    }

    @Test
    void aggregateSampleReportsRejectsOutputBundleOutsideResultsDirectory() throws Exception {
        Path bundle = tempDir.resolve("external/example");
        writeBundleFiles(bundle, "{}", "{}");
        Path report = tempDir.resolve("external-bundle-report.json");
        Files.writeString(report, batchReport("example", bundle));

        assertThrows(ReleaseVerificationException.class,
                () -> ReleaseVerificationMain.run(new String[] {
                        "aggregate-sample",
                        "--output", tempDir.resolve("external-bundle-aggregate.json").toString(),
                        "--expected-case", "example",
                        report.toString()
                }));
    }

    @Test
    void aggregateSampleReportsRejectsPathLikeCaseIdThatEscapesResultsDirectory() throws Exception {
        Path bundle = tempDir.resolve("external-case");
        writeBundleFiles(bundle, "{}", "{}");
        Path report = tempDir.resolve("escaping-id-report.json");
        Files.writeString(report, batchReport("../external-case", bundle));

        assertThrows(ReleaseVerificationException.class,
                () -> ReleaseVerificationMain.run(new String[] {
                        "aggregate-sample",
                        "--output", tempDir.resolve("escaping-id-aggregate.json").toString(),
                        "--expected-case", "../external-case",
                        report.toString()
                }));
    }

    @Test
    void aggregateSampleReportsRejectsSymlinkOutputBundle() throws Exception {
        Path external = tempDir.resolve("external-symlink-bundle");
        writeBundleFiles(external, "{}", "{}");
        Path bundle = tempDir.resolve("results/example");
        Files.createDirectories(bundle.getParent());
        Files.createSymbolicLink(bundle, external);
        Path report = tempDir.resolve("symlink-bundle-report.json");
        Files.writeString(report, batchReport("example", bundle));

        assertThrows(ReleaseVerificationException.class,
                () -> ReleaseVerificationMain.run(new String[] {
                        "aggregate-sample",
                        "--output", tempDir.resolve("symlink-bundle-aggregate.json").toString(),
                        "--expected-case", "example",
                        report.toString()
                }));
    }

    @Test
    void aggregateSampleReportsRejectsSymlinkBundleLeaf() throws Exception {
        Path bundle = tempDir.resolve("results/example");
        Files.createDirectories(bundle);
        Files.writeString(bundle.resolve("result.json"), "{}");
        Path external = tempDir.resolve("external-direct.json");
        Files.writeString(external, "{}");
        Files.createSymbolicLink(bundle.resolve("direct.json"), external);
        Path report = tempDir.resolve("symlink-leaf-report.json");
        Files.writeString(report, batchReport("example", bundle));

        assertThrows(ReleaseVerificationException.class,
                () -> ReleaseVerificationMain.run(new String[] {
                        "aggregate-sample",
                        "--output", tempDir.resolve("symlink-leaf-aggregate.json").toString(),
                        "--expected-case", "example",
                        report.toString()
                }));
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
                    "providedSourceLocationsValid":"PASS",
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
        Files.writeString(fingerprints, "aaa\texample/direct.json\nbbb\texample/result.json\n");
        Path semantic = verification.resolve("semantic-fingerprints.tsv");
        Files.copy(fingerprints, semantic);
        Path output = verification.resolve("verification-manifest.json");

        ReleaseVerificationMain.run(new String[] {
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
        });

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

        ReleaseVerificationMain.run(new String[] {
                "failure-manifest",
                "--output", output.toString(),
                "--phase", "noCache",
                "--status", "7",
                "--message", "no-cache acceptance failed",
                "--commit", "head",
                "--branch", "main",
                "--artifact", log.toString()
        });

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
        writeBundle(results, "example",
                resultWithCounts(2, 3, 4, 0, 0, 0),
                resultWithCounts(2, 3, 4, 5, 6, 7));
        Path summary = tempDir.resolve("summary.tsv");
        Path derived = tempDir.resolve("summary-with-derived.tsv");
        Path warnings = tempDir.resolve("warning-codes.tsv");

        ReleaseVerificationMain.run(new String[] {
                "sample-summary",
                "--result-dir", results.toString(),
                "--config-dir", configs.toString(),
                "--summary", summary.toString(),
                "--derived-summary", derived.toString(),
                "--warnings", warnings.toString(),
                "--requested-case", "example"
        });

        assertEquals(
                "parser\tfixtures\tSQL / DDL\trelations\tlineage\tnamingEvidence\twarnings\tsources\tjson\n"
                        + "example\t3\t2 / 1\t2\t3\t4\t0\t\t"
                        + results.resolve("example/direct.json") + "\n",
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
        ReleaseVerificationMain.run(new String[] {
                "parser-summary",
                "--summary", summary.toString(),
                "--document", document.toString(),
                "--update"
        });
        ReleaseVerificationMain.run(new String[] {
                "parser-summary",
                "--summary", summary.toString(),
                "--document", document.toString()
        });
    }

    @Test
    void environmentReportDoesNotDescribePythonRuntime() throws Exception {
        Path output = tempDir.resolve("environment.json");

        ReleaseVerificationMain.run(new String[] {
                "environment",
                "--output", output.toString(),
                "--commit", "head",
                "--branch", "branch",
                "--origin-main", "origin",
                "--maven-bin", "mvn"
        });

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
        Files.writeString(fingerprints, "abc\tcase/direct.json\n");
        Path maven = tempDir.resolve("maven.log");
        Files.writeString(maven, """
                [INFO] Processing grammar: Example.g4
                slow correctness fixture manifest.yml 123 ms
                [INFO] module-name ........ SUCCESS [ 1.0 s ]
                """);
        Path output = tempDir.resolve("performance.json");

        ReleaseVerificationMain.run(new String[] {
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
        });

        JsonNode report = JSON.readTree(output.toFile());
        assertEquals(2, report.path("tests").path("total").asInt());
        assertEquals(7, report.path("cliCases").get(0).path("elapsedSeconds").asInt());
        assertEquals(1, report.path("maven").path("antlrGrammarProcessCount").asInt());
        assertEquals(1, report.path("canonicalFingerprints").path("count").asInt());
        assertEquals("case/direct.json",
                report.path("canonicalFingerprints").path("items").get(0).path("name").asText());
    }

    private String emptyResult() {
        return """
                {
                  "metadataInventory": {
                    "status": "COMPLETE",
                    "basis": "DDL_DECLARATIONS",
                    "scope": {
                      "catalog": null,
                      "schema": "sample_data",
                      "includeTables": [],
                      "excludeTables": []
                    },
                    "counts": {
                      "tables": 1,
                      "columns": 1,
                      "constraints": 0,
                      "indexes": 0
                    },
                    "tables": [
                      {
                        "catalog": null,
                        "schema": "sample_data",
                        "tableName": "orders",
                        "tableType": "TABLE",
                        "engine": null,
                        "comment": null
                      }
                    ],
                    "columns": [
                      {
                        "catalog": null,
                        "schema": "sample_data",
                        "tableName": "orders",
                        "columnName": "id",
                        "dataType": "UNKNOWN",
                        "columnType": "UNKNOWN",
                        "nullable": false,
                        "defaultValue": null,
                        "extra": "",
                        "generationExpression": "",
                        "ordinalPosition": 1
                      }
                    ],
                    "constraints": [],
                    "indexes": []
                  },
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

    private void writeBundle(Path results, String id, String direct, String result) throws Exception {
        writeBundleFiles(results.resolve(id), direct, result);
    }

    private void writeBundleFiles(Path bundle, String direct, String result) throws Exception {
        Files.createDirectories(bundle);
        Files.writeString(bundle.resolve("direct.json"), direct);
        Files.writeString(bundle.resolve("result.json"), result);
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
                  "metadataInventory": {
                    "status": "COMPLETE",
                    "basis": "DDL_DECLARATIONS",
                    "scope": {
                      "catalog": null,
                      "schema": "sample_data",
                      "includeTables": [],
                      "excludeTables": []
                    },
                    "counts": {
                      "tables": 1,
                      "columns": 1,
                      "constraints": 0,
                      "indexes": 0
                    },
                    "tables": [
                      {
                        "catalog": null,
                        "schema": "sample_data",
                        "tableName": "orders",
                        "tableType": "TABLE",
                        "engine": null,
                        "comment": null
                      }
                    ],
                    "columns": [
                      {
                        "catalog": null,
                        "schema": "sample_data",
                        "tableName": "orders",
                        "columnName": "id",
                        "dataType": "UNKNOWN",
                        "columnType": "UNKNOWN",
                        "nullable": false,
                        "defaultValue": null,
                        "extra": "",
                        "generationExpression": "",
                        "ordinalPosition": 1
                      }
                    ],
                    "constraints": [],
                    "indexes": []
                  },
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

    private String resultWithDerivedEvidenceSet(boolean includeLegacyRawEvidence) throws Exception {
        ObjectNode root = (ObjectNode) JSON.readTree(resultWithExpandedInventory());
        ObjectNode summary = (ObjectNode) root.path("summary");
        summary.put("derivedRelationshipCount", 1);
        summary.put("totalRelationshipCount", 1);
        summary.put("derivedRelationshipEvidenceSetCount", 1);
        summary.put("derivedRelationshipSupportCombinationCount", 6);
        summary.put("derivedDataLineageEvidenceSetCount", 0);
        summary.put("derivedDataLineageSupportCombinationCount", 0);
        summary.put("derivedNamingEvidenceSetCount", 0);
        summary.put("derivedNamingSupportCombinationCount", 0);

        ObjectNode orders = endpoint("sample_data.orders");
        ObjectNode customers = endpoint("sample_data.customers");
        ObjectNode regions = endpoint("sample_data.regions");
        ObjectNode fact = ((ArrayNode) root.path("derivedRelationships")).addObject();
        fact.put("kind", "RELATIONSHIP");
        fact.set("source", orders.deepCopy());
        fact.set("target", regions.deepCopy());
        fact.put("pathLength", 2);
        fact.put("confidence", 0.60d);
        fact.putArray("path").add(orders).add(customers).add(regions);
        fact.putArray("evidence").addObject()
                .put("type", "TRANSITIVE_PATH")
                .put("score", 0.60d)
                .put("sourceType", "INFERENCE")
                .put("source", "derived:relationship")
                .put("detail", "two-hop path")
                .putObject("attributes");
        ObjectNode set = fact.putArray("evidenceSets").addObject();
        set.put("combinationCount", 6);
        set.put("confidence", 0.60d);
        ArrayNode hops = set.putArray("hops");
        addEvidenceHop(hops, 1, orders, customers, "one-a", "one-b", "one-c");
        addEvidenceHop(hops, 2, customers, regions, "two-a", "two-b");
        fact.putObject("attributes").put("pathLength", 2);
        if (includeLegacyRawEvidence) {
            fact.putArray("rawEvidence");
        }
        return JSON.writeValueAsString(root);
    }

    private String resultWithExpandedInventory() throws Exception {
        ObjectNode root = (ObjectNode) JSON.readTree(emptyResult());
        ObjectNode inventory = (ObjectNode) root.path("metadataInventory");
        ArrayNode tables = (ArrayNode) inventory.path("tables");
        ArrayNode columns = (ArrayNode) inventory.path("columns");
        addInventoryTable(tables, columns, "customers");
        addInventoryTable(tables, columns, "regions");
        ((ObjectNode) inventory.path("counts")).put("tables", 3).put("columns", 3);
        return JSON.writeValueAsString(root);
    }

    private void addInventoryTable(ArrayNode tables, ArrayNode columns, String tableName) {
        tables.addObject()
                .putNull("catalog")
                .put("schema", "sample_data")
                .put("tableName", tableName)
                .put("tableType", "TABLE")
                .putNull("engine")
                .putNull("comment");
        columns.addObject()
                .putNull("catalog")
                .put("schema", "sample_data")
                .put("tableName", tableName)
                .put("columnName", "id")
                .put("dataType", "UNKNOWN")
                .put("columnType", "UNKNOWN")
                .put("nullable", false)
                .putNull("defaultValue")
                .put("extra", "")
                .put("generationExpression", "")
                .put("ordinalPosition", 1);
    }

    private ObjectNode endpoint(String table) {
        return JSON.createObjectNode().put("table", table).put("column", "id");
    }

    private void addEvidenceHop(
            ArrayNode hops,
            int ordinal,
            ObjectNode source,
            ObjectNode target,
            String... refs
    ) {
        ObjectNode hop = hops.addObject();
        hop.put("ordinal", ordinal);
        hop.set("source", source.deepCopy());
        hop.set("target", target.deepCopy());
        hop.put("kind", "RELATIONSHIP");
        ArrayNode evidenceRefs = hop.putArray("evidenceRefs");
        for (String ref : refs) {
            evidenceRefs.add(ref);
        }
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
                  "artifactSchemaVersion":2,
                  "summary":{"caseCount":1,"successCount":1,"failedCount":0,"skippedCount":0},
                  "cases":[
                    {
                      "id":"%s",
                      "status":"SUCCESS",
                      "elapsedMillis":10,
                      "outputBundle":"%s"
                    }
                  ]
                }
                """.formatted(id, output);
    }
}
