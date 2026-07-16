package com.relationdetector.core.script;

/**
 * CN: 标识 script framing 所采用的客户端分隔规则；它不表示 SQL parser profile 或数据库版本能力。
 * EN: Selects client-side statement framing rules; it does not identify a SQL parser profile or version capability.
 */
public enum ScriptDialect {
    COMMON,
    MYSQL,
    POSTGRESQL,
    ORACLE,
    SQLSERVER
}
