package com.relationdetector.contracts.spi;

/**
 * CN: 定义外部 database adaptor 必须编译实现的当前二进制 SPI 版本。
 * EN: Defines the current binary SPI version against which external database adaptors must be compiled.
 */
public final class AdaptorApiVersion {
    public static final int CURRENT = 6;

    private AdaptorApiVersion() {
    }
}
