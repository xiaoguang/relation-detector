package com.relationdetector.mysql.fullgrammer.v5_7;

import com.relationdetector.core.fullgrammer.*;
import java.util.Set;

import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.contracts.Enums.DatabaseType;

/**
 * MySQL 5.7 full-grammer module 注册入口。
 *
 * <p>CN: 通过 ServiceLoader 暴露 mysql-5.7 profile 及其 SQL/DDL parser。core 只看
 * FullGrammerDialectModule 接口，不直接依赖本类。
 *
 * <p>EN: MySQL 5.7 full-grammer module entry point registered through
 * ServiceLoader. It exposes the mysql-5.7 profile and SQL/DDL parsers while
 * core depends only on FullGrammerDialectModule.
 */
public final class MySql57FullGrammerDialectModule implements FullGrammerDialectModule {
    private static final SqlGrammarProfile PROFILE = new SqlGrammarProfile(
            "mysql-5.7",
            DatabaseType.MYSQL,
            5,
            7,
            Set.of("generated_columns", "json_basic", "multi_table_dml", "stored_routines"));

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
