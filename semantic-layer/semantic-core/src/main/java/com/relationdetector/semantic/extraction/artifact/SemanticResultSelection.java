package com.relationdetector.semantic.extraction.artifact;

import com.relationdetector.semantic.extraction.shard.SemanticShardingException;

import com.relationdetector.semantic.extraction.shard.SemanticRunPlan;

import com.relationdetector.semantic.extraction.artifact.SemanticResultStore;

import com.relationdetector.semantic.extraction.normalization.SemanticExtractionValidationException;

import com.relationdetector.semantic.extraction.prompt.SemanticPromptBudgetEstimator;

import com.relationdetector.semantic.extraction.prompt.SemanticExtractionPrompt;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.internal.store.ExternalJsonRecordStore;

/**
 * CN: 管理 path-backed semantic 结果中的跨片 variant 选择和展示字段重命名，并据此构造受预算约束的
 * reconciliation prompt。输入是已完成的 section stores 和受限 patch，输出是确定的只读结果视图；
 * 本类不写最终文档、不校验证据闭包，也不改变物理字段或正式语义身份。
 * EN: Owns cross-shard variant selection and display-only renames for path-backed semantic results, including the
 * bounded reconciliation prompt. It exposes a deterministic selected view but neither writes final documents nor
 * validates evidence closure or changes physical fields and semantic identity.
 */
public final class SemanticResultSelection {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Map<SemanticResultStore.Section, ExternalJsonRecordStore> sections;
    private final Map<String, String> conflictSelections = new LinkedHashMap<>();
    private final Map<String, Rename> renames = new LinkedHashMap<>();

    public SemanticResultSelection(
            Map<SemanticResultStore.Section, ExternalJsonRecordStore> sections
    ) {
        this.sections = sections;
    }

    public SemanticExtractionPrompt reconciliationPrompt(SemanticRunPlan plan, int maxInputTokens) {
        ObjectNode bundle = JSON.createObjectNode();
        bundle.put("kind", "SEMANTIC_RECONCILIATION");
        bundle.put("fullBundleHash", plan.fullBundle().sha256());
        ArrayNode shards = bundle.putArray("shards");
        plan.shards().forEach(shard -> shards.addObject()
                .put("id", shard.id())
                .put("ownerKey", shard.ownerKey())
                .put("estimatedInputTokens", shard.estimatedInputTokens()));
        ArrayNode conflicts = bundle.putArray("conflicts");
        long[] approximateBytes = {0};
        for (SemanticResultStore.Section section : SemanticResultStore.Section.values()) {
            sections.get(section).forEach(record -> {
                JsonNode stored = record.value();
                if (stored.path("__semanticVariants").isArray()) {
                    ObjectNode conflict = conflicts.addObject();
                    conflict.put("section", section.wireName);
                    conflict.put("id", record.key());
                    conflict.set("variants", stored.path("__semanticVariants").deepCopy());
                    approximateBytes[0] += conflict.toString().length();
                }
                if (approximateBytes[0] > (long) maxInputTokens * 8L) {
                    throw budgetFailure();
                }
            });
        }
        bundle.putObject("instructions")
                .put("patchOnly", true)
                .put("newPhysicalFactsForbidden", true)
                .put("newEvidenceReferencesForbidden", true);
        SemanticExtractionPrompt prompt = new SemanticExtractionPrompt(
                developerPrompt(),
                "Resolve these semantic shard conflicts and return the constrained patch:\n" + bundle,
                bundle);
        if (new SemanticPromptBudgetEstimator().estimate(prompt) > maxInputTokens) {
            throw budgetFailure();
        }
        return prompt;
    }

    public void applyPatch(JsonNode patch) {
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
            SemanticResultStore.Section section =
                    SemanticResultStore.Section.fromWire(rename.path("section").asText(""));
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

    public void requireConflictFree() {
        if (!conflictKeys().isEmpty()) {
            throw new SemanticExtractionValidationException(
                    "semantic shard results contain unresolved conflicts");
        }
    }

    public void requireResolved() {
        Set<String> conflicts = conflictKeys();
        if (!conflicts.isEmpty() && conflictSelections.size() != conflicts.size()) {
            throw new SemanticExtractionValidationException(
                    "semantic shard results contain unresolved conflicts");
        }
    }

    public JsonNode selectedDocument(SemanticResultStore.Section section, JsonNode stored) {
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

    public JsonNode renamed(SemanticResultStore.Section section, String id, JsonNode source) {
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
        for (SemanticResultStore.Section section : SemanticResultStore.Section.values()) {
            sections.get(section).forEach(record -> {
                if (record.value().path("__semanticVariants").isArray()) {
                    result.add(section.wireName + "\u0000" + record.key());
                }
            });
        }
        return Set.copyOf(result);
    }

    private boolean containsVariant(String sectionName, String id, String hash) {
        SemanticResultStore.Section section = SemanticResultStore.Section.fromWire(sectionName);
        if (section == null) {
            return false;
        }
        boolean[] found = {false};
        sections.get(section).forEach(record -> {
            if (record.key().equals(id)) {
                record.value().path("__semanticVariants").forEach(variant -> {
                    if (hash.equals(variant.path("hash").asText(""))) {
                        found[0] = true;
                    }
                });
            }
        });
        return found[0];
    }

    private String requiredText(JsonNode source, String field) {
        String value = source.path(field).asText("");
        if (value.isBlank()) {
            throw new SemanticExtractionValidationException(
                    "semantic reconciliation text value is required");
        }
        return value;
    }

    private ObjectNode requireObject(JsonNode value) {
        if (value == null || !value.isObject()) {
            throw new SemanticExtractionValidationException("canonical semantic entity must be an object");
        }
        return (ObjectNode) value;
    }

    private SemanticShardingException budgetFailure() {
        return new SemanticShardingException(
                "semantic reconciliation prompt exceeds the configured estimated input-token limit");
    }

    private String developerPrompt() {
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

    private record Rename(String name, String description) {
    }
}
