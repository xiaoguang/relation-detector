package com.relationdetector.oracle.fullgrammar.common;

import com.relationdetector.contracts.parse.SqlStatementRecord;

/**
 * CN: 定义 Oracle version package 到共享 DDL 生命周期的 typed bridge；只将本版本 generated contexts 转成 DDL events，不重建或执行声明。
 * EN: Defines the typed bridge from an Oracle version package to the shared DDL lifecycle, translating only that version's generated contexts into DDL events without rebuilding or executing declarations.
 */
public interface OracleFullGrammarDdlBinding {
    OracleFullGrammarParseSupport.ParsedEvents parseDdl(SqlStatementRecord statement);
}
