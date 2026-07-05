package com.relationdetector.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliEndToEndGoldenTest {
    @TempDir
    Path tempDir;

    @Test
    void mysqlTokenEventSqlLogMatchesExistingRelationshipAndLineageGolden() throws Exception {
        assertCliGolden(
                fixture("mysql/mysql-commerce-promotion-update-explicit-join-sql"),
                "token-event",
                "",
                "",
                SourceKind.LOGS,
                true,
                Set.of());
    }

    @Test
    void postgresFullGrammerDdlMatchesExistingRelationshipGolden() throws Exception {
        assertCliGolden(
                fixture("postgres/v16/ddl-alter-table-fk"),
                "full-grammer",
                "postgresql/16",
                "16.5",
                SourceKind.DDL,
                false,
                Set.of());
    }

    @Test
    void mysqlUpdateLineageMatchesExistingGoldenThroughCli() throws Exception {
        assertCliGolden(
                fixture("mysql/mysql-user-spending-left-join-update-sql"),
                "token-event",
                "",
                "",
                SourceKind.LOGS,
                true,
                Set.of());
    }

    @Test
    void postgresFullGrammerSqlMatchesExistingRelationshipAndLineageGolden() throws Exception {
        assertCliGolden(
                fixture("postgres/v16/sql-merge-using"),
                "full-grammer",
                "",
                "16.5",
                SourceKind.LOGS,
                true,
                Set.of());
    }

    @Test
    void unsupportedFullGrammerVersionFallsBackAndStillMatchesTokenEventGolden() throws Exception {
        assertCliGolden(
                fixture("postgres/sql-merge-using"),
                "full-grammer",
                "",
                "20.0",
                SourceKind.LOGS,
                true,
                Set.of("PARSER_MODE_FALLBACK"));
    }

    @Test
    void commonPortableParserRunsThroughCliScanEngine() throws Exception {
        Path ddl = tempDir.resolve("schema.sql");
        Path sql = tempDir.resolve("queries.sql");
        Path output = tempDir.resolve("common-output.json");
        Path config = tempDir.resolve("common.yml");
        Files.writeString(ddl, """
                CREATE TABLE customers (
                  id INTEGER PRIMARY KEY,
                  name VARCHAR(100)
                );

                CREATE TABLE orders (
                  id INTEGER PRIMARY KEY,
                  customer_id INTEGER,
                  total_amount DECIMAL(12, 2),
                  CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES customers(id)
                );
                """);
        Files.writeString(sql, """
                INSERT INTO customer_order_summary (customer_id, total_amount)
                SELECT c.id, SUM(o.total_amount)
                FROM customers c
                JOIN orders o ON o.customer_id = c.id
                GROUP BY c.id;
                """);
        Files.writeString(config, """
                database:
                  type: common
                  schema: portable
                sources:
                  metadata:
                    enabled: false
                  ddl:
                    enabled: true
                    fromDatabase: false
                    files:
                      - %s
                  logs:
                    enabled: true
                    filterSystemQueries: false
                    format: plain_sql
                    files:
                      - %s
                output:
                  format: json
                  minConfidence: 0.0
                  includeEvidence: true
                  includeWarnings: true
                parser:
                  mode: token-event
                derivedPaths:
                  enabled: true
                """.formatted(ddl, sql));

        int exitCode = new Main.MainCommand().run(new String[] {
                "scan",
                "--config",
                config.toString(),
                "--output",
                output.toString()
        });

        assertEquals(0, exitCode, "common CLI scan should succeed");
        String json = Files.readString(output);
        assertTrue(json.contains("\"type\" : \"COMMON\""), json);
        assertTrue(relationshipFingerprints(json).stream()
                        .anyMatch(fingerprint -> fingerprint.contains("orders.customer_id->customers.id")),
                json);
        assertTrue(lineageFingerprints(json).stream()
                        .anyMatch(fingerprint -> fingerprint.contains("orders.total_amount")
                                && fingerprint.contains("customer_order_summary.total_amount")),
                json);
        assertTrue(json.contains("\"namingEvidence\""), json);
        assertTrue(json.contains("\"derivedRelationships\""), json);
    }

    private void assertCliGolden(
            Path fixtureDir,
            String parserMode,
            String grammarProfile,
            String databaseVersion,
            SourceKind sourceKind,
            boolean compareLineage,
            Set<String> requiredWarningCodes
    ) throws Exception {
        Path output = tempDir.resolve(fixtureDir.getFileName() + ".json");
        Path config = writeConfig(fixtureDir, parserMode, grammarProfile, databaseVersion, sourceKind, output);

        int exitCode = new Main.MainCommand().run(new String[] {
                "scan",
                "--config",
                config.toString(),
                "--output",
                output.toString()
        });

        assertEquals(0, exitCode, "CLI scan should succeed for " + fixtureDir);
        String json = Files.readString(output);
        assertEquals(new TreeSet<>(expectedFingerprints(fixtureDir.resolve("expected-relations.json"))),
                new TreeSet<>(relationshipFingerprints(json)),
                fixtureDir + " relationship fingerprints from CLI JSON");
        if (compareLineage && Files.exists(fixtureDir.resolve("expected-lineage.json"))) {
            assertEquals(new TreeSet<>(expectedFingerprints(fixtureDir.resolve("expected-lineage.json"))),
                    new TreeSet<>(lineageFingerprints(json)),
                    fixtureDir + " lineage fingerprints from CLI JSON");
        }
        Set<String> warningCodes = new TreeSet<>(warningCodes(json));
        for (String requiredWarningCode : requiredWarningCodes) {
            assertTrue(warningCodes.contains(requiredWarningCode),
                    () -> fixtureDir + " should emit warning " + requiredWarningCode + ", actual=" + warningCodes);
        }
    }

    private Path writeConfig(
            Path fixtureDir,
            String parserMode,
            String grammarProfile,
            String databaseVersion,
            SourceKind sourceKind,
            Path output
    ) throws Exception {
        String databaseType = manifestValue(fixtureDir.resolve("manifest.yml"), "databaseType").toLowerCase();
        String input = manifestValue(fixtureDir.resolve("manifest.yml"), "input");
        Path inputFile = fixtureDir.resolve(input).toAbsolutePath();
        Path config = tempDir.resolve(fixtureDir.getFileName() + ".yml");
        StringBuilder yaml = new StringBuilder();
        yaml.append("database:\n")
                .append("  type: ").append(databaseType).append("\n")
                .append("  schema: ").append(manifestValue(fixtureDir.resolve("manifest.yml"), "schema")).append("\n")
                .append("sources:\n")
                .append("  metadata:\n")
                .append("    enabled: false\n")
                .append("  ddl:\n")
                .append("    enabled: ").append(sourceKind == SourceKind.DDL).append("\n")
                .append("    fromDatabase: false\n");
        if (sourceKind == SourceKind.DDL) {
            yaml.append("    files:\n")
                    .append("      - ").append(inputFile).append("\n");
        }
        yaml.append("  logs:\n")
                .append("    enabled: ").append(sourceKind == SourceKind.LOGS).append("\n");
        if (sourceKind == SourceKind.LOGS) {
            yaml.append("    filterSystemQueries: false\n")
                    .append("    format: plain_sql\n")
                    .append("    files:\n")
                    .append("      - ").append(inputFile).append("\n");
        }
        yaml.append("output:\n")
                .append("  format: json\n")
                .append("  minConfidence: 0.0\n")
                .append("  includeEvidence: true\n")
                .append("  includeWarnings: true\n")
                .append("parser:\n")
                .append("  mode: ").append(parserMode).append("\n");
        if (!grammarProfile.isBlank()) {
            yaml.append("  grammarProfile: ").append(grammarProfile).append("\n");
        }
        if (!databaseVersion.isBlank()) {
            yaml.append("  databaseVersion: ").append(databaseVersion).append("\n");
        }
        Files.writeString(config, yaml.toString());
        Files.deleteIfExists(output);
        return config;
    }

    private Path fixture(String relative) {
        return workspaceRoot().resolve("test-fixtures/correctness").resolve(relative);
    }

    private Path workspaceRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml")) && Files.exists(current.resolve("test-fixtures"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate relation-detector workspace root");
    }

    private List<String> relationshipFingerprints(String json) {
        return topLevelObjects(arrayBody(json, "relationships")).stream()
                .map(object -> {
                    Endpoint source = endpoint(object, "source");
                    Endpoint target = endpoint(object, "target");
                    return stringField(object, "relationType") + ":"
                            + source.displayName() + "->" + target.displayName()
                            + ":" + String.join(",", evidenceTypes(object));
                })
                .toList();
    }

    private List<String> lineageFingerprints(String json) {
        return topLevelObjects(arrayBody(json, "dataLineages")).stream()
                .map(object -> {
                    String sources = topLevelObjects(arrayBody(object, "sources")).stream()
                            .map(source -> endpointFromEndpointObject(source).displayName())
                            .sorted()
                            .reduce((left, right) -> left + "," + right)
                            .orElse("");
                    Endpoint target = endpoint(object, "target");
                    return stringField(object, "flowKind") + ":"
                            + stringField(object, "transformType") + ":"
                            + sources + "->" + target.displayName();
                })
                .toList();
    }

    private List<String> warningCodes(String json) {
        Matcher matcher = Pattern.compile("\"code\"\\s*:\\s*\"([^\"]*)\"").matcher(lastArrayBody(json, "warnings"));
        List<String> codes = new ArrayList<>();
        while (matcher.find()) {
            codes.add(matcher.group(1));
        }
        return codes;
    }

    private List<String> evidenceTypes(String relationshipObject) {
        int rawEvidence = relationshipObject.indexOf("\"rawEvidence\"");
        int evidence = relationshipObject.indexOf("\"evidence\"", rawEvidence + 1);
        if (evidence < 0) {
            return List.of();
        }
        String evidenceBody = arrayBody(relationshipObject.substring(evidence), "evidence");
        Matcher matcher = Pattern.compile("\"type\"\\s*:\\s*\"([^\"]*)\"").matcher(evidenceBody);
        List<String> types = new ArrayList<>();
        while (matcher.find()) {
            types.add(matcher.group(1));
        }
        return types;
    }

    private Endpoint endpoint(String object, String field) {
        int key = object.indexOf("\"" + field + "\"");
        if (key < 0) {
            throw new IllegalArgumentException("Missing endpoint " + field + " in " + object);
        }
        int start = object.indexOf('{', key);
        int end = matching(object, start, '{', '}');
        return endpointFromEndpointObject(object.substring(start, end + 1));
    }

    private Endpoint endpointFromEndpointObject(String object) {
        String table = stringField(object, "table");
        String column = nullableStringField(object, "column");
        return new Endpoint(table, column);
    }

    private List<String> expectedFingerprints(Path expectedFile) throws Exception {
        return stringArray(Files.readString(expectedFile), "fingerprints");
    }

    private String manifestValue(Path manifest, String key) throws Exception {
        for (String raw : Files.readAllLines(manifest)) {
            String line = raw.trim();
            if (line.startsWith(key + ":")) {
                return unquote(line.substring(line.indexOf(':') + 1).trim());
            }
        }
        throw new IllegalArgumentException("Missing manifest key " + key + " in " + manifest);
    }

    private List<String> stringArray(String json, String field) {
        String body = arrayBody(json, field).trim();
        if (body.isBlank()) {
            return List.of();
        }
        Matcher matcher = Pattern.compile("\"((?:\\\\.|[^\"])*)\"").matcher(body);
        List<String> values = new ArrayList<>();
        while (matcher.find()) {
            values.add(matcher.group(1).replace("\\\"", "\"").replace("\\\\", "\\"));
        }
        return values;
    }

    private String arrayBody(String text, String field) {
        int key = text.indexOf("\"" + field + "\"");
        if (key < 0) {
            return "";
        }
        int start = text.indexOf('[', key);
        if (start < 0) {
            return "";
        }
        int end = matching(text, start, '[', ']');
        return text.substring(start + 1, end);
    }

    private String lastArrayBody(String text, String field) {
        int key = text.lastIndexOf("\"" + field + "\"");
        if (key < 0) {
            return "";
        }
        int start = text.indexOf('[', key);
        if (start < 0) {
            return "";
        }
        int end = matching(text, start, '[', ']');
        return text.substring(start + 1, end);
    }

    private List<String> topLevelObjects(String arrayBody) {
        List<String> objects = new ArrayList<>();
        int index = 0;
        while (index < arrayBody.length()) {
            int start = arrayBody.indexOf('{', index);
            if (start < 0) {
                break;
            }
            int end = matching(arrayBody, start, '{', '}');
            objects.add(arrayBody.substring(start, end + 1));
            index = end + 1;
        }
        return objects;
    }

    private int matching(String text, int start, char open, char close) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }
            if (ch == '"') {
                inString = true;
                continue;
            }
            if (ch == open) {
                depth++;
            } else if (ch == close && --depth == 0) {
                return i;
            }
        }
        throw new IllegalArgumentException("Unmatched " + open + " in " + text.substring(start));
    }

    private String stringField(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")
                .matcher(json);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Missing string field " + field + " in " + json);
        }
        return matcher.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private String nullableStringField(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*(null|\"((?:\\\\.|[^\"])*)\")")
                .matcher(json);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Missing nullable field " + field + " in " + json);
        }
        if ("null".equals(matcher.group(1))) {
            return null;
        }
        return matcher.group(2).replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private String unquote(String text) {
        if ((text.startsWith("\"") && text.endsWith("\"")) || (text.startsWith("'") && text.endsWith("'"))) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

    private enum SourceKind {
        LOGS,
        DDL
    }

    private record Endpoint(String table, String column) {
        String displayName() {
            return column == null ? table : table + "." + column;
        }
    }
}
