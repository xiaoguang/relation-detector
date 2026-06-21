package com.relationdetector.core.ddl;

import java.util.Locale;

/**
 * Lightweight view over one top-level DDL statement.
 */
record DdlStatementView(String text, int statementIndex, Kind kind) {
    enum Kind {
        CREATE_TABLE,
        ALTER_TABLE,
        CREATE_INDEX,
        OTHER
    }

    static DdlStatementView of(String text, int statementIndex) {
        String prepared = stripLeadingComments(text == null ? "" : text).stripLeading();
        return new DdlStatementView(prepared, statementIndex, classify(normalize(prepared)));
    }

    private static Kind classify(String normalized) {
        if (startsWithWords(normalized, "create", "table")
                || startsWithWords(normalized, "create", "temporary", "table")
                || startsWithWords(normalized, "create", "unlogged", "table")) {
            return Kind.CREATE_TABLE;
        }
        if (startsWithWords(normalized, "alter", "table")) {
            return Kind.ALTER_TABLE;
        }
        if (startsWithWords(normalized, "create", "index")
                || startsWithWords(normalized, "create", "unique", "index")) {
            return Kind.CREATE_INDEX;
        }
        return Kind.OTHER;
    }

    private static boolean startsWithWords(String normalized, String... words) {
        int position = 0;
        for (String word : words) {
            if (position > 0) {
                if (position >= normalized.length() || normalized.charAt(position) != ' ') {
                    return false;
                }
                while (position < normalized.length() && normalized.charAt(position) == ' ') {
                    position++;
                }
            }
            if (!normalized.startsWith(word, position)) {
                return false;
            }
            position += word.length();
        }
        return position == normalized.length()
                || !Character.isLetterOrDigit(normalized.charAt(position)) && normalized.charAt(position) != '_';
    }

    private static String normalize(String text) {
        return (text == null ? "" : text).stripLeading().toLowerCase(Locale.ROOT);
    }

    private static String stripLeadingComments(String text) {
        String value = text.stripLeading();
        boolean changed = true;
        while (changed) {
            changed = false;
            if (value.startsWith("--")) {
                int end = value.indexOf('\n');
                value = end < 0 ? "" : value.substring(end + 1).stripLeading();
                changed = true;
            }
            if (value.startsWith("/*")) {
                int end = value.indexOf("*/", 2);
                value = end < 0 ? "" : value.substring(end + 2).stripLeading();
                changed = true;
            }
        }
        return value;
    }
}
