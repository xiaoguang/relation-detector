package com.relationdetector.mysql.tokenevent;

import java.util.List;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import com.relationdetector.core.tokenevent.MySqlTokenEventSqlEventBuilder;
import com.relationdetector.core.parse.SqlDialect;
import com.relationdetector.core.tokenevent.TokenEventStructuredSqlParser;
import com.relationdetector.core.parse.AntlrSqlParseSupport.ParsedSql;
import com.relationdetector.core.parse.AntlrSqlParseSupport.SyntaxErrorCounter;
import com.relationdetector.core.antlr.mysql.MySqlRelationSqlLexer;
import com.relationdetector.core.antlr.mysql.MySqlRelationSqlParser;

/**
 * MySQL token-event parser used by the production SQL parser.
 *
 * <p>MySQL keeps its own lexer/parser entry point and event builder so
 * MySQL-only syntax such as {@code STRAIGHT_JOIN}, index hints,
 * {@code PARTITION}, and {@code JSON_TABLE} does not leak into PostgreSQL or the
 * common token-event builder.
 */
public final class MySqlTokenEventStructuredSqlParser extends TokenEventStructuredSqlParser {
    public MySqlTokenEventStructuredSqlParser() {
        super(SqlDialect.MYSQL, new MySqlTokenEventSqlEventBuilder());
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
