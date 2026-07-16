package com.relationdetector.contracts.spi;

/**
 * CN: 表示在执行首条 live catalog 查询前即可判定、且不能降级恢复的配置错误；扫描编排器必须向调用方传播该异常。
 *
 * <p>EN: Signals an unrecoverable live-source configuration error detected before the first catalog query; scan
 * orchestration must propagate this exception to the caller instead of converting it into a collection warning.
 */
public final class LiveSourceConfigurationException extends IllegalArgumentException {
    public LiveSourceConfigurationException(String message) {
        super(message);
    }

    public LiveSourceConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
