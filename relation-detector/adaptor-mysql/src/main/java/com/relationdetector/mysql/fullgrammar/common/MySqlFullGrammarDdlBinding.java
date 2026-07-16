package com.relationdetector.mysql.fullgrammar.common;

import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.mysql.fullgrammar.common.MySqlFullGrammarParseSupport.FullGrammarDdlParse;
import java.util.List;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 *
 * Version bridge for MySQL full-grammar DDL parsing.
 */
public interface MySqlFullGrammarDdlBinding {
    String lexerName();

    String parserName();

    String collectorName();

    FullGrammarDdlParse parseDdl(String ddl);

    List<StructuredSqlEvent> collectEvents(String sourceName, ParserRuleContext root);
}
