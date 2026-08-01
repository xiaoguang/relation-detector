package com.relationdetector.semantic.cli;

import java.nio.file.Path;

import com.relationdetector.semantic.extract.SemanticPathBackedPlanner;
import com.relationdetector.semantic.extract.SemanticPathRunArtifactWriter;
import com.relationdetector.semantic.extract.SemanticPathRunPlan;
import com.relationdetector.semantic.reader.SemanticDiskBackedSession;

/**
 * CN: 通过 {@link SemanticDiskBackedSession} 将输入路径汇入全局磁盘 evidence store，流式生成 KG，
 * 再从该 store 建立 path-backed shard plan 和本地提取请求。{@code ScanBundle} 仅是有界兼容/窗口模型；
 * 本 handler 的上游是 CLI 分发，下游是磁盘 planner/writer，禁止完整物化输入或调用外部模型。
 *
 * EN: Uses {@link SemanticDiskBackedSession} to ingest input paths into the global disk evidence store,
 * streams the KG, and then builds a path-backed shard plan and local extraction request from that store.
 * {@code ScanBundle} is only a bounded compatibility/window model. The CLI dispatcher is upstream and the
 * disk planner/writer is downstream; this handler must not materialize the complete input or call a model.
 */
final class SemanticE2eCommandHandler {
    /**
     * CN: 在单个磁盘后备会话中顺序生成确定性 KG 与请求 artifact，返回 CLI 状态；会话或写入失败时
     * 不保留堆内全量状态，也不发起模型请求。
     *
     * EN: Sequentially writes deterministic KG and request artifacts within one disk-backed session and
     * returns the CLI status. Session or write failures retain no whole-input heap state and trigger no model
     * request.
     */
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
            session.writeKgArtifacts(kgOutput, arguments.kgOutput());
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
