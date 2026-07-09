package com.relationdetector.semantic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;

/** Stable ids for semantic-layer facts projected from relation-detector JSON. */
public final class SemanticFactIds {
    private SemanticFactIds() {
    }

    public static String relationship(JsonNode relationship, boolean derived, int index) {
        String prefix = derived ? "derivedRelationship" : "relationship";
        return prefix + ":" + endpoint(relationship.path("source")) + "->"
                + endpoint(relationship.path("target")) + ":"
                + relationship.path("relationType").asText(relationship.path("kind").asText("UNKNOWN")) + ":"
                + index;
    }

    public static String lineage(JsonNode lineage, boolean derived, int index) {
        String prefix = derived ? "derivedLineage" : "lineage";
        String source = sources(lineage).stream().reduce((left, right) -> left + "+" + right).orElse("unknown");
        return prefix + ":" + source + "->" + endpoint(lineage.path("target")) + ":"
                + lineage.path("flowKind").asText(lineage.path("kind").asText("UNKNOWN")) + ":"
                + lineage.path("transformType").asText("UNKNOWN") + ":" + index;
    }

    public static String naming(JsonNode naming, int index) {
        String id = naming.path("id").asText("");
        if (!id.isBlank()) {
            return id.startsWith("naming:") ? id : "naming:" + id;
        }
        return "naming:" + endpoint(naming.path("source")) + "->" + endpoint(naming.path("target")) + ":"
                + naming.path("rule").asText("UNKNOWN") + ":" + index;
    }

    public static String diagnostic(JsonNode diagnostic, int index) {
        return "diagnostic:" + index + ":" + diagnostic.path("code").asText(diagnostic.path("type").asText("UNKNOWN"));
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

    public static String tableOfEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return "";
        }
        int index = endpoint.lastIndexOf('.');
        return index < 0 ? endpoint : endpoint.substring(0, index);
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
