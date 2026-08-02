package com.relationdetector.semantic.cli;

import java.nio.file.Path;

import com.relationdetector.semantic.facade.SemanticExtractionFacade;

/**
 * CN: 将已验证CLI参数映射为semantic-core extraction facade请求并打印发布目录；上游是命令分发，
 * 下游仅是facade，禁止直接装配session、planner、writer或物理事实。
 * EN: Maps validated CLI arguments to a semantic-core extraction-facade request and prints the published directory.
 * Command dispatch is upstream and only the facade is downstream; this handler must not assemble sessions, planners,
 * writers, or physical facts directly.
 */
final class SemanticExtractCommandHandler {
    /**
     * CN: 从CLI参数取得输入路径和提取配置，在单个磁盘后备会话中建立全局evidence store及
     * path-backed shard plan，并在唯一staging中执行Codex、request-only或API路径。成功时打印实际
     * run目录；配置、模型、闭包或I/O失败时不发布半成品run，也不保留堆内全量bundle。
     *
     * EN: Reads input paths and extraction configuration from CLI arguments, builds the global evidence store
     * and path-backed shard plan in one disk-backed session, and executes the Codex, request-only, or API flow
     * inside a unique staging directory. Success prints the actual run path; configuration, model, closure, or
     * I/O failures publish no partial run and retain no whole-bundle heap state.
     */
    SemanticCliExitCode execute(SemanticCommandArguments arguments) {
        SemanticExtractionFacade.Mode mode = arguments.provider() == SemanticExtractProvider.CODEX_SESSION
                ? SemanticExtractionFacade.Mode.CODEX_SESSION
                : arguments.requestOnly()
                        ? SemanticExtractionFacade.Mode.REQUEST_ONLY
                        : SemanticExtractionFacade.Mode.OPENAI_API;
        String apiKey = mode == SemanticExtractionFacade.Mode.OPENAI_API ? arguments.apiKey() : "";
        Path published = new SemanticExtractionFacade().extract(new SemanticExtractionFacade.Request(
                arguments.inputs(), arguments.output(), mode, arguments.model(), arguments.reasoningEffort(),
                arguments.maxOutputTokens(), arguments.baseUrl(), apiKey, arguments.artifactRetention(),
                arguments.kgOutput(), arguments.sharding(), arguments.shardMaxOutputTokens(),
                arguments.reconciliationMaxOutputTokens(), arguments.requestTimeoutSeconds(),
                arguments.maxTransportRetries()));
        System.out.println(published);
        return SemanticCliExitCode.SUCCESS;
    }
}
