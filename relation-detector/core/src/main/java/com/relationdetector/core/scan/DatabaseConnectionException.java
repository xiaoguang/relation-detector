package com.relationdetector.core.scan;

/**
 * CN: 表示在 scan 开始前无法打开已配置 JDBC connection，不包含 URL 或 driver 原始消息。
 * EN: Signals that the configured JDBC connection could not be opened before scanning, without exposing the URL or raw driver message.
 */
public final class DatabaseConnectionException extends RuntimeException {
    public DatabaseConnectionException(Throwable cause) {
        super("Unable to connect to the configured database", cause);
    }
}
