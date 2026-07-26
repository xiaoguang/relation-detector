package com.relationdetector.cli.verification;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * CN: 逐文件、逐顶层数组 item 校验 sample-data 结果矩阵，只保留当前 fact 及小型计数/引用索引；
 * 输出供 manifest 复用的小报告，禁止 manifest 再次物化大 JSON。
 * EN: Validates the sample-data result matrix one file and one top-level array item at a time, retaining only the
 * current fact and compact counters/reference indexes. Its small report is the sole integrity input to the manifest.
 */
final class SampleDataResultValidator {
    private static final String DERIVED_SUFFIX = "-derived-fresh";
    private static final List<String> FACT_SECTIONS = List.of(
            "relationships",
            "dataLineages",
            "namingEvidence",
            "derivedRelationships",
            "derivedDataLineages");

    /**
     * CN: 校验结果目录中的完整 direct/derived 矩阵，并把跨 section 引用写入临时外存索引；
     * 成功输出有界 PASS 报告，覆盖、计数、路径、位置、引用或环校验失败时不生成 PASS。
     * EN: Validates the complete direct/derived result matrix while spilling cross-section references to temporary
     * disk indexes. It emits a bounded PASS report only when coverage, counts, paths, locations, refs, and cycles pass.
     */
    ObjectNode validate(Path resultDirectory, int expectedCategories, Path output) {
        Path workspace = output.toAbsolutePath().resolveSibling(
                ".result-validation-work-" + UUID.randomUUID());
        try {
            List<Path> paths = jsonFiles(resultDirectory);
            if (expectedCategories < 1 || paths.size() != expectedCategories * 2) {
                throw new ReleaseVerificationException("sample-data result JSON coverage mismatch");
            }
            Set<String> stems = new HashSet<>();
            for (Path path : paths) {
                stems.add(stem(path));
            }
            List<String> direct = stems.stream()
                    .filter(stem -> !stem.endsWith(DERIVED_SUFFIX))
                    .sorted()
                    .toList();
            if (direct.size() != expectedCategories) {
                throw new ReleaseVerificationException("sample-data direct category coverage mismatch");
            }
            for (String stem : direct) {
                if (!stems.contains(stem + DERIVED_SUFFIX)) {
                    throw new ReleaseVerificationException(
                            "sample-data derived result is missing for " + stem);
                }
            }
            int diagnostics = 0;
            for (int index = 0; index < paths.size(); index++) {
                diagnostics += validateFile(paths.get(index), workspace.resolve("file-" + index));
            }
            if (diagnostics != 0) {
                throw new ReleaseVerificationException("sample-data results contain diagnostics");
            }
            ObjectNode report = ReleaseVerificationJson.MAPPER.createObjectNode();
            report.put("status", "PASS");
            report.put("categories", direct.size());
            report.put("jsonFiles", paths.size());
            report.put("diagnostics", diagnostics);
            report.putObject("integrity")
                    .put("evidenceRefs", "PASS")
                    .put("sourcePaths", "PASS")
                    .put("sourceLines", "PASS")
                    .put("rawObservationDuplicates", "PASS")
                    .put("derivedCycles", "PASS");
            ReleaseVerificationJson.write(output, report);
            return report;
        } finally {
            deleteRecursively(workspace);
        }
    }

    private int validateFile(Path path, Path workspace) {
        FileState state = new FileState(path, workspace);
        try (JsonParser parser = ReleaseVerificationJson.MAPPER.getFactory().createParser(path.toFile())) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw failure(path, "result root must be an object");
            }
            JsonToken token;
            while ((token = parser.nextToken()) != JsonToken.END_OBJECT) {
                if (token != JsonToken.FIELD_NAME) {
                    throw failure(path, "result field name is required");
                }
                String field = parser.currentName();
                JsonToken value = parser.nextToken();
                if (value == null) {
                    throw failure(path, "result field value is required");
                }
                if ("summary".equals(field)) {
                    state.summary = ReleaseVerificationJson.MAPPER.readTree(parser);
                } else if (arraySection(field) && value == JsonToken.START_ARRAY) {
                    readSection(parser, field, state);
                } else {
                    parser.skipChildren();
                }
            }
            if (parser.nextToken() != null) {
                throw failure(path, "result has trailing JSON content");
            }
        } catch (ReleaseVerificationException error) {
            throw error;
        } catch (IOException error) {
            throw new ReleaseVerificationException("failed to stream sample-data result: " + path, error);
        }
        state.finish();
        return state.count("warnings");
    }

    private boolean arraySection(String field) {
        return FACT_SECTIONS.contains(field)
                || "derivedNamingEvidence".equals(field)
                || "warnings".equals(field);
    }

    private void readSection(JsonParser parser, String section, FileState state) throws IOException {
        JsonToken token;
        while ((token = parser.nextToken()) != JsonToken.END_ARRAY) {
            if (token == null) {
                throw failure(state.path, "unterminated result array " + section);
            }
            JsonNode item = ReleaseVerificationJson.MAPPER.readTree(parser);
            if (item == null || !item.isObject()) {
                throw failure(state.path, section + " item must be an object");
            }
            state.increment(section);
            validatePortableSources(state.path, item);
            validateSourceLocations(state.path, item, state.lineCounts);
            if (FACT_SECTIONS.contains(section)) {
                validateRawObservations(state.path, section, item);
            }
            if ("namingEvidence".equals(section)) {
                String id = item.path("id").asText("");
                if (!id.isBlank()) {
                    state.namingIds.add(id);
                }
                if ("TRANSITIVE_NAMING_PATH".equals(item.path("rule").asText())) {
                    state.derivedNamingCount++;
                }
            } else if ("derivedNamingEvidence".equals(section)) {
                state.derivedNamingViewIds.add(item.path("id").asText(""));
            } else if ("relationships".equals(section) || "derivedRelationships".equals(section)) {
                collectNamingReferences(state.path, section, item, state.namingReferences);
            }
            if ("derivedRelationships".equals(section) || "derivedDataLineages".equals(section)) {
                validateDerivedCycle(state.path, item);
            }
        }
    }

    private void collectNamingReferences(
            Path path,
            String section,
            JsonNode fact,
            ExternalStringIndex references
    ) {
        for (JsonNode evidence : fact.path("evidence")) {
            if (!"NAMING_MATCH".equals(evidence.path("type").asText())) {
                continue;
            }
            String reference = evidence.path("evidenceRef").asText("");
            if (reference.isBlank()) {
                reference = evidence.path("attributes").path("evidenceRef").asText("");
            }
            if (reference.isBlank()) {
                throw failure(path, "NAMING_MATCH evidenceRef is missing in " + section);
            }
            if (evidence.path("rawEvidence").size() != 0) {
                throw failure(path, "relationship NAMING_MATCH duplicates raw evidence");
            }
            references.add(reference);
        }
    }

    private void validatePortableSources(Path path, JsonNode value) {
        if (value.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (("source".equals(field.getKey()) || "sourceFile".equals(field.getKey()))
                        && field.getValue().isTextual()
                        && Path.of(field.getValue().textValue()).isAbsolute()) {
                    throw failure(path, "result contains an absolute source path");
                }
                validatePortableSources(path, field.getValue());
            }
        } else if (value.isArray()) {
            value.forEach(child -> validatePortableSources(path, child));
        }
    }

    private void validateSourceLocations(Path path, JsonNode value, Map<Path, Long> lineCounts) {
        if (value.isObject()) {
            String sourceFile = value.path("sourceFile").asText("");
            JsonNode sourceLine = value.get("sourceLine");
            if (!sourceFile.isBlank() && sourceLine != null && sourceLine.canConvertToLong()) {
                Path source = Path.of(sourceFile);
                if (!Files.isRegularFile(source)) {
                    throw failure(path, "sourceFile does not exist: " + sourceFile);
                }
                long lines = lineCounts.computeIfAbsent(source, this::countLines);
                long line = sourceLine.longValue();
                if (line < 1 || line > lines) {
                    throw failure(path, "sourceLine is outside source file");
                }
                String statementId = value.path("sourceStatementId").asText("");
                StatementSpan span = statementSpan(statementId);
                if (span != null && (line < span.start() || line > span.end())) {
                    throw failure(path, "sourceLine is outside statement span");
                }
            }
            value.forEach(child -> validateSourceLocations(path, child, lineCounts));
        } else if (value.isArray()) {
            value.forEach(child -> validateSourceLocations(path, child, lineCounts));
        }
    }

    private StatementSpan statementSpan(String statementId) {
        int colon = statementId.lastIndexOf(':');
        int separator = colon < 0 ? -1 : statementId.indexOf('-', colon + 1);
        if (colon < 0 || separator <= colon + 1 || separator >= statementId.length() - 1) {
            return null;
        }
        String start = statementId.substring(colon + 1, separator);
        String end = statementId.substring(separator + 1);
        if (!digits(start) || !digits(end)) {
            return null;
        }
        try {
            return new StatementSpan(Long.parseLong(start), Long.parseLong(end));
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private boolean digits(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) {
                return false;
            }
        }
        return !value.isEmpty();
    }

    private long countLines(Path path) {
        try (Stream<String> lines = Files.lines(path)) {
            return lines.count();
        } catch (IOException error) {
            throw new ReleaseVerificationException("failed to count source lines", error);
        }
    }

    private void validateRawObservations(Path path, String section, JsonNode fact) {
        Set<String> seen = new HashSet<>();
        for (JsonNode observation : fact.path("rawEvidence")) {
            String identity = canonicalTree(observation);
            if (!seen.add(identity)) {
                throw failure(path, "duplicate raw observation in " + section);
            }
        }
    }

    private String canonicalTree(JsonNode node) {
        if (node.isObject()) {
            Map<String, String> fields = new java.util.TreeMap<>();
            node.fields().forEachRemaining(field -> fields.put(
                    field.getKey(), canonicalTree(field.getValue())));
            return fields.toString();
        }
        if (node.isArray()) {
            List<String> values = new ArrayList<>();
            node.forEach(value -> values.add(canonicalTree(value)));
            return values.toString();
        }
        return node.toString();
    }

    private void validateDerivedCycle(Path path, JsonNode fact) {
        List<String> keys = new ArrayList<>();
        for (JsonNode endpoint : fact.path("path")) {
            keys.add(endpointKey(endpoint));
        }
        for (int index = 0; index < keys.size(); index++) {
            String key = keys.get(index);
            int earlierLimit = Math.max(0, index - 1);
            if (keys.subList(0, earlierLimit).contains(key)) {
                throw failure(path, "derived path contains a cycle");
            }
        }
    }

    private String endpointKey(JsonNode endpoint) {
        if (!endpoint.isObject()) {
            return endpoint.asText();
        }
        List<String> parts = new ArrayList<>();
        for (String field : List.of("catalog", "schema", "table", "column")) {
            String value = endpoint.path(field).asText("");
            if (!value.isBlank()) {
                parts.add(value);
            }
        }
        return String.join(".", parts);
    }

    private List<Path> jsonFiles(Path directory) {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
        } catch (IOException error) {
            throw new ReleaseVerificationException("failed to list sample-data results", error);
        }
    }

    private String stem(Path path) {
        String name = path.getFileName().toString();
        return name.substring(0, name.length() - ".json".length());
    }

    private ReleaseVerificationException failure(Path path, String message) {
        return new ReleaseVerificationException(path + ": " + message);
    }

    private void deleteRecursively(Path root) {
        if (root == null || Files.notExists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException error) {
                    throw new ReleaseVerificationException(
                            "failed to clean result validation workspace", error);
                }
            });
        } catch (IOException error) {
            throw new ReleaseVerificationException(
                    "failed to clean result validation workspace", error);
        }
    }

    private static final class FileState {
        private final Path path;
        private final Map<String, Integer> counts = new java.util.HashMap<>();
        private final Map<Path, Long> lineCounts = new java.util.HashMap<>();
        private final ExternalStringIndex namingIds;
        private final ExternalStringIndex namingReferences;
        private final ExternalStringIndex derivedNamingViewIds;
        private JsonNode summary;
        private int derivedNamingCount;

        private FileState(Path path, Path workspace) {
            this.path = path;
            namingIds = new ExternalStringIndex(workspace.resolve("naming-ids"), 4_096);
            namingReferences = new ExternalStringIndex(workspace.resolve("naming-refs"), 4_096);
            derivedNamingViewIds =
                    new ExternalStringIndex(workspace.resolve("derived-naming-view"), 4_096);
        }

        private void increment(String section) {
            counts.merge(section, 1, Integer::sum);
        }

        private int count(String section) {
            return counts.getOrDefault(section, 0);
        }

        private void finish() {
            ExternalStringIndex.SortedIndex ids = namingIds.finish(true);
            ExternalStringIndex.SortedIndex references = namingReferences.finish(false);
            ExternalStringIndex.SortedIndex derivedView = derivedNamingViewIds.finish(true);
            if (!ExternalStringIndex.containsAll(ids, references)) {
                throw new ReleaseVerificationException(
                        path + ": unresolved NAMING_MATCH evidenceRef");
            }
            if (!ExternalStringIndex.containsAll(ids, derivedView)
                    || derivedView.size() != count("derivedNamingEvidence")) {
                throw new ReleaseVerificationException(
                        path + ": derived naming view is not closed");
            }
            Map<String, Integer> expected = new LinkedHashMap<>();
            expected.put("directRelationshipCount", count("relationships"));
            expected.put("derivedRelationshipCount", count("derivedRelationships"));
            expected.put("totalRelationshipCount",
                    count("relationships") + count("derivedRelationships"));
            expected.put("directDataLineageCount", count("dataLineages"));
            expected.put("derivedDataLineageCount", count("derivedDataLineages"));
            expected.put("totalDataLineageCount",
                    count("dataLineages") + count("derivedDataLineages"));
            expected.put("directNamingEvidenceCount",
                    count("namingEvidence") - derivedNamingCount);
            expected.put("derivedNamingEvidenceCount", derivedNamingCount);
            expected.put("totalNamingEvidenceCount", count("namingEvidence"));
            expected.put("warningCount", count("warnings"));
            JsonNode resolvedSummary = summary == null
                    ? ReleaseVerificationJson.MAPPER.createObjectNode()
                    : summary;
            for (Map.Entry<String, Integer> entry : expected.entrySet()) {
                JsonNode actual = resolvedSummary.get(entry.getKey());
                if (actual != null
                        && (!actual.canConvertToInt() || actual.intValue() != entry.getValue())) {
                    throw new ReleaseVerificationException(
                            path + ": summary." + entry.getKey() + " does not match streamed count");
                }
            }
            if (count("derivedNamingEvidence") != derivedNamingCount) {
                throw new ReleaseVerificationException(
                        path + ": derived naming view count mismatch");
            }
        }
    }

    private record StatementSpan(long start, long end) {
    }
}
