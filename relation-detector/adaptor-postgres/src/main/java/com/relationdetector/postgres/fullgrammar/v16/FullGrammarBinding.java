package com.relationdetector.postgres.fullgrammar.v16;

import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.postgres.fullgrammar.common.AbstractPostgresFullGrammarStructuredDdlParser.FullGrammarDdlParse;
import com.relationdetector.postgres.fullgrammar.common.AbstractPostgresFullGrammarStructuredSqlParser.FullGrammarSqlParse;
import com.relationdetector.postgres.fullgrammar.common.PostgresFullGrammarDdlBinding;
import com.relationdetector.postgres.fullgrammar.common.PostgresDdlVersionPolicy;
import com.relationdetector.postgres.fullgrammar.common.PostgresFullGrammarParseSupport;
import com.relationdetector.postgres.fullgrammar.common.PostgresFullGrammarSqlBinding;

/**
 * PostgreSQL 16 generated grammar binding.
 *
 * <p>CN: 这是 v16 package 的薄桥接层，只暴露 generated lexer/parser、SQL typed
 * visitor 和 DDL collector 给 common parser。
 *
 * <p>EN: Thin bridge for the v16 package. It exposes the generated lexer/parser,
 * SQL typed visitor, and DDL collector to the shared parser implementation.
 */
final class FullGrammarBinding implements PostgresFullGrammarSqlBinding, PostgresFullGrammarDdlBinding {
    private static final int MAJOR_VERSION = 16;
    private final PostgresFullGrammarDdlEventCollector ddlCollector = new PostgresFullGrammarDdlEventCollector(PostgresDdlVersionPolicy.standard());

    @Override
    public int majorVersion() {
        return MAJOR_VERSION;
    }

    @Override
    public String lexerName() {
        return PostgresFullGrammarLexer.class.getSimpleName();
    }

    @Override
    public String parserName() {
        return PostgresFullGrammarParser.class.getSimpleName();
    }

    @Override
    public FullGrammarSqlParse parseSql(String sql) {
        return PostgresFullGrammarParseSupport.parseSql(
                sql,
                PostgresFullGrammarLexer::new,
                PostgresFullGrammarParser::new,
                PostgresFullGrammarParser::root);
    }

    @Override
    public com.relationdetector.postgres.fullgrammar.common.PostgresFullGrammarEventOutcome extractEvents(
            SqlStatementRecord statement,
            List<Token> visibleTokens,
            ParserRuleContext root
    ) {
        return new PostgresFullGrammarParseTreeVisitor(statement, visibleTokens).extractOutcome(root);
    }

    @Override
    public String collectorName() {
        return ddlCollector.getClass().getSimpleName();
    }

    @Override
    public FullGrammarDdlParse parseDdl(String ddl) {
        return PostgresFullGrammarParseSupport.parseDdl(
                ddl,
                PostgresFullGrammarLexer::new,
                PostgresFullGrammarParser::new,
                PostgresFullGrammarParser::root);
    }

    @Override
    public List<StructuredSqlEvent> collectEvents(String sourceName, ParserRuleContext root) {
        return ddlCollector.collect(sourceName, root);
    }
}
