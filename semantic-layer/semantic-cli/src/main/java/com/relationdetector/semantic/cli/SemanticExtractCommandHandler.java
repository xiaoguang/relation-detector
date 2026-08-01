package com.relationdetector.semantic.cli;

import java.nio.file.Path;

import com.relationdetector.semantic.extract.OpenAiResponsesSemanticExtractor;
import com.relationdetector.semantic.extract.SemanticPathBackedPlanner;
import com.relationdetector.semantic.extract.SemanticPathRunArtifactWriter;
import com.relationdetector.semantic.extract.SemanticPathRunPlan;
import com.relationdetector.semantic.reader.SemanticDiskBackedSession;

/**
 * CN: 通过 {@link SemanticDiskBackedSession} 将输入路径汇入全局磁盘 evidence store，并从该 store
 * 建立 path-backed shard plan，随后生成 Codex 会话请求或调用 Responses API。{@code ScanBundle} 仅是
 * 有界兼容/窗口模型；本 handler 的上游是 CLI 分发，下游是提取 artifact writer，禁止完整物化输入或
 * 构造物理事实。
 *
 * EN: Uses {@link SemanticDiskBackedSession} to ingest input paths into the global disk evidence store and
 * build a path-backed shard plan before writing a Codex-session request or calling the Responses API.
 * {@code ScanBundle} is only a bounded compatibility/window model. The CLI dispatcher is upstream and the
 * extraction artifact writer is downstream; this handler must not materialize the complete input or create
 * physical facts.
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
        try (SemanticDiskBackedSession session =
                     SemanticDiskBackedSession.openForOutput(
                             arguments.inputs(),
                             arguments.output(),
                             "extract",
                             arguments.sharding().maxInputTokens())) {
            SemanticPathRunPlan plan = new SemanticPathBackedPlanner().plan(
                    session.evidenceStore(), session.workPath("plan"), arguments.sharding());
            SemanticPathRunArtifactWriter writer = new SemanticPathRunArtifactWriter();
            java.util.function.Consumer<Path> deterministicArtifacts = staging ->
                    session.writeKgArtifacts(
                            staging.resolve("deterministic-kg"), arguments.kgOutput());
            Path published;
            if (arguments.provider() == SemanticExtractProvider.CODEX_SESSION) {
                published = writer.writeCodexSession(
                        arguments.output(),
                        plan,
                        arguments.model(),
                        arguments.reasoningEffort(),
                        arguments.artifactRetention(),
                        deterministicArtifacts);
                System.out.println(published);
                return SemanticCliExitCode.SUCCESS;
            }
            String apiKey = arguments.requestOnly() ? "" : arguments.apiKey();
            int shardOutputTokens = plan.shards().size() == 1
                    ? arguments.maxOutputTokens()
                    : arguments.shardMaxOutputTokens();
            OpenAiResponsesSemanticExtractor shardExtractor = new OpenAiResponsesSemanticExtractor(
                    arguments.baseUrl(), apiKey, arguments.model(), arguments.reasoningEffort(),
                    shardOutputTokens, arguments.requestTimeoutSeconds(), arguments.maxTransportRetries());
            OpenAiResponsesSemanticExtractor reconciliationExtractor = new OpenAiResponsesSemanticExtractor(
                    arguments.baseUrl(), apiKey, arguments.model(), arguments.reasoningEffort(),
                    arguments.reconciliationMaxOutputTokens(), arguments.requestTimeoutSeconds(),
                    arguments.maxTransportRetries());
            if (arguments.requestOnly()) {
                published = writer.writeRequestOnly(
                        arguments.output(),
                        plan,
                        shardExtractor::requestJson,
                        reconciliationExtractor::requestJson,
                        arguments.model(),
                        arguments.reasoningEffort(),
                        arguments.artifactRetention(),
                        deterministicArtifacts);
                System.out.println(published);
                return SemanticCliExitCode.SUCCESS;
            }
            published = writer.executeAndWrite(
                    arguments.output(),
                    plan,
                    session.evidenceStore(),
                    shardExtractor,
                    reconciliationExtractor,
                    "openai-api",
                    arguments.model(),
                    arguments.reasoningEffort(),
                    arguments.artifactRetention(),
                    deterministicArtifacts);
            System.out.println(published);
            return SemanticCliExitCode.SUCCESS;
        }
    }

}
