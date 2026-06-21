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
