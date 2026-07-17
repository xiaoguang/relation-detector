package com.relationdetector.core.scan;

/**
 * CN: 表示在打开 JDBC 或执行 parser 前发现的不可执行 scan 配置。
 *
 * <p>EN: Signals an executable scan-configuration error detected before JDBC
 * or parser work starts.
 */
public final class ScanConfigurationException extends IllegalArgumentException {
    public ScanConfigurationException(String message) {
        super(message);
    }

    public ScanConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
