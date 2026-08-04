package com.relationdetector.semantic.extraction.artifact;

import com.relationdetector.semantic.extraction.normalization.SemanticExtractionValidationException;
import com.relationdetector.semantic.extraction.shard.SemanticRunPlan;
import com.relationdetector.semantic.extraction.shard.SemanticShardDescriptor;
import com.relationdetector.semantic.internal.io.SemanticFileDigest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SemanticRunPlanSnapshotTest {
    @TempDir
    Path tempDir;

    @Test
    void snapshotsEveryDeclaredPlanArtifactBeforeLaterReads() throws Exception {
        SemanticRunPlan source = plan("one");
        SemanticRunPlan snapshot = SemanticRunPlanSnapshot.capture(
                source, tempDir.resolve("private-scratch"));

        Files.writeString(source.fullBundle().path(), "mutated");
        Files.writeString(source.ownerManifest().path(), "mutated");
        Files.writeString(source.shards().get(0).bundle().path(), "mutated");
        Files.writeString(source.shards().get(0).externalAuditSidecar().path(), "mutated");

        assertEquals("{\"full\":\"one\"}", Files.readString(snapshot.fullBundle().path()));
        assertEquals("owner-one", Files.readString(snapshot.ownerManifest().path()));
        assertEquals("{\"shard\":\"one\"}",
                Files.readString(snapshot.shards().get(0).bundle().path()));
        assertEquals("audit-one", Files.readString(
                snapshot.shards().get(0).externalAuditSidecar().path()));
        assertTrue(snapshot.fullBundle().path().startsWith(tempDir.resolve("private-scratch")));
        assertNotEquals(source.fullBundle().path(), snapshot.fullBundle().path());
    }

    @Test
    void rejectsDeclaredHashOrSizeMismatchWithoutCreatingAUsableSnapshot() throws Exception {
        SemanticRunPlan source = plan("two");
        SemanticArtifactRef tampered = new SemanticArtifactRef(
                source.fullBundle().path(),
                source.fullBundle().bytes() + 1,
                source.fullBundle().sha256());
        SemanticRunPlan invalid = new SemanticRunPlan(
                tampered,
                source.shards(),
                source.reconcile(),
                source.maxInputTokens(),
                source.shardMaxOutputTokens(),
                source.reconciliationMaxOutputTokens(),
                source.ownerManifest());

        assertThrows(SemanticExtractionValidationException.class,
                () -> SemanticRunPlanSnapshot.capture(
                        invalid, tempDir.resolve("invalid-scratch")));
    }

    @Test
    void rejectsSymlinkedPlanArtifact() throws Exception {
        SemanticRunPlan source = plan("three");
        Path link = tempDir.resolve("full-link.json");
        try {
            Files.createSymbolicLink(link, source.fullBundle().path());
        } catch (UnsupportedOperationException failure) {
            return;
        }
        SemanticArtifactRef linked = new SemanticArtifactRef(
                link,
                source.fullBundle().bytes(),
                source.fullBundle().sha256());
        SemanticRunPlan invalid = new SemanticRunPlan(
                linked,
                source.shards(),
                false,
                1000,
                1000,
                1000,
                source.ownerManifest());

        assertThrows(SemanticExtractionValidationException.class,
                () -> SemanticRunPlanSnapshot.capture(
                        invalid, tempDir.resolve("link-scratch")));
    }

    private SemanticRunPlan plan(String suffix) throws Exception {
        Path source = tempDir.resolve("source-" + suffix);
        Files.createDirectories(source);
        SemanticArtifactRef full = artifact(source.resolve("full.json"), "{\"full\":\"" + suffix + "\"}");
        SemanticArtifactRef owner = artifact(source.resolve("owner.tsv"), "owner-" + suffix);
        SemanticArtifactRef bundle = artifact(
                source.resolve("shard.json"), "{\"shard\":\"" + suffix + "\"}");
        SemanticArtifactRef audit = artifact(source.resolve("audit.tsv"), "audit-" + suffix);
        SemanticShardDescriptor shard = new SemanticShardDescriptor(
                "shard-0001", "owner", bundle, audit, 100, 1, 0);
        return new SemanticRunPlan(full, List.of(shard), false, 1000, 1000, 1000, owner);
    }

    private SemanticArtifactRef artifact(Path path, String value) throws Exception {
        Files.writeString(path, value);
        SemanticFileDigest.Digest digest = SemanticFileDigest.compute(path);
        return new SemanticArtifactRef(path, digest.bytes(), digest.sha256());
    }
}
