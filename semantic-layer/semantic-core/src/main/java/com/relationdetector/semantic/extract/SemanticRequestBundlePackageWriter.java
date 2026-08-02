package com.relationdetector.semantic.extract;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.StableSemanticId;
import com.relationdetector.semantic.internal.io.SemanticFileTreeOperations;
import com.relationdetector.semantic.reader.ExternalJsonRecordStore;
import com.relationdetector.semantic.reader.SemanticEvidenceStore;

/**
 * CN: 将request-only运行需要的完整证据拆成bounded shard bundle、可逆引用sidecar和单份压缩evidence
 * archive，并写出文件摘要与owner覆盖index。输入是已验证path plan，输出是可独立搬运的请求包；本类不调用
 * 模型、不改变prompt，也不保留完整bundle副本。
 * EN: Packages a request-only run as bounded shard bundles, reversible reference sidecars, and one compressed
 * evidence archive, with file digests and ownership coverage in an index. It consumes a validated path plan and emits
 * a portable package without calling a model, changing prompts, or retaining a complete bundle copy.
 */
final class SemanticRequestBundlePackageWriter {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, SemanticEvidenceStore.Section> ITEM_SECTIONS = itemSections();
    private final RunArtifactFileStore files;

    SemanticRequestBundlePackageWriter(RunArtifactFileStore files) {
        this.files = files;
    }

    /**
     * CN: 从plan完整bundle构造自包含请求包，并把bounded shard、可逆sidecar、压缩evidence及index写入
     * staging；成功无返回值，任何读取、摘要或owner校验失败都会中止且由外层run事务保留FAILED staging。
     * EN: Builds a self-contained request package from the plan's complete bundle and writes bounded shards,
     * reversible sidecars, compressed evidence, and the index into staging. It returns no value and aborts the outer
     * run transaction on any read, digest, or ownership validation failure.
     */
    void write(Path staging, SemanticPathRunPlan plan) {
        if (staging == null || plan == null) {
            throw new IllegalArgumentException("semantic request staging and path plan are required");
        }
        Path workspace = staging.resolve(".request-package-work");
        try {
            Files.createDirectories(workspace);
            Path packageDirectory = staging.resolve("request-bundle");
            Files.createDirectories(packageDirectory);
            Path ownerManifest = packageDirectory.resolve("owner-manifest.tsv");
            files.copyFile(
                    plan.ownerManifestPath(), ownerManifest,
                    "failed to persist semantic request owner manifest");
            Artifact ownerArtifact = artifact(staging, ownerManifest);
            require(ownerArtifact.sha256().equals(plan.ownerManifestHash()));
            try (Source source = readSource(
                    plan.fullBundlePath(),
                    packageDirectory.resolve("evidence-records.json.gz"),
                    workspace)) {
                ArrayNode shardEntries = JSON.createArrayNode();
                long overlapCount = 0;
                for (SemanticPathShard shard : plan.shards()) {
                    Path shardDirectory = staging.resolve("shards").resolve(shard.id());
                    Files.createDirectories(shardDirectory);
                    Path bundle = shardDirectory.resolve("evidence-bundle.json");
                    files.copyFile(
                            shard.bundlePath(), bundle,
                            "failed to persist semantic shard evidence bundle");
                    Path sidecar = shardDirectory.resolve("external-audit-refs.tsv");
                    ObjectNode shardBundle = readObject(bundle, "semantic shard bundle");
                    writeProjectionSidecar(
                            sidecar,
                            shardBundle,
                            source.records(),
                            SemanticExternalAuditReferences.read(
                                    SemanticExternalAuditReferences.sidecar(shard.bundlePath())));
                    validateExternalReferenceSummary(shardBundle, sidecar);
                    overlapCount += shardBundle.path("shardContext").path("overlapRefs").size();
                    shardEntries.add(shardEntry(staging, shard, bundle, sidecar, shardBundle));
                }
                ObjectNode index = index(
                        staging, plan, source, shardEntries, overlapCount, ownerArtifact);
                files.writeJson(staging.resolve("request-bundle-index.json"), index);
            }
        } catch (IOException failure) {
            throw new SemanticExtractionValidationException(
                    "semantic request bundle package cannot be written");
        } finally {
            SemanticFileTreeOperations.deleteRecursivelyBestEffort(workspace);
        }
    }

    /**
     * CN: 单遍读取完整bundle，将evidence流式压缩并把其余有ID section写入外排record store；返回小型descriptor、
     * section摘要和只读store句柄。输入非法或I/O失败时关闭全部store，不留下可发布的source状态。
     * EN: Reads the complete bundle once, streaming evidence into a compressed archive and id-bearing sections into
     * external record stores. It returns a compact descriptor, section digests, and read-only store handles, closing
     * every store on invalid input or I/O failure so no publishable source state remains.
     */
    private Source readSource(Path fullBundle, Path evidenceArchive, Path workspace) throws IOException {
        Map<SemanticEvidenceStore.Section, ExternalJsonRecordStore> records = new EnumMap<>(
                SemanticEvidenceStore.Section.class);
        Map<String, SemanticRequestBundleCanonicalDigest.Accumulator> digests = new LinkedHashMap<>();
        for (SemanticEvidenceStore.Section section : SemanticEvidenceStore.Section.values()) {
            digests.put(section.wireName(), SemanticRequestBundleCanonicalDigest.accumulator());
            if (section != SemanticEvidenceStore.Section.TABLES
                    && section != SemanticEvidenceStore.Section.EVIDENCE) {
                records.put(section, new ExternalJsonRecordStore(
                        workspace.resolve("records").resolve(section.wireName())));
            }
        }
        ObjectNode descriptor = JSON.createObjectNode();
        try (OutputStream compressed = new GZIPOutputStream(Files.newOutputStream(evidenceArchive));
             JsonGenerator evidence = JSON.getFactory().createGenerator(compressed);
             JsonParser parser = JSON.getFactory().createParser(fullBundle.toFile())) {
            evidence.writeStartArray();
            require(parser.nextToken() == JsonToken.START_OBJECT);
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String field = parser.currentName();
                parser.nextToken();
                SemanticEvidenceStore.Section section = section(field);
                if (section == null) {
                    if (!List.of(
                            "database", "metadataInventory", "inputFiles", "sources", "instructions")
                            .contains(field)) {
                        throw invalidPackage();
                    }
                    descriptor.set(field, JSON.readTree(parser));
                    continue;
                }
                require(parser.currentToken() == JsonToken.START_ARRAY);
                ArrayNode tables = section == SemanticEvidenceStore.Section.TABLES
                        ? descriptor.putArray("tables") : null;
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    JsonNode item = JSON.readTree(parser);
                    digests.get(field).add(item);
                    if (section == SemanticEvidenceStore.Section.TABLES) {
                        tables.add(item);
                    } else if (section == SemanticEvidenceStore.Section.EVIDENCE) {
                        evidence.writeTree(item);
                    } else {
                        String id = item.path("id").asText("");
                        if (id.isBlank()) {
                            throw invalidPackage();
                        }
                        records.get(section).append(id, item);
                    }
                }
            }
            evidence.writeEndArray();
        } catch (RuntimeException | IOException failure) {
            closeRecords(records, failure);
            throw failure;
        }
        requireDescriptor(descriptor);
        records.values().forEach(ExternalJsonRecordStore::finish);
        Map<String, SemanticRequestBundleCanonicalDigest.SectionDigest> sections = new LinkedHashMap<>();
        digests.forEach((name, digest) -> sections.put(name, digest.finish()));
        String canonical = SemanticRequestBundleCanonicalDigest.bundleSha256(descriptor, sections);
        return new Source(
                descriptor,
                sections,
                canonical,
                evidenceArchive,
                records);
    }

    private void writeProjectionSidecar(
            Path sidecar,
            ObjectNode shardBundle,
            Map<SemanticEvidenceStore.Section, ExternalJsonRecordStore> records,
            java.util.Collection<String> externalReferences
    ) {
        try (SemanticExternalAuditReferences.ProjectionWriter writer =
                     SemanticExternalAuditReferences.projectionWriter(sidecar, externalReferences)) {
            for (Map.Entry<String, SemanticEvidenceStore.Section> entry : ITEM_SECTIONS.entrySet()) {
                for (JsonNode projected : shardBundle.path(entry.getKey())) {
                    String id = projected.path("id").asText("");
                    JsonNode original = records.get(entry.getValue()).get(id).orElseThrow(
                            SemanticRequestBundlePackageWriter::invalidPackage).value();
                    if (!StableSemanticId.canonicalJson(projected).equals(
                            StableSemanticId.canonicalJson(
                                    SemanticExternalAuditReferences.project(original)))) {
                        throw invalidPackage();
                    }
                    writer.append(original);
                }
            }
        }
    }

    private void validateExternalReferenceSummary(ObjectNode shardBundle, Path sidecar) {
        JsonNode context = shardBundle.path("shardContext");
        SemanticExternalAuditReferences.Snapshot actual =
                SemanticExternalAuditReferences.snapshot(
                        SemanticExternalAuditReferences.read(sidecar));
        if (actual.count() != requiredNonNegativeInt(context, "externalAuditRefCount")
                || !actual.sha256().equals(context.path("externalAuditRefsSha256").asText(""))) {
            throw invalidPackage();
        }
    }

    private ObjectNode shardEntry(
            Path root,
            SemanticPathShard shard,
            Path bundle,
            Path sidecar,
            ObjectNode document
    ) {
        ObjectNode result = JSON.createObjectNode();
        result.put("id", shard.id());
        result.put("ownerKey", shard.ownerKey());
        result.put("estimatedInputTokens", shard.estimatedInputTokens());
        result.put("ownedFactCount", shard.ownedFactCount());
        result.put("ownedCandidateCount", shard.ownedCandidateCount());
        result.put("overlapCount", document.path("shardContext").path("overlapRefs").size());
        result.set("bundle", artifactNode(artifact(root, bundle)));
        result.set("sidecar", artifactNode(artifact(root, sidecar)));
        return result;
    }

    private ObjectNode index(
            Path staging,
            SemanticPathRunPlan plan,
            Source source,
            ArrayNode shards,
            long overlapCount,
            Artifact ownerManifest
    ) {
        ObjectNode result = JSON.createObjectNode();
        result.put("artifactSchemaVersion", 1);
        result.put("fullBundleCanonicalSha256", source.canonicalSha256());
        result.put("sourceBundleSha256", plan.fullBundleHash());
        result.put("reconcile", plan.reconcile());
        result.put("maxInputTokens", plan.maxInputTokens());
        result.set("ownerManifest", artifactNode(ownerManifest));
        result.set("descriptor", source.descriptor());
        ObjectNode sections = result.putObject("sections");
        source.sections().forEach((name, digest) -> sections.putObject(name)
                .put("count", digest.count())
                .put("sha256", digest.sha256()));
        result.set("evidenceArchive", artifactNode(artifact(staging, source.evidenceArchive())));
        result.set("shards", shards);
        result.putObject("coverage")
                .put("ownedFactCount", plan.ownedFactCount())
                .put("ownedCandidateCount", plan.ownedCandidateCount())
                .put("overlapCount", overlapCount);
        ArrayNode inputs = result.putArray("inputScans");
        for (JsonNode input : source.descriptor().path("inputFiles")) {
            ObjectNode item = inputs.addObject().put("path", input.asText());
            Path path = Path.of(input.asText()).toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) {
                Artifact artifact = artifact(path.getParent(), path);
                item.put("bytes", artifact.bytes()).put("sha256", artifact.sha256());
            }
        }
        return result;
    }

    private Artifact artifact(Path root, Path file) {
        try {
            return new Artifact(
                    root.relativize(file).toString().replace('\\', '/'),
                    Files.size(file),
                    sha256(file));
        } catch (IOException failure) {
            throw invalidPackage();
        }
    }

    private ObjectNode artifactNode(Artifact artifact) {
        return JSON.createObjectNode()
                .put("path", artifact.path())
                .put("bytes", artifact.bytes())
                .put("sha256", artifact.sha256());
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

    private void requireDescriptor(ObjectNode descriptor) {
        for (String field : List.of(
                "database", "metadataInventory", "inputFiles", "sources", "tables", "instructions")) {
            require(descriptor.has(field));
        }
    }

    private SemanticEvidenceStore.Section section(String wireName) {
        for (SemanticEvidenceStore.Section section : SemanticEvidenceStore.Section.values()) {
            if (section.wireName().equals(wireName)) {
                return section;
            }
        }
        return null;
    }

    private void require(boolean valid) {
        if (!valid) {
            throw invalidPackage();
        }
    }

    private int requiredNonNegativeInt(JsonNode object, String field) {
        JsonNode value = object.path(field);
        require(value.isIntegralNumber() && value.canConvertToInt() && value.intValue() >= 0);
        return value.intValue();
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
            throw invalidPackage();
        }
    }

    private void closeRecords(
            Map<SemanticEvidenceStore.Section, ExternalJsonRecordStore> records,
            Exception failure
    ) {
        records.values().forEach(store -> {
            try {
                store.close();
            } catch (RuntimeException cleanup) {
                failure.addSuppressed(cleanup);
            }
        });
    }

    private static Map<String, SemanticEvidenceStore.Section> itemSections() {
        Map<String, SemanticEvidenceStore.Section> result = new LinkedHashMap<>();
        for (SemanticEvidenceStore.Section section : SemanticEvidenceStore.Section.values()) {
            if (section != SemanticEvidenceStore.Section.TABLES
                    && section != SemanticEvidenceStore.Section.EVIDENCE) {
                result.put(section.wireName(), section);
            }
        }
        return Map.copyOf(result);
    }

    private static SemanticExtractionValidationException invalidPackage() {
        return new SemanticExtractionValidationException(
                "semantic request bundle package is invalid");
    }

    private record Artifact(String path, long bytes, String sha256) {
    }

    private record Source(
            ObjectNode descriptor,
            Map<String, SemanticRequestBundleCanonicalDigest.SectionDigest> sections,
            String canonicalSha256,
            Path evidenceArchive,
            Map<SemanticEvidenceStore.Section, ExternalJsonRecordStore> records
    ) implements AutoCloseable {
        private Source {
            descriptor = descriptor.deepCopy();
            sections = SemanticRequestBundleCanonicalDigest.immutable(sections);
            records = Map.copyOf(records);
        }

        @Override
        public void close() {
            RuntimeException failure = null;
            for (ExternalJsonRecordStore store : records.values()) {
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
    }
}
