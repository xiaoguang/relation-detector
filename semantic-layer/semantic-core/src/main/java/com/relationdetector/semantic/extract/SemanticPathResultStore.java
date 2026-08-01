package com.relationdetector.semantic.extract;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.StableSemanticId;
import com.relationdetector.semantic.internal.io.SemanticFileTreeOperations;
import com.relationdetector.semantic.reader.ExternalJsonRecordStore;
import com.relationdetector.semantic.reader.ScanResultContractException;
import com.relationdetector.semantic.reader.SemanticEvidenceStore;

/**
 * CN: 逐片接收已归一化semantic文档，以外排ID存储完成canonical merge、full-bundle evidence closure和
 * graph重建；输入一次仅保留一个shard，输出流式final document，禁止保留全部模型结果。
 * EN: Accepts one normalized shard at a time and uses external ID stores for canonical merge, full-bundle evidence
 * closure, and graph reconstruction. It streams the final document without retaining all model results.
 */
final class SemanticPathResultStore implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Path workspace;
    private final SemanticPathRunPlan runPlan;
    private final SemanticOwnerManifestValidator ownerManifestValidator;
    private final Map<Section, ExternalJsonRecordStore> sections = new EnumMap<>(Section.class);
    private final SemanticPathResultSelection selection;
    private final SemanticPathResultValidator validator;
    private final SemanticPathResultDocumentWriter documentWriter;
    private boolean finished;
    private boolean validated;
    private boolean closed;

    SemanticPathResultStore(
            Path workspace,
            SemanticEvidenceStore evidenceStore,
            SemanticPathRunPlan runPlan
    ) {
        if (workspace == null || evidenceStore == null || runPlan == null) {
            throw new IllegalArgumentException(
                    "semantic result workspace, evidence store, and owner plan are required");
        }
        this.workspace = workspace;
        this.runPlan = runPlan;
        this.ownerManifestValidator = new SemanticOwnerManifestValidator(runPlan, evidenceStore);
        try {
            if (Files.exists(workspace)) {
                throw new SemanticExtractionValidationException(
                        "semantic normalized result workspace already exists");
            }
            Files.createDirectories(workspace);
            for (Section section : Section.values()) {
                sections.put(section, section == Section.ENTITIES
                        ? new ExternalJsonRecordStore(
                                workspace.resolve(section.wireName), this::mergeCanonicalEntity)
                        : new ExternalJsonRecordStore(
                                workspace.resolve(section.wireName), this::mergeVariants));
            }
        } catch (IOException failure) {
            throw new ScanResultContractException("failed to create semantic normalized result store", failure);
        }
        this.selection = new SemanticPathResultSelection(sections);
        this.validator = new SemanticPathResultValidator(
                workspace, evidenceStore, sections, selection);
        this.documentWriter = new SemanticPathResultDocumentWriter(
                workspace, sections, selection);
    }

    void append(
            SemanticPathShard descriptor,
            ObjectNode shardBundle,
        ObjectNode normalized
    ) {
        ensureWritable();
        ownerManifestValidator.validate(descriptor, shardBundle);
        ObjectNode canonical = canonicalize(
                descriptor, shardBundle, normalized, runPlan.fullBundleHash());
        for (Section section : Section.values()) {
            JsonNode values = canonical.path(section.wireName);
            if (!values.isArray()) {
                throw new SemanticExtractionValidationException(
                        "normalized semantic section must be an array: " + section.wireName);
            }
            for (JsonNode value : values) {
                String id = value.path("id").asText("");
                if (id.isBlank()) {
                    throw new SemanticExtractionValidationException(
                            "normalized semantic item is missing id in " + section.wireName);
                }
                validator.requireEvidence(section, id, value.path("evidenceRefs"));
                sections.get(section).append(id, value);
            }
        }
    }

    void finish() {
        ensureOpen();
        if (finished) {
            return;
        }
        sections.values().forEach(ExternalJsonRecordStore::finish);
        finished = true;
    }

    SemanticExtractionPrompt reconciliationPrompt(
            SemanticPathRunPlan plan,
            int maxInputTokens
    ) {
        finish();
        return selection.reconciliationPrompt(plan, maxInputTokens);
    }

    void applyReconciliationPatch(JsonNode patch) {
        finish();
        selection.applyPatch(patch);
    }

    void requireConflictFree() {
        finish();
        selection.requireConflictFree();
    }

    void writeMergedDraft(Path target) {
        validateFinalState();
        documentWriter.write(target, false);
    }

    void writeFinalDocument(Path target) {
        validateFinalState();
        documentWriter.write(target, true);
    }

    private ObjectNode canonicalize(
            SemanticPathShard descriptor,
            ObjectNode bundle,
            ObjectNode normalized,
            String fullBundleHash
    ) {
        JsonNode context = bundle.path("shardContext");
        Set<String> ownedFacts = textSet(context.path("ownedFactRefs"));
        Set<String> ownedCandidates = textSet(context.path("ownedCandidateRefs"));
        Set<String> overlap = textSet(context.path("overlapRefs"));
        SemanticShard shard = new SemanticShard(
                descriptor.id(),
                descriptor.ownerKey(),
                bundle,
                ownedFacts,
                ownedCandidates,
                overlap,
                descriptor.estimatedInputTokens());
        Map<String, String> factOwners = owners(ownedFacts, descriptor.id());
        Map<String, String> candidateOwners = owners(ownedCandidates, descriptor.id());
        SemanticShardPlan plan = new SemanticShardPlan(
                fullBundleHash, List.of(shard), factOwners, candidateOwners);
        SemanticShardIdentityCanonicalizer.CanonicalizedShardResults result =
                new SemanticShardIdentityCanonicalizer().canonicalize(
                        List.of(new SemanticShardNormalizedResult(descriptor.id(), normalized)), plan);
        ObjectNode document = result.results().get(0).document();
        ArrayNode reviews = document.withArray(Section.REVIEW_ITEMS.wireName);
        result.generatedReviews().forEach(review -> reviews.add(review.deepCopy()));
        document.remove("semanticGraph");
        document.remove("validation");
        return document;
    }

    private Map<String, String> owners(Set<String> values, String shardId) {
        Map<String, String> result = new LinkedHashMap<>();
        values.forEach(value -> result.put(value, shardId));
        return result;
    }

    private Set<String> textSet(JsonNode values) {
        Set<String> result = new LinkedHashSet<>();
        if (!values.isArray()) {
            throw new SemanticExtractionValidationException("semantic shard ownership set must be an array");
        }
        values.forEach(value -> {
            if (!value.isTextual() || value.asText().isBlank() || !result.add(value.asText())) {
                throw new SemanticExtractionValidationException(
                        "semantic shard ownership set contains an invalid reference");
            }
        });
        return Set.copyOf(result);
    }

    private ObjectNode mergeCanonicalEntity(JsonNode leftValue, JsonNode rightValue) {
        ObjectNode left = requireObject(leftValue);
        ObjectNode right = requireObject(rightValue);
        requireSameIfPresent(left, right, "physicalName");
        requireSameIfPresent(left, right, "machineType");
        requireSameIfPresent(left, right, "type");
        ObjectNode result = StableSemanticId.canonicalJson(left).compareTo(
                StableSemanticId.canonicalJson(right)) <= 0 ? left.deepCopy() : right.deepCopy();
        mergeReferences(result, left, right, "ownedGroundingRefs");
        mergeReferences(result, left, right, "evidenceRefs");
        return result;
    }

    private JsonNode mergeVariants(JsonNode leftValue, JsonNode rightValue) {
        ObjectNode result = JSON.createObjectNode();
        String id = firstNonBlank(
                leftValue.path("id").asText(""),
                rightValue.path("id").asText(""));
        result.put("id", id);
        Map<String, JsonNode> variants = new LinkedHashMap<>();
        appendVariants(variants, leftValue);
        appendVariants(variants, rightValue);
        ArrayNode output = result.putArray("__semanticVariants");
        variants.forEach((hash, document) -> output.addObject()
                .put("hash", hash)
                .set("document", document.deepCopy()));
        return result;
    }

    private void appendVariants(Map<String, JsonNode> target, JsonNode value) {
        JsonNode variants = value.path("__semanticVariants");
        if (variants.isArray()) {
            variants.forEach(variant -> {
                String hash = variant.path("hash").asText("");
                JsonNode document = variant.path("document");
                if (hash.isBlank() || !document.isObject()) {
                    throw new SemanticExtractionValidationException(
                            "semantic conflict variant is malformed");
                }
                target.putIfAbsent(hash, document.deepCopy());
            });
            return;
        }
        String hash = StableSemanticId.of(
                "semantic-shard-variant", StableSemanticId.canonicalJson(value));
        target.putIfAbsent(hash, value.deepCopy());
    }

    private void requireSameIfPresent(ObjectNode left, ObjectNode right, String field) {
        String first = left.path(field).asText("");
        String second = right.path(field).asText("");
        if (!first.isBlank() && !second.isBlank() && !first.equals(second)) {
            throw new SemanticExtractionValidationException(
                    "canonical semantic entity has conflicting " + field);
        }
    }

    private void mergeReferences(
            ObjectNode target,
            ObjectNode left,
            ObjectNode right,
            String field
    ) {
        Set<String> values = new LinkedHashSet<>();
        left.path(field).forEach(value -> addText(values, value));
        right.path(field).forEach(value -> addText(values, value));
        ArrayNode output = target.putArray(field);
        values.stream().sorted().forEach(output::add);
    }

    private void validateFinalState() {
        finish();
        selection.requireResolved();
        if (validated) {
            return;
        }
        validator.validate();
        validated = true;
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private ObjectNode requireObject(JsonNode value) {
        if (value == null || !value.isObject()) {
            throw new SemanticExtractionValidationException("canonical semantic entity must be an object");
        }
        return (ObjectNode) value;
    }

    private void addText(Set<String> values, JsonNode value) {
        if (value.isTextual() && !value.asText().isBlank()) {
            values.add(value.asText());
        }
    }

    private void ensureWritable() {
        ensureOpen();
        if (finished) {
            throw new IllegalStateException("semantic result store is already finished");
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("semantic result store is closed");
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        RuntimeException failure = null;
        for (ExternalJsonRecordStore store : sections.values()) {
            try {
                store.close();
            } catch (RuntimeException error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
        }
        try {
            SemanticFileTreeOperations.deleteRecursively(workspace);
        } catch (IOException error) {
            if (failure == null) failure = new IllegalStateException("failed to clean semantic result store", error);
            else failure.addSuppressed(error);
        }
        if (failure != null) {
            throw failure;
        }
    }

    enum Section {
        ENTITIES("entities", "Entity"),
        EVENTS("events", "Event"),
        RELATIONS("relations", "Relation"),
        LINEAGE("lineage", "Lineage"),
        METRICS("metrics", "Metric"),
        DIMENSIONS("dimensions", "Dimension"),
        TRIPLETS("triplets", "Triplet"),
        REVIEW_ITEMS("reviewItems", "ReviewItem");

        final String wireName;
        final String graphKind;

        Section(String wireName, String graphKind) {
            this.wireName = wireName;
            this.graphKind = graphKind;
        }

        static Section fromWire(String value) {
            for (Section section : values()) {
                if (section.wireName.equals(value)) {
                    return section;
                }
            }
            return null;
        }
    }

}
