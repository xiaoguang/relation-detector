package com.relationdetector.mysql.fullgrammar.common;

import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.mysql.fullgrammar.common.MySqlFullGrammarParseSupport.FullGrammarSqlParse;
import java.util.List;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

/**
 * CN: 定义 version package 到共享 full-grammar SQL 生命周期的 typed bridge；输入 SQL 和本版本 generated root，输出 structured events，禁止跨版本或 token-event delegate。
 * EN: Defines the typed bridge from a version package to the shared full-grammar SQL lifecycle. It accepts SQL and that version's generated root, emits structured events, and forbids cross-version or token-event delegation.
 */
public interface MySqlFullGrammarSqlBinding {
    String lexerName();

    String parserName();

    FullGrammarSqlParse parseSql(String sql);

    List<StructuredSqlEvent> extractEvents(
            SqlStatementRecord statement,
            List<Token> visibleTokens,
            ParserRuleContext root);
}
