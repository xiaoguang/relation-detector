package com.relationdetector.core.scan;

/**
 * CN: 表示选定 adaptor 无法履行当前 scan 所需的 SPI、数据库类型、parser 或 collector 契约；
 * direct API 接收该失败，CLI 将其稳定映射为 ADAPTOR_ERROR。本异常不表示 YAML 格式错误，也不负责
 * JDBC 连接或 live namespace 校验。
 *
 * <p>EN: Signals that the selected adaptor cannot satisfy the SPI, database-type, parser, or collector contract
 * required by a scan. Direct callers receive this failure and the CLI maps it to ADAPTOR_ERROR. It does not
 * represent malformed configuration and does not validate JDBC connectivity or live namespaces.
 */
public final class AdaptorContractException extends IllegalArgumentException {
    public AdaptorContractException(String message) {
        super(message);
    }
}
