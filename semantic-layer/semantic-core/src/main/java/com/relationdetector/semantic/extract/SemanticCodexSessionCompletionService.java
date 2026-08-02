package com.relationdetector.semantic.extract;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.internal.io.SemanticFileTreeOperations;

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
    private final SemanticRequestBundleReconstructor reconstructor =
            new SemanticRequestBundleReconstructor();
    private final SemanticExtractionDocumentNormalizer normalizer =
            new SemanticExtractionDocumentNormalizer();
    private final SemanticExtractionPromptBuilder promptBuilder =
            new SemanticExtractionPromptBuilder();
    private final SemanticRequestArtifactWriter requests = new SemanticRequestArtifactWriter();
    private final SemanticBoundedJsonReader bounded = new SemanticBoundedJsonReader();

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
            LoadedRequest loaded = load(run, workspace);
            List<String> missing = missingShardResponses(loaded.plan(), responses);
            if (!missing.isEmpty()) {
                return pending(responses, "SHARDS", missing);
            }
            try (SemanticReconstructedEvidenceLookup lookup =
                         new SemanticReconstructedEvidenceLookup(
                                 loaded.plan().fullBundlePath(), workspace.resolve("evidence-lookup"))) {
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
                Path published = new SemanticPathRunArtifactWriter().completeCodexSession(
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
    private LoadedRequest load(Path run, Path workspace) {
        ObjectNode manifest = readObject(run.resolve("run-manifest.json"), "semantic request run manifest");
        require("codex-session".equals(manifest.path("provider").asText("")));
        require(Set.of("AWAITING_MODEL_RESULTS", "REQUESTS_READY")
                .contains(manifest.path("status").asText("")));
        ObjectNode index = readObject(
                run.resolve("request-bundle-index.json"), "semantic request bundle index");
        require(requiredNonNegativeInt(index, "artifactSchemaVersion") == 1);
        Path bundle = workspace.resolve("full-evidence-bundle.json");
        SemanticRequestBundleReconstructor.Result reconstructed = reconstructor.reconstruct(run, bundle);
        require(reconstructed.canonicalSha256().equals(
                index.path("fullBundleCanonicalSha256").asText("")));
        Path ownerManifest = verifiedArtifact(run, index.path("ownerManifest"));
        String ownerHash = index.path("ownerManifest").path("sha256").asText("");
        String fullBundleHash = requireHash(index.path("sourceBundleSha256").asText(""));
        int maxInputTokens = requiredPositiveInt(index, "maxInputTokens");
        require(index.path("reconcile").isBoolean());
        ArrayNode shards = requireArray(index, "shards");
        List<SemanticPathShard> descriptors = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonNode shard : shards) {
            String id = shard.path("id").asText("");
            require(simpleName(id) && ids.add(id));
            descriptors.add(new SemanticPathShard(
                    id,
                    requiredText(shard, "ownerKey"),
                    verifiedArtifact(run, shard.path("bundle")),
                    requiredPositiveInt(shard, "estimatedInputTokens"),
                    requiredNonNegativeInt(shard, "ownedFactCount"),
                    requiredNonNegativeInt(shard, "ownedCandidateCount")));
        }
        require(!descriptors.isEmpty());
        require(requiredNonNegativeInt(manifest, "shardCount") == descriptors.size());
        SemanticPathRunPlan plan = new SemanticPathRunPlan(
                bundle,
                fullBundleHash,
                descriptors,
                index.path("reconcile").booleanValue(),
                maxInputTokens,
                ownerManifest,
                ownerHash);
        return new LoadedRequest(
                plan,
                requiredText(manifest, "model"),
                requiredText(manifest, "reasoningEffort"),
                ArtifactRetention.parse(manifest.path("retention").asText("full")));
    }

    private List<String> missingShardResponses(SemanticPathRunPlan plan, Path responses) {
        List<String> missing = new ArrayList<>();
        for (SemanticPathShard shard : plan.shards()) {
            Path response = shardResponse(responses, shard.id());
            if (!Files.isRegularFile(response)) {
                missing.add(responses.relativize(response).toString().replace('\\', '/'));
            }
        }
        return List.copyOf(missing);
    }

    private SemanticExtractionPrompt reconciliationPrompt(
            SemanticPathRunPlan plan,
            SemanticEvidenceLookup lookup,
            Path responses,
            Path workspace
    ) {
        try (SemanticPathResultStore results = new SemanticPathResultStore(workspace, lookup, plan)) {
            for (SemanticPathShard shard : plan.shards()) {
                ObjectNode bundle = readObject(shard.bundlePath(), "semantic request shard bundle");
                ObjectNode raw = bounded.readObject(
                        shardResponse(responses, shard.id()),
                        plan.maxInputTokens(),
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
        Path temporary = target.resolveSibling("." + target.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            JSON.writeValue(temporary.toFile(), value);
            Files.move(
                    temporary, target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failure) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw new SemanticExtractionValidationException(
                    "semantic pending response manifest cannot be written");
        }
    }

    private Path verifiedArtifact(Path run, JsonNode artifact) {
        String relative = artifact.path("path").asText("");
        Path target = run.resolve(relative).normalize();
        require(!relative.isBlank() && target.startsWith(run) && Files.isRegularFile(target));
        require(size(target) == requiredNonNegativeLong(artifact, "bytes"));
        require(sha256(target).equals(requireHash(artifact.path("sha256").asText(""))));
        return target;
    }

    private ObjectNode readObject(Path path, String label) {
        try {
            JsonNode value = JSON.readTree(path.toFile());
            if (value == null || !value.isObject()) {
                throw new SemanticExtractionValidationException(label + " must be a JSON object");
            }
            return (ObjectNode) value;
        } catch (IOException failure) {
            throw invalidRun();
        }
    }

    private ArrayNode requireArray(JsonNode object, String field) {
        JsonNode value = object.path(field);
        require(value.isArray());
        return (ArrayNode) value;
    }

    private int requiredPositiveInt(JsonNode object, String field) {
        JsonNode value = object.path(field);
        require(value.isIntegralNumber() && value.canConvertToInt() && value.intValue() > 0);
        return value.intValue();
    }

    private int requiredNonNegativeInt(JsonNode object, String field) {
        JsonNode value = object.path(field);
        require(value.isIntegralNumber() && value.canConvertToInt() && value.intValue() >= 0);
        return value.intValue();
    }

    private long requiredNonNegativeLong(JsonNode object, String field) {
        JsonNode value = object.path(field);
        require(value.isIntegralNumber() && value.canConvertToLong() && value.longValue() >= 0);
        return value.longValue();
    }

    private String requiredText(JsonNode object, String field) {
        String value = object.path(field).asText("");
        require(!value.isBlank());
        return value;
    }

    private String requireHash(String value) {
        require(value != null && value.matches("[0-9a-f]{64}"));
        return value;
    }

    private boolean simpleName(String value) {
        return value != null && !value.isBlank()
                && Path.of(value).getNameCount() == 1
                && value.equals(Path.of(value).getFileName().toString());
    }

    private boolean reconciliationRequired(SemanticPathRunPlan plan) {
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

    private long size(Path path) {
        try {
            return Files.size(path);
        } catch (IOException failure) {
            throw invalidRun();
        }
    }

    private String sha256(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException failure) {
            throw invalidRun();
        }
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

    private record LoadedRequest(
            SemanticPathRunPlan plan,
            String model,
            String reasoningEffort,
            ArtifactRetention retention
    ) {
    }
}
