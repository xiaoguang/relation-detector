package com.relationdetector.postgres.fullgrammer.v16;

import com.relationdetector.core.*;
import com.relationdetector.core.fullgrammer.*;
import java.util.Set;

import com.relationdetector.api.Collectors.StructuredDdlParser;
import com.relationdetector.api.Collectors.StructuredSqlParser;
import com.relationdetector.api.Enums.DatabaseType;

/** PostgreSQL 16 full-grammer module. */
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
