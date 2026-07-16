package com.relationdetector.core.diagnostics;

/**
 * CN: 区分 live JDBC 操作的权限、超时和普通查询失败，供 warning 类型与安全消息映射使用。
 *
 * <p>EN: Classifies live JDBC failures for warning-type and sanitized-message mapping.
 */
public enum JdbcFailureKind {
    PERMISSION,
    TIMEOUT,
    QUERY_FAILED
}
