package com.relationdetector.semantic.extraction.artifact;

import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.extraction.normalization.SemanticExtractionValidationException;
import com.relationdetector.semantic.extraction.prompt.SemanticTokenEstimateBudget;
import com.relationdetector.semantic.extraction.shard.SemanticRunPlan;
import com.relationdetector.semantic.extraction.shard.SemanticShardDescriptor;
import com.relationdetector.semantic.internal.io.SemanticAtomicFiles;
import com.relationdetector.semantic.internal.io.SemanticFileDigest;
import com.relationdetector.semantic.internal.io.SemanticFileTreeOperations;
import com.relationdetector.semantic.internal.store.ExternalJsonRecordStore;
import com.relationdetector.semantic.evidence.SemanticEvidenceStore;

/**
 * CN: 仅在 v1/v2 索引、文件、owner manifest、projection、压缩记录、section digest 与 canonical digest 全部通过可信上限后，原子重建完整 evidence bundle。
 * EN: Atomically reconstructs a complete evidence bundle only after the v1/v2 index, artifacts, owner manifest,
 * projections, compressed records, section digests, and canonical digest pass caller-trusted limits.
 */
public final class SemanticRequestBundleReconstructor {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final SemanticRequestPackageJsonReader jsonReader = new SemanticRequestPackageJsonReader();

    public Result reconstruct(Path runDirectory, Path target) {
        return reconstruct(runDirectory, target, SemanticRequestPackageLimits.defaults());
    }

    public Result reconstruct(
            Path runDirectory,
            Path target,
            SemanticRequestPackageLimits limits
    ) {
        return reconstruct(runDirectory, target, limits, null).result();
    }

    /**
     * Reconstructs a v2 request package and returns a plan whose artifacts and metadata are detached into the
     * caller-owned snapshot root. Package token declarations are accepted only when they tighten trusted limits.
     */
    CompletionSnapshot reconstructCompletionSnapshot(
            Path runDirectory,
            Path target,
            Path snapshotRoot,
            SemanticRequestPackageLimits limits
    ) {
        if (snapshotRoot == null) {
            throw new IllegalArgumentException("semantic completion snapshot root is required");
        }
        Reconstruction reconstruction = reconstruct(
                runDirectory, target, limits, snapshotRoot.toAbsolutePath().normalize());
        if (reconstruction.completionPlan() == null) {
            throw invalidPackage();
        }
        return new CompletionSnapshot(
                reconstruction.completionPlan(), reconstruction.result().canonicalSha256());
    }

    private Reconstruction reconstruct(
            Path runDirectory,
            Path target,
            SemanticRequestPackageLimits limits,
            Path completionSnapshotRoot
    ) {
        if (runDirectory == null || target == null || limits == null) {
            throw new IllegalArgumentException(
                    "semantic request run, reconstruction target, and limits are required");
        }
        Path run = runDirectory.toAbsolutePath().normalize();
        Path normalizedTarget = target.toAbsolutePath().normalize();
        Path parent = normalizedTarget.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("semantic reconstruction target parent is required");
        }
        boolean completionTargetOwned = completionSnapshotRoot != null;
        if (completionTargetOwned && Files.exists(normalizedTarget, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            throw invalidPackage();
        }
        Path workspace = parent.resolve(".request-reconstruct-" + UUID.randomUUID());
        try {
            Files.createDirectories(parent);
            Files.createDirectories(workspace);
            Path indexSnapshot = SemanticRequestPackageArtifactVerifier.snapshotUntrusted(
                    run,
                    "request-bundle-index.json",
                    limits.maxIndexBytes(),
                    workspace.resolve("index"),
                    "index");
            ObjectNode index = jsonReader.readObject(
                    indexSnapshot, limits.maxIndexBytes(), null, limits);
            validateIndexShapeAndTrustedDeclarations(index, limits);

            Path ownerSnapshot = verifiedArtifact(
                    run,
                    index.path("ownerManifest"),
                    limits.maxOwnerManifestBytes(),
                    workspace.resolve("owner-artifact"));
            Path evidenceSnapshot = verifiedArtifact(
                    run,
                    index.path("evidenceArchive"),
                    limits.maxCompressedEvidenceBytes(),
                    workspace.resolve("evidence-artifact"));
            List<ShardArtifacts> shardArtifacts = snapshotShardArtifacts(
                    run, index, limits, workspace.resolve("shard-artifacts"));
            prevalidateShardBundles(index, shardArtifacts, limits);

            try (SemanticOwnerManifestIndex owners = SemanticOwnerManifestIndex.open(
                         ownerSnapshot, workspace.resolve("owner-index"), limits);
                 ExternalJsonRecordStore matchedOwners = new ExternalJsonRecordStore(
                         workspace.resolve("matched-owners"));
                 ExternalJsonRecordStore externalAudit = new ExternalJsonRecordStore(
                         workspace.resolve("external-audit"));
                 ExternalJsonRecordStore evidenceIds = new ExternalJsonRecordStore(
                         workspace.resolve("evidence-ids"))) {
                Map<SemanticEvidenceStore.Section, ExternalJsonRecordStore> stores =
                        stores(workspace.resolve("sections"));
                boolean closed = false;
                try {
                    Coverage actualCoverage = restoreOwnedRecords(
                            index, shardArtifacts, limits, workspace,
                            owners, matchedOwners, externalAudit, stores);
                    stores.values().forEach(ExternalJsonRecordStore::finish);
                    matchedOwners.finish();
                    externalAudit.finish();
                    Map<String, SemanticRequestBundleCanonicalDigest.SectionDigest> sections =
                            validateSections(
                                    index, evidenceSnapshot, limits, stores,
                                    evidenceIds);
                    evidenceIds.finish();
                    externalAudit.forEach(record -> require(
                            owners.find(record.key()).isPresent()
                                    || evidenceIds.containsKey(record.key())));
                    String canonical = SemanticRequestBundleCanonicalDigest.bundleSha256(
                            index.path("descriptor"), sections);
                    require(canonical.equals(index.path("fullBundleCanonicalSha256").asText("")));
                    require(actualCoverage.ownedOccurrences() == matchedOwners.count());
                    require(matchedOwners.count() == owners.count());
                    validateCoverage(index.path("coverage"), actualCoverage);

                    SemanticAtomicFiles.replace(
                            normalizedTarget,
                            temporary -> writeBundle(
                                    temporary, index, evidenceSnapshot, limits, stores));
                    close(stores);
                    closed = true;
                    Map<String, Long> counts = new LinkedHashMap<>();
                    sections.forEach((name, digest) -> counts.put(name, digest.count()));
                    SemanticRunPlan completionPlan = completionSnapshotRoot == null
                            ? null
                            : captureCompletionPlan(
                                    index,
                                    normalizedTarget,
                                    ownerSnapshot,
                                    shardArtifacts,
                                    completionSnapshotRoot);
                    return new Reconstruction(
                            new Result(canonical, counts), completionPlan);
                } finally {
                    if (!closed) {
                        close(stores);
                    }
                }
            }
        } catch (IOException | RuntimeException failure) {
            if (completionTargetOwned) {
                try {
                    Files.deleteIfExists(normalizedTarget);
                } catch (IOException ignored) {
                    // The target is inside an application-owned completion snapshot root.
                }
            }
            throw invalidPackage();
        } finally {
            SemanticFileTreeOperations.deleteRecursivelyBestEffort(workspace);
        }
    }

    private void validateIndexShapeAndTrustedDeclarations(
            ObjectNode index,
            SemanticRequestPackageLimits limits
    ) {
        JsonNode version = index.path("artifactSchemaVersion");
        require(version.isInt() && (version.intValue() == 1 || version.intValue() == 2));
        validateTokenDeclaration(index, "maxInputTokens", limits, version.intValue() == 2);
        validateTokenDeclaration(index, "shardMaxOutputTokens", limits, version.intValue() == 2);
        validateTokenDeclaration(
                index, "reconciliationMaxOutputTokens", limits, version.intValue() == 2);
        if (version.intValue() == 2) {
            require(index.path("reconcile").isBoolean());
            require(index.path("sourceBundleSha256").asText("").matches("[0-9a-f]{64}"));
        }
        require(index.path("descriptor").isObject());
        require(index.path("sections").isObject());
        require(index.path("ownerManifest").isObject());
        require(index.path("evidenceArchive").isObject());
        require(index.path("coverage").isObject());
        require(index.path("fullBundleCanonicalSha256").asText("").matches("[0-9a-f]{64}"));
        JsonNode descriptor = index.path("descriptor");
        require(descriptor.path("database").isObject());
        require(descriptor.path("metadataInventory").isObject());
        require(descriptor.path("inputFiles").isArray());
        require(descriptor.path("sources").isArray());
        require(descriptor.path("tables").isArray());
        require(descriptor.path("instructions").isObject());
        JsonNode shards = index.path("shards");
        require(shards.isArray() && !shards.isEmpty() && shards.size() <= limits.maxShards());
        Set<String> shardIds = new LinkedHashSet<>();
        long declaredOwnedFacts = 0;
        long declaredOwnedCandidates = 0;
        long declaredOverlap = 0;
        for (JsonNode shard : shards) {
            require(shard.isObject());
            String id = shard.path("id").asText("");
            require(simpleName(id) && shardIds.add(id));
            int estimatedTokens = requiredPositiveInt(shard, "estimatedInputTokens");
            require(estimatedTokens <= limits.maxEstimatedTokensPerShardOrRecord());
            long ownedFacts = boundedCount(shard, "ownedFactCount", limits);
            long ownedCandidates = boundedCount(shard, "ownedCandidateCount", limits);
            long overlap = boundedCount(shard, "overlapCount", limits);
            declaredOwnedFacts = Math.addExact(declaredOwnedFacts, ownedFacts);
            declaredOwnedCandidates = Math.addExact(declaredOwnedCandidates, ownedCandidates);
            declaredOverlap = Math.addExact(declaredOverlap, overlap);
            require(declaredOwnedFacts <= limits.maxReconstructedBytes());
            require(declaredOwnedCandidates <= limits.maxReconstructedBytes());
            require(declaredOverlap <= limits.maxReconstructedBytes());
            require(shard.path("bundle").isObject() && shard.path("sidecar").isObject());
            if (version.intValue() == 2) {
                require(shard.path("ownerKey").isTextual()
                        && !shard.path("ownerKey").textValue().isBlank());
                require(shard.path("ownedFactCount").canConvertToInt()
                        && shard.path("ownedCandidateCount").canConvertToInt());
            }
        }
        for (SemanticEvidenceStore.Section section : SemanticEvidenceStore.Section.values()) {
            JsonNode expected = index.path("sections").path(section.wireName());
            require(expected.isObject());
            long count = requiredNonNegativeLong(expected, "count");
            require(count <= limits.maxReconstructedBytes());
            require(expected.path("sha256").asText("").matches("[0-9a-f]{64}"));
        }
        require(declaredOwnedFacts == boundedCount(
                index.path("coverage"), "ownedFactCount", limits));
        require(declaredOwnedCandidates == boundedCount(
                index.path("coverage"), "ownedCandidateCount", limits));
        require(declaredOverlap == boundedCount(
                index.path("coverage"), "overlapCount", limits));
    }

    private void validateTokenDeclaration(
            ObjectNode index,
            String field,
            SemanticRequestPackageLimits limits,
            boolean required
    ) {
        JsonNode value = index.path(field);
        if (!required && value.isMissingNode()) {
            return;
        }
        require(value.isIntegralNumber()
                && value.canConvertToInt()
                && value.intValue() > 0
                && value.intValue() <= limits.maxEstimatedTokensPerShardOrRecord());
    }

    private SemanticRunPlan captureCompletionPlan(
            ObjectNode index,
            Path fullBundle,
            Path ownerManifest,
            List<ShardArtifacts> shardArtifacts,
            Path snapshotRoot
    ) {
        require(index.path("artifactSchemaVersion").intValue() == 2);
        List<SemanticShardDescriptor> shards = new ArrayList<>();
        for (int ordinal = 0; ordinal < shardArtifacts.size(); ordinal++) {
            JsonNode shard = index.path("shards").get(ordinal);
            ShardArtifacts artifacts = shardArtifacts.get(ordinal);
            shards.add(new SemanticShardDescriptor(
                    shard.path("id").textValue(),
                    shard.path("ownerKey").textValue(),
                    artifact(artifacts.bundle()),
                    artifact(artifacts.sidecar()),
                    requiredPositiveInt(shard, "estimatedInputTokens"),
                    requiredNonNegativeInt(shard, "ownedFactCount"),
                    requiredNonNegativeInt(shard, "ownedCandidateCount")));
        }
        SemanticRunPlan plan = new SemanticRunPlan(
                artifact(fullBundle),
                shards,
                index.path("reconcile").booleanValue(),
                requiredPositiveInt(index, "maxInputTokens"),
                requiredPositiveInt(index, "shardMaxOutputTokens"),
                requiredPositiveInt(index, "reconciliationMaxOutputTokens"),
                artifact(ownerManifest));
        return SemanticRunPlanSnapshot.capture(plan, snapshotRoot);
    }

    private SemanticArtifactRef artifact(Path path) {
        try {
            SemanticFileDigest.Digest digest = SemanticFileDigest.computeNoFollow(path);
            return new SemanticArtifactRef(path, digest.bytes(), digest.sha256());
        } catch (IOException failure) {
            throw invalidPackage();
        }
    }

    private Map<SemanticEvidenceStore.Section, ExternalJsonRecordStore> stores(Path workspace) {
        Map<SemanticEvidenceStore.Section, ExternalJsonRecordStore> result = new EnumMap<>(
                SemanticEvidenceStore.Section.class);
        for (SemanticEvidenceStore.Section section : SemanticEvidenceStore.Section.values()) {
            if (section != SemanticEvidenceStore.Section.TABLES
                    && section != SemanticEvidenceStore.Section.EVIDENCE) {
                result.put(section, new ExternalJsonRecordStore(
                        workspace.resolve(section.wireName())));
            }
        }
        return result;
    }

    private Coverage restoreOwnedRecords(
            ObjectNode index,
            List<ShardArtifacts> shardArtifacts,
            SemanticRequestPackageLimits limits,
            Path workspace,
            SemanticOwnerManifestIndex owners,
            ExternalJsonRecordStore matchedOwners,
            ExternalJsonRecordStore externalAudit,
            Map<SemanticEvidenceStore.Section, ExternalJsonRecordStore> stores
    ) {
        long ownedFacts = 0;
        long ownedCandidates = 0;
        long overlap = 0;
        long ownedOccurrences = 0;
        for (int ordinal = 0; ordinal < shardArtifacts.size(); ordinal++) {
            JsonNode shard = index.path("shards").get(ordinal);
            ShardArtifacts artifacts = shardArtifacts.get(ordinal);
            String shardId = shard.path("id").asText("");
            requiredPositiveInt(shard, "estimatedInputTokens");
            long maximumBundleBytes = maximumShardBundleBytes(limits);
            Path shardWorkspace = workspace.resolve("shard-" + ordinal);
            ObjectNode bundle = jsonReader.readObject(
                    artifacts.bundle(), maximumBundleBytes,
                    limits.maxEstimatedTokensPerShardOrRecord(), limits);
            JsonNode context = bundle.path("shardContext");
            require(context.isObject()
                    && shardId.equals(context.path("shardId").asText("")));
            Set<String> ownedFactRefs = textSet(context.path("ownedFactRefs"));
            Set<String> ownedCandidateRefs = textSet(context.path("ownedCandidateRefs"));
            Set<String> overlapRefs = textSet(context.path("overlapRefs"));
            requireDisjoint(ownedFactRefs, ownedCandidateRefs, overlapRefs);
            require(ownedFactRefs.size() == requiredNonNegativeLong(shard, "ownedFactCount"));
            require(ownedCandidateRefs.size()
                    == requiredNonNegativeLong(shard, "ownedCandidateCount"));
            require(overlapRefs.size() == requiredNonNegativeLong(shard, "overlapCount"));

            try (SemanticProjectionStore projections = SemanticProjectionStore.open(
                    artifacts.sidecar(), shardWorkspace.resolve("projection-index"), limits)) {
                validateExternalSummary(context, projections.externalReferences());
                Set<String> classified = new LinkedHashSet<>();
                for (SemanticEvidenceStore.Section section : stores.keySet()) {
                    JsonNode values = bundle.path(section.wireName());
                    require(values.isArray());
                    for (JsonNode projected : values) {
                        String id = projected.path("id").asText("");
                        require(!id.isBlank() && classified.add(id));
                        boolean fact = isFact(section);
                        boolean owned = fact
                                ? ownedFactRefs.contains(id)
                                : ownedCandidateRefs.contains(id);
                        require(owned || overlapRefs.contains(id));
                        SemanticOwnerManifestIndex.Entry owner = owners.find(id).orElseThrow(
                                SemanticRequestBundleReconstructor::invalidPackage);
                        require(owner.fact() == fact);
                        require(owner.section().equals(
                                SemanticOwnerManifestIndex.manifestSection(section.wireName())));
                        require(owned == owner.ownerShardId().equals(shardId));
                        if (owned) {
                            stores.get(section).append(id, projections.restore(projected));
                            matchedOwners.append(id, ownerDocument(owner));
                            ownedOccurrences++;
                        }
                    }
                }
                Set<String> expectedClassified = new LinkedHashSet<>(ownedFactRefs);
                expectedClassified.addAll(ownedCandidateRefs);
                expectedClassified.addAll(overlapRefs);
                require(classified.equals(expectedClassified));
                projections.forEachExternalReference(reference -> {
                    require(!classified.contains(reference));
                    externalAudit.append(reference, JSON.createObjectNode());
                });
            }
            ownedFacts = Math.addExact(ownedFacts, ownedFactRefs.size());
            ownedCandidates = Math.addExact(ownedCandidates, ownedCandidateRefs.size());
            overlap = Math.addExact(overlap, overlapRefs.size());
        }
        return new Coverage(ownedFacts, ownedCandidates, overlap, ownedOccurrences);
    }

    private List<ShardArtifacts> snapshotShardArtifacts(
            Path run,
            ObjectNode index,
            SemanticRequestPackageLimits limits,
            Path workspace
    ) {
        List<ShardArtifacts> result = new ArrayList<>();
        int ordinal = 0;
        for (JsonNode shard : index.path("shards")) {
            requiredPositiveInt(shard, "estimatedInputTokens");
            long maximumBundleBytes = maximumShardBundleBytes(limits);
            Path shardWorkspace = workspace.resolve(Integer.toString(ordinal++));
            result.add(new ShardArtifacts(
                    verifiedArtifact(
                            run, shard.path("bundle"), maximumBundleBytes,
                            shardWorkspace.resolve("bundle")),
                    verifiedArtifact(
                            run, shard.path("sidecar"), limits.maxSidecarBytes(),
                            shardWorkspace.resolve("sidecar"))));
        }
        return List.copyOf(result);
    }

    private void prevalidateShardBundles(
            ObjectNode index,
            List<ShardArtifacts> artifacts,
            SemanticRequestPackageLimits limits
    ) {
        for (int ordinal = 0; ordinal < artifacts.size(); ordinal++) {
            JsonNode shard = index.path("shards").get(ordinal);
            requiredPositiveInt(shard, "estimatedInputTokens");
            long maximumBundleBytes = maximumShardBundleBytes(limits);
            ObjectNode bundle = jsonReader.readObject(
                    artifacts.get(ordinal).bundle(),
                    maximumBundleBytes,
                    limits.maxEstimatedTokensPerShardOrRecord(),
                    limits);
            require(bundle.path("shardContext").isObject());
            for (SemanticEvidenceStore.Section section : SemanticEvidenceStore.Section.values()) {
                if (section != SemanticEvidenceStore.Section.TABLES
                        && section != SemanticEvidenceStore.Section.EVIDENCE) {
                    require(bundle.path(section.wireName()).isArray());
                }
            }
        }
    }

    private Map<String, SemanticRequestBundleCanonicalDigest.SectionDigest> validateSections(
            ObjectNode index,
            Path evidenceArchive,
            SemanticRequestPackageLimits limits,
            Map<SemanticEvidenceStore.Section, ExternalJsonRecordStore> stores,
            ExternalJsonRecordStore evidenceIds
    ) {
        Map<String, SemanticRequestBundleCanonicalDigest.SectionDigest> actual = new LinkedHashMap<>();
        ObjectNode descriptor = object(index.path("descriptor"));
        SemanticRequestBundleCanonicalDigest.Accumulator tables =
                SemanticRequestBundleCanonicalDigest.accumulator();
        require(descriptor.path("tables").isArray());
        descriptor.path("tables").forEach(tables::add);
        actual.put(SemanticEvidenceStore.Section.TABLES.wireName(), tables.finish());

        SemanticRequestBundleCanonicalDigest.Accumulator evidence =
                SemanticRequestBundleCanonicalDigest.accumulator();
        readEvidence(evidenceArchive, limits, value -> {
            evidence.add(value);
            String id = value.path("id").asText("");
            if (!id.isBlank()) {
                evidenceIds.append(id, JSON.createObjectNode());
            }
        });
        actual.put(SemanticEvidenceStore.Section.EVIDENCE.wireName(), evidence.finish());
        for (Map.Entry<SemanticEvidenceStore.Section, ExternalJsonRecordStore> entry : stores.entrySet()) {
            SemanticRequestBundleCanonicalDigest.Accumulator digest =
                    SemanticRequestBundleCanonicalDigest.accumulator();
            entry.getValue().forEach(record -> digest.add(record.value()));
            actual.put(entry.getKey().wireName(), digest.finish());
        }
        for (SemanticEvidenceStore.Section section : SemanticEvidenceStore.Section.values()) {
            JsonNode expected = index.path("sections").path(section.wireName());
            SemanticRequestBundleCanonicalDigest.SectionDigest value = actual.get(section.wireName());
            require(value != null
                    && value.count() == requiredNonNegativeLong(expected, "count")
                    && value.sha256().equals(expected.path("sha256").asText("")));
        }
        return SemanticRequestBundleCanonicalDigest.immutable(actual);
    }

    private void writeBundle(
            Path target,
            ObjectNode index,
            Path evidenceArchive,
            SemanticRequestPackageLimits limits,
            Map<SemanticEvidenceStore.Section, ExternalJsonRecordStore> stores
    ) throws IOException {
        ObjectNode descriptor = object(index.path("descriptor"));
        try (OutputStream file = Files.newOutputStream(target);
             OutputStream bounded = new OutputLimitStream(file, limits.maxReconstructedBytes());
             JsonGenerator generator = jsonReader.mapper(limits).getFactory().createGenerator(bounded)) {
            generator.useDefaultPrettyPrinter();
            generator.writeStartObject();
            generator.writeObjectField("database", descriptor.path("database"));
            generator.writeObjectField("metadataInventory", descriptor.path("metadataInventory"));
            generator.writeObjectField("inputFiles", descriptor.path("inputFiles"));
            generator.writeObjectField("sources", descriptor.path("sources"));
            for (SemanticEvidenceStore.Section section : SemanticEvidenceStore.Section.values()) {
                if (section == SemanticEvidenceStore.Section.TABLES) {
                    generator.writeObjectField("tables", descriptor.path("tables"));
                } else if (section == SemanticEvidenceStore.Section.EVIDENCE) {
                    generator.writeArrayFieldStart(section.wireName());
                    readEvidence(evidenceArchive, limits, value -> writeTree(generator, value));
                    generator.writeEndArray();
                } else {
                    stores.get(section).writeArray(generator, section.wireName());
                }
            }
            generator.writeObjectField("instructions", descriptor.path("instructions"));
            generator.writeEndObject();
            generator.writeRaw('\n');
        }
    }

    private void readEvidence(
            Path archive,
            SemanticRequestPackageLimits limits,
            java.util.function.Consumer<JsonNode> consumer
    ) {
        ObjectMapper mapper = jsonReader.mapper(limits);
        try (InputStream file = Files.newInputStream(archive);
             InputStream expanded = new ExpandedLimitInputStream(
                     new GZIPInputStream(file), limits.maxReconstructedBytes());
             Reader decoded = new InputStreamReader(
                     expanded,
                     StandardCharsets.UTF_8.newDecoder()
                             .onMalformedInput(CodingErrorAction.REPORT)
                             .onUnmappableCharacter(CodingErrorAction.REPORT));
             JsonParser parser = mapper.getFactory().createParser(decoded)) {
            require(parser.nextToken() == JsonToken.START_ARRAY);
            long records = 0;
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                records = Math.addExact(records, 1);
                require(records <= limits.maxReconstructedBytes());
                SemanticTokenEstimateBudget budget = new SemanticTokenEstimateBudget(
                        limits.maxEstimatedTokensPerShardOrRecord());
                JsonNode value = budget.readValue(
                        parser, mapper, "semantic request evidence record");
                budget.requireCompleteCodePoint("semantic request evidence record");
                jsonReader.requireStringLimits(value, limits.maxStringCodePoints());
                require(value.isObject());
                consumer.accept(value);
            }
            require(parser.nextToken() == null);
        } catch (IOException | RuntimeException failure) {
            throw invalidPackage();
        }
    }

    private Path verifiedArtifact(
            Path run,
            JsonNode artifact,
            long maximumBytes,
            Path workspace
    ) {
        require(artifact.isObject());
        return SemanticRequestPackageArtifactVerifier.snapshot(
                run,
                artifact.path("path").asText(""),
                requiredNonNegativeLong(artifact, "bytes"),
                artifact.path("sha256").asText(""),
                maximumBytes,
                workspace,
                "artifact");
    }

    private void validateCoverage(JsonNode expected, Coverage actual) {
        require(actual.ownedFacts() == requiredNonNegativeLong(expected, "ownedFactCount"));
        require(actual.ownedCandidates()
                == requiredNonNegativeLong(expected, "ownedCandidateCount"));
        require(actual.overlap() == requiredNonNegativeLong(expected, "overlapCount"));
    }

    private void validateExternalSummary(
            JsonNode context,
            SemanticExternalAuditReferences.Snapshot actual
    ) {
        require(actual.count() == requiredNonNegativeLong(context, "externalAuditRefCount")
                && actual.sha256().equals(context.path("externalAuditRefsSha256").asText("")));
    }

    private ObjectNode ownerDocument(SemanticOwnerManifestIndex.Entry owner) {
        return JSON.createObjectNode()
                .put("section", owner.section())
                .put("owner", owner.ownerShardId())
                .put("fact", owner.fact());
    }

    private boolean isFact(SemanticEvidenceStore.Section section) {
        return switch (section) {
            case EVENT_CANDIDATES, REVIEW_ITEM_CANDIDATES, TRIPLET_CANDIDATES -> false;
            case TABLES, EVIDENCE -> throw invalidPackage();
            default -> true;
        };
    }

    private Set<String> textSet(JsonNode values) {
        require(values.isArray());
        Set<String> result = new LinkedHashSet<>();
        values.forEach(value -> require(value.isTextual()
                && !value.asText().isBlank()
                && result.add(value.asText())));
        return Set.copyOf(result);
    }

    private void requireDisjoint(Set<String> facts, Set<String> candidates, Set<String> overlap) {
        require(java.util.Collections.disjoint(facts, candidates));
        require(java.util.Collections.disjoint(facts, overlap));
        require(java.util.Collections.disjoint(candidates, overlap));
    }

    private ObjectNode object(JsonNode value) {
        require(value != null && value.isObject());
        return (ObjectNode) value;
    }

    private int requiredPositiveInt(JsonNode object, String field) {
        JsonNode value = object.path(field);
        require(value.isIntegralNumber() && value.canConvertToInt() && value.intValue() > 0);
        return value.intValue();
    }

    private long requiredNonNegativeLong(JsonNode object, String field) {
        JsonNode value = object.path(field);
        require(value.isIntegralNumber() && value.canConvertToLong() && value.longValue() >= 0);
        return value.longValue();
    }

    private int requiredNonNegativeInt(JsonNode object, String field) {
        JsonNode value = object.path(field);
        require(value.isIntegralNumber() && value.canConvertToInt() && value.intValue() >= 0);
        return value.intValue();
    }

    private long boundedCount(
            JsonNode object,
            String field,
            SemanticRequestPackageLimits limits
    ) {
        long value = requiredNonNegativeLong(object, field);
        require(value <= limits.maxReconstructedBytes());
        return value;
    }

    private long maximumShardBundleBytes(SemanticRequestPackageLimits limits) {
        return limits.maximumJsonBytesForEstimatedTokens(
                limits.maxEstimatedTokensPerShardOrRecord());
    }

    private boolean simpleName(String value) {
        return value != null && value.matches("[A-Za-z0-9._-]+");
    }

    private void writeTree(JsonGenerator generator, JsonNode value) {
        try {
            generator.writeTree(value);
        } catch (IOException failure) {
            throw invalidPackage();
        }
    }

    private void close(Map<SemanticEvidenceStore.Section, ExternalJsonRecordStore> stores) {
        RuntimeException failure = null;
        for (ExternalJsonRecordStore store : stores.values()) {
            try {
                store.close();
            } catch (RuntimeException error) {
                if (failure == null) {
                    failure = error;
                } else {
                    failure.addSuppressed(error);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void require(boolean valid) {
        if (!valid) {
            throw invalidPackage();
        }
    }

    private static SemanticExtractionValidationException invalidPackage() {
        return new SemanticExtractionValidationException(
                "semantic request bundle package is invalid");
    }

    private static final class ExpandedLimitInputStream extends FilterInputStream {
        private final long maximumBytes;
        private long bytes;

        private ExpandedLimitInputStream(InputStream delegate, long maximumBytes) {
            super(delegate);
            this.maximumBytes = maximumBytes;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
                add(1);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = super.read(buffer, offset, length);
            if (read > 0) {
                add(read);
            }
            return read;
        }

        private void add(int count) {
            bytes = Math.addExact(bytes, count);
            if (bytes > maximumBytes) {
                throw invalidPackage();
            }
        }
    }

    private static final class OutputLimitStream extends FilterOutputStream {
        private final long maximumBytes;
        private long bytes;

        private OutputLimitStream(OutputStream delegate, long maximumBytes) {
            super(delegate);
            this.maximumBytes = maximumBytes;
        }

        @Override
        public void write(int value) throws IOException {
            add(1);
            out.write(value);
        }

        @Override
        public void write(byte[] buffer, int offset, int length) throws IOException {
            add(length);
            out.write(buffer, offset, length);
        }

        private void add(int count) {
            bytes = Math.addExact(bytes, count);
            if (bytes > maximumBytes) {
                throw invalidPackage();
            }
        }
    }

    private record Coverage(
            long ownedFacts,
            long ownedCandidates,
            long overlap,
            long ownedOccurrences
    ) {
    }

    private record ShardArtifacts(Path bundle, Path sidecar) {
    }

    private record Reconstruction(Result result, SemanticRunPlan completionPlan) {
    }

    record CompletionSnapshot(SemanticRunPlan plan, String canonicalSha256) {
        public CompletionSnapshot {
            if (plan == null || canonicalSha256 == null
                    || !canonicalSha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException(
                        "semantic completion request snapshot is invalid");
            }
        }
    }

    public record Result(String canonicalSha256, Map<String, Long> sectionCounts) {
        public Result {
            if (canonicalSha256 == null || !canonicalSha256.matches("[0-9a-f]{64}")
                    || sectionCounts == null) {
                throw new IllegalArgumentException("semantic reconstruction result is invalid");
            }
            sectionCounts = Map.copyOf(sectionCounts);
        }
    }
}
