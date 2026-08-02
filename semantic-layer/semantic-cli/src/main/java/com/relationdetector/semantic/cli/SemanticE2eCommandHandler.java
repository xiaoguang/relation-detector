package com.relationdetector.semantic.cli;

import java.nio.file.Path;

import com.relationdetector.semantic.facade.SemanticExtractionFacade;

/**
 * CN: 将已验证CLI参数交给semantic-core extraction facade，一次生成KG与Codex-session请求；上游是命令分发，
 * 下游仅是facade，禁止直接依赖磁盘session、planner、writer或外部模型。
 * EN: Sends validated CLI arguments to the semantic-core extraction facade to produce KG and Codex-session requests
 * together. Command dispatch is upstream and only the facade is downstream; this handler must not depend directly on
 * disk sessions, planners, writers, or external models.
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
        new SemanticExtractionFacade().writeE2eRequest(new SemanticExtractionFacade.E2eRequest(
                arguments.inputs(), arguments.output(), kgOutput, extractionOutput, arguments.model(),
                arguments.reasoningEffort(), arguments.artifactRetention(), arguments.kgOutput(),
                arguments.sharding()));
        return SemanticCliExitCode.SUCCESS;
    }

    private String defaultName(Path input) {
        String fileName = input.getFileName() == null ? "scan-result" : input.getFileName().toString();
        return fileName.endsWith(".json") ? fileName.substring(0, fileName.length() - ".json".length()) : fileName;
    }
}
