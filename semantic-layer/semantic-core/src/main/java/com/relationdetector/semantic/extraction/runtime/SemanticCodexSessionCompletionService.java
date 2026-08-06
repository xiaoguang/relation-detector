package com.relationdetector.semantic.extraction.runtime;

import com.relationdetector.semantic.extraction.artifact.SemanticRequestArtifactWriter;

import com.relationdetector.semantic.extraction.artifact.SemanticCodexRequestSnapshot;

import com.relationdetector.semantic.extraction.artifact.SemanticRunArtifactWriter;

import com.relationdetector.semantic.extraction.artifact.SemanticResultStore;

import com.relationdetector.semantic.extraction.artifact.SemanticRequestPackageLimits;

import com.relationdetector.semantic.extraction.artifact.SemanticReconstructedEvidenceLookup;

import com.relationdetector.semantic.extraction.shard.SemanticShardDescriptor;

import com.relationdetector.semantic.extraction.shard.SemanticRunPlan;

import com.relationdetector.semantic.extraction.normalization.SemanticExtractionValidationException;

import com.relationdetector.semantic.extraction.normalization.SemanticExtractionDocumentNormalizer;

import com.relationdetector.semantic.extraction.normalization.SemanticEvidenceLookup;

import com.relationdetector.semantic.extraction.normalization.SemanticBoundedJsonReader;

import com.relationdetector.semantic.extraction.prompt.SemanticExtractionPrompt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.internal.io.SemanticFileTreeOperations;
import com.relationdetector.semantic.internal.io.SemanticAtomicFiles;

/**
 * CN: 消费不可变Codex request run和独立response目录，重建完整证据、校验owner、顺序归一化分片并在
 * reconciliation与最终closure完成后原子发布正式结果。缺少响应时只写pending清单；本服务不调用模型、
 * 不修改request run，也不复制normalization、merge或patch规则。
 * EN: Consumes an immutable Codex request run plus a separate response directory, reconstructs complete evidence,
 * validates ownership, normalizes shards sequentially, and atomically publishes only after reconciliation and final
 * closure. Missing responses produce only a pending manifest; this service never calls a model, mutates the request
 * run, or duplicates normalization, merge, or patch rules.
 */
public final class SemanticCodexSessionCompletionService {
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final SemanticExtractionDocumentNormalizer normalizer =
            new SemanticExtractionDocumentNormalizer();
    private final SemanticRequestArtifactWriter requests = new SemanticRequestArtifactWriter();
    private final SemanticBoundedJsonReader bounded = new SemanticBoundedJsonReader();
    private final SemanticRequestPackageLimits trustedLimits;

    public SemanticCodexSessionCompletionService() {
        this(SemanticRequestPackageLimits.defaults());
    }

    SemanticCodexSessionCompletionService(SemanticRequestPackageLimits trustedLimits) {
        if (trustedLimits == null) {
            throw new IllegalArgumentException("semantic request package limits are required");
        }
        this.trustedLimits = trustedLimits;
    }

    /**
     * CN: 校验request package和现有response集合；缺失分片或reconciliation时返回PENDING并写固定清单，
     * 全部存在时通过既有path result store完成owner、引用、merge和closure后发布COMPLETE run。任何畸形或
     * 越界输入原子失败，request run始终只读。
     * EN: Validates the request package and available responses. Missing shard or reconciliation results return
     * PENDING with a fixed manifest; complete inputs reuse the path result store for ownership, references, merge,
     * and closure before publishing a COMPLETE run. Malformed or out-of-owner inputs fail atomically and the request
     * run remains read-only.
     */
    public Result complete(Path requestRun, Path responseDirectory, Path outputRoot) {
        if (requestRun == null || responseDirectory == null || outputRoot == null) {
            throw new IllegalArgumentException(
                    "semantic Codex request run, response directory and output root are required");
        }
        Path run = requestRun.toAbsolutePath().normalize();
        Path responses = responseDirectory.toAbsolutePath().normalize();
        Path output = outputRoot.toAbsolutePath().normalize();
        Path workspace = output.resolve(".codex-completion-work-" + UUID.randomUUID());
        try {
            Files.createDirectories(responses);
            Files.createDirectories(output);
            Files.createDirectory(workspace);
            SemanticCodexRequestSnapshot.Snapshot loaded = load(run, workspace);
            List<String> missing = missingShardResponses(loaded.plan(), responses);
            if (!missing.isEmpty()) {
                return pending(responses, "SHARDS", missing);
            }
            try (SemanticReconstructedEvidenceLookup lookup =
                         new SemanticReconstructedEvidenceLookup(
                                 loaded.plan().fullBundle().path(), workspace.resolve("evidence-lookup"))) {
                if (reconciliationRequired(loaded.plan())
                        && !Files.isRegularFile(reconciliationResponse(responses))) {
                    SemanticExtractionPrompt prompt = reconciliationPrompt(
                            loaded.plan(), lookup, responses, workspace.resolve("pending-results"));
                    Path directory = responses.resolve("reconciliation");
                    requests.writeCodexSessionReconciliationRequest(
                            directory, prompt, "semantic-reconciliation-result.json");
                    return pending(
                            responses,
                            "RECONCILIATION",
                            List.of("reconciliation/semantic-reconciliation-result.json"));
                }
                Path published = new SemanticRunArtifactWriter().completeCodexSession(
                        output,
                        loaded.plan(),
                        lookup,
                        responses,
                        loaded.model(),
                        loaded.reasoningEffort(),
                        loaded.retention());
                Files.deleteIfExists(responses.resolve("pending-responses.json"));
                return new Result(Status.COMPLETE, published, null);
            }
        } catch (SemanticExtractionValidationException failure) {
            throw failure;
        } catch (IOException failure) {
            throw new SemanticExtractionValidationException(
                    "semantic Codex completion artifacts cannot be prepared");
        } finally {
            SemanticFileTreeOperations.deleteRecursivelyBestEffort(workspace);
        }
    }

    /**
     * CN: 校验已发布request run的manifest、package摘要、owner manifest与全部shard descriptor，并在独立
     * workspace流式重建完整bundle。返回的plan只指向已校验文件；任何缺失、篡改或路径越界都会在响应处理前失败。
     * EN: Validates the published request manifest, package digests, owner manifest, and every shard descriptor while
     * reconstructing the complete bundle in an isolated workspace. The returned plan references only verified files;
     * missing, tampered, or escaping paths fail before any response is processed.
     */
    private SemanticCodexRequestSnapshot.Snapshot load(Path run, Path workspace) {
        return SemanticCodexRequestSnapshot.capture(
                run, workspace.resolve("request-snapshot"), trustedLimits);
    }

    private List<String> missingShardResponses(SemanticRunPlan plan, Path responses) {
        List<String> missing = new ArrayList<>();
        for (SemanticShardDescriptor shard : plan.shards()) {
            Path response = shardResponse(responses, shard.id());
            if (!Files.isRegularFile(response)) {
                missing.add(responses.relativize(response).toString().replace('\\', '/'));
            }
        }
        return List.copyOf(missing);
    }

    private SemanticExtractionPrompt reconciliationPrompt(
            SemanticRunPlan plan,
            SemanticEvidenceLookup lookup,
            Path responses,
            Path workspace
    ) {
        try (SemanticResultStore results = new SemanticResultStore(workspace, lookup, plan)) {
            for (SemanticShardDescriptor shard : plan.shards()) {
                ObjectNode bundle = bounded.readObject(
                        shard.bundle().path(), plan.maxInputTokens(),
                        "semantic request shard bundle");
                ObjectNode raw = bounded.readObject(
                        shardResponse(responses, shard.id()),
                        plan.shardMaxOutputTokens(),
                        "semantic Codex shard result");
                results.append(shard, bundle, normalizer.normalizeOwnedShard(raw, bundle));
            }
            results.finish();
            return results.reconciliationPrompt(plan, plan.maxInputTokens());
        }
    }

    private Result pending(Path responses, String phase, List<String> missing) {
        ObjectNode document = JSON.createObjectNode();
        document.put("status", "PENDING_RESPONSES");
        document.put("phase", phase);
        document.put("generatedAt", Instant.now().toString());
        ArrayNode values = document.putArray("missing");
        missing.forEach(values::add);
        Path target = responses.resolve("pending-responses.json");
        writeAtomically(target, document);
        return new Result(Status.PENDING, null, target);
    }

    private void writeAtomically(Path target, JsonNode value) {
        try {
            SemanticAtomicFiles.replace(target, temporary -> JSON.writeValue(temporary.toFile(), value));
        } catch (IOException failure) {
            throw new SemanticExtractionValidationException(
                    "semantic pending response manifest cannot be written");
        }
    }

    private boolean reconciliationRequired(SemanticRunPlan plan) {
        return plan.reconcile() && plan.shards().size() > 1;
    }

    private Path shardResponse(Path responses, String shardId) {
        return responses.resolve("shards").resolve(shardId)
                .resolve("semantic-extraction-result.json");
    }

    private Path reconciliationResponse(Path responses) {
        return responses.resolve("reconciliation")
                .resolve("semantic-reconciliation-result.json");
    }

    private void require(boolean valid) {
        if (!valid) {
            throw invalidRun();
        }
    }

    private SemanticExtractionValidationException invalidRun() {
        return new SemanticExtractionValidationException(
                "semantic Codex request run is invalid");
    }

    public enum Status {
        PENDING,
        COMPLETE
    }

    public record Result(Status status, Path runDirectory, Path pendingManifest) {
        public Result {
            if (status == null
                    || status == Status.COMPLETE && runDirectory == null
                    || status == Status.PENDING && pendingManifest == null) {
                throw new IllegalArgumentException("semantic Codex completion result is incomplete");
            }
            runDirectory = runDirectory == null ? null : runDirectory.toAbsolutePath().normalize();
            pendingManifest = pendingManifest == null ? null : pendingManifest.toAbsolutePath().normalize();
        }
    }

}
