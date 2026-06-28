package com.relationdetector.mysql.tokenevent;

import java.util.List;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.core.parse.SqlDialect;
import com.relationdetector.core.parse.AntlrSqlParseSupport.SyntaxErrorCounter;
import com.relationdetector.core.antlr.mysql.MySqlRelationSqlLexer;
import com.relationdetector.core.antlr.mysql.MySqlRelationSqlParser;
import com.relationdetector.core.tokenevent.TypedDialectTokenEventStructuredSqlParser;

/**
 * MySQL token-event SQL parser。
 *
 * <p>CN: MySQL token-event 运行 MySQL typed structural grammar，并由 typed
 * visitor 直接生成结构事件。公共 portable subset 由 typed visitor 处理；
 * {@code STRAIGHT_JOIN}、index hints、{@code PARTITION}、{@code JSON_TABLE} 等
 * MySQL-only 语法只留在 MySQL 方言层，不泄漏到 PostgreSQL 或 common parser。
 *
 * <p>EN: MySQL token-event SQL parser. It runs the MySQL typed structural
 * grammar and emits structured events directly from the typed visitor. The
 * portable subset is handled by the typed visitor; MySQL-only syntax stays in
 * this dialect layer.
 */
public final class MySqlTokenEventStructuredSqlParser
        extends TypedDialectTokenEventStructuredSqlParser<MySqlRelationSqlParser.ScriptContext> {
    public MySqlTokenEventStructuredSqlParser() {
        super(SqlDialect.MYSQL,
                "MySqlRelationSql",
                MySqlRelationSqlLexer.class.getSimpleName(),
                MySqlRelationSqlParser.class.getSimpleName(),
                MySqlTokenEventParseTreeVisitor.class.getSimpleName());
    }

    @Override
    protected TypedParse<MySqlRelationSqlParser.ScriptContext> parseTyped(String sql, SyntaxErrorCounter errors) {
        MySqlRelationSqlLexer lexer = new MySqlRelationSqlLexer(CharStreams.fromString(sql));
        lexer.removeErrorListeners();
        lexer.addErrorListener(errors);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        MySqlRelationSqlParser parser = new MySqlRelationSqlParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(errors);

        MySqlRelationSqlParser.ScriptContext root = parser.script();
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
            MySqlRelationSqlParser.ScriptContext root
    ) {
        return new MySqlTokenEventParseTreeVisitor(statement).collect(root);
    }
}
