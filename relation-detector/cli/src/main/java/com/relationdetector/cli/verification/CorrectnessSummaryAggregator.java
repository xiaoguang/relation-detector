package com.relationdetector.cli.verification;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * CN: 聚合顺序隔离运行的 correctness 小型 summary，验证 discovered 总量和 parser category 完整性；
 * 输入为 summary 文件，输出单一稳定 summary，不读取 fixture 或 parser 大结果。
 * EN: Aggregates small correctness summaries from isolated runs and proves discovered-count and parser-category
 * coverage. It emits one stable summary without reading fixture or parser result payloads.
 */
final class CorrectnessSummaryAggregator {
    void aggregate(List<Path> summaries, Set<String> expectedCategories, Path output) {
        if (summaries.isEmpty() || expectedCategories.isEmpty()) {
            throw new ReleaseVerificationException(
                    "correctness summaries and expected categories are required");
        }
        Integer discovered = null;
        int selected = 0;
        int executed = 0;
        int passed = 0;
        int failed = 0;
        long elapsed = 0;
        Map<String, JsonNode> categories = new LinkedHashMap<>();
        for (Path path : summaries) {
            JsonNode summary = read(path);
            int currentDiscovered = requiredInt(summary, "discovered", path);
            if (discovered == null) {
                discovered = currentDiscovered;
            } else if (discovered != currentDiscovered) {
                throw new ReleaseVerificationException(
                        "inconsistent discovered correctness counts");
            }
            int groupSelected = requiredInt(summary, "selected", path);
            int groupExecuted = requiredInt(summary, "executed", path);
            int groupPassed = requiredInt(summary, "passed", path);
            int groupFailed = requiredInt(summary, "failed", path);
            if (groupSelected != groupExecuted || groupExecuted != groupPassed || groupFailed != 0) {
                throw new ReleaseVerificationException("correctness group did not pass: " + path);
            }
            selected += groupSelected;
            executed += groupExecuted;
            passed += groupPassed;
            failed += groupFailed;
            elapsed += summary.path("elapsedMillis").asLong(0);
            for (JsonNode category : summary.path("dialectVersions")) {
                String id = category.path("id").asText("");
                if (id.isBlank() || categories.putIfAbsent(id, category.deepCopy()) != null) {
                    throw new ReleaseVerificationException(
                            "duplicate or missing correctness parser category");
                }
            }
        }
        Set<String> actual = new TreeSet<>(categories.keySet());
        if (!actual.equals(new TreeSet<>(expectedCategories))) {
            throw new ReleaseVerificationException("correctness parser category coverage mismatch");
        }
        if (discovered == null || selected != discovered) {
            throw new ReleaseVerificationException("correctness fixture coverage mismatch");
        }
        ObjectNode result = ReleaseVerificationJson.MAPPER.createObjectNode();
        result.put("profile", "full-isolated");
        result.put("discovered", discovered);
        result.put("selected", selected);
        result.put("executed", executed);
        result.put("passed", passed);
        result.put("failed", failed);
        result.put("elapsedMillis", elapsed);
        ArrayNode dialects = result.putArray("dialectVersions");
        categories.keySet().stream().sorted().forEach(id -> dialects.add(categories.get(id)));
        ReleaseVerificationJson.write(output, result);
    }

    private JsonNode read(Path path) {
        try {
            return ReleaseVerificationJson.MAPPER.readTree(path.toFile());
        } catch (IOException error) {
            throw new ReleaseVerificationException("failed to read correctness summary", error);
        }
    }

    private int requiredInt(JsonNode root, String field, Path source) {
        JsonNode value = root.get(field);
        if (value == null || !value.canConvertToInt()) {
            throw new ReleaseVerificationException(
                    "invalid correctness " + field + " in " + source);
        }
        return value.intValue();
    }
}
