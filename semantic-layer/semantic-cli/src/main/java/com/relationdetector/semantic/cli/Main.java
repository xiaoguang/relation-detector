package com.relationdetector.semantic.cli;

/**
 * CN: semantic-layer CLI 的进程入口，只负责参数解析、命令分发和稳定退出码；输入是命令行参数，输出由命令 handler 生成，禁止在此组装证据图或业务 artifact。
 * EN: Process entry point for the semantic-layer CLI; it only parses arguments, dispatches commands, and maps stable exit codes. Artifact assembly belongs to command handlers, never this class.
 */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        int code = run(args);
        if (code != 0) {
            System.exit(code);
        }
    }

    public static int run(String[] args) {
        try {
            SemanticCommandArguments arguments = SemanticCommandArguments.parse(args);
            if (arguments.help()) {
                System.out.print(SemanticCommandArguments.usageText());
                return 0;
            }
            return new SemanticCommandDispatcher().execute(arguments);
        } catch (IllegalArgumentException ex) {
            SemanticCliErrorRenderer.renderArgumentError();
            return 2;
        } catch (Exception ex) {
            SemanticCliErrorRenderer.renderRuntimeError();
            return 1;
        }
    }
}
