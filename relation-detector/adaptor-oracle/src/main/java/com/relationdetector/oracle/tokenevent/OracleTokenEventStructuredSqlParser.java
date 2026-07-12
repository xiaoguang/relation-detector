package com.relationdetector.oracle.tokenevent;

import java.util.List;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.core.parse.SqlDialect;
import com.relationdetector.core.parse.AntlrSqlParseSupport.SyntaxErrorCounter;
import com.relationdetector.core.tokenevent.TypedDialectTokenEventStructuredSqlParser;

/**
 * Oracle token-event SQL parser.
 *
 * <p>CN: Oracle token-event 运行 Oracle 自己的 typed structural grammar，
 * 作为无版本 profile 或 full-grammar hard failure 时的保守 fallback。Oracle-only
 * PL/SQL 与版本特性必须通过 grammar context 和 visitor 扩展，不由 scanner 猜测。
 *
 * <p>EN: Oracle token-event SQL parser backed by OracleRelationSql.g4. Oracle
 * structures are added through typed grammar contexts and visitors rather than
 * scanners.
 */
public final class OracleTokenEventStructuredSqlParser
        extends TypedDialectTokenEventStructuredSqlParser<OracleRelationSqlParser.ScriptContext> {
    public OracleTokenEventStructuredSqlParser() {
        super(SqlDialect.ORACLE,
                "OracleRelationSql",
                OracleRelationSqlLexer.class.getSimpleName(),
                OracleRelationSqlParser.class.getSimpleName(),
                OracleTokenEventParseTreeVisitor.class.getSimpleName());
    }

    @Override
    protected TypedParse<OracleRelationSqlParser.ScriptContext> parseTyped(String sql, SyntaxErrorCounter errors) {
        OracleRelationSqlLexer lexer = new OracleRelationSqlLexer(CharStreams.fromString(sql));
        lexer.removeErrorListeners();
        lexer.addErrorListener(errors);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        OracleRelationSqlParser parser = new OracleRelationSqlParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(errors);

        OracleRelationSqlParser.ScriptContext root = parser.script();
        tokens.fill();
        List<Token> visibleTokens = tokens.getTokens().stream()
                .filter(token -> token.getType() != Token.EOF)
                .filter(token -> token.getChannel() == Token.DEFAULT_CHANNEL)
                .toList();
        return new TypedParse<>(root, visibleTokens);
    }

    @Override
    protected List<StructuredSqlEvent> collectTypedEvents(
            SqlStatementRecord statement,
            OracleRelationSqlParser.ScriptContext root
    ) {
        return new OracleTokenEventParseTreeVisitor(statement).collect(root);
    }

    @Override
    protected boolean isUnknownStatement(ParseTree tree) {
        return tree instanceof OracleRelationSqlParser.UnknownStatementContext;
    }
}
