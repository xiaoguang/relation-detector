package com.relationdetector.oracle.fullgrammar.common;

import com.relationdetector.contracts.parse.SqlStatementRecord;

/**
 *
 * Thin version bridge for Oracle full-grammar DDL parsing.
 */
public interface OracleFullGrammarDdlBinding {
    OracleFullGrammarParseSupport.ParsedEvents parseDdl(SqlStatementRecord statement);
}
