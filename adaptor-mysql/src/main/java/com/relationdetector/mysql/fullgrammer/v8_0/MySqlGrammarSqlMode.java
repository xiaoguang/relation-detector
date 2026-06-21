package com.relationdetector.mysql.fullgrammer.v8_0;

/**
 * MySQL {@code SQL_MODE} flags that affect the MySQL 8.0 full-grammer runtime.
 *
 * <p>This enum models MySQL server/session SQL_MODE values such as
 * {@code ANSI_QUOTES} and {@code PIPES_AS_CONCAT}; it is unrelated to the
 * product-level {@code parser.mode} setting.
 */
public enum MySqlGrammarSqlMode {
    NoMode,
    AnsiQuotes,
    HighNotPrecedence,
    PipesAsConcat,
    IgnoreSpace,
    NoBackslashEscapes
}
