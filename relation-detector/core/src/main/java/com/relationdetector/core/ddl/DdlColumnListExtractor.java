package com.relationdetector.core.ddl;

import java.util.List;

/**
 *
 * Shared identifier cleanup helpers for typed DDL visitors.
 */
public final class DdlColumnListExtractor {
    public String clean(String value) {
        if (value == null) {
            return "";
        }
        String text = value.strip();
        while ((text.startsWith("`") && text.endsWith("`"))
                || (text.startsWith("\"") && text.endsWith("\""))
                || (text.startsWith("[") && text.endsWith("]"))) {
            text = text.substring(1, text.length() - 1);
        }
        return text.replace("`.", ".")
                .replace(".`", ".")
                .replace("\".", ".")
                .replace(".\"", ".")
                .replace(" ", "");
    }

    public List<String> nonBlank(List<String> values) {
        return values.stream()
                .map(this::clean)
                .filter(value -> !value.isBlank())
                .toList();
    }
}
