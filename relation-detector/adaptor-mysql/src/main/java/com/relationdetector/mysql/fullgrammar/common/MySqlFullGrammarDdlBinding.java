package com.relationdetector.mysql.fullgrammar.common;

import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.mysql.fullgrammar.common.MySqlFullGrammarParseSupport.FullGrammarDdlParse;
import java.util.List;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 * CN: 定义 version package 到共享 full-grammar DDL 生命周期的 typed bridge；本版本 generated context 由绑定消费，输出仅为 DDL events，不承担 relationship 合并。
 * EN: Defines the typed bridge from a version package to the shared full-grammar DDL lifecycle. The binding consumes only its generated contexts and emits DDL events without merging relationships.
 */
public interface MySqlFullGrammarDdlBinding {
    String lexerName();

    String parserName();

    String collectorName();

    FullGrammarDdlParse parseDdl(String ddl);

    List<StructuredSqlEvent> collectEvents(String sourceName, ParserRuleContext root);
}
