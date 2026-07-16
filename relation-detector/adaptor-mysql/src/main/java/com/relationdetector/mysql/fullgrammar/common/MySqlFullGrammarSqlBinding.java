package com.relationdetector.mysql.fullgrammar.common;

import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.mysql.fullgrammar.common.MySqlFullGrammarParseSupport.FullGrammarSqlParse;
import java.util.List;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

/**
 *
 * Version bridge for MySQL full-grammar SQL parsing.
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
