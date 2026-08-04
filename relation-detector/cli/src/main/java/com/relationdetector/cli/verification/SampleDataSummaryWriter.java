package com.relationdetector.cli.verification;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

/**
 * CN: 逐个流式读取 sample-data 结果的 summary、warning 和 source 小字段并生成发布 TSV；
 * 输入为完整 direct/derived 结果矩阵和生成配置，输出不保留大 fact 数组。
 * EN: Streams only compact summary, warning, and source fields from each sample-data result and writes release
 * TSV reports. It consumes the complete direct/derived matrix without retaining unbounded fact arrays.
 */
final class SampleDataSummaryWriter {
    private static final YAMLMapper YAML = new YAMLMapper();

    /**
     * CN: 读取请求中的完整 case 清单、配置和 direct/derived 结果，流式汇总后写三个 TSV；
     * 输出只包含小型计数与诊断代码，缺失配置或无法读取结果时整体失败。
     * EN: Reads the complete requested case list, configs, and direct/derived results, then writes three streamed
     * TSV summaries. Output contains only compact counts and diagnostic codes; unreadable inputs fail the operation.
     */
    void write(Request request) {
        Set<String> requested = new LinkedHashSet<>(request.requestedCases());
        if (requested.size() != request.requestedCases().size()) {
            throw new ReleaseVerificationException("duplicate requested sample-data case");
        }
        List<String> summary = new ArrayList<>();
        List<String> derived = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        summary.add("parser\tfixtures\tSQL / DDL\trelations\tlineage\tnamingEvidence"
                + "\twarnings\tsources\tjson");
        derived.add("Parser\tFix\tSQL/DDL\tRel\tLin\tName\tDiag\tDerRel\tDerLin\tDerName");
        warnings.add("parser\twarningCode\tcount");
        for (Path directPath : directResults(request.resultDirectory())) {
            String id = logicalId(request.resultDirectory(), directPath);
            if (!requested.isEmpty() && !requested.contains(id)) {
                continue;
            }
            Path config = request.configDirectory().resolve(id + ".yml");
            if (!Files.isRegularFile(config)) {
                continue;
            }
            InputCounts input = inputCounts(config);
            ResultSummary directResult = readResult(directPath);
            summary.add(String.join("\t",
                    id,
                    Integer.toString(input.fixtures()),
                    input.sql() + " / " + input.ddl(),
                    Integer.toString(directResult.directRelationships()),
                    Integer.toString(directResult.directLineage()),
                    Integer.toString(directResult.directNaming()),
                    Integer.toString(directResult.warningCount()),
                    String.join(",", directResult.sources()),
                    directPath.toString()));
            if (directResult.warningCodes().isEmpty()) {
                warnings.add(id + "\tNONE\t0");
            } else {
                directResult.warningCodes().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> warnings.add(
                                id + "\t" + entry.getKey() + "\t" + entry.getValue()));
            }
            Path resultPath = directPath.resolveSibling("result.json");
            if (Files.isRegularFile(resultPath)) {
                ResultSummary derivedResult = readResult(resultPath);
                derived.add(String.join("\t",
                        id,
                        Integer.toString(input.fixtures()),
                        input.sql() + " / " + input.ddl(),
                        Integer.toString(derivedResult.directRelationships()),
                        Integer.toString(derivedResult.directLineage()),
                        Integer.toString(derivedResult.directNaming()),
                        Integer.toString(derivedResult.warningCount()),
                        Integer.toString(derivedResult.derivedRelationships()),
                        Integer.toString(derivedResult.derivedLineage()),
                        Integer.toString(derivedResult.derivedNaming())));
            }
        }
        writeLines(request.summary(), summary);
        writeLines(request.derivedSummary(), derived);
        writeLines(request.warnings(), warnings);
    }

    private List<Path> directResults(Path directory) {
        try (Stream<Path> paths = Files.walk(directory)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals("direct.json"))
                    .sorted()
                    .toList();
        } catch (IOException error) {
            throw new ReleaseVerificationException("failed to list sample-data results", error);
        }
    }

    private InputCounts inputCounts(Path config) {
        try {
            JsonNode root = YAML.readTree(config.toFile());
            JsonNode sources = root.path("sources");
            int ddl = fileListSize(sources.path("ddl").path("files"));
            JsonNode objects = sources.path("objects");
            int sql = fileListSize(objects.path("files"))
                    + fileListSize(sources.path("logs").path("files"));
            for (JsonNode value : objects.path("paths")) {
                Path path = Path.of(value.asText());
                if (Files.isDirectory(path)) {
                    try (Stream<Path> entries = Files.walk(path)) {
                        sql += (int) entries.filter(Files::isRegularFile)
                                .filter(item -> item.getFileName().toString().endsWith(".sql"))
                                .count();
                    }
                }
            }
            return new InputCounts(ddl + sql, sql, ddl);
        } catch (IOException error) {
            throw new ReleaseVerificationException("failed to read sample-data config", error);
        }
    }

    private int fileListSize(JsonNode value) {
        return value.isArray() ? value.size() : 0;
    }

    /**
     * CN: 单遍读取一个结果对象，仅保留 summary、sources 和根 warning codes；返回有界摘要，
     * JSON 损坏或无法读取时抛出发布验证错误，不物化 fact 数组。
     * EN: Reads one result object once while retaining only summary, sources, and root warning codes. It returns a
     * bounded summary and rejects unreadable or malformed JSON without materializing fact arrays.
     */
    private ResultSummary readResult(Path path) {
        JsonNode summary = null;
        List<String> sources = List.of();
        Map<String, Integer> warningCodes = new LinkedHashMap<>();
        int warnings = 0;
        try (JsonParser parser = ReleaseVerificationJson.MAPPER.getFactory().createParser(path.toFile())) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new ReleaseVerificationException("sample-data result root must be an object");
            }
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String field = parser.currentName();
                JsonToken value = parser.nextToken();
                if ("summary".equals(field)) {
                    summary = ReleaseVerificationJson.MAPPER.readTree(parser);
                } else if ("sources".equals(field)) {
                    sources = readTextArray(parser, value);
                } else if ("warnings".equals(field) && value == JsonToken.START_ARRAY) {
                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        JsonNode warning = ReleaseVerificationJson.MAPPER.readTree(parser);
                        warningCodes.merge(warning.path("code").asText("UNKNOWN"), 1, Integer::sum);
                        warnings++;
                    }
                } else {
                    parser.skipChildren();
                }
            }
        } catch (IOException error) {
            throw new ReleaseVerificationException("failed to stream sample-data summary", error);
        }
        JsonNode resolved = summary == null
                ? ReleaseVerificationJson.MAPPER.createObjectNode()
                : summary;
        if (sources.isEmpty() && resolved.path("sources").isArray()) {
            sources = textValues(resolved.path("sources"));
        }
        return new ResultSummary(
                count(resolved, "directRelationshipCount", "relationshipCount"),
                count(resolved, "directDataLineageCount", "dataLineageCount"),
                count(resolved, "directNamingEvidenceCount", "namingEvidenceCount"),
                resolved.path("derivedRelationshipCount").asInt(),
                resolved.path("derivedDataLineageCount").asInt(),
                resolved.path("derivedNamingEvidenceCount").asInt(),
                resolved.has("warningCount") ? resolved.path("warningCount").asInt() : warnings,
                sources,
                Map.copyOf(warningCodes));
    }

    private List<String> readTextArray(JsonParser parser, JsonToken token) throws IOException {
        if (token != JsonToken.START_ARRAY) {
            parser.skipChildren();
            return List.of();
        }
        List<String> values = new ArrayList<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            values.add(parser.getValueAsString(""));
            parser.skipChildren();
        }
        return List.copyOf(values);
    }

    private List<String> textValues(JsonNode values) {
        List<String> result = new ArrayList<>();
        values.forEach(value -> result.add(value.asText()));
        return List.copyOf(result);
    }

    private int count(JsonNode summary, String preferred, String fallback) {
        return summary.has(preferred)
                ? summary.path(preferred).asInt()
                : summary.path(fallback).asInt();
    }

    private void writeLines(Path path, List<String> values) {
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, String.join("\n", values) + "\n");
        } catch (IOException error) {
            throw new ReleaseVerificationException("failed to write sample-data summary", error);
        }
    }

    private String logicalId(Path root, Path view) {
        Path parent = view.getParent();
        if (parent == null) {
            throw new ReleaseVerificationException("sample-data bundle view has no case directory");
        }
        String id = root.toAbsolutePath().normalize()
                .relativize(parent.toAbsolutePath().normalize())
                .toString()
                .replace('\\', '/');
        if (id.isBlank()) {
            throw new ReleaseVerificationException("sample-data bundle case identity is empty");
        }
        return id;
    }

    record Request(
            Path resultDirectory,
            Path configDirectory,
            Path summary,
            Path derivedSummary,
            Path warnings,
            List<String> requestedCases
    ) {
        Request {
            requestedCases = List.copyOf(requestedCases);
        }
    }

    private record InputCounts(int fixtures, int sql, int ddl) {
    }

    private record ResultSummary(
            int directRelationships,
            int directLineage,
            int directNaming,
            int derivedRelationships,
            int derivedLineage,
            int derivedNaming,
            int warningCount,
            List<String> sources,
            Map<String, Integer> warningCodes
    ) {
    }
}
