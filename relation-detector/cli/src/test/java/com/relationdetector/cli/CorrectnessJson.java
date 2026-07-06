package com.relationdetector.cli;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class CorrectnessJson {
    private CorrectnessJson() {
    }

    static String expectedRelationsJson(List<String> fingerprints, List<String> forbiddenTables) {
        return "{\n"
                + "  \"fingerprints\": " + stringArrayJson(fingerprints) + ",\n"
                + "  \"forbiddenTables\": " + stringArrayJson(forbiddenTables) + "\n"
                + "}\n";
    }

    static String expectedLineageJson(
            List<String> fingerprints,
            List<String> forbiddenSources,
            List<String> forbiddenTargets,
            Map<String, Long> warningCodes
    ) {
        return "{\n"
                + "  \"fingerprints\": " + stringArrayJson(fingerprints) + ",\n"
                + "  \"forbiddenSources\": " + stringArrayJson(forbiddenSources) + ",\n"
                + "  \"forbiddenTargets\": " + stringArrayJson(forbiddenTargets) + ",\n"
                + "  \"warningCodes\": " + longMapJson(warningCodes) + "\n"
                + "}\n";
    }

    static String expectedDiagnosticsJson(String fixtureSha256, Map<String, Long> warningCodes) {
        return "{\n"
                + "  \"fixtureSha256\": \"" + escapeJson(fixtureSha256) + "\",\n"
                + "  \"warningCodes\": " + longMapJson(warningCodes) + "\n"
                + "}\n";
    }

    static String expectedNamingEvidenceJson(List<String> fingerprints) {
        return "{\n"
                + "  \"fingerprints\": " + stringArrayJson(fingerprints) + "\n"
                + "}\n";
    }

    static String stringField(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"([^\"]*)\"")
                .matcher(json);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Missing string field " + field);
        }
        return matcher.group(1);
    }

    static List<String> stringArray(String json, String field) {
        String body = arrayBody(json, field);
        if (body == null) {
            return List.of();
        }
        return stringArrayFromBody(body);
    }

    static Map<String, Long> objectLongs(String json, String field) {
        String body = objectBody(json, field);
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        return objectLongsFromBody(body);
    }

    private static List<String> stringArrayFromBody(String bodyText) {
        String body = bodyText.trim();
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

    private static Map<String, Long> objectLongsFromBody(String body) {
        if (body.isEmpty()) {
            return Map.of();
        }
        Map<String, Long> values = new LinkedHashMap<>();
        Matcher entry = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(\\d+)").matcher(body);
        while (entry.find()) {
            values.put(entry.group(1), Long.parseLong(entry.group(2)));
        }
        return values;
    }

    private static String arrayBody(String json, String field) {
        Matcher fieldMatcher = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:").matcher(json);
        if (!fieldMatcher.find()) {
            return null;
        }
        int start = json.indexOf('[', fieldMatcher.end());
        if (start < 0) {
            return null;
        }
        boolean inString = false;
        boolean escaped = false;
        for (int i = start + 1; i < json.length(); i++) {
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
            if (!inString && c == ']') {
                return json.substring(start + 1, i).trim();
            }
        }
        throw new IllegalArgumentException("Unclosed array field " + field);
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

    private static String stringArrayJson(List<String> values) {
        if (values.isEmpty()) {
            return "[]";
        }
        return values.stream()
                .map(value -> "    \"" + escapeJson(value) + "\"")
                .collect(Collectors.joining(",\n", "[\n", "\n  ]"));
    }

    private static String longMapJson(Map<String, Long> values) {
        if (values.isEmpty()) {
            return "{}";
        }
        return values.entrySet().stream()
                .map(entry -> "    \"" + escapeJson(entry.getKey()) + "\": " + entry.getValue())
                .collect(Collectors.joining(",\n", "{\n", "\n  }"));
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
