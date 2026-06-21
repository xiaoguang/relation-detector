package com.relationdetector.postgres.fullgrammer.v18;

import java.util.Set;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.core.fullgrammer.FullGrammerDialectModule;
import com.relationdetector.core.fullgrammer.SqlGrammarProfile;

/**
 * PostgreSQL 18 full-grammer module 注册入口。
 *
 * <p>CN: 版本由 package/profile 表达。该 module 只返回 v18 package 内的 parser，
 * 避免 18.x correctness 误用较低版本 full-grammer。
 *
 * <p>EN: PostgreSQL 18 full-grammer module entry point. The package/profile carry
 * the version boundary and return only parsers from the v18 package so 18.x
 * correctness cannot silently reuse a lower-version full-grammer implementation.
 */
public final class PostgresFullGrammerDialectModule implements FullGrammerDialectModule {
    private static final SqlGrammarProfile PROFILE = new SqlGrammarProfile(
            "postgresql-18",
            DatabaseType.POSTGRESQL,
            18,
            0,
            Set.of("returning_old_new", "virtual_generated_columns", "temporal_constraints",
                    "merge_returning", "json_table", "sql_json",
                    "materialized_cte", "lateral", "set_returning_functions"));

    @Override
    public SqlGrammarProfile profile() {
        return PROFILE;
    }

    @Override
    public String implementationName() {
        return "POSTGRESQL_18_FULL_GRAMMER_PARSE_TREE_VISITOR";
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
