package com.relationdetector.mysql.fullgrammar.v8_0;

import com.relationdetector.core.fullgrammar.*;
import java.util.Set;

import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.mysql.fullgrammar.common.MySqlFullGrammarVersionPolicy;

/**
 * MySQL 8.0 full-grammar module 注册入口。
 *
 * <p>CN: 通过 ServiceLoader 暴露 mysql-8.0 profile 及其 SQL/DDL parser。core 只看
 * FullGrammarDialectModule 接口，不直接依赖本类。
 *
 * <p>EN: MySQL 8.0 full-grammar module entry point registered through
 * ServiceLoader. It exposes the mysql-8.0 profile and SQL/DDL parsers while
 * core depends only on FullGrammarDialectModule.
 */
public final class FullGrammarDialectModule
        implements com.relationdetector.core.fullgrammar.FullGrammarDialectModule {
    private static final MySqlFullGrammarVersionPolicy POLICY = new MySqlFullGrammarVersionPolicy(
            8,
            0,
            Set.of("cte", "json_table", "window_functions", "multi_table_dml"));
    private static final SqlGrammarProfile PROFILE = new SqlGrammarProfile(
            POLICY.profileId(),
            DatabaseType.MYSQL,
            POLICY.major(),
            POLICY.minor(),
            POLICY.capabilities());

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
        return new MySqlFullGrammarStructuredSqlParser();
    }

    @Override
    public StructuredDdlParser structuredDdlParser() {
        return new MySqlFullGrammarStructuredDdlParser();
    }
}
