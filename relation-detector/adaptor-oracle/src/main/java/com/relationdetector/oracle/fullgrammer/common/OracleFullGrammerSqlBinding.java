package com.relationdetector.oracle.fullgrammer.common;

import com.relationdetector.contracts.parse.SqlStatementRecord;

/**
 * Thin version bridge for Oracle full-grammer SQL parsing.
 */
public interface OracleFullGrammerSqlBinding {
    String lexerName();

    String parserName();

    String visitorName();

    OracleFullGrammerParseSupport.ParsedEvents parseSql(SqlStatementRecord statement);
}
