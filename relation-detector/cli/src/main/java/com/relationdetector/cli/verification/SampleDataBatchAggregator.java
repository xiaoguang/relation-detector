package com.relationdetector.cli.verification;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * CN: 聚合按数据库组隔离执行的 sample-data batch 小报告，验证每个 case 成功及产物存在，
 * 再按调用方给出的发布顺序输出；不读取 case 的大 JSON 内容。
 * EN: Aggregates small sample-data batch reports from isolated database groups, verifies successful cases and
 * artifact presence, and emits them in the caller's release order without reading large case JSON payloads.
 */
final class SampleDataBatchAggregator {
    void aggregate(List<Path> reports, List<String> expectedCases, Path output) {
        if (reports.isEmpty() || expectedCases.isEmpty()
                || expectedCases.size() != new java.util.HashSet<>(expectedCases).size()) {
            throw new ReleaseVerificationException(
                    "sample-data reports and unique expected cases are required");
        }
        Path outputParent = output.toAbsolutePath().normalize().getParent();
        if (outputParent == null) {
            throw new ReleaseVerificationException("sample-data aggregate output has no parent directory");
        }
        Path resultRoot = outputParent.resolve("results");
        Map<String, JsonNode> cases = new LinkedHashMap<>();
        for (Path path : reports) {
            JsonNode report = read(path);
            JsonNode values = report.path("cases");
            if (!integerEquals(report, "artifactSchemaVersion", 2)
                    || !values.isArray()
                    || !integerEquals(report.path("summary"), "caseCount", values.size())
                    || !integerEquals(report.path("summary"), "successCount", values.size())
                    || !integerEquals(report.path("summary"), "failedCount", 0)
                    || !integerEquals(report.path("summary"), "skippedCount", 0)) {
                throw new ReleaseVerificationException(
                        "isolated sample-data group did not complete");
            }
            for (JsonNode item : values) {
                String id = item.path("id").asText("");
                if (!isCaseId(id)
                        || cases.putIfAbsent(id, item.deepCopy()) != null
                        || !"SUCCESS".equals(item.path("status").asText())) {
                    throw new ReleaseVerificationException(
                            "duplicate, missing or unsuccessful sample-data case");
                }
                String value = item.path("outputBundle").asText("");
                if (value.isBlank()) {
                    throw new ReleaseVerificationException(
                            "sample-data case output bundle is missing");
                }
                Path bundle = Path.of(value);
                Path expectedBundle = resultRoot.resolve(id).normalize();
                if (!bundle.toAbsolutePath().normalize().equals(expectedBundle)
                        || !Files.isDirectory(bundle, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                        || !Files.isRegularFile(bundle.resolve("result.json"),
                                java.nio.file.LinkOption.NOFOLLOW_LINKS)
                        || !Files.isRegularFile(bundle.resolve("direct.json"),
                                java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                    throw new ReleaseVerificationException(
                            "sample-data case output bundle is incomplete");
                }
            }
        }
        if (cases.size() != expectedCases.size()
                || !cases.keySet().equals(new java.util.HashSet<>(expectedCases))) {
            throw new ReleaseVerificationException("sample-data case coverage mismatch");
        }
        ObjectNode result = ReleaseVerificationJson.MAPPER.createObjectNode();
        result.put("artifactSchemaVersion", 2);
        result.putObject("summary")
                .put("caseCount", expectedCases.size())
                .put("successCount", expectedCases.size())
                .put("failedCount", 0)
                .put("skippedCount", 0);
        ArrayNode ordered = result.putArray("cases");
        expectedCases.forEach(id -> ordered.add(cases.get(id)));
        ReleaseVerificationJson.write(output, result);
    }

    private boolean integerEquals(JsonNode parent, String field, int expected) {
        JsonNode value = parent.get(field);
        return value != null && value.isIntegralNumber() && value.canConvertToInt()
                && value.intValue() == expected;
    }

    private boolean isCaseId(String value) {
        if (value == null || value.isEmpty() || !isLowercaseLetterOrDigit(value.charAt(0))) {
            return false;
        }
        for (int index = 1; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!isLowercaseLetterOrDigit(character)
                    && character != '.' && character != '_' && character != '-') {
                return false;
            }
        }
        return true;
    }

    private boolean isLowercaseLetterOrDigit(char character) {
        return character >= 'a' && character <= 'z'
                || character >= '0' && character <= '9';
    }

    private JsonNode read(Path path) {
        try {
            return ReleaseVerificationJson.MAPPER.readTree(path.toFile());
        } catch (IOException error) {
            throw new ReleaseVerificationException("failed to read sample-data batch report", error);
        }
    }
}
