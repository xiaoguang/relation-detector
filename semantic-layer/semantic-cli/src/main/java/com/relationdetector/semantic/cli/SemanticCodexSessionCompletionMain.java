package com.relationdetector.semantic.cli;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.relationdetector.semantic.extract.SemanticCodexSessionCompletionService;

/**
 * CN: 为发布验证消费Codex-session响应的内部入口；输入是不可变request run、独立response目录和输出根，
 * 输出COMPLETE run或pending清单路径。它不注册为公开CLI命令，不调用模型，也不暴露异常原文。
 * EN: Internal release-verification entry point for Codex-session responses. It consumes an immutable request run,
 * separate response directory, and output root, then prints a COMPLETE run or pending-manifest path. It is not a
 * public CLI command, never calls a model, and never exposes raw exception text.
 */
public final class SemanticCodexSessionCompletionMain {
    private SemanticCodexSessionCompletionMain() {
    }

    public static void main(String[] args) {
        SemanticCliExitCode code = run(args);
        if (code != SemanticCliExitCode.SUCCESS) {
            System.exit(code.processCode());
        }
    }

    static SemanticCliExitCode run(String[] args) {
        try {
            Map<String, String> values = parse(args);
            SemanticCodexSessionCompletionService.Result result =
                    new SemanticCodexSessionCompletionService().complete(
                            Path.of(values.get("--request-run")),
                            Path.of(values.get("--responses")),
                            Path.of(values.get("--output")));
            if (result.status() == SemanticCodexSessionCompletionService.Status.PENDING) {
                System.out.println(result.pendingManifest());
                return SemanticCliExitCode.PENDING;
            }
            System.out.println(result.runDirectory());
            return SemanticCliExitCode.SUCCESS;
        } catch (IllegalArgumentException failure) {
            System.err.println("Semantic Codex completion input is invalid");
            return SemanticCliExitCode.RUNTIME_ERROR;
        } catch (RuntimeException failure) {
            System.err.println("Semantic Codex completion failed");
            return SemanticCliExitCode.RUNTIME_ERROR;
        }
    }

    private static Map<String, String> parse(String[] args) {
        if (args == null || args.length != 6) {
            throw new IllegalArgumentException("three semantic Codex completion options are required");
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (int index = 0; index < args.length; index += 2) {
            String option = args[index];
            String value = args[index + 1];
            if (!java.util.Set.of("--request-run", "--responses", "--output").contains(option)
                    || value == null || value.isBlank() || values.put(option, value) != null) {
                throw new IllegalArgumentException("semantic Codex completion option is invalid");
            }
        }
        if (values.size() != 3) {
            throw new IllegalArgumentException("semantic Codex completion options are incomplete");
        }
        return values;
    }
}
