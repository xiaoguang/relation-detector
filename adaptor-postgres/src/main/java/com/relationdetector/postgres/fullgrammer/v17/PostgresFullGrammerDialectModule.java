package com.relationdetector.postgres.fullgrammer.v17;

import java.util.Set;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.core.fullgrammer.FullGrammerDialectModule;
import com.relationdetector.core.fullgrammer.SqlGrammarProfile;
import com.relationdetector.postgres.fullgrammer.common.PostgresFullGrammerStructuredDdlParser;
import com.relationdetector.postgres.fullgrammer.common.PostgresFullGrammerStructuredSqlParser;

/**
 * PostgreSQL 17 full-grammer module 注册入口。
 *
 * <p>CN: 版本由 package/profile 表达。该 module 只返回 v17 package 内的 parser，
 * 避免 17.x correctness 误用 v16 full-grammer。
 *
 * <p>EN: PostgreSQL 17 full-grammer module entry point. The version is carried by
 * the package/profile; it returns only parsers from the v17 package so 17.x
 * correctness cannot silently reuse the v16 full-grammer implementation.
 */
public final class PostgresFullGrammerDialectModule implements FullGrammerDialectModule {
    private final PostgresFullGrammerVersionBinding binding = new PostgresFullGrammerVersionBinding();
    private static final SqlGrammarProfile PROFILE = new SqlGrammarProfile(
            "postgresql-17",
            DatabaseType.POSTGRESQL,
            17,
            0,
            Set.of("merge", "merge_returning", "json_table", "sql_json",
                    "materialized_cte", "lateral", "set_returning_functions"));

    @Override
    public SqlGrammarProfile profile() {
        return PROFILE;
    }

    @Override
    public String implementationName() {
        return "POSTGRESQL_17_FULL_GRAMMER_PARSE_TREE_VISITOR";
    }

    @Override
    public StructuredSqlParser sqlParser() {
        return new PostgresFullGrammerStructuredSqlParser(binding);
    }

    @Override
    public StructuredDdlParser structuredDdlParser() {
        return new PostgresFullGrammerStructuredDdlParser(binding);
    }
}
