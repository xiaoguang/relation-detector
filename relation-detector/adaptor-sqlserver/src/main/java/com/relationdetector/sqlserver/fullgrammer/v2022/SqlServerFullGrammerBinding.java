package com.relationdetector.sqlserver.fullgrammer.v2022;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.core.parse.AntlrSqlParseSupport.SyntaxErrorCounter;
import com.relationdetector.sqlserver.fullgrammer.common.SqlServerFullGrammerSqlBinding;

public final class SqlServerFullGrammerBinding implements SqlServerFullGrammerSqlBinding {
    @Override
    public ParsedTree parse(SqlStatementRecord statement) {
        SyntaxErrorCounter errors = new SyntaxErrorCounter();
        SqlServerFullGrammerLexer lexer = new SqlServerFullGrammerLexer(CharStreams.fromString(statement.sql()));
        lexer.removeErrorListeners();
        lexer.addErrorListener(errors);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        SqlServerFullGrammerParser parser = new SqlServerFullGrammerParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(errors);
        SqlServerFullGrammerParser.Tsql_fileContext root = parser.tsql_file();
        tokens.fill();
        return new ParsedTree(parser, root, tokens.getTokens(), errors.count());
    }

    @Override
    public String lexerName() {
        return SqlServerFullGrammerLexer.class.getSimpleName();
    }

    @Override
    public String parserName() {
        return SqlServerFullGrammerParser.class.getSimpleName();
    }

    @Override
    public String visitorName() {
        return "SqlServerParseTreeEventCollector";
    }
}
