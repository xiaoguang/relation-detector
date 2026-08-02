package com.relationdetector.semantic.extraction.artifact;

import com.relationdetector.semantic.extraction.normalization.SemanticExtractionValidationException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
import com.relationdetector.semantic.internal.io.SemanticFileTreeOperations;
import com.relationdetector.semantic.internal.io.SemanticAtomicFiles;
import com.relationdetector.semantic.internal.store.ExternalJsonRecordStore;
import com.relationdetector.semantic.evidence.SemanticEvidenceStore;

/**
 * CN: 从request-only运行中的owned shard bundle、可逆引用sidecar和压缩evidence archive流式重建完整
 * evidence bundle；输入文件逐一校验大小与SHA，输出仅在section摘要、owner覆盖和完整canonical摘要全部匹配
 * 后原子发布。本类不依赖原始scan，也不把overlap记录重复写入正式结果。
 * EN: Streams a complete evidence bundle from owned shard bundles, reversible reference sidecars, and the compressed
 * evidence archive in a request-only run. It verifies each file, section digest, ownership coverage, and the complete
 * canonical digest before atomically publishing output, without depending on original scans or emitting overlap twice.
 */
public final class SemanticRequestBundleReconstructor {
    private static final ObjectMapper JSON = new ObjectMapper();

    public Result reconstruct(Path runDirectory, Path target) {
        if (runDirectory == null || target == null) {
            throw new IllegalArgumentException("semantic request run and reconstruction target are required");
        }
        Path normalizedRun = runDirectory.toAbsolutePath().normalize();
        Path parent = target.toAbsolutePath().normalize().getParent();
        if (parent == null) {
            throw new IllegalArgumentException("semantic reconstruction target parent is required");
        }
        Path workspace = parent.resolve(".request-reconstruct-" + UUID.randomUUID());
        try {
            Files.createDirectories(parent);
            Files.createDirectories(workspace);
            ObjectNode index = readObject(
                    normalizedRun.resolve("request-bundle-index.json"),
                    "semantic request bundle index");
            require(index.path("artifactSchemaVersion").isInt()
                    && index.path("artifactSchemaVersion").intValue() == 1);
            Map<SemanticEvidenceStore.Section, ExternalJsonRecordStore> stores = stores(workspace);
            boolean storesClosed = false;
            try {
                restoreOwnedRecords(normalizedRun, index, stores);
                stores.values().forEach(ExternalJsonRecordStore::finish);
                Map<String, SemanticRequestBundleCanonicalDigest.SectionDigest> sections =
                        validateSections(normalizedRun, index, stores);
                String canonical = SemanticRequestBundleCanonicalDigest.bundleSha256(
                        index.path("descriptor"), sections);
                require(canonical.equals(index.path("fullBundleCanonicalSha256").asText("")));
                SemanticAtomicFiles.replace(
                        target.toAbsolutePath().normalize(),
                        temporary -> writeBundle(temporary, normalizedRun, index, stores));
                close(stores);
                storesClosed = true;
                Map<String, Long> counts = new LinkedHashMap<>();
                sections.forEach((name, digest) -> counts.put(name, digest.count()));
                return new Result(canonical, counts);
            } finally {
                if (!storesClosed) {
                    close(stores);
                }
            }
        } catch (SemanticExtractionValidationException failure) {
            throw failure;
        } catch (IOException | RuntimeException failure) {
            throw invalidPackage();
        } finally {
            SemanticFileTreeOperations.deleteRecursivelyBestEffort(workspace);
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

    private void restoreOwnedRecords(
            Path run,
            ObjectNode index,
            Map<SemanticEvidenceStore.Section, ExternalJsonRecordStore> stores
    ) {
        require(index.path("shards").isArray() && !index.path("shards").isEmpty());
        long ownedFacts = 0;
        long ownedCandidates = 0;
        for (JsonNode shard : index.path("shards")) {
            Path bundlePath = verifiedArtifact(run, shard.path("bundle"));
            Path sidecarPath = verifiedArtifact(run, shard.path("sidecar"));
            ObjectNode bundle = readObject(bundlePath, "semantic request shard bundle");
            JsonNode context = bundle.path("shardContext");
            require(shard.path("id").asText("").equals(context.path("shardId").asText("")));
            Set<String> ownedFactRefs = textSet(context.path("ownedFactRefs"));
            Set<String> ownedCandidateRefs = textSet(context.path("ownedCandidateRefs"));
            Set<String> overlapRefs = textSet(context.path("overlapRefs"));
            Set<String> owned = new LinkedHashSet<>(ownedFactRefs);
            require(java.util.Collections.disjoint(owned, ownedCandidateRefs));
            owned.addAll(ownedCandidateRefs);
            require(java.util.Collections.disjoint(owned, overlapRefs));
            require(ownedFactRefs.size() == requiredNonNegativeLong(shard, "ownedFactCount"));
            require(ownedCandidateRefs.size() == requiredNonNegativeLong(shard, "ownedCandidateCount"));
            require(overlapRefs.size() == requiredNonNegativeLong(shard, "overlapCount"));
            SemanticExternalAuditReferences.ProjectionIndex projections =
                    SemanticExternalAuditReferences.readProjection(sidecarPath);
            validateExternalSummary(context, projections.externalReferences());
            for (SemanticEvidenceStore.Section section : stores.keySet()) {
                for (JsonNode projected : bundle.path(section.wireName())) {
                    String id = projected.path("id").asText("");
                    require(!id.isBlank() && (owned.contains(id) || overlapRefs.contains(id)));
                    if (owned.contains(id)) {
                        stores.get(section).append(id, projections.restore(projected));
                    }
                }
            }
            ownedFacts += ownedFactRefs.size();
            ownedCandidates += ownedCandidateRefs.size();
        }
        JsonNode coverage = index.path("coverage");
        require(ownedFacts == requiredNonNegativeLong(coverage, "ownedFactCount"));
        require(ownedCandidates == requiredNonNegativeLong(coverage, "ownedCandidateCount"));
    }

    private Map<String, SemanticRequestBundleCanonicalDigest.SectionDigest> validateSections(
            Path run,
            ObjectNode index,
            Map<SemanticEvidenceStore.Section, ExternalJsonRecordStore> stores
    ) {
        Map<String, SemanticRequestBundleCanonicalDigest.SectionDigest> actual = new LinkedHashMap<>();
        ObjectNode descriptor = object(index.path("descriptor"));
        SemanticRequestBundleCanonicalDigest.Accumulator tables =
                SemanticRequestBundleCanonicalDigest.accumulator();
        descriptor.path("tables").forEach(tables::add);
        actual.put(SemanticEvidenceStore.Section.TABLES.wireName(), tables.finish());

        Path evidence = verifiedArtifact(run, index.path("evidenceArchive"));
        actual.put(
                SemanticEvidenceStore.Section.EVIDENCE.wireName(),
                digestEvidence(evidence));
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

    private SemanticRequestBundleCanonicalDigest.SectionDigest digestEvidence(Path archive) {
        SemanticRequestBundleCanonicalDigest.Accumulator digest =
                SemanticRequestBundleCanonicalDigest.accumulator();
        readEvidence(archive, digest::add);
        return digest.finish();
    }

    private void writeBundle(
            Path target,
            Path run,
            ObjectNode index,
            Map<SemanticEvidenceStore.Section, ExternalJsonRecordStore> stores
    ) throws IOException {
        ObjectNode descriptor = object(index.path("descriptor"));
        try (JsonGenerator generator = JSON.getFactory().createGenerator(Files.newOutputStream(target))) {
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
                    readEvidence(
                            verifiedArtifact(run, index.path("evidenceArchive")),
                            value -> writeTree(generator, value));
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

    private void readEvidence(Path archive, java.util.function.Consumer<JsonNode> consumer) {
        try (InputStream compressed = new GZIPInputStream(Files.newInputStream(archive));
             JsonParser parser = JSON.getFactory().createParser(compressed)) {
            require(parser.nextToken() == JsonToken.START_ARRAY);
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                consumer.accept(JSON.readTree(parser));
            }
            require(parser.nextToken() == null);
        } catch (IOException failure) {
            throw invalidPackage();
        }
    }

    private void writeTree(JsonGenerator generator, JsonNode value) {
        try {
            generator.writeTree(value);
        } catch (IOException failure) {
            throw invalidPackage();
        }
    }

    private Path verifiedArtifact(Path run, JsonNode artifact) {
        try {
            return SemanticArtifactVerifier.verify(
                    run,
                    artifact.path("path").asText(""),
                    requiredNonNegativeLong(artifact, "bytes"),
                    artifact.path("sha256").asText(""));
        } catch (SemanticExtractionValidationException failure) {
            throw invalidPackage();
        }
    }

    private void validateExternalSummary(JsonNode context, Set<String> references) {
        SemanticExternalAuditReferences.Snapshot actual =
                SemanticExternalAuditReferences.snapshot(references);
        require(actual.count() == requiredNonNegativeLong(context, "externalAuditRefCount")
                && actual.sha256().equals(context.path("externalAuditRefsSha256").asText("")));
    }

    private Set<String> textSet(JsonNode values) {
        require(values.isArray());
        Set<String> result = new LinkedHashSet<>();
        values.forEach(value -> require(value.isTextual()
                && !value.asText().isBlank()
                && result.add(value.asText())));
        return Set.copyOf(result);
    }

    private ObjectNode readObject(Path path, String label) {
        try {
            JsonNode value = JSON.readTree(path.toFile());
            if (value == null || !value.isObject()) {
                throw new SemanticExtractionValidationException(label + " must be a JSON object");
            }
            return (ObjectNode) value;
        } catch (IOException failure) {
            throw invalidPackage();
        }
    }

    private ObjectNode object(JsonNode value) {
        require(value != null && value.isObject());
        return (ObjectNode) value;
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

    private long requiredNonNegativeLong(JsonNode object, String field) {
        JsonNode value = object.path(field);
        require(value.isIntegralNumber() && value.canConvertToLong() && value.longValue() >= 0);
        return value.longValue();
    }

    private static SemanticExtractionValidationException invalidPackage() {
        return new SemanticExtractionValidationException(
                "semantic request bundle package is invalid");
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
