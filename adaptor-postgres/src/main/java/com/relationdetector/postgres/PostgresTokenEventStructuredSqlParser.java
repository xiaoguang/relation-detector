package com.relationdetector.postgres;

import java.util.List;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import com.relationdetector.core.PostgresTokenEventSqlEventBuilder;
import com.relationdetector.core.SqlDialect;
import com.relationdetector.core.TokenEventStructuredSqlParser;
import com.relationdetector.core.AntlrSqlParseSupport.ParsedSql;
import com.relationdetector.core.AntlrSqlParseSupport.SyntaxErrorCounter;
import com.relationdetector.core.antlr.postgres.PostgresRelationSqlLexer;
import com.relationdetector.core.antlr.postgres.PostgresRelationSqlParser;

/**
 * PostgreSQL Token/Event parser used by production SQL parsing.
 *
 * <p>PostgreSQL keeps its own lexer/parser entry point and event builder so
 * PostgreSQL-only rowsets such as {@code ONLY}, {@code ROWS FROM},
 * {@code UNNEST ... WITH ORDINALITY}, and {@code MATERIALIZED} CTEs stay out of
 * MySQL and the common Token/Event builder.
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
