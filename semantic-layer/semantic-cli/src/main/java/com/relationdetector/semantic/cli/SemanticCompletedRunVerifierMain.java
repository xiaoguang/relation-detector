package com.relationdetector.semantic.cli;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.relationdetector.semantic.facade.SemanticExtractionFacade;

/**
 * CN: 为确定性 release acceptance 提供未注册为公开命令的 completed-run 校验入口；输入是 run、
 * model 与 reasoning effort，输出仅为进程状态，不修改或补写 run artifact。
 * EN: Provides a non-command completed-run verification entry point for deterministic release acceptance. It reads
 * a run, model, and reasoning effort, emits only process status, and never repairs or writes run artifacts.
 */
public final class SemanticCompletedRunVerifierMain {
    private SemanticCompletedRunVerifierMain() {
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
            new SemanticExtractionFacade().verifyCompletedCodexRun(
                    Path.of(values.get("--run")),
                    values.get("--model"),
                    values.get("--reasoning-effort"));
            return SemanticCliExitCode.SUCCESS;
        } catch (IllegalArgumentException failure) {
            System.err.println("Semantic completed-run verifier input is invalid");
            return SemanticCliExitCode.RUNTIME_ERROR;
        } catch (RuntimeException failure) {
            System.err.println("Semantic completed run is invalid");
            return SemanticCliExitCode.RUNTIME_ERROR;
        }
    }

    private static Map<String, String> parse(String[] args) {
        if (args == null || args.length != 6) {
            throw new IllegalArgumentException("three semantic completed-run verifier options are required");
        }
        Set<String> allowed = Set.of("--run", "--model", "--reasoning-effort");
        Map<String, String> values = new LinkedHashMap<>();
        for (int index = 0; index < args.length; index += 2) {
            String option = args[index];
            String value = args[index + 1];
            if (!allowed.contains(option) || value == null || value.isBlank()
                    || values.put(option, value) != null) {
                throw new IllegalArgumentException("semantic completed-run verifier option is invalid");
            }
        }
        if (values.size() != allowed.size()) {
            throw new IllegalArgumentException("semantic completed-run verifier options are incomplete");
        }
        return values;
    }
}
