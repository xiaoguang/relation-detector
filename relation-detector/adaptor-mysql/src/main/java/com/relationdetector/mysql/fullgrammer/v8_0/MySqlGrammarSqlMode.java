package com.relationdetector.mysql.fullgrammer.v8_0;

/**
 * MySQL full-grammer runtime 的 {@code SQL_MODE} flags。
 *
 * <p>CN: 这些值对应 MySQL server/session SQL_MODE，例如 {@code ANSI_QUOTES} 和
 * {@code PIPES_AS_CONCAT}。它们只影响 MySQL grammar runtime，不是系统级
 * {@code parser.mode}。
 *
 * <p>EN: MySQL SQL_MODE flags used by the MySQL 8.0 full-grammer runtime. They
 * model server/session values such as ANSI_QUOTES and PIPES_AS_CONCAT and are
 * unrelated to the product-level parser.mode setting.
 */
public enum MySqlGrammarSqlMode {
    NoMode,
    AnsiQuotes,
    HighNotPrecedence,
    PipesAsConcat,
    IgnoreSpace,
    NoBackslashEscapes
}
