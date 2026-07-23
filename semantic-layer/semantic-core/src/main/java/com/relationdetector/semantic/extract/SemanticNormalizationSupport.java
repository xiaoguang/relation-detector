package com.relationdetector.semantic.extract;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.relationdetector.semantic.model.PhysicalEndpointRef;

final class SemanticNormalizationSupport {
    private SemanticNormalizationSupport() {
    }

    static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    static String tableOf(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return "";
        }
        return PhysicalEndpointRef.column(endpoint).table();
    }

    static String slug(String value) {
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

    static List<String> mutableStrings(List<String> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }

    static void addIfAbsent(List<String> values, String value, Set<String> linkedEntities) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!values.contains(value)) {
            values.add(value);
        }
        linkedEntities.add(value);
    }

    static List<String> distinct(List<String> values) {
        return new ArrayList<>(new LinkedHashSet<>(values == null ? List.of() : values));
    }
}
