package com.relationdetector.semantic.cli;

/**
 * CN: 表示 semantic CLI 内部的封闭退出类别，并仅在进程边界转换为稳定整数；输入来自命令分发或异常分类，
 * 输出供 Main.run 使用，禁止承载 HTTP、模型或子进程的原始状态码。
 * EN: Represents the semantic CLI's closed exit categories and converts them to stable integers only at the
 * process boundary. It consumes command or exception classification and must not carry raw HTTP, model, or child
 * process status codes.
 */
enum SemanticCliExitCode {
    SUCCESS(0),
    RUNTIME_ERROR(1),
    USAGE_ERROR(2),
    PENDING(2);

    private final int processCode;

    SemanticCliExitCode(int processCode) {
        this.processCode = processCode;
    }

    int processCode() {
        return processCode;
    }
}
