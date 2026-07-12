package com.relationdetector.postgres.tokenevent;

import java.util.List;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.core.parse.SqlDialect;
import com.relationdetector.core.parse.AntlrSqlParseSupport.SyntaxErrorCounter;
import com.relationdetector.postgres.tokenevent.PostgresRelationSqlLexer;
import com.relationdetector.postgres.tokenevent.PostgresRelationSqlParser;
import com.relationdetector.core.tokenevent.TypedDialectTokenEventStructuredSqlParser;

/**
 * PostgreSQL token-event SQL parser。
 *
 * <p>CN: PostgreSQL token-event 运行 PostgreSQL typed structural grammar，
 * 并由 typed visitor 直接生成结构事件。公共 portable subset 由 typed visitor
 * 处理；{@code ONLY}、{@code ROWS FROM}、{@code UNNEST ... WITH ORDINALITY}、
 * {@code MATERIALIZED} CTE 等 PostgreSQL-only rowset 规则只留在 PostgreSQL 方言层。
 *
 * <p>EN: PostgreSQL token-event SQL parser. It runs the PostgreSQL typed
 * structural grammar and emits structured events directly from the typed
 * visitor. The portable subset is handled by the typed visitor;
 * PostgreSQL-only rowsets stay in this dialect layer.
 */
public final class PostgresTokenEventStructuredSqlParser
        extends TypedDialectTokenEventStructuredSqlParser<PostgresRelationSqlParser.ScriptContext> {
    public PostgresTokenEventStructuredSqlParser() {
        super(SqlDialect.POSTGRES,
                "PostgresRelationSql",
                PostgresRelationSqlLexer.class.getSimpleName(),
                PostgresRelationSqlParser.class.getSimpleName(),
                PostgresTokenEventParseTreeVisitor.class.getSimpleName());
    }

    @Override
    protected TypedParse<PostgresRelationSqlParser.ScriptContext> parseTyped(String sql, SyntaxErrorCounter errors) {
        PostgresRelationSqlLexer lexer = new PostgresRelationSqlLexer(CharStreams.fromString(sql));
        lexer.removeErrorListeners();
        lexer.addErrorListener(errors);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        PostgresRelationSqlParser parser = new PostgresRelationSqlParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(errors);

        PostgresRelationSqlParser.ScriptContext root = parser.script();
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
            PostgresRelationSqlParser.ScriptContext root
    ) {
        return new PostgresTokenEventParseTreeVisitor(statement).collect(root);
    }

    @Override
    protected TypedEventCollection collectTypedResult(
            SqlStatementRecord statement,
            PostgresRelationSqlParser.ScriptContext root
    ) {
        PostgresTokenEventParseTreeVisitor visitor = new PostgresTokenEventParseTreeVisitor(statement);
        List<StructuredSqlEvent> events = visitor.collect(root);
        return new TypedEventCollection(events, visitor.warnings());
    }

    @Override
    protected boolean isUnknownStatement(ParseTree tree) {
        return tree instanceof PostgresRelationSqlParser.UnknownStatementContext;
    }
}
