package com.relationdetector.contracts.spi;

/**
 * 数据库 identifier 规范化规则。
 *
 * <p>CN: MySQL/PostgreSQL 等数据库大小写和引用符规则不同，因此 identifier
 * normalization 属于 adaptor。
 *
 * <p>EN: Database-specific identifier normalization rules. Case folding and
 * quoting differ by dialect, so normalization belongs to adaptors.
 */
public interface IdentifierRules {
    enum QualifiedNameSemantics {
        SCHEMA_TABLE,
        CATALOG_TABLE
    }

    String normalize(String identifier);

    /**
     * Maps a two-component qualified name onto the owning dialect namespace axes.
     */
    default QualifiedNameSemantics qualifiedNameSemantics() {
        return QualifiedNameSemantics.SCHEMA_TABLE;
    }

    default String unquote(String identifier) {
        if (identifier == null || identifier.length() < 2) {
            return identifier;
        }
        char first = identifier.charAt(0);
        char last = identifier.charAt(identifier.length() - 1);
        if ((first == '`' && last == '`') || (first == '"' && last == '"')
                || (first == '[' && last == ']')) {
            String body = identifier.substring(1, identifier.length() - 1);
            String doubled = String.valueOf(last) + last;
            return body.replace(doubled, String.valueOf(last));
        }
        return identifier;
    }
}
