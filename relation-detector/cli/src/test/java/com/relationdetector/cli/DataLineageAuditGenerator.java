package com.relationdetector.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.StatementSourceType;

/**
 * Generates the human-review audit for Data Lineage correctness fixtures.
 *
 * <p>This is intentionally a test-scope maintenance tool. It makes the audit
 * deterministic from repo fixtures and their audited golden. Actual parser output
 * is verified once by CorrectnessFixtureRunnerTest, not recomputed by this report.
 */
final class DataLineageAuditGenerator {
    private static final int PREVIEW_MAX_LINES = 8;
    private static final int PREVIEW_MAX_CHARS = 800;

    private DataLineageAuditGenerator() {
    }

    static String generate(Path workspaceRoot) throws IOException {
        Path correctnessRoot = workspaceRoot.resolve("test-fixtures/correctness");
        List<AuditFixture> fixtures = readFixtures(workspaceRoot, correctnessRoot);
        Map<Classification, Long> counts = fixtures.stream()
                .collect(Collectors.groupingBy(AuditFixture::classification, TreeMap::new, Collectors.counting()));

        StringBuilder markdown = new StringBuilder();
        markdown.append("# Data Lineage Full Audit\n\n");
        markdown.append("This file is generated from `test-fixtures/correctness` by ")
                .append("`DataLineageAuditGeneratorTest`. Do not edit it by hand.\n\n");
        markdown.append("The report lists every correctness fixture and explains whether Data Lineage v1 ")
                .append("already has golden coverage, can propose golden coverage, needs manual review, ")
                .append("or is not applicable.\n\n");
        appendOverview(markdown, counts, fixtures.size());
        for (AuditFixture fixture : fixtures) {
            appendFixture(markdown, fixture);
        }
        return markdown.toString();
    }

    private static List<AuditFixture> readFixtures(Path workspaceRoot, Path correctnessRoot) throws IOException {
        List<Path> manifests;
        try (Stream<Path> paths = Files.walk(correctnessRoot)) {
            manifests = paths
                    .filter(path -> path.getFileName().toString().equals("manifest.yml"))
                    .sorted()
                    .toList();
        }
        List<AuditFixture> fixtures = new ArrayList<>();
        for (Path manifest : manifests) {
            fixtures.add(AuditFixture.read(workspaceRoot, manifest));
        }
        return fixtures.stream()
                .sorted(Comparator
                        .comparing(AuditFixture::databaseType)
                        .thenComparing(AuditFixture::parserTarget)
                        .thenComparing(AuditFixture::id))
                .toList();
    }

    private static void appendOverview(
            StringBuilder markdown,
            Map<Classification, Long> counts,
            int total
    ) {
        markdown.append("## Overview\n\n");
        markdown.append("| Classification | Count |\n");
        markdown.append("| --- | ---: |\n");
        markdown.append("| TOTAL | ").append(total).append(" |\n");
        for (Classification classification : Classification.values()) {
            markdown.append("| ").append(classification).append(" | ")
                    .append(counts.getOrDefault(classification, 0L)).append(" |\n");
        }
        markdown.append("\n");
    }

    private static void appendFixture(StringBuilder markdown, AuditFixture fixture) {
        markdown.append("## `").append(fixture.id()).append("`\n\n");
        markdown.append("| Field | Value |\n");
        markdown.append("| --- | --- |\n");
        markdown.append("| Classification | `").append(fixture.classification()).append("` |\n");
        markdown.append("| Reason | ").append(fixture.reason()).append(" |\n");
        markdown.append("| Database | `").append(fixture.databaseType()).append("` |\n");
        markdown.append("| Parser target | `").append(fixture.parserTarget()).append("` |\n");
        markdown.append("| Source type | `").append(fixture.sourceType()).append("` |\n");
        markdown.append("| Input | `").append(fixture.inputRelativePath()).append("` |\n");
        markdown.append("| Expected lineage | ");
        if (fixture.expectedLineageRelativePath().isBlank()) {
            markdown.append("None");
        } else {
            markdown.append("`").append(fixture.expectedLineageRelativePath()).append("`");
        }
        markdown.append(" |\n\n");

        appendList(markdown, "Expected Lineage Fingerprints", fixture.expectedLineageFingerprints());
        appendList(markdown, "Extractor Candidate Fingerprints", fixture.candidateFingerprints());

        markdown.append("**Input Preview**\n\n");
        markdown.append("```sql\n");
        markdown.append(fixture.preview());
        if (!fixture.preview().endsWith("\n")) {
            markdown.append("\n");
        }
        markdown.append("```\n\n");
    }

    private static void appendList(StringBuilder markdown, String title, List<String> values) {
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

    private record AuditFixture(
            String id,
            String databaseType,
            String parserTarget,
            StatementSourceType sourceType,
            String statementFormat,
            String inputRelativePath,
            String expectedLineageRelativePath,
            String preview,
            List<String> expectedLineageFingerprints,
            List<String> candidateFingerprints,
            Classification classification,
            String reason
    ) {
        static AuditFixture read(Path workspaceRoot, Path manifest) throws IOException {
            Map<String, String> values = readSimpleManifest(manifest);
            Path root = manifest.getParent();
            Path input = root.resolve(required(values, "input", manifest)).normalize();
            Path expectedLineageFile = root.resolve(values.getOrDefault("expectedLineage", "expected-lineage.json"))
                    .normalize();
            String inputText = Files.readString(input);
            String parserTarget = required(values, "parserTarget", manifest);
            StatementSourceType sourceType = StatementSourceType.valueOf(values.getOrDefault("sourceType", "PLAIN_SQL"));
            String statementFormat = values.getOrDefault("statementFormat", "SEMICOLON");
            List<String> expectedLineageFingerprints = Files.exists(expectedLineageFile)
                    ? stringArray(Files.readString(expectedLineageFile), "fingerprints")
                    : List.of();
            DatabaseType databaseType = DatabaseType.valueOf(required(values, "databaseType", manifest));
            boolean versionBoundaryUnsupported = expectedDiagnosticsContains(
                    root.resolve(values.getOrDefault("expectedDiagnostics", "expected-diagnostics.json")).normalize(),
                    "FULL_GRAMMAR_VERSION_UNSUPPORTED_SYNTAX");
            List<String> candidates = List.of();
            Decision decision = classify(parserTarget, inputText, expectedLineageFingerprints,
                    versionBoundaryUnsupported);

            return new AuditFixture(
                    required(values, "id", manifest),
                    databaseType.name(),
                    parserTarget,
                    sourceType,
                    statementFormat,
                    normalizePath(workspaceRoot.relativize(input)),
                    Files.exists(expectedLineageFile) ? normalizePath(workspaceRoot.relativize(expectedLineageFile)) : "",
                    DataLineageAuditGenerator.preview(inputText),
                    expectedLineageFingerprints,
                    candidates,
                    decision.classification(),
                    decision.reason());
        }
    }

    private static Decision classify(
            String parserTarget,
            String inputText,
            List<String> expectedLineage,
            boolean versionBoundaryUnsupported
    ) {
        String lower = inputText.toLowerCase(Locale.ROOT);
        if (!expectedLineage.isEmpty()) {
            return new Decision(Classification.EXISTING_GOLD, "fixture already has expected-lineage.json");
        }
        if (versionBoundaryUnsupported) {
            return new Decision(Classification.NOT_APPLICABLE,
                    "negative full-grammer version-boundary fixture; unsupported SQL is not lineage golden");
        }
        if (!parserTarget.equals("SQL")) {
            return new Decision(Classification.NOT_APPLICABLE,
                    "DDL does not write target column values in Data Lineage v1");
        }
        if (containsDelete(lower) && !containsValueWrite(lower)) {
            return new Decision(Classification.NOT_APPLICABLE,
                    "DELETE does not write target column values in Data Lineage v1");
        }
        if (!containsValueWrite(lower)) {
            return new Decision(Classification.NOT_APPLICABLE,
                    "no UPDATE, INSERT SELECT, or MERGE target column write");
        }
        if (hasExplicitLocalTemporaryTable(lower)) {
            return new Decision(Classification.NOT_APPLICABLE,
                    "local temporary table sources are excluded from Data Lineage v1");
        }
        return new Decision(Classification.NOT_APPLICABLE,
                "write statement has no physical table.column source in Data Lineage v1");
    }

    private static boolean containsDelete(String lower) {
        return Pattern.compile("(?is)\\bdelete\\b").matcher(lower).find();
    }

    private static boolean containsValueWrite(String lower) {
        return Pattern.compile("(?is)\\bupdate\\b|\\binsert\\s+into\\b|\\bmerge\\s+into\\b").matcher(lower).find();
    }

    private static boolean hasExplicitLocalTemporaryTable(String lower) {
        return Pattern.compile("(?is)\\bcreate\\s+(?:temporary|temp)\\s+table\\b").matcher(lower).find();
    }

    private static String preview(String input) {
        String[] lines = input.split("\\R", -1);
        StringBuilder preview = new StringBuilder();
        for (int i = 0; i < Math.min(lines.length, PREVIEW_MAX_LINES); i++) {
            if (i > 0) {
                preview.append('\n');
            }
            preview.append(lines[i]);
        }
        if (preview.length() > PREVIEW_MAX_CHARS) {
            preview.setLength(PREVIEW_MAX_CHARS);
        }
        return preview.toString();
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

    private static boolean expectedDiagnosticsContains(Path expectedDiagnosticsFile, String warningCode) throws IOException {
        return Files.exists(expectedDiagnosticsFile)
                && Files.readString(expectedDiagnosticsFile).contains("\"" + warningCode + "\"");
    }

    private static String normalizePath(Path path) {
        return path.toString().replace('\\', '/');
    }

    private enum Classification {
        EXISTING_GOLD,
        SUGGESTED_GOLD,
        PENDING_REVIEW,
        NOT_APPLICABLE
    }

    private record Decision(Classification classification, String reason) {
    }
}
