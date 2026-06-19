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
 * <p>MySQL owns a generated lexer/parser pair rather than only passing a
 * dialect enum to the generic parser. The SQL runtime uses this ANTLR path as
 * the MySQL correctness baseline.
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
