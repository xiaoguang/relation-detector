package com.relationdetector.mysql;

import java.util.List;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import com.relationdetector.core.AntlrStructuredSqlParser;
import com.relationdetector.core.MySqlStructuredSqlEventVisitor;
import com.relationdetector.core.SqlDialect;
import com.relationdetector.core.antlr.mysql.MySqlRelationSqlLexer;
import com.relationdetector.core.antlr.mysql.MySqlRelationSqlParser;

/**
 * MySQL dialect selection for the ANTLR structured SQL parser.
 *
 * <p>MySQL now owns a generated lexer/parser pair rather than only passing a
 * dialect enum to the generic parser. Runtime parser mode now defaults to
 * {@code antlr-primary + fallbackOnFailure=true} for MySQL, while
 * {@code antlr-shadow} remains available for comparison diagnostics.
 * {@link com.relationdetector.core.SqlRelationParserRunner} falls back to
 * Simple output if the ANTLR relation extractor misses a Simple baseline
 * relationship.
 */
public final class MySqlAntlrSqlParser extends AntlrStructuredSqlParser {
    public MySqlAntlrSqlParser() {
        super(SqlDialect.MYSQL, new MySqlStructuredSqlEventVisitor());
    }

    @Override
    protected ParsedSql parseAntlr(String sql, SyntaxErrorCounter errors) {
        MySqlRelationSqlLexer lexer = new MySqlRelationSqlLexer(CharStreams.fromString(sql));
        lexer.removeErrorListeners();
        lexer.addErrorListener(errors);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        MySqlRelationSqlParser parser = new MySqlRelationSqlParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(errors);

        parser.script();
        tokens.fill();

        List<Token> visibleTokens = tokens.getTokens().stream()
                .filter(token -> token.getType() != Token.EOF)
                .filter(token -> token.getChannel() == Token.DEFAULT_CHANNEL)
                .toList();
        return new ParsedSql("MySqlRelationSql",
                MySqlRelationSqlLexer.class.getSimpleName(),
                MySqlRelationSqlParser.class.getSimpleName(),
                visibleTokens);
    }
}
