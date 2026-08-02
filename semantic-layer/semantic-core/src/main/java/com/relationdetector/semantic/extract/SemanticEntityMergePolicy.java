package com.relationdetector.semantic.extract;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.StableSemanticId;

/**
 * CN: 统一内存与磁盘分片合并中的物理实体兼容规则；输入是同一canonical identity的实体变体，输出证据并集
 * 和确定性内容。core生成的PHYSICAL_ENTITY仅是引用闭合占位值，遇到正式语义类型时让位；两个不同正式
 * 类型仍明确失败。本类不改变实体identity、不合并不同物理名，也不替reconciliation选择业务冲突。
 *
 * EN: Defines the shared physical-entity compatibility rule for in-memory and disk-backed shard merges. It consumes
 * variants of one canonical identity and returns deterministic content with unioned grounding. A core-generated
 * PHYSICAL_ENTITY is only a closure placeholder and yields to an evidence-grounded semantic type, while conflicting
 * non-placeholder types still fail. This policy never changes identity, merges different physical names, or selects
 * business conflicts on behalf of reconciliation.
 */
final class SemanticEntityMergePolicy {
    private static final String PHYSICAL_PLACEHOLDER = "PHYSICAL_ENTITY";

    private SemanticEntityMergePolicy() {
    }

    static ObjectNode merge(List<ObjectNode> variants) {
        if (variants == null || variants.isEmpty()) {
            throw new IllegalArgumentException("semantic entity variants are required");
        }
        if (!canMerge(variants)) {
            throw new SemanticExtractionValidationException(
                    "canonical semantic entity has incompatible structural content");
        }
        List<ObjectNode> preferred = preferred(variants);
        ObjectNode result = preferred.stream()
                .min(Comparator.comparing(StableSemanticId::canonicalJson))
                .orElseThrow()
                .deepCopy();
        mergeReferences(result, variants, "ownedGroundingRefs");
        mergeReferences(result, variants, "evidenceRefs");
        return result;
    }

    static boolean canMerge(List<ObjectNode> variants) {
        if (variants == null || variants.isEmpty()) {
            throw new IllegalArgumentException("semantic entity variants are required");
        }
        validatePhysicalIdentity(variants);
        return semanticTypes(variants).size() <= 1;
    }

    static List<ObjectNode> reconciliationVariants(List<ObjectNode> variants) {
        if (variants == null || variants.isEmpty()) {
            throw new IllegalArgumentException("semantic entity variants are required");
        }
        validatePhysicalIdentity(variants);
        MapByCanonicalJson unique = new MapByCanonicalJson();
        for (ObjectNode variant : preferred(variants)) {
            ObjectNode candidate = variant.deepCopy();
            mergeReferences(candidate, variants, "ownedGroundingRefs");
            mergeReferences(candidate, variants, "evidenceRefs");
            unique.add(candidate);
        }
        return unique.values();
    }

    private static void validatePhysicalIdentity(List<ObjectNode> variants) {
        Set<String> physicalNames = new LinkedHashSet<>();
        variants.forEach(variant -> {
            String physicalName = text(variant, "physicalName");
            if (!physicalName.isBlank()) {
                physicalNames.add(physicalName);
            }
        });
        if (physicalNames.size() > 1) {
            throw new SemanticExtractionValidationException(
                    "canonical semantic entity has incompatible physical identity");
        }
    }

    private static Set<String> semanticTypes(List<ObjectNode> variants) {
        Set<String> semanticTypes = new LinkedHashSet<>();
        variants.stream()
                .filter(variant -> !placeholder(variant))
                .map(SemanticEntityMergePolicy::semanticType)
                .filter(value -> !value.isBlank())
                .forEach(semanticTypes::add);
        return semanticTypes;
    }

    private static List<ObjectNode> preferred(List<ObjectNode> variants) {
        List<ObjectNode> preferred = variants.stream()
                .filter(variant -> !placeholder(variant))
                .toList();
        return preferred.isEmpty() ? variants : preferred;
    }

    private static boolean placeholder(ObjectNode entity) {
        return PHYSICAL_PLACEHOLDER.equals(text(entity, "machineType"));
    }

    private static String semanticType(ObjectNode entity) {
        String machineType = text(entity, "machineType");
        return SemanticCanonicalIdentity.normalizeText(
                machineType.isBlank() ? text(entity, "type") : machineType);
    }

    private static void mergeReferences(
            ObjectNode target,
            List<ObjectNode> variants,
            String field
    ) {
        Set<String> values = new LinkedHashSet<>();
        variants.forEach(variant -> variant.path(field).forEach(value -> {
            if (value.isTextual() && !value.asText().isBlank()) {
                values.add(value.asText());
            }
        }));
        ArrayNode output = target.putArray(field);
        values.stream().sorted().forEach(output::add);
    }

    private static String text(ObjectNode value, String field) {
        return value.path(field).asText("").trim();
    }

    private static final class MapByCanonicalJson {
        private final java.util.TreeMap<String, ObjectNode> values = new java.util.TreeMap<>();

        void add(ObjectNode value) {
            values.putIfAbsent(StableSemanticId.canonicalJson(value), value);
        }

        List<ObjectNode> values() {
            return List.copyOf(values.values());
        }
    }
}
