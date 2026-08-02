package com.relationdetector.core.parser.fullgrammar.expression;

import com.relationdetector.core.parser.fullgrammar.expression.FullGrammarColumnReference;

import java.util.List;
import java.util.Optional;

import com.relationdetector.contracts.identifier.QualifiedIdentifierParser;

/**
 * CN: 对 typed context 已确认的 identifier 做引号感知的精确分段，不推断 SQL 结构。
 * EN: Performs quote-aware exact segmentation of identifiers already selected by typed contexts without inferring SQL structure.
 */
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
        return QualifiedIdentifierParser.parts(raw).stream().map(FullGrammarIdentifiers::clean).toList();
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

}
