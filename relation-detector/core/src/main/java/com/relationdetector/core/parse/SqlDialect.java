package com.relationdetector.core.parse;

/**
 *
 * SQL dialect selected by a database adaptor for the structured parser.
 */
public enum SqlDialect {
    MYSQL,
    POSTGRES,
    ORACLE,
    SQLSERVER,
    GENERIC
}
