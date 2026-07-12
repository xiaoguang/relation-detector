package com.relationdetector.postgres.fullgrammer.common;

import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;

/**
 * Shared PostgreSQL full-grammer SQL parser implementation.
 *
 * <p>CN: 使用版本 binding 注入 generated parser 和 typed visitor，避免 v16/v17/v18
 * 重复 parser lifecycle。
 *
 * <p>EN: Shared PostgreSQL full-grammer SQL parser. Version bindings inject the
 * generated parser and typed visitor so parser lifecycle code is not repeated
 * across v16/v17/v18.
 */
public final class PostgresFullGrammerStructuredSqlParser extends AbstractPostgresFullGrammerStructuredSqlParser {
    private final PostgresFullGrammerSqlBinding binding;

    public PostgresFullGrammerStructuredSqlParser(PostgresFullGrammerSqlBinding binding) {
        this.binding = binding;
    }

    @Override
    protected int majorVersion() {
        return binding.majorVersion();
    }

    @Override
    protected String lexerName() {
        return binding.lexerName();
    }

    @Override
    protected String parserName() {
        return binding.parserName();
    }

    @Override
    protected FullGrammerSqlParse parseFullGrammer(String sql) {
        return binding.parseSql(sql);
    }

    @Override
    protected PostgresFullGrammerEventOutcome extractEvents(
            SqlStatementRecord statement,
            List<Token> visibleTokens,
            ParserRuleContext root
    ) {
        return binding.extractEvents(statement, visibleTokens, root);
    }
}
