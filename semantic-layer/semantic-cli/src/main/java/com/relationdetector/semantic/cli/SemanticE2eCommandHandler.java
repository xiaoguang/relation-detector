package com.relationdetector.semantic.cli;

import java.nio.file.Path;

import com.relationdetector.semantic.extract.SemanticPathBackedPlanner;
import com.relationdetector.semantic.extract.SemanticPathRunArtifactWriter;
import com.relationdetector.semantic.extract.SemanticPathRunPlan;
import com.relationdetector.semantic.reader.SemanticDiskBackedSession;

/**
 * CN: 执行确定性 e2e 命令，复用 KG service 并生成本地提取请求；输入是 scan bundle，输出两组 artifact，禁止调用外部模型。
 * EN: Executes deterministic e2e generation by reusing the KG service and writing a local extraction request; it never calls an external model.
 */
final class SemanticE2eCommandHandler {
    SemanticCliExitCode execute(SemanticCommandArguments arguments) {
        String name = arguments.name().isBlank() ? defaultName(arguments.inputs().get(0)) : arguments.name();
        Path kgOutput = arguments.output().resolve("semantic-kg").resolve(name);
        Path extractionOutput = arguments.output().resolve("semantic-extraction").resolve(name);
        try (SemanticDiskBackedSession session =
                     SemanticDiskBackedSession.openForOutput(
                             arguments.inputs(),
                             arguments.output(),
                             "e2e",
                             arguments.sharding().maxInputTokens())) {
            session.writeKgArtifacts(kgOutput);
            SemanticPathRunPlan plan = new SemanticPathBackedPlanner().plan(
                    session.evidenceStore(), session.workPath("plan"), arguments.sharding());
            new SemanticPathRunArtifactWriter().writeCodexSession(
                    extractionOutput,
                    plan,
                    arguments.model(),
                    arguments.reasoningEffort(),
                    arguments.artifactRetention(),
                    ignored -> {
                    });
        }
        return SemanticCliExitCode.SUCCESS;
    }

    private String defaultName(Path input) {
        String fileName = input.getFileName() == null ? "scan-result" : input.getFileName().toString();
        return fileName.endsWith(".json") ? fileName.substring(0, fileName.length() - ".json".length()) : fileName;
    }
}
