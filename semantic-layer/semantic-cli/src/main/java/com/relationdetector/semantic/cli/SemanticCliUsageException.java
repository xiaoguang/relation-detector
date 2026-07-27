package com.relationdetector.semantic.cli;

/**
 * CN: 标识 semantic CLI 参数、配置或 API key 缺失等用户可修正的启动错误；输入来自参数解析边界，
 * 输出由 Main 映射为 exit 2，禁止承载 wire、模型、分片或 artifact 运行失败。
 * EN: Marks user-correctable semantic CLI startup errors in arguments, configuration, or API-key availability.
 * Main maps it to exit 2; wire, model, sharding, and artifact runtime failures must not use this type.
 */
final class SemanticCliUsageException extends IllegalArgumentException {
    SemanticCliUsageException(String message) {
        super(message);
    }

    SemanticCliUsageException(String message, Throwable cause) {
        super(message, cause);
    }
}
