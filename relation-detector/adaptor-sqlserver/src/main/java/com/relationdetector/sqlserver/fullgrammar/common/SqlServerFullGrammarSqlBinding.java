package com.relationdetector.sqlserver.fullgrammar.common;

import java.util.List;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter;

/**
 * CN: 隔离 versioned generated lexer/parser 与共享 SQL Server collector，返回 typed root、tokens 和语法错误数；不解释事件语义。
 * EN: Isolates a versioned generated lexer/parser from the shared SQL Server collector and returns its typed root,
 * tokens, and syntax-error count; it does not interpret event semantics.
 */
public interface SqlServerFullGrammarSqlBinding {
    ParsedTree parse(SqlStatementRecord statement);

    String lexerName();

    String parserName();

    String visitorName();

    FullGrammarParseTreeAdapter parseTreeAdapter();

    record ParsedTree(Parser parser, ParserRuleContext root, List<Token> tokens, int syntaxErrors) {
    }
}
