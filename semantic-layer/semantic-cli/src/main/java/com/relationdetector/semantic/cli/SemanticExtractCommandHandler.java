package com.relationdetector.semantic.cli;

import java.nio.file.Path;

import com.relationdetector.semantic.extract.OpenAiResponsesSemanticExtractor;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.extract.SemanticExtractionBundleBuilder;
import com.relationdetector.semantic.extract.SemanticExtractionRunArtifactWriter;
import com.relationdetector.semantic.extract.SemanticExtractionRunPlan;
import com.relationdetector.semantic.extract.SemanticExtractionService;
import com.relationdetector.semantic.extract.SemanticShardMode;
import com.relationdetector.semantic.reader.ScanBundle;

/**
 * CN: 执行 extract 命令，生成 Codex 会话请求或调用 Responses API；输入是 scan bundle 和提取配置，输出是审计 artifact，禁止构造物理事实。
 * EN: Executes extraction by writing a Codex-session request or calling the Responses API; it emits auditable artifacts and never creates physical facts.
 */
final class SemanticExtractCommandHandler {
    /**
     * CN: 从CLI参数读取完整scan bundle，构建deterministic KG和shard plan，并在唯一staging中执行
     * codex/request-only/API路径；成功打印实际run目录，配置、模型、闭包或I/O失败时不发布半成品run。
     *
     * EN: Reads the complete scan bundle from CLI arguments, builds the deterministic KG and shard plan, and executes
     * Codex, request-only, or API flow inside one unique staging directory. Success prints the actual run path;
     * configuration, model, closure, or I/O failures never publish a partial run.
     */
    int execute(SemanticCommandArguments arguments) {
        ScanBundle bundle = new SemanticScanBundleReader().read(arguments);
        requireCompleteShardingInput(arguments);
        ObjectNode fullBundle = new SemanticExtractionBundleBuilder().build(
                bundle,
                arguments.focus(),
                arguments.maxRelationships(),
                arguments.maxLineage(),
                arguments.maxNamingEvidence());
        SemanticExtractionService service = new SemanticExtractionService();
        SemanticExtractionRunPlan plan = service.plan(fullBundle, arguments.sharding());
        SemanticExtractionRunArtifactWriter writer = new SemanticExtractionRunArtifactWriter();
        java.util.function.Consumer<Path> deterministicArtifacts = staging ->
                new SemanticKgBuildService().build(bundle, staging.resolve("deterministic-kg"));
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
            return 0;
        }
        String apiKey = arguments.requestOnly() ? "" : arguments.apiKey();
        int shardOutputTokens = plan.shardRequests().size() == 1
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
                    shardExtractor,
                    reconciliationExtractor,
                    arguments.model(),
                    arguments.reasoningEffort(),
                    arguments.artifactRetention(),
                    deterministicArtifacts);
            System.out.println(published);
            return 0;
        }
        published = writer.executeAndWriteResult(
                arguments.output(),
                plan,
                () -> service.execute(plan, shardExtractor, reconciliationExtractor),
                "openai-api",
                arguments.model(),
                arguments.reasoningEffort(),
                arguments.artifactRetention(),
                deterministicArtifacts);
        System.out.println(published);
        return 0;
    }

    private void requireCompleteShardingInput(SemanticCommandArguments arguments) {
        boolean preview = arguments.maxRelationships() > 0
                || arguments.maxLineage() > 0
                || arguments.maxNamingEvidence() > 0;
        if (preview && arguments.sharding().mode() != SemanticShardMode.OFF) {
            throw new IllegalArgumentException(
                    "preview evidence limits require shard mode off");
        }
    }
}
