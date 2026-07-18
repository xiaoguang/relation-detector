package com.relationdetector.semantic.cli;

/**
 * CN: 将 semantic CLI 失败映射为固定脱敏消息；上游是进程入口，下游是 stderr，禁止输出异常消息、请求体、路径或凭据。
 * EN: Maps semantic CLI failures to fixed sanitized stderr messages; it must never expose exception text, request bodies, paths, or credentials.
 */
final class SemanticCliErrorRenderer {
    private SemanticCliErrorRenderer() {
    }

    static void renderArgumentError() {
        System.err.println("Semantic command error");
    }

    static void renderRuntimeError() {
        System.err.println("Semantic command failed");
    }
}
