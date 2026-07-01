package com.relationdetector.oracle.fullgrammer.common;

import com.relationdetector.contracts.parse.SqlStatementRecord;

/**
 * Thin version bridge for Oracle full-grammer DDL parsing.
 */
public interface OracleFullGrammerDdlBinding {
    OracleFullGrammerParseSupport.ParsedEvents parseDdl(SqlStatementRecord statement);
}
