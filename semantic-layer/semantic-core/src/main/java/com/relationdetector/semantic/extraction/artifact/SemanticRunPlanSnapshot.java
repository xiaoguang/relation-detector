package com.relationdetector.semantic.extraction.artifact;

import com.relationdetector.semantic.extraction.normalization.SemanticExtractionValidationException;
import com.relationdetector.semantic.extraction.shard.SemanticRunPlan;
import com.relationdetector.semantic.extraction.shard.SemanticShardDescriptor;
import com.relationdetector.semantic.internal.io.SemanticFileDigest;
import com.relationdetector.semantic.internal.io.SemanticFileTreeOperations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * CN: 在任何 request render 或模型调用前复核并流式复制 plan 全部文件，返回只指向私有 scratch 的快照。
 * EN: Verifies and stream-copies every plan artifact before any request rendering or model call, returning a plan
 * that points only into an application-private scratch snapshot.
 */
public final class SemanticRunPlanSnapshot {
    private SemanticRunPlanSnapshot() {
    }

    public static SemanticRunPlan capture(SemanticRunPlan plan, Path scratchRoot) {
        if (plan == null || scratchRoot == null) {
            throw new IllegalArgumentException("semantic run plan and snapshot root are required");
        }
        Path root = scratchRoot.toAbsolutePath().normalize();
        boolean created = false;
        try {
            Path parent = root.getParent();
            if (parent == null) {
                throw invalid();
            }
            Files.createDirectories(parent);
            Files.createDirectory(root);
            created = true;
            restrict(root);
            SemanticArtifactRef full = copy(
                    plan.fullBundle(), root.resolve("full-evidence-bundle.json"));
            SemanticArtifactRef owner = copy(
                    plan.ownerManifest(), root.resolve("owner-manifest.tsv"));
            List<SemanticShardDescriptor> shards = new ArrayList<>();
            for (SemanticShardDescriptor shard : plan.shards()) {
                Path directory = root.resolve("shards").resolve(shard.id()).normalize();
                if (!directory.startsWith(root) || !simpleName(shard.id())) {
                    throw invalid();
                }
                Files.createDirectories(directory);
                SemanticArtifactRef bundle = copy(
                        shard.bundle(), directory.resolve("evidence-bundle.json"));
                SemanticArtifactRef sidecar = copy(
                        shard.externalAuditSidecar(), directory.resolve("external-audit-refs.tsv"));
                shards.add(new SemanticShardDescriptor(
                        shard.id(), shard.ownerKey(), bundle, sidecar,
                        shard.estimatedInputTokens(), shard.ownedFactCount(),
                        shard.ownedCandidateCount()));
            }
            return new SemanticRunPlan(
                    full, shards, plan.reconcile(), plan.maxInputTokens(),
                    plan.shardMaxOutputTokens(), plan.reconciliationMaxOutputTokens(), owner);
        } catch (SemanticExtractionValidationException failure) {
            if (created) {
                SemanticFileTreeOperations.deleteRecursivelyBestEffort(root);
            }
            throw failure;
        } catch (IOException | ArithmeticException failure) {
            if (created) {
                SemanticFileTreeOperations.deleteRecursivelyBestEffort(root);
            }
            throw invalid();
        }
    }

    private static SemanticArtifactRef copy(SemanticArtifactRef source, Path target)
            throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(
                source.path(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile() || attributes.size() != source.bytes()) {
            throw invalid();
        }
        SemanticFileDigest.Digest digest = SemanticFileDigest.copyNoFollow(
                source.path(), target, source.bytes());
        if (digest.bytes() != source.bytes() || !digest.sha256().equals(source.sha256())) {
            throw invalid();
        }
        return new SemanticArtifactRef(target, digest.bytes(), digest.sha256());
    }

    private static void restrict(Path directory) throws IOException {
        try {
            Files.setPosixFilePermissions(directory, EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE));
        } catch (UnsupportedOperationException ignored) {
            // The platform does not expose POSIX permissions; CREATE_NEW still owns the scratch directory.
        }
    }

    private static boolean simpleName(String value) {
        return value != null && !value.isBlank()
                && Path.of(value).getNameCount() == 1
                && value.equals(Path.of(value).getFileName().toString());
    }

    private static SemanticExtractionValidationException invalid() {
        return new SemanticExtractionValidationException(
                "semantic run plan artifact cannot be verified");
    }
}
