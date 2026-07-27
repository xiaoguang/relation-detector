package com.relationdetector.semantic.extract;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * CN: 校验并应用受限 reconciliation patch，只允许选择已知 conflict variant 或修改展示名称；禁止创建语义对象、关系、物理事实或新引用。
 * EN: Validates and applies a constrained reconciliation patch that may only select known variants or adjust display text. It cannot create semantic objects, relations, physical facts, or references.
 */
final class SemanticReconciliationPatchValidator {
    private static final Set<String> PATCH_SECTIONS = Set.of("resolutions", "renames");
    private static final Set<String> RENAME_SECTIONS = Set.of(
            "entities", "events", "relations", "lineage", "metrics", "dimensions", "triplets", "reviewItems");

    public ObjectNode apply(SemanticShardMergeResult merge, JsonNode patch, JsonNode fullBundle) {
        if (merge == null || patch == null || !patch.isObject()) {
            throw new SemanticExtractionValidationException("semantic reconciliation patch is required");
        }
        requireSections(patch);
        ObjectNode result = merge.trustedMergedDocument().deepCopy();
        resolveConflicts(result, merge.conflicts(), requireArray(patch, "resolutions"));
        applyRenames(result, requireArray(patch, "renames"));
        return result;
    }

    private void resolveConflicts(ObjectNode result, List<SemanticShardConflict> conflicts, ArrayNode resolutions) {
        Map<String, SemanticShardConflict> expected = new LinkedHashMap<>();
        conflicts.forEach(conflict -> expected.put(conflict.section() + "\u0000" + conflict.id(), conflict));
        Set<String> resolved = new LinkedHashSet<>();
        for (JsonNode resolution : resolutions) {
            String section = resolution.path("section").asText("");
            String id = resolution.path("id").asText("");
            String hash = resolution.path("selectedVariantHash").asText("");
            String key = section + "\u0000" + id;
            SemanticShardConflict conflict = expected.get(key);
            if (conflict == null || !resolved.add(key)) {
                throw new SemanticExtractionValidationException("reconciliation resolution does not match one conflict");
            }
            SemanticShardVariant selected = conflict.variants().stream()
                    .filter(variant -> variant.hash().equals(hash))
                    .findFirst()
                    .orElseThrow(() -> new SemanticExtractionValidationException(
                            "reconciliation selected an unknown conflict variant"));
            replaceById(result.withArray(section), id, selected.trustedDocument());
        }
        if (resolved.size() != expected.size()) {
            throw new SemanticExtractionValidationException("reconciliation did not resolve every shard conflict");
        }
    }

    private void applyRenames(ObjectNode result, ArrayNode renames) {
        for (JsonNode rename : renames) {
            String section = rename.path("section").asText("");
            String id = rename.path("id").asText("");
            if (!RENAME_SECTIONS.contains(section) || id.isBlank()) {
                throw new SemanticExtractionValidationException("reconciliation rename target is invalid");
            }
            ObjectNode item = findById(result.withArray(section), id);
            if (item == null) {
                throw new SemanticExtractionValidationException("reconciliation rename target does not exist");
            }
            if (rename.has("name")) item.put("name", requireText(rename, "name"));
            if (rename.has("description")) item.put("description", requireText(rename, "description"));
        }
    }

    private void requireSections(JsonNode patch) {
        patch.fieldNames().forEachRemaining(section -> {
            if (!PATCH_SECTIONS.contains(section)) {
                throw new SemanticExtractionValidationException(
                        "reconciliation patch contains unsupported section: " + section);
            }
        });
    }

    private ArrayNode requireArray(JsonNode patch, String field) {
        JsonNode value = patch.path(field);
        if (!value.isArray()) {
            throw new SemanticExtractionValidationException("reconciliation patch section must be an array: " + field);
        }
        return (ArrayNode) value;
    }

    private String requireText(JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (value.isBlank()) {
            throw new SemanticExtractionValidationException("reconciliation text value is required");
        }
        return value;
    }

    private void replaceById(ArrayNode section, String id, JsonNode replacement) {
        for (int index = 0; index < section.size(); index++) {
            if (id.equals(section.get(index).path("id").asText())) {
                section.set(index, replacement.deepCopy());
                return;
            }
        }
        throw new SemanticExtractionValidationException("reconciliation conflict target does not exist");
    }

    private ObjectNode findById(ArrayNode section, String id) {
        for (JsonNode item : section) {
            if (id.equals(item.path("id").asText()) && item.isObject()) {
                return (ObjectNode) item;
            }
        }
        return null;
    }

}
