package com.relationdetector.core.parse;

/**
 * CN: 列举 database adaptor 为 structured token-event parser 选择的 SQL 方言。
 * EN: Enumerates the SQL dialect selected by a database adaptor for the structured token-event parser.
 */
public enum SqlDialect {
    MYSQL,
    POSTGRES,
    ORACLE,
    SQLSERVER,
    GENERIC
}
