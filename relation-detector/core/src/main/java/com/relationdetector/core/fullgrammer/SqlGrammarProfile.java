package com.relationdetector.core.fullgrammer;

import java.util.Set;

import com.relationdetector.contracts.Enums.DatabaseType;

/**
 * 版本化 SQL grammar profile。
 *
 * <p>CN: profile 标识数据库方言和大版本语法面，例如 mysql-8.0 或 postgresql-16。
 * 它不拥有 relationship 或 lineage 语义；parse-tree visitor 仍必须输出统一
 * StructuredSqlEvent。
 *
 * <p>EN: Versioned SQL grammar profile identifying dialect/version grammar
 * surface such as mysql-8.0 or postgresql-16. It owns no relationship or
 * lineage semantics; parse-tree visitors still emit the shared StructuredSqlEvent model.
 */
public record SqlGrammarProfile(
        String id,
        DatabaseType databaseType,
        int majorVersion,
        int minorVersion,
        Set<String> capabilities
) {
    public SqlGrammarProfile {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id is required");
        }
        if (databaseType == null) {
            throw new IllegalArgumentException("databaseType is required");
        }
        capabilities = Set.copyOf(capabilities == null ? Set.of() : capabilities);
    }
}
