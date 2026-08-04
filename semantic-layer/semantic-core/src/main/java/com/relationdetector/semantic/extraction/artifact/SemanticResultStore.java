package com.relationdetector.semantic.extraction.artifact;

import com.relationdetector.semantic.extraction.shard.SemanticShardDescriptor;

import com.relationdetector.semantic.extraction.shard.SemanticRunPlan;

import com.relationdetector.semantic.extraction.artifact.SemanticResultSelection;

import com.relationdetector.semantic.extraction.artifact.SemanticOwnerManifestValidator;

import com.relationdetector.semantic.extraction.normalization.SemanticShardNormalizedResult;

import com.relationdetector.semantic.extraction.normalization.SemanticShardIdentityCanonicalizer;

import com.relationdetector.semantic.extraction.normalization.SemanticExtractionValidationException;

import com.relationdetector.semantic.extraction.normalization.SemanticEvidenceLookup;

import com.relationdetector.semantic.extraction.normalization.SemanticEntityMergePolicy;

import com.relationdetector.semantic.extraction.prompt.SemanticExtractionPrompt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
import com.relationdetector.semantic.internal.store.ExternalJsonRecordStore;
import com.relationdetector.semantic.ingest.ScanResultContractException;
import com.relationdetector.semantic.evidence.SemanticEvidenceStore;

/**
 * CN: 逐片接收已归一化semantic文档，以外排ID存储完成canonical merge、full-bundle evidence closure和
 * graph重建；输入一次仅保留一个shard，输出流式final document，禁止保留全部模型结果。
 * EN: Accepts one normalized shard at a time and uses external ID stores for canonical merge, full-bundle evidence
 * closure, and graph reconstruction. It streams the final document without retaining all model results.
 */
public final class SemanticResultStore implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Path workspace;
    private final SemanticRunPlan runPlan;
    private final SemanticOwnerManifestValidator ownerManifestValidator;
    private final Map<Section, ExternalJsonRecordStore> sections = new EnumMap<>(Section.class);
    private final SemanticResultSelection selection;
    private final SemanticResultValidator validator;
    private final SemanticResultDocumentWriter documentWriter;
    private boolean finished;
    private boolean validated;
    private boolean closed;

    public SemanticResultStore(
            Path workspace,
            SemanticEvidenceStore evidenceStore,
            SemanticRunPlan runPlan
    ) {
        this(workspace, SemanticEvidenceLookup.from(evidenceStore), runPlan);
    }

    public SemanticResultStore(
            Path workspace,
            SemanticEvidenceLookup evidenceLookup,
            SemanticRunPlan runPlan
    ) {
        if (workspace == null || evidenceLookup == null || runPlan == null) {
            throw new IllegalArgumentException(
                    "semantic result workspace, evidence store, and owner plan are required");
        }
        this.workspace = workspace;
        this.runPlan = runPlan;
        if (Files.exists(workspace)) {
            throw new SemanticExtractionValidationException(
                    "semantic normalized result workspace already exists");
        }
        this.ownerManifestValidator = new SemanticOwnerManifestValidator(runPlan, evidenceLookup);
        try {
            Files.createDirectories(workspace);
            for (Section section : Section.values()) {
                sections.put(section, section == Section.ENTITIES
                        ? new ExternalJsonRecordStore(
                                workspace.resolve(section.wireName), this::mergeCanonicalEntity)
                        : new ExternalJsonRecordStore(
                                workspace.resolve(section.wireName), this::mergeVariants));
            }
        } catch (IOException failure) {
            closeAfterConstructionFailure(failure);
            throw new ScanResultContractException("failed to create semantic normalized result store", failure);
        } catch (RuntimeException failure) {
            closeAfterConstructionFailure(failure);
            throw failure;
        }
        this.selection = new SemanticResultSelection(sections);
        this.validator = new SemanticResultValidator(
                evidenceLookup, sections, selection);
        this.documentWriter = new SemanticResultDocumentWriter(
                workspace, sections, selection);
    }

    private void closeAfterConstructionFailure(Exception primary) {
        try {
            ownerManifestValidator.close();
        } catch (RuntimeException cleanup) {
            primary.addSuppressed(cleanup);
        }
        for (ExternalJsonRecordStore store : sections.values()) {
            try {
                store.close();
            } catch (RuntimeException cleanup) {
                primary.addSuppressed(cleanup);
            }
        }
        SemanticFileTreeOperations.deleteRecursivelyBestEffort(workspace);
    }

    public void append(
            SemanticShardDescriptor descriptor,
            ObjectNode shardBundle,
        ObjectNode normalized
    ) {
        ensureWritable();
        ownerManifestValidator.validate(descriptor, shardBundle);
        ObjectNode canonical = canonicalize(descriptor, shardBundle, normalized);
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

    public void finish() {
        ensureOpen();
        if (finished) {
            return;
        }
        sections.values().forEach(ExternalJsonRecordStore::finish);
        finished = true;
    }

    public SemanticExtractionPrompt reconciliationPrompt(
            SemanticRunPlan plan,
            int maxInputTokens
    ) {
        finish();
        return selection.reconciliationPrompt(plan, maxInputTokens);
    }

    public void applyReconciliationPatch(JsonNode patch) {
        finish();
        selection.applyPatch(patch);
    }

    public void requireConflictFree() {
        finish();
        selection.requireConflictFree();
    }

    public void writeMergedDraft(Path target) {
        validateFinalState();
        documentWriter.write(target, false);
    }

    public void writeFinalDocument(Path target) {
        validateFinalState();
        documentWriter.write(target, true);
    }

    private ObjectNode canonicalize(
            SemanticShardDescriptor descriptor,
            ObjectNode bundle,
            ObjectNode normalized
    ) {
        JsonNode context = bundle.path("shardContext");
        Set<String> ownedFacts = textSet(context.path("ownedFactRefs"));
        Set<String> ownedCandidates = textSet(context.path("ownedCandidateRefs"));
        Set<String> ownedReferences = new LinkedHashSet<>(ownedFacts);
        ownedReferences.addAll(ownedCandidates);
        SemanticShardIdentityCanonicalizer.CanonicalizedShardResults result =
                new SemanticShardIdentityCanonicalizer().canonicalize(
                        List.of(new SemanticShardNormalizedResult(descriptor.id(), normalized)),
                        Map.of(descriptor.id(), Set.copyOf(ownedReferences)));
        ObjectNode document = result.results().get(0).document();
        ArrayNode reviews = document.withArray(Section.REVIEW_ITEMS.wireName);
        result.generatedReviews().forEach(review -> reviews.add(review.deepCopy()));
        document.remove("semanticGraph");
        document.remove("validation");
        return document;
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
        List<ObjectNode> documents = new ArrayList<>();
        appendEntityDocuments(documents, leftValue);
        appendEntityDocuments(documents, rightValue);
        if (SemanticEntityMergePolicy.canMerge(documents)) {
            return SemanticEntityMergePolicy.merge(documents);
        }
        List<ObjectNode> variants = SemanticEntityMergePolicy.reconciliationVariants(documents);
        ObjectNode result = JSON.createObjectNode();
        result.put("id", variants.get(0).path("id").asText(""));
        ArrayNode output = result.putArray("__semanticVariants");
        variants.forEach(document -> output.addObject()
                .put("hash", StableSemanticId.of(
                        "semantic-shard-variant", StableSemanticId.canonicalJson(document)))
                .set("document", document.deepCopy()));
        return result;
    }

    private void appendEntityDocuments(List<ObjectNode> target, JsonNode value) {
        JsonNode variants = value.path("__semanticVariants");
        if (variants.isArray()) {
            variants.forEach(variant -> target.add(requireObject(variant.path("document")).deepCopy()));
        } else {
            target.add(requireObject(value).deepCopy());
        }
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
        try {
            ownerManifestValidator.close();
        } catch (RuntimeException error) {
            failure = error;
        }
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

    public enum Section {
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

        public String wireName() {
            return wireName;
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
