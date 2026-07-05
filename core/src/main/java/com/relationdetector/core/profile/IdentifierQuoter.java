package com.relationdetector.core.profile;

import java.util.ArrayList;
import java.util.List;

/**
 * Dialect-aware identifier rendering for bounded profiling queries.
 */
public final class IdentifierQuoter {
    private final String openQuote;
    private final String closeQuote;

    private IdentifierQuoter(String openQuote, String closeQuote) {
        this.openQuote = openQuote;
        this.closeQuote = closeQuote;
    }

    public static IdentifierQuoter mysql() {
        return new IdentifierQuoter("`", "`");
    }

    public static IdentifierQuoter doubleQuote() {
        return new IdentifierQuoter("\"", "\"");
    }

    public static IdentifierQuoter sqlServer() {
        return new IdentifierQuoter("[", "]");
    }

    public String table(String qualifiedName) {
        if (qualifiedName == null || qualifiedName.isBlank()) {
            return "";
        }
        List<String> rendered = new ArrayList<>();
        for (String part : qualifiedName.split("\\.")) {
            String clean = part.trim();
            if (!clean.isBlank()) {
                rendered.add(identifier(clean));
            }
        }
        return String.join(".", rendered);
    }

    public String column(String columnName) {
        return identifier(columnName);
    }

    private String identifier(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String text = raw.trim();
        if (alreadyQuoted(text) || safeIdentifier(text)) {
            return text;
        }
        return openQuote + text.replace(closeQuote, closeQuote + closeQuote) + closeQuote;
    }

    private boolean alreadyQuoted(String text) {
        return (text.startsWith("`") && text.endsWith("`"))
                || (text.startsWith("\"") && text.endsWith("\""))
                || (text.startsWith("[") && text.endsWith("]"));
    }

    private boolean safeIdentifier(String text) {
        if (text.isBlank()) {
            return false;
        }
        char first = text.charAt(0);
        if (!(Character.isLetter(first) || first == '_')) {
            return false;
        }
        for (int index = 1; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (!(Character.isLetterOrDigit(ch) || ch == '_' || ch == '$')) {
                return false;
            }
        }
        return true;
    }
}
