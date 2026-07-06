package com.relationdetector.contracts.spi;

import java.util.Map;
import java.util.function.Consumer;

import com.relationdetector.contracts.model.WarningMessage;

/**
 * adaptor hook 的运行时上下文。
 *
 * <p>CN: core 把 scan scope、可选参数和 warning sink 传给 adaptor，adaptor 用它报告
 * partial failure，而不是直接操作 ScanResult。
 *
 * <p>EN: Runtime context passed from core to adaptor hooks. Adaptors use it for
 * scope/options and partial-failure warnings without mutating ScanResult directly.
 */
public record AdaptorContext(ScanScope scope, Map<String, Object> options, Consumer<WarningMessage> warningSink) {
    public AdaptorContext(ScanScope scope, Map<String, Object> options) {
        this(scope, options, warning -> {
        });
    }

    public AdaptorContext {
        options = options == null ? Map.of() : Map.copyOf(options);
        warningSink = warningSink == null ? warning -> {
        } : warningSink;
    }

    /**
     * 将非致命诊断加入当前 scan。
     *
     * <p>CN: parser 和 adaptor 用它报告 partial failure，例如 DDL 文件局部失败后
     * 仍继续 metadata、对象定义和日志扫描。
     *
     * <p>EN: Adds a non-fatal diagnostic to the current scan. Parsers and
     * adaptors use this for partial-failure reporting while allowing other scan
     * sources to continue.
     */
    public void warn(WarningMessage warning) {
        warningSink.accept(warning);
    }
}
