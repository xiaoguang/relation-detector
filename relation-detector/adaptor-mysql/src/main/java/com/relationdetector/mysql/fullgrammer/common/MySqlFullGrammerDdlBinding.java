package com.relationdetector.mysql.fullgrammer.common;

import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.mysql.fullgrammer.common.MySqlFullGrammerParseSupport.FullGrammerDdlParse;
import java.util.List;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 * Version bridge for MySQL full-grammer DDL parsing.
 */
public interface MySqlFullGrammerDdlBinding {
    String lexerName();

    String parserName();

    String collectorName();

    FullGrammerDdlParse parseDdl(String ddl);

    List<StructuredSqlEvent> collectEvents(String sourceName, ParserRuleContext root);
}
