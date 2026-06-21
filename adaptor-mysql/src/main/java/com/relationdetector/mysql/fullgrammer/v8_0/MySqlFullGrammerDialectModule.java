package com.relationdetector.mysql.fullgrammer.v8_0;

import com.relationdetector.core.*;
import com.relationdetector.core.fullgrammer.*;
import java.util.Set;

import com.relationdetector.api.Collectors.StructuredDdlParser;
import com.relationdetector.api.Collectors.StructuredSqlParser;
import com.relationdetector.api.Enums.DatabaseType;

/** MySQL 8.0 full-grammer module. */
public final class MySqlFullGrammerDialectModule implements FullGrammerDialectModule {
    private static final SqlGrammarProfile PROFILE = new SqlGrammarProfile(
            "mysql-8.0",
            DatabaseType.MYSQL,
            8,
            0,
            Set.of("cte", "json_table", "window_functions", "multi_table_dml"));

    @Override
    public SqlGrammarProfile profile() {
        return PROFILE;
    }

    @Override
    public String implementationName() {
        return "MYSQL_FULL_GRAMMAR_PARSE_TREE_VISITOR";
    }

    @Override
    public StructuredSqlParser sqlParser() {
        return new MySqlFullGrammerStructuredSqlParser();
    }

    @Override
    public StructuredDdlParser structuredDdlParser() {
        return new MySqlFullGrammerStructuredDdlParser();
    }
}
