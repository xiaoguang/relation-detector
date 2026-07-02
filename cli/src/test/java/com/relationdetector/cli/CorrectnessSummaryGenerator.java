package com.relationdetector.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Deterministic Markdown generator for the file-based correctness suite.
 *
 * <p>This class intentionally lives under {@code src/test}: it is a repository
 * maintenance tool, not production CLI behavior. It reads fixture files and
 * golden expectations directly, so the generated report is reproducible and
 * does not depend on an LLM or free-form summarization.
 */
final class CorrectnessSummaryGenerator {
    private static final int PREVIEW_MAX_LINES = 8;
    private static final int PREVIEW_MAX_CHARS = 800;
    private static final String TRUNCATED_NOTICE = "Preview truncated; see input file for full content.";

    private CorrectnessSummaryGenerator() {
    }

    static String generate(Path workspaceRoot) throws IOException {
        Path correctnessRoot = workspaceRoot.resolve("test-fixtures/correctness");
        List<FixtureSummary> fixtures = readFixtures(correctnessRoot);
        SummaryStats stats = SummaryStats.from(fixtures);

        StringBuilder markdown = new StringBuilder();
        markdown.append("# Correctness Test Summary\n\n");
        markdown.append("This file is generated from `test-fixtures/correctness` by ")
                .append("`CorrectnessSummaryGeneratorTest`. Do not edit it by hand.\n\n");
        markdown.append("Lightweight index report. Full SQL/DDL is available in each input file.\n\n");
        appendOverview(markdown, stats);
        appendSection(markdown, "Common Fixtures", fixtures, "common");
        appendSection(markdown, "MySQL Fixtures", fixtures, "mysql");
        appendSection(markdown, "PostgreSQL Fixtures", fixtures, "postgres");
        appendSection(markdown, "Oracle Fixtures", fixtures, "oracle");
        appendSection(markdown, "SQL Server Fixtures", fixtures, "sqlserver");
        return markdown.toString();
    }

    private static List<FixtureSummary> readFixtures(Path correctnessRoot) throws IOException {
        List<Path> manifests;
        try (Stream<Path> paths = Files.walk(correctnessRoot)) {
            manifests = paths
                    .filter(path -> path.getFileName().toString().equals("manifest.yml"))
                    .sorted()
                    .toList();
        }
        List<FixtureSummary> fixtures = new ArrayList<>();
        for (Path manifest : manifests) {
            fixtures.add(FixtureSummary.read(correctnessRoot, manifest));
        }
        return fixtures.stream()
                .sorted(Comparator
                        .comparing(FixtureSummary::section)
                        .thenComparing(FixtureSummary::databaseType)
                        .thenComparing(FixtureSummary::parserTarget)
                        .thenComparing(FixtureSummary::id))
                .toList();
    }

    private static void appendOverview(StringBuilder markdown, SummaryStats stats) {
        markdown.append("## Overview\n\n");
        markdown.append("| Metric | Count |\n");
        markdown.append("| --- | ---: |\n");
        markdown.append("| Total correctness fixtures | ").append(stats.total()).append(" |\n");
        markdown.append("| SQL fixtures | ").append(stats.targetCounts().getOrDefault("SQL", 0)).append(" |\n");
        markdown.append("| DDL fixtures | ").append(stats.targetCounts().getOrDefault("DDL", 0)).append(" |\n");
        markdown.append("| Fixtures with expected lineage | ").append(stats.lineageFixtures()).append(" |\n");
        markdown.append("| Common directory fixtures | ").append(stats.sectionCounts().getOrDefault("common", 0)).append(" |\n");
        markdown.append("| MySQL directory fixtures | ").append(stats.sectionCounts().getOrDefault("mysql", 0)).append(" |\n");
        markdown.append("| PostgreSQL directory fixtures | ").append(stats.sectionCounts().getOrDefault("postgres", 0)).append(" |\n");
        markdown.append("| Oracle directory fixtures | ").append(stats.sectionCounts().getOrDefault("oracle", 0)).append(" |\n");
        markdown.append("| SQL Server directory fixtures | ").append(stats.sectionCounts().getOrDefault("sqlserver", 0)).append(" |\n");
        markdown.append("\n");

        markdown.append("| Database type | Total | SQL | DDL |\n");
        markdown.append("| --- | ---: | ---: | ---: |\n");
        for (String databaseType : stats.databaseTypes()) {
            markdown.append("| ").append(databaseType)
                    .append(" | ").append(stats.databaseCounts().getOrDefault(databaseType, 0))
                    .append(" | ").append(stats.databaseTargetCounts().getOrDefault(databaseType + ":SQL", 0))
                    .append(" | ").append(stats.databaseTargetCounts().getOrDefault(databaseType + ":DDL", 0))
                    .append(" |\n");
        }
        markdown.append("\n");
    }

    private static void appendSection(
            StringBuilder markdown,
            String title,
            List<FixtureSummary> fixtures,
            String section
    ) {
        List<FixtureSummary> sectionFixtures = fixtures.stream()
                .filter(fixture -> fixture.section().equals(section))
                .toList();
        if (sectionFixtures.isEmpty()) {
            return;
        }
        markdown.append("## ").append(title).append("\n\n");
        for (FixtureSummary fixture : sectionFixtures) {
            appendFixture(markdown, fixture);
        }
    }

    private static void appendFixture(StringBuilder markdown, FixtureSummary fixture) {
        markdown.append("### `").append(fixture.id()).append("`\n\n");
        markdown.append("| Field | Value |\n");
        markdown.append("| --- | --- |\n");
        markdown.append("| Database | `").append(fixture.databaseType()).append("` |\n");
        markdown.append("| Parser target | `").append(fixture.parserTarget()).append("` |\n");
        markdown.append("| Source type | `").append(fixture.sourceType()).append("` |\n");
        markdown.append("| Schema | `").append(fixture.schema()).append("` |\n");
        markdown.append("| Input | `").append(fixture.inputRelativePath()).append("` |\n");
        markdown.append("| Expected relations | `").append(fixture.expectedRelationsRelativePath()).append("` |\n");
        markdown.append("| Expected lineage | ");
        if (fixture.expectedLineageRelativePath().isBlank()) {
            markdown.append("None");
        } else {
            markdown.append("`").append(fixture.expectedLineageRelativePath()).append("`");
        }
        markdown.append(" |\n");
        markdown.append("| Expected diagnostics | `").append(fixture.expectedDiagnosticsRelativePath()).append("` |\n");
        markdown.append("\n");

        appendStringList(markdown, "Expected Relation Fingerprints", fixture.fingerprints());
        appendStringList(markdown, "Expected Data Lineage Fingerprints", fixture.lineageFingerprints());
        appendStringList(markdown, "Forbidden Tables", fixture.forbiddenTables());
        appendWarningCodes(markdown, fixture.warningCodes());

        Preview preview = Preview.from(fixture.inputText());
        markdown.append("**Input Preview**\n\n");
        markdown.append("```sql\n");
        markdown.append(preview.text());
        if (!preview.text().endsWith("\n")) {
            markdown.append("\n");
        }
        markdown.append("```\n");
        if (preview.truncated()) {
            markdown.append("_").append(TRUNCATED_NOTICE).append("_\n");
        }
        markdown.append("\n");
    }

    private static void appendStringList(StringBuilder markdown, String title, List<String> values) {
        markdown.append("**").append(title).append("**\n\n");
        if (values.isEmpty()) {
            markdown.append("- None\n\n");
            return;
        }
        for (String value : values) {
            markdown.append("- `").append(value).append("`\n");
        }
        markdown.append("\n");
    }

    private static void appendWarningCodes(StringBuilder markdown, Map<String, Long> warningCodes) {
        markdown.append("**Expected Warning Codes**\n\n");
        if (warningCodes.isEmpty()) {
            markdown.append("- None\n\n");
            return;
        }
        for (Map.Entry<String, Long> entry : warningCodes.entrySet()) {
            markdown.append("- `").append(entry.getKey()).append("`: ").append(entry.getValue()).append("\n");
        }
        markdown.append("\n");
    }

    private record FixtureSummary(
            String section,
            String id,
            String databaseType,
            String parserTarget,
            String sourceType,
            String schema,
            String inputRelativePath,
            String expectedRelationsRelativePath,
            String expectedLineageRelativePath,
            String expectedDiagnosticsRelativePath,
            String inputText,
            List<String> fingerprints,
            List<String> lineageFingerprints,
            List<String> forbiddenTables,
            Map<String, Long> warningCodes
    ) {
        static FixtureSummary read(Path correctnessRoot, Path manifest) throws IOException {
            Map<String, String> values = readSimpleManifest(manifest);
            Path root = manifest.getParent();
            Path input = root.resolve(required(values, "input", manifest)).normalize();
            Path expectedRelations = root.resolve(required(values, "expectedRelations", manifest)).normalize();
            Path expectedLineage = root.resolve(values.getOrDefault("expectedLineage", "expected-lineage.json"))
                    .normalize();
            Path expectedDiagnostics = root.resolve(required(values, "expectedDiagnostics", manifest)).normalize();
            String relationsJson = Files.readString(expectedRelations);
            String lineageJson = Files.exists(expectedLineage) ? Files.readString(expectedLineage) : "";
            String diagnosticsJson = Files.readString(expectedDiagnostics);

            return new FixtureSummary(
                    correctnessRoot.relativize(manifest).getName(0).toString(),
                    required(values, "id", manifest),
                    required(values, "databaseType", manifest),
                    required(values, "parserTarget", manifest),
                    values.getOrDefault("sourceType", "PLAIN_SQL"),
                    values.getOrDefault("schema", "public"),
                    normalizePath(correctnessRoot.getParent().getParent().relativize(input)),
                    normalizePath(correctnessRoot.getParent().getParent().relativize(expectedRelations)),
                    Files.exists(expectedLineage)
                            ? normalizePath(correctnessRoot.getParent().getParent().relativize(expectedLineage))
                            : "",
                    normalizePath(correctnessRoot.getParent().getParent().relativize(expectedDiagnostics)),
                    Files.readString(input),
                    stringArray(relationsJson, "fingerprints"),
                    stringArray(lineageJson, "fingerprints"),
                    stringArray(relationsJson, "forbiddenTables"),
                    objectLongs(diagnosticsJson, "warningCodes"));
        }
    }

    private record SummaryStats(
            int total,
            Map<String, Integer> sectionCounts,
            Map<String, Integer> targetCounts,
            Map<String, Integer> databaseCounts,
            Map<String, Integer> databaseTargetCounts,
            int lineageFixtures
    ) {
        static SummaryStats from(List<FixtureSummary> fixtures) {
            Map<String, Integer> sectionCounts = new TreeMap<>();
            Map<String, Integer> targetCounts = new TreeMap<>();
            Map<String, Integer> databaseCounts = new TreeMap<>();
            Map<String, Integer> databaseTargetCounts = new TreeMap<>();
            int lineageFixtures = 0;
            for (FixtureSummary fixture : fixtures) {
                increment(sectionCounts, fixture.section());
                increment(targetCounts, fixture.parserTarget());
                increment(databaseCounts, fixture.databaseType());
                increment(databaseTargetCounts, fixture.databaseType() + ":" + fixture.parserTarget());
                if (!fixture.lineageFingerprints().isEmpty()) {
                    lineageFixtures++;
                }
            }
            return new SummaryStats(
                    fixtures.size(),
                    Map.copyOf(sectionCounts),
                    Map.copyOf(targetCounts),
                    Map.copyOf(databaseCounts),
                    Map.copyOf(databaseTargetCounts),
                    lineageFixtures);
        }

        List<String> databaseTypes() {
            return databaseCounts.keySet().stream().sorted().toList();
        }
    }

    private record Preview(String text, boolean truncated) {
        static Preview from(String input) {
            String normalized = input == null ? "" : input;
            String[] lines = normalized.split("\\R", -1);
            StringBuilder preview = new StringBuilder();
            boolean truncatedByLine = false;
            for (int i = 0; i < lines.length; i++) {
                if (i >= PREVIEW_MAX_LINES) {
                    truncatedByLine = true;
                    break;
                }
                if (i > 0) {
                    preview.append('\n');
                }
                preview.append(lines[i]);
            }

            boolean truncatedByChar = preview.length() > PREVIEW_MAX_CHARS;
            if (truncatedByChar) {
                preview.setLength(PREVIEW_MAX_CHARS);
            }
            boolean inputLongerThanPreview = !normalized.equals(preview.toString());
            return new Preview(preview.toString(), truncatedByLine || truncatedByChar || inputLongerThanPreview);
        }
    }

    private static void increment(Map<String, Integer> values, String key) {
        values.put(key, values.getOrDefault(key, 0) + 1);
    }

    private static Map<String, String> readSimpleManifest(Path manifest) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        for (String rawLine : Files.readAllLines(manifest)) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon < 0) {
                throw new IllegalArgumentException("Invalid manifest line in " + manifest + ": " + rawLine);
            }
            values.put(line.substring(0, colon).trim(), unquote(line.substring(colon + 1).trim()));
        }
        return values;
    }

    private static String required(Map<String, String> values, String key, Path manifest) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing manifest key " + key + " in " + manifest);
        }
        return value;
    }

    private static String unquote(String text) {
        if ((text.startsWith("\"") && text.endsWith("\"")) || (text.startsWith("'") && text.endsWith("'"))) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

    private static List<String> stringArray(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL)
                .matcher(json);
        if (!matcher.find()) {
            return List.of();
        }
        String body = matcher.group(1).trim();
        if (body.isEmpty()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        Matcher item = Pattern.compile("\"((?:\\\\.|[^\"])*)\"").matcher(body);
        while (item.find()) {
            values.add(item.group(1).replace("\\\"", "\"").replace("\\\\", "\\"));
        }
        return List.copyOf(values);
    }

    private static Map<String, Long> objectLongs(String json, String field) {
        String body = objectBody(json, field);
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        Map<String, Long> values = new TreeMap<>();
        Matcher entry = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(\\d+)").matcher(body);
        while (entry.find()) {
            values.put(entry.group(1), Long.parseLong(entry.group(2)));
        }
        return Map.copyOf(values);
    }

    private static String objectBody(String json, String field) {
        Matcher fieldMatcher = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:").matcher(json);
        if (!fieldMatcher.find()) {
            return null;
        }
        int start = json.indexOf('{', fieldMatcher.end());
        if (start < 0) {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return json.substring(start + 1, i).trim();
                }
            }
        }
        throw new IllegalArgumentException("Unclosed object field " + field);
    }

    private static String normalizePath(Path path) {
        return path.toString().replace('\\', '/');
    }
}
