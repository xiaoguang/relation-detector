package com.relationdetector.semantic.extraction.artifact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.extraction.normalization.SemanticExtractionValidationException;
import com.relationdetector.semantic.evidence.SemanticEvidenceStore;
import com.relationdetector.semantic.internal.io.SemanticFileDigest;

final class SemanticRequestBundleReconstructorLimitsTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final byte[] EXISTING_TARGET = "existing-target".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path tempDir;

    @Test
    void defaultLimitsAndExactTokenByteCapMatchTheTrustedContract() {
        SemanticRequestPackageLimits limits = SemanticRequestPackageLimits.defaults();

        assertEquals(64L * 1024 * 1024, limits.maxIndexBytes());
        assertEquals(4096, limits.maxShards());
        assertEquals(8_000_000, limits.maxEstimatedTokensPerShardOrRecord());
        assertEquals(256L * 1024 * 1024, limits.maxOwnerManifestBytes());
        assertEquals(1024L * 1024 * 1024, limits.maxSidecarBytes());
        assertEquals(8L * 1024 * 1024 * 1024, limits.maxCompressedEvidenceBytes());
        assertEquals(64L * 1024 * 1024 * 1024, limits.maxReconstructedBytes());
        assertEquals(1024 * 1024, limits.maxLineBytes());
        assertEquals(128, limits.maxJsonDepth());
        assertEquals(1024 * 1024, limits.maxStringCodePoints());
        assertEquals(88, limits.maximumJsonBytesForEstimatedTokens(100));
        assertThrows(ArithmeticException.class,
                () -> limits.maximumJsonBytesForEstimatedTokens(Long.MAX_VALUE));
    }

    @Test
    void reconstructsBothV1AndV2OnlyWhenOwnerManifestIsPresent() throws Exception {
        Fixture v2 = fixture("v2", 2);
        Fixture v1 = fixture("v1", 1);

        assertEquals(1L, reconstruct(v2).sectionCounts().get("metadataTables").longValue());
        assertEquals(1L, reconstruct(v1).sectionCounts().get("metadataTables").longValue());

        v1.index().remove("ownerManifest");
        v1.writeIndex();
        assertRejectedWithoutTargetChange(v1, SemanticRequestPackageLimits.defaults());
    }

    @Test
    void rejectsMissingOrMalformedV2SourceBundleHashWithoutPublishing() throws Exception {
        Fixture missing = fixture("missing-source-hash", 2);
        missing.index().remove("sourceBundleSha256");
        missing.writeIndex();
        assertRejectedWithoutTargetChange(missing, SemanticRequestPackageLimits.defaults());

        Fixture malformed = fixture("malformed-source-hash", 2);
        malformed.index().put("sourceBundleSha256", "ABC123");
        malformed.writeIndex();
        assertRejectedWithoutTargetChange(malformed, SemanticRequestPackageLimits.defaults());
    }

    @Test
    void completionReconstructionDoesNotPublishTargetWhenPlanSnapshotFails() throws Exception {
        Fixture fixture = fixture("late-snapshot-failure", 2);
        Path blockedSnapshotRoot = tempDir.resolve("blocked-plan-snapshot");
        Files.writeString(blockedSnapshotRoot, "not-a-directory", StandardCharsets.UTF_8);

        assertThrows(SemanticExtractionValidationException.class,
                () -> new SemanticRequestBundleReconstructor().reconstructCompletionSnapshot(
                        fixture.run(), fixture.target(), blockedSnapshotRoot,
                        SemanticRequestPackageLimits.defaults()));

        assertFalse(Files.exists(fixture.target()));
    }

    @Test
    void rejectsShardAndTokenDeclarationsAboveTrustedLimitsBeforeArtifacts() throws Exception {
        Fixture fixture = fixture("count-and-token", 2);
        fixture.index().withArray("shards").add(fixture.index().path("shards").get(0).deepCopy());
        fixture.writeIndex();

        assertRejectedWithoutTargetChange(fixture, limits(1, 8_000_000));

        fixture = fixture("token-limit", 2);
        ((ObjectNode) fixture.index().path("shards").get(0)).put("estimatedInputTokens", 101);
        fixture.writeIndex();
        assertRejectedWithoutTargetChange(fixture, limits(4096, 100));
    }

    @Test
    void rejectsWrongOwnerAssignmentAndOwnerLineOverLimit() throws Exception {
        Fixture wrongOwner = fixture("wrong-owner", 2);
        Files.writeString(
                wrongOwner.ownerManifest(),
                encode("metadata-table:orders") + "\tMETADATA_TABLES\tshard-other\n",
                StandardCharsets.UTF_8);
        wrongOwner.refreshArtifact("ownerManifest", wrongOwner.ownerManifest());
        wrongOwner.writeIndex();
        assertRejectedWithoutTargetChange(wrongOwner, SemanticRequestPackageLimits.defaults());

        Fixture wrongSection = fixture("wrong-section", 2);
        Files.writeString(
                wrongSection.ownerManifest(),
                encode("metadata-table:orders") + "\tMETADATA_COLUMNS\tshard-0001\n",
                StandardCharsets.UTF_8);
        wrongSection.refreshArtifact("ownerManifest", wrongSection.ownerManifest());
        wrongSection.writeIndex();
        assertRejectedWithoutTargetChange(wrongSection, SemanticRequestPackageLimits.defaults());

        Fixture longLine = fixture("long-owner-line", 2);
        assertRejectedWithoutTargetChange(longLine, limitsWithLineBytes(8));
    }

    @Test
    void acceptsEmptyOwnerAndLegacySidecarIndexesButRejectsUnresolvedExternalAudit()
            throws Exception {
        Path emptyOwner = tempDir.resolve("empty-owner.tsv");
        Path emptySidecar = tempDir.resolve("empty-sidecar.tsv");
        Files.createFile(emptyOwner);
        Files.createFile(emptySidecar);
        try (SemanticOwnerManifestIndex owners = SemanticOwnerManifestIndex.open(
                     emptyOwner, tempDir.resolve("empty-owner-index"),
                     SemanticRequestPackageLimits.defaults());
             SemanticProjectionStore projections = SemanticProjectionStore.open(
                     emptySidecar, tempDir.resolve("empty-projection-index"),
                     SemanticRequestPackageLimits.defaults())) {
            assertEquals(0, owners.count());
            assertEquals(0, projections.externalReferences().count());
        }

        Fixture resolved = fixture("resolved-external", 2);
        appendExternalReference(resolved, "evidence:orders");
        assertEquals(1L, reconstruct(resolved).sectionCounts().get("evidence").longValue());

        Fixture unresolved = fixture("unresolved-external", 2);
        String unknown = "evidence:unknown";
        appendExternalReference(unresolved, unknown);

        assertRejectedWithoutTargetChange(unresolved, SemanticRequestPackageLimits.defaults());
    }

    @Test
    void rejectsMalformedUtf8DepthStringAndTrailingDataBeforePublishing() throws Exception {
        Fixture malformed = fixture("malformed-utf8", 2);
        Files.write(malformed.bundle(), new byte[] {'{', '"', (byte) 0xc3, 0x28, '"', ':', '1', '}'});
        malformed.refreshShardArtifact("bundle", malformed.bundle());
        malformed.writeIndex();
        assertRejectedWithoutTargetChange(malformed, SemanticRequestPackageLimits.defaults());

        Fixture deep = fixture("deep-json", 2);
        assertRejectedWithoutTargetChange(deep, limitsWithJsonShape(2, 1024 * 1024));

        Fixture string = fixture("long-string", 2);
        assertRejectedWithoutTargetChange(string, limitsWithJsonShape(128, 3));

        Fixture trailing = fixture("trailing-index", 2);
        Files.writeString(trailing.indexPath(), "\n{}\n", StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.APPEND);
        assertRejectedWithoutTargetChange(trailing, SemanticRequestPackageLimits.defaults());
    }

    @Test
    void rejectsPathEscapeSymlinkHashAndSizeWithoutPublishing() throws Exception {
        Fixture escaped = fixture("escaped", 2);
        ((ObjectNode) escaped.index().path("shards").get(0).path("bundle"))
                .put("path", "../outside.json");
        escaped.writeIndex();
        assertRejectedWithoutTargetChange(escaped, SemanticRequestPackageLimits.defaults());

        Fixture linked = fixture("symlink", 2);
        Path original = linked.bundle();
        Path real = tempDir.resolve("symlink-source.json");
        Files.move(original, real);
        Files.createSymbolicLink(original, real);
        linked.refreshShardArtifact("bundle", real);
        ((ObjectNode) linked.index().path("shards").get(0).path("bundle"))
                .put("path", linked.run().relativize(original).toString());
        linked.writeIndex();
        assertRejectedWithoutTargetChange(linked, SemanticRequestPackageLimits.defaults());

        Fixture hash = fixture("hash", 2);
        ((ObjectNode) hash.index().path("shards").get(0).path("bundle"))
                .put("sha256", "0".repeat(64));
        hash.writeIndex();
        assertRejectedWithoutTargetChange(hash, SemanticRequestPackageLimits.defaults());

        Fixture size = fixture("size", 2);
        ((ObjectNode) size.index().path("shards").get(0).path("bundle"))
                .put("bytes", Files.size(size.bundle()) + 1);
        size.writeIndex();
        assertRejectedWithoutTargetChange(size, SemanticRequestPackageLimits.defaults());
    }

    @Test
    void rejectsSidecarAndCompressedOrExpandedEvidenceOverLimits() throws Exception {
        Fixture sidecar = fixture("sidecar-limit", 2);
        assertRejectedWithoutTargetChange(sidecar, limitsWithArtifactBytes(
                8, 8L * 1024 * 1024 * 1024, 64L * 1024 * 1024 * 1024));

        Fixture compressed = fixture("compressed-limit", 2);
        assertRejectedWithoutTargetChange(compressed, limitsWithArtifactBytes(
                1024L * 1024 * 1024, 8, 64L * 1024 * 1024 * 1024));

        Fixture expanded = fixture("expanded-limit", 2);
        assertRejectedWithoutTargetChange(expanded, limitsWithArtifactBytes(
                1024L * 1024 * 1024, 8L * 1024 * 1024 * 1024, 16));
    }

    @Test
    void artifactSnapshotIsDetachedFromLaterPathMutation() throws Exception {
        Path source = tempDir.resolve("mutable.json");
        Files.writeString(source, "{\"value\":1}", StandardCharsets.UTF_8);
        SemanticFileDigest.Digest digest = SemanticFileDigest.compute(source);
        Path workspace = tempDir.resolve("snapshot-work");
        Files.createDirectories(workspace);

        Path snapshot = SemanticRequestPackageArtifactVerifier.snapshot(
                tempDir, source.getFileName().toString(), digest.bytes(), digest.sha256(),
                1024, workspace, "artifact");
        Files.writeString(source, "{\"value\":2}", StandardCharsets.UTF_8);

        assertEquals("{\"value\":1}", Files.readString(snapshot));
    }

    @Test
    void projectionStoreRestoresOneRecordWithoutMaterializingHundredThousandRecords()
            throws Exception {
        Path sidecar = tempDir.resolve("large-sidecar.tsv");
        try (var writer = Files.newBufferedWriter(sidecar, StandardCharsets.UTF_8)) {
            writer.write("#semantic-external-audit-refs-v2\n");
            for (int index = 0; index < 100_000; index++) {
                String id = "item:" + index;
                String reference = "evidence:" + index;
                SemanticExternalAuditReferences.Snapshot snapshot =
                        SemanticExternalAuditReferences.snapshot(List.of(reference));
                writer.write("F\t" + encode(id) + "\t" + encode("evidenceRefs")
                        + "\t1\t" + snapshot.sha256() + "\n");
                writer.write("R\t" + encode(id) + "\t" + encode("evidenceRefs")
                        + "\t0\t" + encode(reference) + "\n");
            }
        }

        try (SemanticProjectionStore store = SemanticProjectionStore.open(
                sidecar, tempDir.resolve("projection-store"),
                limitsWithArtifactBytes(Files.size(sidecar) + 1,
                        8L * 1024 * 1024 * 1024, 64L * 1024 * 1024 * 1024))) {
            ObjectNode projected = JSON.createObjectNode()
                    .put("id", "item:99999")
                    .put("evidenceRefCount", 1)
                    .put("evidenceRefsSha256",
                            SemanticExternalAuditReferences.snapshot(List.of("evidence:99999")).sha256());

            assertEquals("evidence:99999",
                    store.restore(projected).path("evidenceRefs").get(0).asText());
        }
    }

    private SemanticRequestBundleReconstructor.Result reconstruct(Fixture fixture) {
        return new SemanticRequestBundleReconstructor().reconstruct(
                fixture.run(), fixture.target(), SemanticRequestPackageLimits.defaults());
    }

    private void assertRejectedWithoutTargetChange(
            Fixture fixture,
            SemanticRequestPackageLimits limits
    ) throws Exception {
        Files.write(fixture.target(), EXISTING_TARGET);
        assertThrows(SemanticExtractionValidationException.class,
                () -> new SemanticRequestBundleReconstructor().reconstruct(
                        fixture.run(), fixture.target(), limits));
        assertEquals("existing-target", Files.readString(fixture.target()));
        try (var paths = Files.list(fixture.target().getParent())) {
            assertFalse(paths.anyMatch(path -> path.getFileName().toString()
                    .startsWith(".request-reconstruct-")));
        }
    }

    private SemanticRequestPackageLimits limits(int shards, int tokens) {
        return new SemanticRequestPackageLimits(
                64L * 1024 * 1024, shards, tokens,
                256L * 1024 * 1024, 1024L * 1024 * 1024,
                8L * 1024 * 1024 * 1024, 64L * 1024 * 1024 * 1024,
                1024 * 1024, 128, 1024 * 1024);
    }

    private SemanticRequestPackageLimits limitsWithLineBytes(int bytes) {
        SemanticRequestPackageLimits defaults = SemanticRequestPackageLimits.defaults();
        return new SemanticRequestPackageLimits(
                defaults.maxIndexBytes(), defaults.maxShards(),
                defaults.maxEstimatedTokensPerShardOrRecord(), defaults.maxOwnerManifestBytes(),
                defaults.maxSidecarBytes(), defaults.maxCompressedEvidenceBytes(),
                defaults.maxReconstructedBytes(), bytes,
                defaults.maxJsonDepth(), defaults.maxStringCodePoints());
    }

    private SemanticRequestPackageLimits limitsWithJsonShape(int depth, int stringCodePoints) {
        SemanticRequestPackageLimits defaults = SemanticRequestPackageLimits.defaults();
        return new SemanticRequestPackageLimits(
                defaults.maxIndexBytes(), defaults.maxShards(),
                defaults.maxEstimatedTokensPerShardOrRecord(), defaults.maxOwnerManifestBytes(),
                defaults.maxSidecarBytes(), defaults.maxCompressedEvidenceBytes(),
                defaults.maxReconstructedBytes(), defaults.maxLineBytes(),
                depth, stringCodePoints);
    }

    private SemanticRequestPackageLimits limitsWithArtifactBytes(
            long sidecar,
            long compressed,
            long reconstructed
    ) {
        SemanticRequestPackageLimits defaults = SemanticRequestPackageLimits.defaults();
        return new SemanticRequestPackageLimits(
                defaults.maxIndexBytes(), defaults.maxShards(),
                defaults.maxEstimatedTokensPerShardOrRecord(), defaults.maxOwnerManifestBytes(),
                sidecar, compressed, reconstructed, defaults.maxLineBytes(),
                defaults.maxJsonDepth(), defaults.maxStringCodePoints());
    }

    private void appendExternalReference(Fixture fixture, String reference) throws Exception {
        Files.writeString(
                fixture.sidecar(),
                "E\t" + encode(reference) + "\n",
                StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.APPEND);
        ObjectNode bundle = (ObjectNode) JSON.readTree(fixture.bundle().toFile());
        SemanticExternalAuditReferences.appendSummary(
                (ObjectNode) bundle.path("shardContext"), List.of(reference));
        JSON.writeValue(fixture.bundle().toFile(), bundle);
        fixture.refreshShardArtifact("bundle", fixture.bundle());
        fixture.refreshShardArtifact("sidecar", fixture.sidecar());
        fixture.writeIndex();
    }

    private Fixture fixture(String name, int version) throws Exception {
        Path run = tempDir.resolve(name + "-run");
        Files.createDirectories(run.resolve("request-bundle"));
        Files.createDirectories(run.resolve("shards/shard-0001"));
        Path owner = run.resolve("request-bundle/owner-manifest.tsv");
        Path bundle = run.resolve("shards/shard-0001/evidence-bundle.json");
        Path sidecar = run.resolve("shards/shard-0001/external-audit-refs.tsv");
        Path evidence = run.resolve("request-bundle/evidence-records.json.gz");

        ObjectNode fact = JSON.createObjectNode()
                .put("id", "metadata-table:orders")
                .put("catalog", "sales")
                .put("name", "orders");
        ObjectNode evidenceRecord = JSON.createObjectNode()
                .put("id", "evidence:orders")
                .put("type", "TEST")
                .put("source", "fixture")
                .put("detail", "orders");
        ObjectNode descriptor = descriptor();
        Map<String, SemanticRequestBundleCanonicalDigest.SectionDigest> digests =
                sectionDigests(descriptor, fact, evidenceRecord);

        ObjectNode shardBundle = descriptor.deepCopy();
        addSections(shardBundle);
        shardBundle.withArray("metadataTables").add(
                SemanticExternalAuditReferences.project(fact));
        ObjectNode context = shardBundle.putObject("shardContext");
        context.put("shardId", "shard-0001");
        context.putArray("ownedFactRefs").add(fact.path("id").asText());
        context.putArray("ownedCandidateRefs");
        context.putArray("overlapRefs");
        SemanticExternalAuditReferences.appendSummary(context, List.of());
        JSON.writeValue(bundle.toFile(), shardBundle);
        try (SemanticExternalAuditReferences.ProjectionWriter writer =
                     SemanticExternalAuditReferences.projectionWriter(sidecar, List.of())) {
            writer.append(fact);
        }
        Files.writeString(owner,
                encode(fact.path("id").asText()) + "\tMETADATA_TABLES\tshard-0001\n",
                StandardCharsets.UTF_8);
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(evidence))) {
            JSON.writeValue(output, List.of(evidenceRecord));
        }

        ObjectNode index = JSON.createObjectNode();
        index.put("artifactSchemaVersion", version);
        if (version == 2) {
            index.put("sourceBundleSha256", "a".repeat(64));
            index.put("reconcile", false);
            index.put("maxInputTokens", 10_000);
            index.put("shardMaxOutputTokens", 100);
            index.put("reconciliationMaxOutputTokens", 100);
        }
        index.put("fullBundleCanonicalSha256",
                SemanticRequestBundleCanonicalDigest.bundleSha256(descriptor, digests));
        index.set("descriptor", descriptor);
        ObjectNode sections = index.putObject("sections");
        digests.forEach((section, digest) -> sections.putObject(section)
                .put("count", digest.count()).put("sha256", digest.sha256()));
        index.set("ownerManifest", artifact(run, owner));
        index.set("evidenceArchive", artifact(run, evidence));
        ObjectNode shard = index.putArray("shards").addObject();
        shard.put("id", "shard-0001").put("ownerKey", "orders")
                .put("estimatedInputTokens", 10_000)
                .put("ownedFactCount", 1).put("ownedCandidateCount", 0).put("overlapCount", 0);
        shard.set("bundle", artifact(run, bundle));
        shard.set("sidecar", artifact(run, sidecar));
        index.putObject("coverage")
                .put("ownedFactCount", 1).put("ownedCandidateCount", 0).put("overlapCount", 0);
        index.putArray("inputScans");
        Fixture fixture = new Fixture(
                run, run.resolve("request-bundle-index.json"), index,
                owner, bundle, sidecar, evidence, tempDir.resolve(name + "-target.json"));
        fixture.writeIndex();
        return fixture;
    }

    private ObjectNode descriptor() {
        ObjectNode descriptor = JSON.createObjectNode();
        descriptor.putObject("database")
                .put("type", "mysql")
                .put("catalog", "sales")
                .put("schema", "");
        descriptor.putObject("metadataInventory");
        descriptor.putArray("inputFiles");
        descriptor.putArray("sources");
        descriptor.putArray("tables").add("sales.orders");
        descriptor.putObject("instructions");
        return descriptor;
    }

    private Map<String, SemanticRequestBundleCanonicalDigest.SectionDigest> sectionDigests(
            ObjectNode descriptor,
            ObjectNode fact,
            ObjectNode evidence
    ) {
        Map<String, SemanticRequestBundleCanonicalDigest.SectionDigest> result = new LinkedHashMap<>();
        for (SemanticEvidenceStore.Section section : SemanticEvidenceStore.Section.values()) {
            SemanticRequestBundleCanonicalDigest.Accumulator accumulator =
                    SemanticRequestBundleCanonicalDigest.accumulator();
            if (section == SemanticEvidenceStore.Section.TABLES) {
                descriptor.path("tables").forEach(accumulator::add);
            } else if (section == SemanticEvidenceStore.Section.EVIDENCE) {
                accumulator.add(evidence);
            } else if (section == SemanticEvidenceStore.Section.METADATA_TABLES) {
                accumulator.add(fact);
            }
            result.put(section.wireName(), accumulator.finish());
        }
        return Map.copyOf(result);
    }

    private void addSections(ObjectNode document) {
        for (SemanticEvidenceStore.Section section : SemanticEvidenceStore.Section.values()) {
            if (!document.has(section.wireName())) {
                document.putArray(section.wireName());
            }
        }
    }

    private ObjectNode artifact(Path root, Path file) throws IOException {
        SemanticFileDigest.Digest digest = SemanticFileDigest.compute(file);
        return JSON.createObjectNode()
                .put("path", root.relativize(file).toString().replace('\\', '/'))
                .put("bytes", digest.bytes())
                .put("sha256", digest.sha256());
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private final class Fixture {
        private final Path run;
        private final Path indexPath;
        private final ObjectNode index;
        private final Path ownerManifest;
        private final Path bundle;
        private final Path sidecar;
        private final Path evidence;
        private final Path target;

        private Fixture(
                Path run,
                Path indexPath,
                ObjectNode index,
                Path ownerManifest,
                Path bundle,
                Path sidecar,
                Path evidence,
                Path target
        ) {
            this.run = run;
            this.indexPath = indexPath;
            this.index = index;
            this.ownerManifest = ownerManifest;
            this.bundle = bundle;
            this.sidecar = sidecar;
            this.evidence = evidence;
            this.target = target;
        }

        void writeIndex() throws IOException {
            JSON.writeValue(indexPath.toFile(), index);
        }

        void refreshArtifact(String field, Path file) throws IOException {
            index.set(field, artifact(run, file));
        }

        void refreshShardArtifact(String field, Path file) throws IOException {
            ((ObjectNode) index.path("shards").get(0)).set(field, artifact(run, file));
        }

        Path run() { return run; }
        Path indexPath() { return indexPath; }
        ObjectNode index() { return index; }
        Path ownerManifest() { return ownerManifest; }
        Path bundle() { return bundle; }
        Path sidecar() { return sidecar; }
        Path evidence() { return evidence; }
        Path target() { return target; }
    }
}
