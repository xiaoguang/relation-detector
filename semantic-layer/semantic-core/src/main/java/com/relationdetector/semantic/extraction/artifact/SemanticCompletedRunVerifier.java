package com.relationdetector.semantic.extraction.artifact;

import com.relationdetector.semantic.extraction.normalization.SemanticBoundedJsonReader;
import com.relationdetector.semantic.extraction.normalization.SemanticExtractionValidationException;
import com.relationdetector.semantic.internal.io.SemanticFileDigest;
import com.relationdetector.semantic.internal.io.SemanticFileTreeOperations;
import com.relationdetector.semantic.internal.store.ExternalJsonRecordStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * CN: 独立验证已发布 Codex completion run 的状态、模型配置、完整 artifact inventory、摘要和最终引用闭包。
 * EN: Independently verifies a published Codex completion run, its complete artifact inventory, and final closure.
 */
public final class SemanticCompletedRunVerifier {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long MAX_MANIFEST_BYTES = 16L * 1024L * 1024L;
    private static final int MAX_MANIFEST_TOKENS = 4_000_000;

    public void verify(Path runDirectory, String expectedModel, String expectedReasoningEffort) {
        if (runDirectory == null || expectedModel == null || expectedModel.isBlank()
                || expectedReasoningEffort == null || expectedReasoningEffort.isBlank()) {
            throw invalid();
        }
        Path run = runDirectory.toAbsolutePath().normalize();
        try {
            if (!Files.isDirectory(run, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(run)) {
                throw invalid();
            }
            ObjectNode manifest = new SemanticBoundedJsonReader().readObject(
                    run.resolve("run-manifest.json"),
                    new SemanticBoundedJsonReader.Limits(MAX_MANIFEST_BYTES, MAX_MANIFEST_TOKENS),
                    "semantic completed run manifest");
            validateManifest(manifest, expectedModel, expectedReasoningEffort);
            validateInventory(run, manifest.path("artifacts"));
            validateFinalDocument(run.resolve("semantic-extraction-result.json"));
        } catch (SemanticExtractionValidationException failure) {
            throw failure;
        } catch (IOException | RuntimeException failure) {
            throw invalid();
        }
    }

    private void validateManifest(ObjectNode manifest, String model, String reasoningEffort) {
        require(manifest.path("schemaVersion").isInt() && manifest.path("schemaVersion").asInt() == 1);
        require("COMPLETE".equals(manifest.path("status").asText("")));
        require("codex-session".equals(manifest.path("provider").asText("")));
        require(model.equals(manifest.path("model").asText("")));
        require(reasoningEffort.equals(manifest.path("reasoningEffort").asText("")));
        require(manifest.path("finalRefClosed").asBoolean(false));
        require(manifest.path("shardCount").canConvertToInt() && manifest.path("shardCount").asInt() > 0);
        JsonNode shards = manifest.path("shards");
        require(shards.isArray() && shards.size() == manifest.path("shardCount").asInt());
        Set<String> shardIds = new HashSet<>();
        shards.forEach(shard -> require(shard.isObject()
                && !shard.path("id").asText("").isBlank()
                && shardIds.add(shard.path("id").asText())
                && "COMPLETE".equals(shard.path("status").asText(""))));
        JsonNode reconciliation = manifest.path("reconciliation");
        require(reconciliation.isObject());
        require(reconciliation.path("required").asBoolean(false)
                ? "COMPLETE".equals(reconciliation.path("status").asText(""))
                : "NOT_REQUIRED".equals(reconciliation.path("status").asText("")));
        try {
            Instant.parse(manifest.path("publishedAt").asText(""));
        } catch (DateTimeParseException failure) {
            throw invalid();
        }
        require(manifest.path("artifacts").isArray());
    }

    private void validateInventory(Path run, JsonNode artifacts) throws IOException {
        Set<String> actual = new LinkedHashSet<>();
        try (var paths = Files.walk(run)) {
            for (Path path : paths.toList()) {
                if (path.equals(run)) continue;
                if (Files.isSymbolicLink(path)) throw invalid();
                if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    String relative = run.relativize(path).toString().replace(java.io.File.separatorChar, '/');
                    if (!"run-manifest.json".equals(relative)) actual.add(relative);
                } else if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw invalid();
                }
            }
        }
        Set<String> declared = new LinkedHashSet<>();
        for (JsonNode artifact : artifacts) {
            String relative = artifact.path("path").asText("");
            long size = artifact.path("size").asLong(-1);
            String sha256 = artifact.path("sha256").asText("");
            Path path = run.resolve(relative).normalize();
            require(!relative.isBlank() && !Path.of(relative).isAbsolute()
                    && path.startsWith(run) && declared.add(relative)
                    && size >= 0 && sha256.matches("[0-9a-f]{64}")
                    && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isSymbolicLink(path));
            SemanticFileDigest.Digest digest = SemanticFileDigest.compute(path);
            require(digest.bytes() == size && digest.sha256().equals(sha256));
        }
        require(declared.equals(actual));
        require(declared.contains("semantic-extraction-result.json"));
    }

    private void validateFinalDocument(Path result) {
        Path scratch = null;
        try {
            scratch = Files.createTempDirectory("semantic-completed-run-verifier-");
            MapStores stores = new MapStores(scratch);
            try (stores) {
                SemanticBoundedJsonReader bounded = new SemanticBoundedJsonReader();
                Set<String> fields = new HashSet<>();
                boolean[] validationSeen = {false};
                bounded.streamObjectFields(
                        result,
                        new SemanticBoundedJsonReader.Limits(
                                SemanticRequestPackageLimits.defaults().maxReconstructedBytes(),
                                Integer.MAX_VALUE),
                        "semantic completed result",
                        (field, parser) -> {
                            require(fields.add(field));
                            SemanticResultStore.Section section = SemanticResultStore.Section.fromWire(field);
                            if (section != null) {
                                streamSection(section, parser, bounded, stores);
                            } else if ("validation".equals(field)) {
                                require(!validationSeen[0]);
                                validationSeen[0] = true;
                                validateEmbeddedClosure(readValue(parser, bounded));
                            } else {
                                parser.skipChildren();
                            }
                        });
                require(validationSeen[0]);
                for (SemanticResultStore.Section section : SemanticResultStore.Section.values()) {
                    require(fields.contains(section.wireName));
                }
                stores.finishAndValidate();
            }
        } catch (SemanticExtractionValidationException failure) {
            throw failure;
        } catch (IOException | RuntimeException failure) {
            throw invalid();
        } finally {
            if (scratch != null) {
                SemanticFileTreeOperations.deleteRecursivelyBestEffort(scratch);
            }
        }
    }

    private void streamSection(
            SemanticResultStore.Section section,
            JsonParser parser,
            SemanticBoundedJsonReader bounded,
            MapStores stores
    ) throws IOException {
        require(parser.currentToken() == JsonToken.START_ARRAY);
        long ordinal = 0;
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            JsonNode item = readValue(parser, bounded);
            require(item.isObject());
            String id = item.path("id").asText("");
            require(!id.isBlank());
            ObjectNode marker = JSON.createObjectNode();
            marker.put("ordinal", ordinal++);
            stores.ids(section).append(id, marker);
            for (String field : SemanticResultValidator.ENTITY_REF_FIELDS) {
                appendReferences(item.path(field), stores.entityReferences());
            }
            if (section == SemanticResultStore.Section.REVIEW_ITEMS) {
                String targetSection = item.path("targetSection").asText("");
                String targetRef = item.path("targetRef").asText("");
                require(!targetSection.isBlank() && !targetRef.isBlank());
                ObjectNode target = JSON.createObjectNode();
                target.put("section", targetSection);
                target.put("ref", targetRef);
                stores.reviewTargets().append(id, target);
            }
        }
    }

    private JsonNode readValue(JsonParser parser, SemanticBoundedJsonReader bounded) throws IOException {
        JsonNode value = parser.readValueAsTree();
        require(value != null);
        bounded.validateStringLimits(value, "semantic completed result");
        return value;
    }

    private void appendReferences(JsonNode value, ExternalJsonRecordStore references) {
        if (value.isMissingNode() || value.isNull() || value.isTextual() && value.asText().isBlank()) return;
        if (value.isTextual()) {
            references.append(value.asText(), JSON.getNodeFactory().textNode(value.asText()));
            return;
        }
        require(value.isArray());
        value.forEach(reference -> {
            require(reference.isTextual() && !reference.asText().isBlank());
            references.append(reference.asText(), JSON.getNodeFactory().textNode(reference.asText()));
        });
    }

    private void validateEmbeddedClosure(JsonNode validation) {
        require(validation.isObject()
                && validation.path("isRefClosed").asBoolean(false)
                && validation.path("unresolvedReferences").isArray()
                && validation.path("unresolvedReferences").isEmpty()
                && validation.path("missingEvidenceRefs").isArray()
                && validation.path("missingEvidenceRefs").isEmpty());
    }

    private final class MapStores implements AutoCloseable {
        private final java.util.EnumMap<SemanticResultStore.Section, ExternalJsonRecordStore> ids =
                new java.util.EnumMap<>(SemanticResultStore.Section.class);
        private final ExternalJsonRecordStore entityReferences;
        private final ExternalJsonRecordStore reviewTargets;

        private MapStores(Path scratch) {
            for (SemanticResultStore.Section section : SemanticResultStore.Section.values()) {
                ids.put(section, new ExternalJsonRecordStore(scratch.resolve(section.wireName)));
            }
            entityReferences = new ExternalJsonRecordStore(scratch.resolve("entity-references"));
            reviewTargets = new ExternalJsonRecordStore(scratch.resolve("review-targets"));
        }

        private ExternalJsonRecordStore ids(SemanticResultStore.Section section) {
            return ids.get(section);
        }

        private ExternalJsonRecordStore entityReferences() {
            return entityReferences;
        }

        private ExternalJsonRecordStore reviewTargets() {
            return reviewTargets;
        }

        private void finishAndValidate() {
            ids.values().forEach(ExternalJsonRecordStore::finish);
            entityReferences.finish();
            reviewTargets.finish();
            entityReferences.forEach(record -> require(
                    ids.get(SemanticResultStore.Section.ENTITIES).containsKey(record.key())));
            reviewTargets.forEach(record -> {
                SemanticResultStore.Section section = SemanticResultStore.Section.fromWire(
                        record.value().path("section").asText(""));
                require(section != null && ids.get(section).containsKey(
                        record.value().path("ref").asText("")));
            });
        }

        @Override
        public void close() {
            RuntimeException failure = null;
            for (ExternalJsonRecordStore store : ids.values()) {
                try {
                    store.close();
                } catch (RuntimeException error) {
                    if (failure == null) failure = error;
                    else failure.addSuppressed(error);
                }
            }
            for (ExternalJsonRecordStore store : java.util.List.of(entityReferences, reviewTargets)) {
                try {
                    store.close();
                } catch (RuntimeException error) {
                    if (failure == null) failure = error;
                    else failure.addSuppressed(error);
                }
            }
            if (failure != null) throw failure;
        }
    }

    private void require(boolean condition) {
        if (!condition) throw invalid();
    }

    private SemanticExtractionValidationException invalid() {
        return new SemanticExtractionValidationException("semantic completed run is invalid");
    }
}
