package com.relationdetector.sqlserver.tokenevent;

import java.util.List;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.core.parse.AntlrSllParseSupport;
import com.relationdetector.core.parse.AntlrSqlParseSupport.SyntaxErrorCounter;
import com.relationdetector.core.parse.SqlDialect;
import com.relationdetector.core.tokenevent.TypedDialectTokenEventStructuredSqlParser;

public final class SqlServerTokenEventStructuredSqlParser
        extends TypedDialectTokenEventStructuredSqlParser<SqlServerRelationSqlParser.Tsql_fileContext> {
    public SqlServerTokenEventStructuredSqlParser() {
        super(SqlDialect.SQLSERVER,
                "SqlServerRelationSql",
                SqlServerRelationSqlLexer.class.getSimpleName(),
                SqlServerRelationSqlParser.class.getSimpleName(),
                SqlServerTokenEventParseTreeVisitor.class.getSimpleName());
    }

    @Override
    protected TypedParse<SqlServerRelationSqlParser.Tsql_fileContext> parseTyped(String sql, SyntaxErrorCounter errors) {
        SqlServerRelationSqlLexer lexer = new SqlServerRelationSqlLexer(CharStreams.fromString(sql));
        lexer.removeErrorListeners();
        lexer.addErrorListener(errors);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        SqlServerRelationSqlParser.Tsql_fileContext root = AntlrSllParseSupport.parse(
                tokens, SqlServerRelationSqlParser::new, SqlServerRelationSqlParser::tsql_file, errors).root();
        List<Token> visibleTokens = tokens.getTokens().stream()
                .filter(token -> token.getType() != Token.EOF)
                .filter(token -> token.getChannel() == Token.DEFAULT_CHANNEL)
                .toList();
        return new TypedParse<>(root, visibleTokens);
    }

    @Override
    protected List<StructuredSqlEvent> collectTypedEvents(
            SqlStatementRecord statement,
            SqlServerRelationSqlParser.Tsql_fileContext root
    ) {
        return new SqlServerTokenEventParseTreeVisitor(statement).collect(root);
    }

    @Override
    protected boolean isUnknownStatement(ParseTree tree) {
        return tree instanceof SqlServerRelationSqlParser.Unknown_statementContext;
    }
}
