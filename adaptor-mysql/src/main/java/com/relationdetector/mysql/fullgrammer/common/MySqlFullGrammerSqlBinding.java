package com.relationdetector.mysql.fullgrammer.common;

import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.mysql.fullgrammer.common.MySqlFullGrammerParseSupport.FullGrammerSqlParse;
import java.util.List;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

/**
 * Version bridge for MySQL full-grammer SQL parsing.
 */
public interface MySqlFullGrammerSqlBinding {
    String lexerName();

    String parserName();

    FullGrammerSqlParse parseSql(String sql);

    List<StructuredSqlEvent> extractEvents(
            SqlStatementRecord statement,
            List<Token> visibleTokens,
            ParserRuleContext root);
}
