package com.relationdetector.semantic.extract;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.StableSemanticId;
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
    private static final List<String> ENTITY_REF_FIELDS = List.of(
            "inputEntityRefs", "outputEntityRefs", "fromEntityRef", "toEntityRef",
            "sourceEntityRefs", "targetEntityRef", "ownerEntityRef", "dimensionEntityRef",
            "subjectRef", "objectRef");
    private final Path workspace;
    private final SemanticEvidenceStore evidenceStore;
    private final Map<Section, ExternalJsonRecordStore> sections = new EnumMap<>(Section.class);
    private final Map<String, String> conflictSelections = new LinkedHashMap<>();
    private final Map<String, Rename> renames = new LinkedHashMap<>();
    private boolean finished;
    private boolean validated;
    private boolean closed;

    SemanticPathResultStore(Path workspace, SemanticEvidenceStore evidenceStore) {
        if (workspace == null || evidenceStore == null) {
            throw new IllegalArgumentException("semantic result workspace and evidence store are required");
        }
        this.workspace = workspace;
        this.evidenceStore = evidenceStore;
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
    }

    void append(
            SemanticPathShard descriptor,
            ObjectNode shardBundle,
            ObjectNode normalized,
            String fullBundleHash
    ) {
        ensureWritable();
        ObjectNode canonical = canonicalize(descriptor, shardBundle, normalized, fullBundleHash);
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
                requireEvidence(section, id, value.path("evidenceRefs"));
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
        ObjectNode bundle = JSON.createObjectNode();
        bundle.put("kind", "SEMANTIC_RECONCILIATION");
        bundle.put("fullBundleHash", plan.fullBundleHash());
        ArrayNode shards = bundle.putArray("shards");
        plan.shards().forEach(shard -> shards.addObject()
                .put("id", shard.id())
                .put("ownerKey", shard.ownerKey())
                .put("estimatedInputTokens", shard.estimatedInputTokens()));
        ObjectNode summary = bundle.putObject("semanticSummary");
        ArrayNode conflicts = bundle.putArray("conflicts");
        long[] approximateBytes = {0};
        for (Section section : Section.values()) {
            ArrayNode compact = summary.putArray(section.wireName);
            sections.get(section).forEach(record -> {
                JsonNode stored = record.value();
                JsonNode selected = selectedDocument(section, stored);
                ObjectNode item = compact.addObject();
                copy(selected, item, "id");
                copy(selected, item, "name");
                copy(selected, item, "type");
                copy(selected, item, "machineType");
                copy(selected, item, "physicalName");
                copy(selected, item, "fromEntityRef");
                copy(selected, item, "toEntityRef");
                item.set("evidenceRefs", selected.path("evidenceRefs").deepCopy());
                approximateBytes[0] += item.toString().length();
                if (stored.path("__semanticVariants").isArray()) {
                    ObjectNode conflict = conflicts.addObject();
                    conflict.put("section", section.wireName);
                    conflict.put("id", record.key());
                    conflict.set("variants", stored.path("__semanticVariants").deepCopy());
                    approximateBytes[0] += conflict.toString().length();
                }
                if (approximateBytes[0] > (long) maxInputTokens * 8L) {
                    throw new SemanticShardingException(
                            "semantic reconciliation prompt exceeds the configured estimated input-token limit");
                }
            });
        }
        bundle.putObject("instructions")
                .put("patchOnly", true)
                .put("newPhysicalFactsForbidden", true)
                .put("newEvidenceReferencesForbidden", true);
        SemanticExtractionPrompt prompt = new SemanticExtractionPrompt(
                reconciliationDeveloperPrompt(),
                "Reconcile this semantic shard summary and return the constrained patch:\n" + bundle,
                bundle);
        if (new SemanticPromptBudgetEstimator().estimate(prompt) > maxInputTokens) {
            throw new SemanticShardingException(
                    "semantic reconciliation prompt exceeds the configured estimated input-token limit");
        }
        return prompt;
    }

    void applyReconciliationPatch(JsonNode patch) {
        finish();
        if (patch == null || !patch.isObject()
                || !patch.path("resolutions").isArray()
                || !patch.path("renames").isArray()) {
            throw new SemanticExtractionValidationException(
                    "semantic reconciliation patch must contain resolutions and renames arrays");
        }
        patch.fieldNames().forEachRemaining(field -> {
            if (!"resolutions".equals(field) && !"renames".equals(field)) {
                throw new SemanticExtractionValidationException(
                        "semantic reconciliation patch contains an unsupported section");
            }
        });
        Set<String> expected = conflictKeys();
        for (JsonNode resolution : patch.path("resolutions")) {
            String section = resolution.path("section").asText("");
            String id = resolution.path("id").asText("");
            String hash = resolution.path("selectedVariantHash").asText("");
            String key = section + "\u0000" + id;
            if (!expected.contains(key) || hash.isBlank()
                    || conflictSelections.putIfAbsent(key, hash) != null
                    || !containsVariant(section, id, hash)) {
                throw new SemanticExtractionValidationException(
                        "semantic reconciliation resolution does not match one conflict");
            }
        }
        if (conflictSelections.size() != expected.size()) {
            throw new SemanticExtractionValidationException(
                    "semantic reconciliation did not resolve every shard conflict");
        }
        for (JsonNode rename : patch.path("renames")) {
            Section section = Section.fromWire(rename.path("section").asText(""));
            String id = rename.path("id").asText("");
            if (section == null || id.isBlank() || !sections.get(section).containsKey(id)) {
                throw new SemanticExtractionValidationException(
                        "semantic reconciliation rename target is invalid");
            }
            String name = rename.has("name") ? requiredText(rename, "name") : null;
            String description = rename.has("description") ? requiredText(rename, "description") : null;
            if (name == null && description == null) {
                throw new SemanticExtractionValidationException(
                        "semantic reconciliation rename requires display content");
            }
            String key = section.wireName + "\u0000" + id;
            if (renames.putIfAbsent(key, new Rename(name, description)) != null) {
                throw new SemanticExtractionValidationException(
                        "semantic reconciliation contains a duplicate rename");
            }
        }
    }

    void requireConflictFree() {
        finish();
        if (!conflictKeys().isEmpty()) {
            throw new SemanticExtractionValidationException(
                    "semantic shard results contain unresolved conflicts");
        }
    }

    void writeMergedDraft(Path target) {
        validateFinalState();
        writeDocument(target, false);
    }

    void writeFinalDocument(Path target) {
        validateFinalState();
        writeDocument(target, true);
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

    private void requireEvidence(Section section, String id, JsonNode evidenceRefs) {
        if (!evidenceRefs.isArray() || evidenceRefs.isEmpty()) {
            throw new SemanticExtractionValidationException(
                    "semantic item requires evidenceRefs: " + section.wireName + ":" + id);
        }
        for (JsonNode value : evidenceRefs) {
            String reference = value.isTextual() ? value.asText() : "";
            if (reference.isBlank() || !evidenceStore.containsReference(reference)) {
                throw new SemanticExtractionValidationException(
                        "semantic item contains unresolved evidence reference");
            }
        }
    }

    private void validateSemanticReferences() {
        for (Section section : Section.values()) {
            sections.get(section).forEach(record ->
                    validateReferences(section, selectedDocument(section, record.value())));
        }
    }

    private void validateReferences(Section section, JsonNode item) {
        for (String field : ENTITY_REF_FIELDS) {
            JsonNode value = item.path(field);
            if (value.isTextual() && !value.asText().isBlank()) {
                requireOwner(Section.ENTITIES, value.asText(), section, field);
            } else if (value.isArray()) {
                value.forEach(reference -> {
                    if (reference.isTextual() && !reference.asText().isBlank()) {
                        requireOwner(Section.ENTITIES, reference.asText(), section, field);
                    }
                });
            }
        }
        if (section == Section.REVIEW_ITEMS) {
            String targetSection = item.path("targetSection").asText("");
            String targetRef = item.path("targetRef").asText("");
            Section expected = Section.fromWire(targetSection);
            if (expected == null || targetRef.isBlank()) {
                throw new SemanticExtractionValidationException(
                        "semantic review target section and reference are required");
            }
            requireOwner(expected, targetRef, section, "targetRef");
        }
    }

    private void requireOwner(Section expected, String id, Section source, String field) {
        if (!sections.get(expected).containsKey(id)) {
            throw new SemanticExtractionValidationException(
                    source.wireName + " contains unresolved " + field + " reference");
        }
    }

    private void validateNoIsolatedEntities() {
        try (ExternalJsonRecordStore linked = new ExternalJsonRecordStore(
                workspace.resolve("linked-entities"))) {
            for (Section section : Section.values()) {
                if (section == Section.ENTITIES || section == Section.REVIEW_ITEMS) {
                    continue;
                }
                sections.get(section).forEach(record ->
                        appendEntityRefs(selectedDocument(section, record.value()), linked));
            }
            linked.finish();
            sections.get(Section.ENTITIES).forEach(record -> {
                if (!linked.containsKey(record.key())) {
                    throw new SemanticExtractionValidationException(
                            "semantic extraction contains an isolated entity");
                }
            });
        }
    }

    private void appendEntityRefs(JsonNode item, ExternalJsonRecordStore linked) {
        for (String field : ENTITY_REF_FIELDS) {
            JsonNode value = item.path(field);
            if (value.isTextual() && !value.asText().isBlank()) {
                linked.append(value.asText(), JSON.getNodeFactory().textNode(value.asText()));
            } else if (value.isArray()) {
                for (JsonNode reference : value) {
                    if (reference.isTextual() && !reference.asText().isBlank()) {
                        linked.append(reference.asText(), JSON.getNodeFactory().textNode(reference.asText()));
                    }
                }
            }
        }
    }

    private void writeDocument(Path target, boolean includeGraph) {
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream output = Files.newOutputStream(target);
                 JsonGenerator generator = JSON.getFactory().createGenerator(output)) {
                generator.useDefaultPrettyPrinter();
                generator.writeStartObject();
                for (Section section : Section.values()) {
                    writeSection(generator, section);
                }
                if (includeGraph) {
                    writeGraph(generator);
                    generator.writeObjectFieldStart("validation");
                    generator.writeArrayFieldStart("isolatedEntities");
                    generator.writeEndArray();
                    generator.writeArrayFieldStart("unresolvedReferences");
                    generator.writeEndArray();
                    generator.writeArrayFieldStart("missingEvidenceRefs");
                    generator.writeEndArray();
                    generator.writeNumberField("generatedReviewItemCount", 0);
                    generator.writeBooleanField("isRefClosed", true);
                    generator.writeEndObject();
                }
                generator.writeEndObject();
                generator.writeRaw('\n');
            }
        } catch (IOException failure) {
            throw new ScanResultContractException("failed to stream semantic extraction result", failure);
        }
    }

    private void writeGraph(JsonGenerator generator) throws IOException {
        try (ExternalJsonRecordStore nodes = new ExternalJsonRecordStore(workspace.resolve("graph-nodes"));
             ExternalJsonRecordStore edges = new ExternalJsonRecordStore(workspace.resolve("graph-edges"))) {
            for (Section section : Section.values()) {
                sections.get(section).forEach(record -> addGraphRecords(
                        section, renamed(section, record.key(),
                                selectedDocument(section, record.value())), nodes, edges));
            }
            nodes.finish();
            edges.finish();
            generator.writeObjectFieldStart("semanticGraph");
            nodes.writeArray(generator, "nodes");
            edges.writeArray(generator, "edges");
            generator.writeObjectFieldStart("summary");
            generator.writeNumberField("nodeCount", nodes.count());
            generator.writeNumberField("edgeCount", edges.count());
            generator.writeEndObject();
            generator.writeEndObject();
        }
    }

    private void writeSection(JsonGenerator generator, Section section) throws IOException {
        generator.writeArrayFieldStart(section.wireName);
        sections.get(section).forEach(record -> {
            try {
                generator.writeTree(renamed(
                        section,
                        record.key(),
                        selectedDocument(section, record.value())));
            } catch (IOException failure) {
                throw new ScanResultContractException(
                        "failed to stream normalized semantic section", failure);
            }
        });
        generator.writeEndArray();
    }

    private JsonNode selectedDocument(Section section, JsonNode stored) {
        JsonNode variants = stored.path("__semanticVariants");
        if (!variants.isArray()) {
            return stored;
        }
        String selectedHash = conflictSelections.get(section.wireName + "\u0000"
                + stored.path("id").asText(""));
        JsonNode fallback = null;
        for (JsonNode variant : variants) {
            if (fallback == null) {
                fallback = variant.path("document");
            }
            if (variant.path("hash").asText("").equals(selectedHash)) {
                return variant.path("document");
            }
        }
        if (selectedHash != null) {
            throw new SemanticExtractionValidationException(
                    "semantic reconciliation selected an unknown conflict variant");
        }
        return fallback == null ? stored : fallback;
    }

    private JsonNode renamed(Section section, String id, JsonNode source) {
        Rename rename = renames.get(section.wireName + "\u0000" + id);
        if (rename == null) {
            return source;
        }
        ObjectNode result = requireObject(source).deepCopy();
        if (rename.name != null) {
            result.put("name", rename.name);
        }
        if (rename.description != null) {
            result.put("description", rename.description);
        }
        return result;
    }

    private Set<String> conflictKeys() {
        Set<String> result = new LinkedHashSet<>();
        for (Section section : Section.values()) {
            sections.get(section).forEach(record -> {
                if (record.value().path("__semanticVariants").isArray()) {
                    result.add(section.wireName + "\u0000" + record.key());
                }
            });
        }
        return Set.copyOf(result);
    }

    private boolean containsVariant(String sectionName, String id, String hash) {
        Section section = Section.fromWire(sectionName);
        if (section == null) {
            return false;
        }
        boolean[] found = {false};
        sections.get(section).forEach(record -> {
            if (!record.key().equals(id)) {
                return;
            }
            record.value().path("__semanticVariants").forEach(variant -> {
                if (hash.equals(variant.path("hash").asText(""))) {
                    found[0] = true;
                }
            });
        });
        return found[0];
    }

    private void validateFinalState() {
        finish();
        Set<String> conflicts = conflictKeys();
        if (!conflicts.isEmpty() && conflictSelections.size() != conflicts.size()) {
            throw new SemanticExtractionValidationException(
                    "semantic shard results contain unresolved conflicts");
        }
        if (validated) {
            return;
        }
        validateSemanticReferences();
        validateNoIsolatedEntities();
        validated = true;
    }

    private void copy(JsonNode source, ObjectNode target, String field) {
        if (source.has(field)) {
            target.set(field, source.path(field).deepCopy());
        }
    }

    private String requiredText(JsonNode source, String field) {
        String value = source.path(field).asText("");
        if (value.isBlank()) {
            throw new SemanticExtractionValidationException(
                    "semantic reconciliation text value is required");
        }
        return value;
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private String reconciliationDeveloperPrompt() {
        return """
                You reconcile already normalized evidence-grounded semantic shards.
                Return one JSON patch only with exactly these arrays:
                - resolutions: {section,id,selectedVariantHash} for every listed conflict.
                - renames: optional {section,id,name,description} display-only changes.

                Never create semantic objects or relations, physical facts, entity ids, candidate refs, or evidence refs.
                Never modify physical names, lineage, triplet candidate coverage, or governance status.
                Return JSON only.
                """;
    }

    private void addGraphRecords(
            Section section,
            JsonNode item,
            ExternalJsonRecordStore nodes,
            ExternalJsonRecordStore edges
    ) {
        String id = item.path("id").asText("");
        String kind = section.graphKind;
        String label = switch (section) {
            case ENTITIES, EVENTS, METRICS, DIMENSIONS -> item.path("name").asText("");
            case RELATIONS -> item.path("type").asText("");
            case LINEAGE -> item.path("to").asText("");
            case TRIPLETS -> item.path("readable").asText("");
            case REVIEW_ITEMS -> item.path("targetRef").asText("");
        };
        String type = switch (section) {
            case ENTITIES, EVENTS, RELATIONS, METRICS, DIMENSIONS -> item.path("type").asText("");
            case LINEAGE -> item.path("transform").asText("");
            case TRIPLETS -> item.path("predicate").asText("");
            case REVIEW_ITEMS -> "REVIEW_NEEDED";
        };
        ObjectNode node = JSON.createObjectNode();
        node.put("id", id);
        node.put("kind", kind);
        node.put("label", label);
        node.put("type", type);
        node.set("evidenceRefs", item.path("evidenceRefs").deepCopy());
        nodes.append(id, node);

        switch (section) {
            case EVENTS -> {
                addEdges(edges, "event-input", id, item.path("inputEntityRefs"), "EVENT_INPUT", item);
                addEdges(edges, "event-output", id, item.path("outputEntityRefs"), "EVENT_OUTPUT", item);
            }
            case RELATIONS -> {
                addEdge(edges, "relation-from", id, item.path("fromEntityRef").asText(""),
                        "RELATION_FROM", item);
                addEdge(edges, "relation-to", id, item.path("toEntityRef").asText(""),
                        "RELATION_TO", item);
                addEdge(edges, "relation", item.path("fromEntityRef").asText(""),
                        item.path("toEntityRef").asText(""),
                        SemanticNormalizationSupport.nonBlank(item.path("type").asText(""), "RELATES_TO"), item);
            }
            case LINEAGE -> {
                addEdges(edges, "lineage-source", id, item.path("sourceEntityRefs"), "LINEAGE_SOURCE", item);
                addEdge(edges, "lineage-target", id, item.path("targetEntityRef").asText(""),
                        "LINEAGE_TARGET", item);
            }
            case METRICS -> addEdge(edges, "metric-owner", id, item.path("ownerEntityRef").asText(""),
                    "METRIC_OWNER", item);
            case DIMENSIONS -> {
                addEdge(edges, "dimension-owner", id, item.path("ownerEntityRef").asText(""),
                        "DIMENSION_OWNER", item);
                addEdge(edges, "dimension-target", id, item.path("dimensionEntityRef").asText(""),
                        "DIMENSION_TARGET", item);
            }
            case TRIPLETS -> {
                addEdge(edges, "triplet-subject", id, item.path("subjectRef").asText(""),
                        "TRIPLET_SUBJECT", item);
                addEdge(edges, "triplet-object", id, item.path("objectRef").asText(""),
                        "TRIPLET_OBJECT", item);
            }
            case REVIEW_ITEMS -> addEdge(edges, "review-target", id, item.path("targetRef").asText(""),
                    "REVIEW_TARGET", item);
            case ENTITIES -> {
                // Entity nodes own no outgoing typed graph edge.
            }
        }
    }

    private void addEdges(
            ExternalJsonRecordStore edges,
            String prefix,
            String source,
            JsonNode targets,
            String type,
            JsonNode owner
    ) {
        if (!targets.isArray()) {
            return;
        }
        targets.forEach(target -> {
            if (target.isTextual()) {
                addEdge(edges, prefix, source, target.asText(), type, owner);
            }
        });
    }

    private void addEdge(
            ExternalJsonRecordStore edges,
            String prefix,
            String source,
            String target,
            String type,
            JsonNode owner
    ) {
        if (source == null || source.isBlank() || target == null || target.isBlank()) {
            return;
        }
        String id = StableSemanticId.of("semantic-edge", prefix, source, target, type);
        ObjectNode edge = JSON.createObjectNode();
        edge.put("id", id);
        edge.put("source", source);
        edge.put("target", target);
        edge.put("type", type);
        edge.set("evidenceRefs", owner.path("evidenceRefs").deepCopy());
        edges.append(id, edge);
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
            deleteRecursively(workspace);
        } catch (IOException error) {
            if (failure == null) failure = new IllegalStateException("failed to clean semantic result store", error);
            else failure.addSuppressed(error);
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private enum Section {
        ENTITIES("entities", "Entity"),
        EVENTS("events", "Event"),
        RELATIONS("relations", "Relation"),
        LINEAGE("lineage", "Lineage"),
        METRICS("metrics", "Metric"),
        DIMENSIONS("dimensions", "Dimension"),
        TRIPLETS("triplets", "Triplet"),
        REVIEW_ITEMS("reviewItems", "ReviewItem");

        private final String wireName;
        private final String graphKind;

        Section(String wireName, String graphKind) {
            this.wireName = wireName;
            this.graphKind = graphKind;
        }

        private static Section fromWire(String value) {
            for (Section section : values()) {
                if (section.wireName.equals(value)) {
                    return section;
                }
            }
            return null;
        }
    }

    private record Rename(String name, String description) {
    }
}
