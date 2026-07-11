package com.relationdetector.sqlserver.fullgrammer.common;

import java.util.List;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.core.fullgrammer.FullGrammerParseTreeAdapter;

public interface SqlServerFullGrammerSqlBinding {
    ParsedTree parse(SqlStatementRecord statement);

    String lexerName();

    String parserName();

    String visitorName();

    FullGrammerParseTreeAdapter parseTreeAdapter();

    record ParsedTree(Parser parser, ParserRuleContext root, List<Token> tokens, int syntaxErrors) {
    }
}
