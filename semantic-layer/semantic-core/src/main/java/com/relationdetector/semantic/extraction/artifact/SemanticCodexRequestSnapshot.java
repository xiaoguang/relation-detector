package com.relationdetector.semantic.extraction.artifact;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.extraction.config.ArtifactRetention;
import com.relationdetector.semantic.extraction.normalization.SemanticExtractionValidationException;
import com.relationdetector.semantic.extraction.shard.SemanticRunPlan;
import com.relationdetector.semantic.internal.io.SemanticFileTreeOperations;

/**
 * CN: 将一个不可信 Codex request run 在调用方提供的全新 scratch root 中完成有界校验与脱离，输出不可变 plan、内容哈希及模型配置；失败时只清理本次独占目录，禁止 completion 再读取原始 manifest 或 index。
 * EN: Boundedly validates and detaches one untrusted Codex request run into a caller-owned expected-new scratch root, returning an immutable plan, content hash, and model configuration. Failures clean only that owned root, and completion must not reread the original manifest or index.
 */
public final class SemanticCodexRequestSnapshot {
    private SemanticCodexRequestSnapshot() {
    }

    public static Snapshot capture(
            Path runDirectory,
            Path expectedNewScratchRoot,
            SemanticRequestPackageLimits limits
    ) {
        if (runDirectory == null || expectedNewScratchRoot == null || limits == null) {
            throw new IllegalArgumentException(
                    "semantic Codex request run, snapshot root, and limits are required");
        }
        Path root = expectedNewScratchRoot.toAbsolutePath().normalize();
        boolean created = false;
        try {
            Path parent = root.getParent();
            if (parent == null) {
                throw invalid();
            }
            Files.createDirectories(parent);
            Files.createDirectory(root);
            created = true;

            Manifest manifest = captureManifest(
                    runDirectory, root.resolve("manifest-snapshot"), limits);
            SemanticRequestBundleReconstructor.CompletionSnapshot request =
                    new SemanticRequestBundleReconstructor().reconstructCompletionSnapshot(
                            runDirectory,
                            root.resolve("full-evidence-bundle.json"),
                            root.resolve("plan-snapshot"),
                            limits);
            require(manifest.shardCount() == request.plan().shards().size());
            return new Snapshot(
                    request.plan(),
                    request.canonicalSha256(),
                    manifest.model(),
                    manifest.reasoningEffort(),
                    manifest.retention());
        } catch (SemanticExtractionValidationException failure) {
            if (created) {
                SemanticFileTreeOperations.deleteRecursivelyBestEffort(root);
            }
            throw failure;
        } catch (IOException | RuntimeException failure) {
            if (created) {
                SemanticFileTreeOperations.deleteRecursivelyBestEffort(root);
            }
            throw invalid();
        }
    }

    private static Manifest captureManifest(
            Path runDirectory,
            Path workspace,
            SemanticRequestPackageLimits limits
    ) {
        Path snapshot = SemanticRequestPackageArtifactVerifier.snapshotUntrusted(
                runDirectory,
                "run-manifest.json",
                limits.maxIndexBytes(),
                workspace,
                "manifest");
        ObjectNode manifest = new SemanticRequestPackageJsonReader().readObject(
                snapshot, limits.maxIndexBytes(), null, limits);
        require("codex-session".equals(requiredText(manifest, "provider")));
        require(Set.of("AWAITING_MODEL_RESULTS", "REQUESTS_READY")
                .contains(requiredText(manifest, "status")));
        return new Manifest(
                requiredNonNegativeInt(manifest, "shardCount"),
                requiredText(manifest, "model"),
                requiredText(manifest, "reasoningEffort"),
                ArtifactRetention.parse(manifest.path("retention").asText("full")));
    }

    private static String requiredText(JsonNode object, String field) {
        JsonNode value = object.path(field);
        require(value.isTextual() && !value.textValue().isBlank());
        return value.textValue();
    }

    private static int requiredNonNegativeInt(JsonNode object, String field) {
        JsonNode value = object.path(field);
        require(value.isIntegralNumber() && value.canConvertToInt() && value.intValue() >= 0);
        return value.intValue();
    }

    private static void require(boolean valid) {
        if (!valid) {
            throw invalid();
        }
    }

    private static SemanticExtractionValidationException invalid() {
        return new SemanticExtractionValidationException(
                "semantic Codex request run is invalid");
    }

    private record Manifest(
            int shardCount,
            String model,
            String reasoningEffort,
            ArtifactRetention retention
    ) {
    }

    public record Snapshot(
            SemanticRunPlan plan,
            String canonicalSha256,
            String model,
            String reasoningEffort,
            ArtifactRetention retention
    ) {
        public Snapshot {
            if (plan == null || canonicalSha256 == null
                    || !canonicalSha256.matches("[0-9a-f]{64}")
                    || model == null || model.isBlank()
                    || reasoningEffort == null || reasoningEffort.isBlank()
                    || retention == null) {
                throw new IllegalArgumentException(
                        "semantic Codex request snapshot is incomplete");
            }
        }
    }
}
