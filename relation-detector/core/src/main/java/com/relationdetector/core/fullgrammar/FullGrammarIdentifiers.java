package com.relationdetector.core.fullgrammar;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Exact identifier normalization for values already selected by typed contexts. */
public final class FullGrammarIdentifiers {
    private FullGrammarIdentifiers() {
    }

    public static Optional<FullGrammarColumnReference> columnReference(String raw) {
        List<String> parts = qualifiedParts(raw);
        if (parts.isEmpty()) {
            return Optional.empty();
        }
        String column = parts.get(parts.size() - 1);
        String qualifier = parts.size() < 2 ? "" : parts.get(parts.size() - 2);
        return column.isBlank()
                ? Optional.empty()
                : Optional.of(new FullGrammarColumnReference(qualifier, column));
    }

    public static List<String> qualifiedParts(String raw) {
        String text = raw == null ? "" : raw.strip();
        if (text.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (quote == 0 && (character == '`' || character == '"' || character == '[')) {
                quote = character == '[' ? ']' : character;
                current.append(character);
            } else if (quote != 0 && character == quote) {
                current.append(character);
                quote = 0;
            } else if (quote == 0 && character == '.') {
                add(result, current);
            } else {
                current.append(character);
            }
        }
        add(result, current);
        return List.copyOf(result);
    }

    public static String clean(String raw) {
        String value = raw == null ? "" : raw.strip();
        while (value.length() >= 2
                && ((value.startsWith("`") && value.endsWith("`"))
                || (value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("[") && value.endsWith("]")))) {
            value = value.substring(1, value.length() - 1).strip();
        }
        return value;
    }

    private static void add(List<String> result, StringBuilder current) {
        String value = clean(current.toString());
        if (!value.isBlank()) {
            result.add(value);
        }
        current.setLength(0);
    }
}
