package com.relationdetector.semantic.facade;

import java.nio.file.Path;
import java.util.List;

import com.relationdetector.semantic.extraction.artifact.SemanticRequestBundleReconstructor;
import com.relationdetector.semantic.extraction.artifact.SemanticRunArtifactWriter;
import com.relationdetector.semantic.extraction.config.ArtifactRetention;
import com.relationdetector.semantic.extraction.config.SemanticShardingOptions;
import com.relationdetector.semantic.extraction.runtime.OpenAiResponsesSemanticExtractor;
import com.relationdetector.semantic.extraction.runtime.SemanticCodexSessionCompletionService;
import com.relationdetector.semantic.extraction.runtime.SemanticProcessingSession;
import com.relationdetector.semantic.extraction.shard.SemanticRunPlan;
import com.relationdetector.semantic.extraction.shard.SemanticShardPlanner;
import com.relationdetector.semantic.kg.store.SemanticKgArtifactMode;

/**
 * CN: 统一 semantic extraction 的生产入口，负责磁盘会话、全局 owner 分片、请求/API 执行、Codex 响应完成
 * 与 request package 重建。输入是已校验的运行请求，输出是原子发布目录或有界状态；上游是 CLI，
 * 下游是 extraction runtime/artifact。禁止 CLI 直接装配 planner、session 或 writer，也不创建物理事实。
 *
 * <p>EN: Production facade for semantic extraction. It owns disk sessions, global-owner sharding, request/API
 * execution, Codex-response completion, and request-package reconstruction. It accepts validated run requests and
 * returns published paths or bounded status. CLI is upstream and extraction runtime/artifacts are downstream; CLI
 * must not assemble planners, sessions, or writers directly, and this facade never creates physical facts.
 */
public final class SemanticExtractionFacade {
    public Path extract(Request request) {
        require(request != null, "semantic extraction request is required");
        try (SemanticProcessingSession session = SemanticProcessingSession.openForOutput(
                request.inputs(), request.output(), "extract", request.sharding().maxInputTokens())) {
            SemanticRunPlan plan = new SemanticShardPlanner().plan(
                    session.evidenceStore(), session.workPath("plan"), request.sharding(),
                    request.shardMaxOutputTokens(), request.reconciliationMaxOutputTokens());
            SemanticRunArtifactWriter writer = new SemanticRunArtifactWriter();
            java.util.function.Consumer<Path> deterministicArtifacts = staging -> session.writeKgArtifacts(
                    staging.resolve("deterministic-kg"), request.kgOutput());
            if (request.mode() == Mode.CODEX_SESSION) {
                return writer.writeCodexSession(
                        request.output(), plan, request.model(), request.reasoningEffort(), request.retention(),
                        deterministicArtifacts);
            }
            OpenAiResponsesSemanticExtractor shardExtractor = extractor(
                    request, plan.shards().size() == 1
                            ? request.maxOutputTokens()
                            : request.shardMaxOutputTokens());
            OpenAiResponsesSemanticExtractor reconciliationExtractor = extractor(
                    request, request.reconciliationMaxOutputTokens());
            if (request.mode() == Mode.REQUEST_ONLY) {
                return writer.writeRequestOnly(
                        request.output(), plan, shardExtractor::requestJson, reconciliationExtractor::requestJson,
                        request.model(), request.reasoningEffort(), request.retention(), deterministicArtifacts);
            }
            return writer.executeAndWrite(
                    request.output(), plan, session.evidenceStore(), shardExtractor, reconciliationExtractor,
                    "openai-api", request.model(), request.reasoningEffort(), request.retention(),
                    deterministicArtifacts);
        }
    }

    public void writeE2eRequest(E2eRequest request) {
        require(request != null, "semantic E2E request is required");
        try (SemanticProcessingSession session = SemanticProcessingSession.openForOutput(
                request.inputs(), request.outputRoot(), "e2e", request.sharding().maxInputTokens())) {
            session.writeKgArtifacts(request.kgOutput(), request.kgArtifactMode());
            SemanticRunPlan plan = new SemanticShardPlanner().plan(
                    session.evidenceStore(), session.workPath("plan"), request.sharding(),
                    request.shardMaxOutputTokens(), request.reconciliationMaxOutputTokens());
            new SemanticRunArtifactWriter().writeCodexSession(
                    request.extractionOutput(), plan, request.model(), request.reasoningEffort(), request.retention(),
                    ignored -> {
                    });
        }
    }

    public CompletionResult completeCodexSession(
            Path requestRun,
            Path responseDirectory,
            Path outputRoot
    ) {
        SemanticCodexSessionCompletionService.Result result =
                new SemanticCodexSessionCompletionService().complete(
                        requestRun, responseDirectory, outputRoot);
        return new CompletionResult(
                result.status() == SemanticCodexSessionCompletionService.Status.PENDING,
                result.runDirectory(), result.pendingManifest());
    }

    public ReconstructionResult reconstructRequestBundle(Path runDirectory, Path target) {
        SemanticRequestBundleReconstructor.Result result =
                new SemanticRequestBundleReconstructor().reconstruct(runDirectory, target);
        return new ReconstructionResult(result.canonicalSha256(), result.sectionCounts());
    }

    private OpenAiResponsesSemanticExtractor extractor(Request request, int maxOutputTokens) {
        return new OpenAiResponsesSemanticExtractor(
                request.baseUrl(), request.apiKey(), request.model(), request.reasoningEffort(), maxOutputTokens,
                request.requestTimeoutSeconds(), request.maxTransportRetries());
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    public enum Mode {
        CODEX_SESSION,
        REQUEST_ONLY,
        OPENAI_API
    }

    public record Request(
            List<Path> inputs,
            Path output,
            Mode mode,
            String model,
            String reasoningEffort,
            int maxOutputTokens,
            String baseUrl,
            String apiKey,
            ArtifactRetention retention,
            SemanticKgArtifactMode kgOutput,
            SemanticShardingOptions sharding,
            int shardMaxOutputTokens,
            int reconciliationMaxOutputTokens,
            int requestTimeoutSeconds,
            int maxTransportRetries
    ) {
        public Request {
            inputs = List.copyOf(inputs);
        }
    }

    public record E2eRequest(
            List<Path> inputs,
            Path outputRoot,
            Path kgOutput,
            Path extractionOutput,
            String model,
            String reasoningEffort,
            ArtifactRetention retention,
            SemanticKgArtifactMode kgArtifactMode,
            SemanticShardingOptions sharding,
            int shardMaxOutputTokens,
            int reconciliationMaxOutputTokens
    ) {
        public E2eRequest {
            inputs = List.copyOf(inputs);
            requirePositive(shardMaxOutputTokens, "shardMaxOutputTokens");
            requirePositive(reconciliationMaxOutputTokens, "reconciliationMaxOutputTokens");
        }

        private static void requirePositive(int value, String name) {
            if (value <= 0) {
                throw new IllegalArgumentException(name + " must be positive");
            }
        }
    }

    public record CompletionResult(boolean pending, Path runDirectory, Path pendingManifest) {
    }

    public record ReconstructionResult(String canonicalSha256, java.util.Map<String, Long> sectionCounts) {
        public ReconstructionResult {
            sectionCounts = java.util.Map.copyOf(sectionCounts);
        }
    }
}
