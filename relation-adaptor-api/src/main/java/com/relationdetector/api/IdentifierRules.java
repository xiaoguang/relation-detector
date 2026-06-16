package com.relationdetector.api;

/** Database-specific identifier normalization rules. */
public interface IdentifierRules {
    String normalize(String identifier);

    default String unquote(String identifier) {
        if (identifier == null || identifier.length() < 2) {
            return identifier;
        }
        char first = identifier.charAt(0);
        char last = identifier.charAt(identifier.length() - 1);
        if ((first == '`' && last == '`') || (first == '"' && last == '"')) {
            return identifier.substring(1, identifier.length() - 1);
        }
        return identifier;
    }
}
