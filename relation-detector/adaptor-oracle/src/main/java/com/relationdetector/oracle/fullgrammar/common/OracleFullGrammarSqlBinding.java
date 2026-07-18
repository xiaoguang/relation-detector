package com.relationdetector.oracle.fullgrammar.common;

import com.relationdetector.contracts.parse.SqlStatementRecord;

/**
 * CN: 定义 Oracle version package 到共享 SQL parser 生命周期的 typed bridge；绑定消费本版本 generated root 并返回 events/warnings，不跨版本 delegate。
 * EN: Defines the typed bridge from an Oracle version package to the shared SQL lifecycle. The binding consumes its own generated root and returns events and warnings without cross-version delegation.
 */
public interface OracleFullGrammarSqlBinding {
    String lexerName();

    String parserName();

    String visitorName();

    OracleFullGrammarParseSupport.ParsedEvents parseSql(SqlStatementRecord statement);
}
