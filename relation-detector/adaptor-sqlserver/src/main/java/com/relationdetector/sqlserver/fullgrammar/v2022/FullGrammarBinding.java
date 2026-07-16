package com.relationdetector.sqlserver.fullgrammar.v2022;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.core.parse.AntlrSqlParseSupport.SyntaxErrorCounter;
import com.relationdetector.sqlserver.fullgrammar.common.SqlServerFullGrammarSqlBinding;

/** CN: 绑定 SQL Server 2022 generated parser 与共享 collector，不接受其他版本 context。 EN: Binds only the SQL Server 2022 generated parser to the shared collector. */
public final class FullGrammarBinding implements SqlServerFullGrammarSqlBinding {
    @Override
    public ParsedTree parse(SqlStatementRecord statement) {
        SyntaxErrorCounter errors = new SyntaxErrorCounter();
        SqlServerFullGrammarLexer lexer = new SqlServerFullGrammarLexer(CharStreams.fromString(statement.sql()));
        lexer.removeErrorListeners();
        lexer.addErrorListener(errors);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        SqlServerFullGrammarParser parser = new SqlServerFullGrammarParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(errors);
        SqlServerFullGrammarParser.Tsql_fileContext root = parser.tsql_file();
        tokens.fill();
        return new ParsedTree(parser, root, tokens.getTokens(), errors.count());
    }

    @Override
    public String lexerName() {
        return SqlServerFullGrammarLexer.class.getSimpleName();
    }

    @Override
    public String parserName() {
        return SqlServerFullGrammarParser.class.getSimpleName();
    }

    @Override
    public String visitorName() {
        return "SqlServerParseTreeEventCollector";
    }

    @Override
    public com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter parseTreeAdapter() {
        return new SqlServerParseTreeAdapter();
    }
}
