package com.relationdetector.postgres.fullgrammer.v17;

import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.postgres.fullgrammer.common.AbstractPostgresFullGrammerStructuredDdlParser.FullGrammerDdlParse;
import com.relationdetector.postgres.fullgrammer.common.AbstractPostgresFullGrammerStructuredSqlParser.FullGrammerSqlParse;
import com.relationdetector.postgres.fullgrammer.common.PostgresFullGrammerDdlBinding;
import com.relationdetector.postgres.fullgrammer.common.PostgresDdlVersionPolicy;
import com.relationdetector.postgres.fullgrammer.common.PostgresFullGrammerParseSupport;
import com.relationdetector.postgres.fullgrammer.common.PostgresFullGrammerSqlBinding;

/**
 * PostgreSQL 17 generated grammar binding.
 *
 * <p>CN: 这是 v17 package 的薄桥接层，只暴露 generated lexer/parser、SQL typed
 * visitor 和 DDL collector 给 common parser。
 *
 * <p>EN: Thin bridge for the v17 package. It exposes the generated lexer/parser,
 * SQL typed visitor, and DDL collector to the shared parser implementation.
 */
final class PostgresFullGrammerVersionBinding implements PostgresFullGrammerSqlBinding, PostgresFullGrammerDdlBinding {
    private static final int MAJOR_VERSION = 17;
    private final PostgresFullGrammerDdlEventCollector ddlCollector = new PostgresFullGrammerDdlEventCollector(PostgresDdlVersionPolicy.standard());

    @Override
    public int majorVersion() {
        return MAJOR_VERSION;
    }

    @Override
    public String lexerName() {
        return PostgresFullGrammerLexer.class.getSimpleName();
    }

    @Override
    public String parserName() {
        return PostgresFullGrammerParser.class.getSimpleName();
    }

    @Override
    public FullGrammerSqlParse parseSql(String sql) {
        return PostgresFullGrammerParseSupport.parseSql(
                sql,
                PostgresFullGrammerLexer::new,
                PostgresFullGrammerParser::new,
                PostgresFullGrammerParser::root);
    }

    @Override
    public List<StructuredSqlEvent> extractEvents(
            SqlStatementRecord statement,
            List<Token> visibleTokens,
            ParserRuleContext root
    ) {
        return new PostgresFullGrammerParseTreeVisitor(statement, visibleTokens).extract(root);
    }

    @Override
    public String collectorName() {
        return ddlCollector.getClass().getSimpleName();
    }

    @Override
    public FullGrammerDdlParse parseDdl(String ddl) {
        return PostgresFullGrammerParseSupport.parseDdl(
                ddl,
                PostgresFullGrammerLexer::new,
                PostgresFullGrammerParser::new,
                PostgresFullGrammerParser::root);
    }

    @Override
    public List<StructuredSqlEvent> collectEvents(String sourceName, ParserRuleContext root) {
        return ddlCollector.collect(sourceName, root);
    }
}
