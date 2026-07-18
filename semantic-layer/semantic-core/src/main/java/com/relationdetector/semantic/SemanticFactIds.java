package com.relationdetector.semantic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * CN: 从 relation-detector fact 的 canonical physical identity 和语义类型生成 semantic-layer stable ids；derived/direct 前缀分离，字段顺序固定，不使用运行路径或时间。
 * EN: Produces stable semantic-layer ids from canonical physical identity and fact semantics, separating direct and derived prefixes with fixed field order and no runtime paths or timestamps.
 */
public final class SemanticFactIds {
    private SemanticFactIds() {
    }

    public static String relationship(JsonNode relationship, boolean derived) {
        String prefix = derived ? "derivedRelationship" : "relationship";
        return StableSemanticId.of(prefix,
                endpoint(relationship.path("source")),
                endpoint(relationship.path("target")),
                relationship.path("relationType").asText(relationship.path("kind").asText("UNKNOWN")),
                relationship.path("relationSubType").asText(""));
    }

    public static String lineage(JsonNode lineage, boolean derived) {
        String prefix = derived ? "derivedLineage" : "lineage";
        String source = sources(lineage).stream().distinct().sorted()
                .reduce((left, right) -> left + "+" + right).orElse("unknown");
        return StableSemanticId.of(prefix,
                source,
                endpoint(lineage.path("target")),
                lineage.path("flowKind").asText(lineage.path("kind").asText("UNKNOWN")),
                lineage.path("transformType").asText("UNKNOWN"));
    }

    public static String naming(JsonNode naming) {
        return StableSemanticId.of("naming",
                endpoint(naming.path("source")),
                endpoint(naming.path("target")),
                naming.path("rule").asText("UNKNOWN"),
                naming.path("directionHint").asText("false"));
    }

    public static String diagnostic(JsonNode diagnostic) {
        return StableSemanticId.of("diagnostic",
                diagnostic.path("code").asText(diagnostic.path("type").asText("UNKNOWN")),
                diagnostic.path("severity").asText(""),
                diagnostic.path("source").asText(""),
                diagnostic.path("line").asText("0"),
                diagnostic.path("message").asText(""));
    }

    public static List<String> sources(JsonNode lineage) {
        List<String> result = new ArrayList<>();
        JsonNode sources = lineage.path("sources");
        if (sources.isArray()) {
            sources.forEach(source -> {
                String endpoint = endpoint(source);
                if (!endpoint.isBlank()) {
                    result.add(endpoint);
                }
            });
        }
        if (result.isEmpty()) {
            String source = endpoint(lineage.path("source"));
            if (!source.isBlank()) {
                result.add(source);
            }
        }
        return result;
    }

    public static String endpoint(JsonNode endpoint) {
        if (endpoint == null || !endpoint.isObject()) {
            return "";
        }
        String schema = endpoint.path("schema").asText("");
        String table = endpoint.path("table").asText("");
        String column = endpoint.path("column").asText("");
        if (table.isBlank()) {
            return "";
        }
        String tableName = schema.isBlank() ? table : schema + "." + table;
        return column.isBlank() ? tableName : tableName + "." + column;
    }

    public static String slug(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}._-]+", "_");
        normalized = normalized.replaceAll("_+", "_");
        if (normalized.startsWith("_")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("_")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isBlank() ? "unknown" : normalized;
    }
}
