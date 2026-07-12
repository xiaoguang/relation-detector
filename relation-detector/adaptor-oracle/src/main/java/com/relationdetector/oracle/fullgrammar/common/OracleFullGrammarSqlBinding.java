package com.relationdetector.oracle.fullgrammar.common;

import com.relationdetector.contracts.parse.SqlStatementRecord;

/**
 * Thin version bridge for Oracle full-grammar SQL parsing.
 */
public interface OracleFullGrammarSqlBinding {
    String lexerName();

    String parserName();

    String visitorName();

    OracleFullGrammarParseSupport.ParsedEvents parseSql(SqlStatementRecord statement);
}
