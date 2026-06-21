package com.relationdetector.postgres.fullgrammer.v18;

import java.util.Set;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.core.fullgrammer.FullGrammerDialectModule;
import com.relationdetector.core.fullgrammer.SqlGrammarProfile;
import com.relationdetector.postgres.fullgrammer.v16.PostgresFullGrammerStructuredDdlParser;
import com.relationdetector.postgres.fullgrammer.v16.PostgresFullGrammerStructuredSqlParser;

/**
 * PostgreSQL 18 full-grammer module 注册入口。
 *
 * <p>CN: 版本由 package/profile 表达。该 profile 锁定 PostgreSQL 18.x correctness
 * fixture 的选择路径，避免它们被误判为 16/17 兼容输入。
 *
 * <p>EN: PostgreSQL 18 full-grammer module entry point. The package/profile carry
 * the version boundary so 18.x correctness fixtures exercise the intended grammar
 * profile instead of a lower-version fallback.
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
