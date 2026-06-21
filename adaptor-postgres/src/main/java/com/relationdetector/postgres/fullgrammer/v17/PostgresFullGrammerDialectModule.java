package com.relationdetector.postgres.fullgrammer.v17;

import java.util.Set;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.core.fullgrammer.FullGrammerDialectModule;
import com.relationdetector.core.fullgrammer.SqlGrammarProfile;
import com.relationdetector.postgres.fullgrammer.v16.PostgresFullGrammerStructuredDdlParser;
import com.relationdetector.postgres.fullgrammer.v16.PostgresFullGrammerStructuredSqlParser;

/**
 * PostgreSQL 17 full-grammer module 注册入口。
 *
 * <p>CN: 版本由 package/profile 表达。当前 parser 入口复用 PostgreSQL full-grammer
 * typed visitor；profile 层负责让 17.x fixture 不再降级到 postgresql-16。
 *
 * <p>EN: PostgreSQL 17 full-grammer module entry point. The version is carried by
 * the package/profile; the parser entry point reuses the PostgreSQL full-grammer
 * typed visitor while profile selection keeps 17.x fixtures off the v16 fallback path.
 */
public final class PostgresFullGrammerDialectModule implements FullGrammerDialectModule {
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
        return new PostgresFullGrammerStructuredSqlParser();
    }

    @Override
    public StructuredDdlParser structuredDdlParser() {
        return new PostgresFullGrammerStructuredDdlParser();
    }
}
