package com.relationdetector.postgres;

import java.util.List;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import com.relationdetector.core.AntlrStructuredSqlParser;
import com.relationdetector.core.PostgresStructuredSqlEventVisitor;
import com.relationdetector.core.SqlDialect;
import com.relationdetector.core.antlr.postgres.PostgresRelationSqlLexer;
import com.relationdetector.core.antlr.postgres.PostgresRelationSqlParser;

/**
 * PostgreSQL dialect selection for the ANTLR structured SQL parser.
 *
 * <p>PostgreSQL now owns a generated lexer/parser pair rather than only passing
 * a dialect enum to the generic parser. Runtime parser mode now defaults to
 * {@code antlr-primary + fallbackOnFailure=true} for PostgreSQL, while
 * {@code antlr-shadow} remains available for comparison diagnostics.
 * {@link com.relationdetector.core.SqlRelationParserRunner} falls back to
 * Simple output if the ANTLR relation extractor misses a Simple baseline
 * relationship.
 */
public final class PostgresAntlrSqlParser extends AntlrStructuredSqlParser {
    public PostgresAntlrSqlParser() {
        super(SqlDialect.POSTGRES, new PostgresStructuredSqlEventVisitor());
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
