package com.relationdetector.postgres.tokenevent;

import java.util.List;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import com.relationdetector.core.tokenevent.PostgresTokenEventSqlEventBuilder;
import com.relationdetector.core.parse.SqlDialect;
import com.relationdetector.core.tokenevent.TokenEventStructuredSqlParser;
import com.relationdetector.core.parse.AntlrSqlParseSupport.ParsedSql;
import com.relationdetector.core.parse.AntlrSqlParseSupport.SyntaxErrorCounter;
import com.relationdetector.core.antlr.postgres.PostgresRelationSqlLexer;
import com.relationdetector.core.antlr.postgres.PostgresRelationSqlParser;

/**
 * PostgreSQL token-event SQL parser。
 *
 * <p>CN: PostgreSQL 保留自己的 tolerant lexer/parser 入口和 event builder，使
 * {@code ONLY}、{@code ROWS FROM}、{@code UNNEST ... WITH ORDINALITY}、
 * {@code MATERIALIZED} CTE 等 PostgreSQL-only rowset 规则不会泄漏到 MySQL。
 *
 * <p>EN: PostgreSQL token-event SQL parser. PostgreSQL keeps its own tolerant
 * lexer/parser entry point and event builder so PostgreSQL-only rowsets such as
 * ONLY, ROWS FROM, UNNEST WITH ORDINALITY, and MATERIALIZED CTEs stay out of
 * MySQL and the common token-event builder.
 */
public final class PostgresTokenEventStructuredSqlParser extends TokenEventStructuredSqlParser {
    public PostgresTokenEventStructuredSqlParser() {
        super(SqlDialect.POSTGRES, new PostgresTokenEventSqlEventBuilder());
    }

    @Override
    protected ParsedSql parseAntlr(String sql, SyntaxErrorCounter errors) {
        PostgresRelationSqlLexer lexer = new PostgresRelationSqlLexer(CharStreams.fromString(sql));
        lexer.removeErrorListeners();
        lexer.addErrorListener(errors);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        PostgresRelationSqlParser parser = new PostgresRelationSqlParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(errors);

        parser.script();
        tokens.fill();

        List<Token> visibleTokens = tokens.getTokens().stream()
                .filter(token -> token.getType() != Token.EOF)
                .filter(token -> token.getChannel() == Token.DEFAULT_CHANNEL)
                .toList();
        return new ParsedSql("PostgresRelationSql",
                PostgresRelationSqlLexer.class.getSimpleName(),
                PostgresRelationSqlParser.class.getSimpleName(),
                visibleTokens);
    }
}
