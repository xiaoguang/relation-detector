package com.relationdetector.semantic.extract;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * CN: 索引 bundle 中 typed section item、table 触达和 ref 依赖，供 planner 构造闭包；输入是完整 bundle，输出稳定查询视图，不修改 JSON。
 * EN: Indexes typed bundle sections, touched tables, and reference dependencies for closure construction. It exposes stable queries and never mutates the source JSON.
 */
final class SemanticShardBundleIndex {
    static final List<String> FACT_SECTIONS = List.of(
            "relationships", "lineage", "derivedRelationships", "derivedLineage", "namingEvidence", "diagnostics");
    static final List<String> CANDIDATE_SECTIONS = List.of(
            "eventCandidates", "reviewItemCandidates", "tripletCandidates");
    static final List<String> ITEM_SECTIONS = List.of(
            "relationships", "lineage", "eventCandidates", "derivedRelationships", "derivedLineage",
            "namingEvidence", "reviewItemCandidates", "tripletCandidates", "diagnostics");
    private static final List<String> REFERENCE_FIELDS = List.of(
            "factRef", "eventCandidateRef", "targetRef", "lineageRefs", "supportingDerivedLineageRefs",
            "relationshipRefs");

    private final ObjectNode bundle;
    private final List<String> tables;
    private final Map<String, Item> itemsById = new LinkedHashMap<>();
    private final Map<String, JsonNode> evidenceById = new LinkedHashMap<>();

    SemanticShardBundleIndex(ObjectNode bundle) {
        if (bundle == null) {
            throw new IllegalArgumentException("semantic evidence bundle is required");
        }
        this.bundle = bundle;
        this.tables = new ArrayList<>();
        bundle.path("tables").forEach(node -> {
            if (node.isTextual() && !node.asText().isBlank()) {
                tables.add(node.asText());
            }
        });
        tables.sort(Comparator.comparingInt(String::length).reversed().thenComparing(String::compareTo));
        for (String section : ITEM_SECTIONS) {
            for (JsonNode item : bundle.path(section)) {
                String id = item.path("id").asText("");
                if (id.isBlank() || itemsById.putIfAbsent(id,
                        new Item(section, id, item.deepCopy(), directTables(section, item))) != null) {
                    throw new SemanticShardingException("semantic bundle contains missing or duplicate item id");
                }
            }
        }
        propagateReferenceTables();
        for (JsonNode evidence : bundle.path("evidence")) {
            String id = evidence.path("id").asText("");
            if (id.isBlank() || evidenceById.putIfAbsent(id, evidence.deepCopy()) != null) {
                throw new SemanticShardingException("semantic bundle contains missing or duplicate evidence id");
            }
        }
    }

    ObjectNode bundle() {
        return bundle;
    }

    List<String> tables() {
        return List.copyOf(tables);
    }

    Collection<Item> items() {
        return List.copyOf(itemsById.values());
    }

    Item item(String id) {
        return itemsById.get(id);
    }

    JsonNode evidence(String id) {
        return evidenceById.get(id);
    }

    Set<String> evidenceIds() {
        return Set.copyOf(evidenceById.keySet());
    }

    Set<String> dependencyRefs(JsonNode item) {
        Set<String> refs = new LinkedHashSet<>();
        for (String field : REFERENCE_FIELDS) {
            JsonNode value = item.path(field);
            if (value.isTextual() && !value.asText().isBlank()) {
                refs.add(value.asText());
            } else if (value.isArray()) {
                value.forEach(ref -> {
                    if (ref.isTextual() && !ref.asText().isBlank()) {
                        refs.add(ref.asText());
                    }
                });
            }
        }
        return refs;
    }

    Set<String> evidenceRefs(JsonNode item) {
        Set<String> refs = new LinkedHashSet<>();
        item.path("evidenceRefs").forEach(ref -> {
            if (ref.isTextual() && !ref.asText().isBlank()) {
                refs.add(ref.asText());
            }
        });
        return refs;
    }

    Set<String> touchedTables(JsonNode node) {
        return endpointTables(node);
    }

    private Set<String> directTables(String section, JsonNode item) {
        Set<String> result = new LinkedHashSet<>();
        switch (section) {
            case "relationships", "derivedRelationships", "namingEvidence" -> {
                result.addAll(endpointTables(item.path("source")));
                result.addAll(endpointTables(item.path("target")));
            }
            case "lineage", "derivedLineage" -> {
                result.addAll(endpointTables(item.path("sources")));
                result.addAll(endpointTables(item.path("target")));
            }
            case "eventCandidates" -> {
                result.addAll(endpointTables(item.path("inputEndpoints")));
                result.addAll(endpointTables(item.path("outputEndpoints")));
            }
            default -> {
                // Candidate and diagnostic ownership is inherited only through typed references.
            }
        }
        return result;
    }

    private Set<String> endpointTables(JsonNode value) {
        Set<String> result = new LinkedHashSet<>();
        if (value == null) return result;
        if (value.isArray()) {
            value.forEach(endpoint -> result.addAll(endpointTables(endpoint)));
            return result;
        }
        if (!value.isTextual()) return result;
        String endpoint = value.asText();
        for (String table : tables) {
            if (endpoint.equals(table) || endpoint.startsWith(table + ".")) {
                result.add(table);
                break;
            }
        }
        return result;
    }

    private void propagateReferenceTables() {
        boolean changed;
        do {
            changed = false;
            for (Map.Entry<String, Item> entry : List.copyOf(itemsById.entrySet())) {
                Item item = entry.getValue();
                Set<String> resolved = new LinkedHashSet<>(item.tables());
                for (String reference : dependencyRefs(item.document())) {
                    Item dependency = itemsById.get(reference);
                    if (dependency != null) resolved.addAll(dependency.tables());
                }
                if (!resolved.equals(item.tables())) {
                    itemsById.put(entry.getKey(),
                            new Item(item.section(), item.id(), item.document(), resolved));
                    changed = true;
                }
            }
        } while (changed);
    }

    record Item(String section, String id, JsonNode document, Set<String> tables) {
        Item {
            tables = Set.copyOf(tables == null ? Set.of() : tables);
        }
    }
}
