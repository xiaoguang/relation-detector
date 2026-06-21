package com.relationdetector.postgres.fullgrammer.v16;

import com.relationdetector.core.fullgrammer.*;
import java.util.Set;

import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.contracts.Enums.DatabaseType;

/**
 * PostgreSQL 16 full-grammer module 注册入口。
 *
 * <p>CN: 通过 ServiceLoader 暴露 postgresql-16 profile 及其 SQL/DDL parser。core
 * 只看 FullGrammerDialectModule 接口，不直接依赖本类。
 *
 * <p>EN: PostgreSQL 16 full-grammer module entry point registered through
 * ServiceLoader. It exposes the postgresql-16 profile and SQL/DDL parsers while
 * core depends only on FullGrammerDialectModule.
 */
public final class PostgresFullGrammerDialectModule implements FullGrammerDialectModule {
    private static final SqlGrammarProfile PROFILE = new SqlGrammarProfile(
            "postgresql-16",
            DatabaseType.POSTGRESQL,
            16,
            0,
            Set.of("merge", "materialized_cte", "lateral", "set_returning_functions"));

    @Override
    public SqlGrammarProfile profile() {
        return PROFILE;
    }

    @Override
    public String implementationName() {
        return "POSTGRESQL_FULL_GRAMMAR_PARSE_TREE_VISITOR";
    }

    @Override
    public StructuredSqlParser sqlParser() {
        return new PostgresFullGrammerStructuredSqlParser();
    }

    @Override
    public StructuredDdlParser structuredDdlParser() {
        return new PostgresFullGrammerStructuredDdlParser();
    }
}
