package com.relationdetector.semantic.extraction.shard;

import com.relationdetector.semantic.extraction.normalization.SemanticExtractionValidationException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * CN: 在片内 backfill 和 formal normalization 前验证模型对象只由当前 shard 拥有的事实或候选直接支撑；
 * 输入是原始模型 JSON 与 shard owner 集合，输出无状态校验结果，禁止把 overlap 或裸 evidence 当作所有权。
 *
 * EN: Validates raw model objects against the current shard's owned facts and candidates before backfill and formal
 * normalization. Overlap references and evidence-only references never establish output ownership.
 */
public final class SemanticShardOutputOwnershipValidator {
    static final List<String> OUTPUT_SECTIONS = List.of(
            "entities", "events", "relations", "lineage", "metrics", "dimensions", "triplets", "reviewItems");
    private static final List<String> DIRECT_REFERENCE_FIELDS = List.of(
            "eventCandidateRef", "candidateRef", "factRef");

    public void validate(JsonNode rawDocument, JsonNode bundle) {
        if (rawDocument == null || !rawDocument.isObject() || bundle == null || !bundle.isObject()) {
            throw new SemanticExtractionValidationException("semantic shard output ownership input is invalid");
        }
        OwnershipContext context = ownershipContext(bundle);
        Set<String> owned = new LinkedHashSet<>(context.ownedFacts());
        owned.addAll(context.ownedCandidates());
        Set<String> evidence = evidenceIds(bundle);
        for (String section : OUTPUT_SECTIONS) {
            JsonNode items = rawDocument.path(section);
            if (!items.isArray()) {
                throw new SemanticExtractionValidationException(
                        "semantic shard output section must be an array: " + section);
            }
            for (JsonNode item : items) {
                validateItem(section, item, owned, context.overlap(), evidence);
            }
        }
    }

    private OwnershipContext ownershipContext(JsonNode bundle) {
        JsonNode context = bundle.path("shardContext");
        if (!context.isObject()
                || context.path("shardId").asText("").isBlank()
                || context.path("ownerKey").asText("").isBlank()
                || !context.path("outputOwnedReferencesOnly").asBoolean(false)) {
            throw new SemanticExtractionValidationException("semantic shardContext is missing or invalid");
        }
        Set<String> ownedFacts = textSet(context, "ownedFactRefs");
        Set<String> ownedCandidates = textSet(context, "ownedCandidateRefs");
        Set<String> overlap = textSet(context, "overlapRefs");
        requireDisjoint(ownedFacts, ownedCandidates, overlap);

        Set<String> factIds = itemIds(bundle, SemanticShardBundleIndex.FACT_SECTIONS);
        Set<String> candidateIds = itemIds(bundle, SemanticShardBundleIndex.CANDIDATE_SECTIONS);
        Set<String> allIds = new LinkedHashSet<>(factIds);
        Set<String> duplicateIds = new LinkedHashSet<>(factIds);
        duplicateIds.retainAll(candidateIds);
        if (!duplicateIds.isEmpty()) {
            throw new SemanticExtractionValidationException(
                    "semantic evidence bundle item ids must be globally unique");
        }
        allIds.addAll(candidateIds);
        if (!factIds.containsAll(ownedFacts)
                || !candidateIds.containsAll(ownedCandidates)
                || !allIds.containsAll(overlap)) {
            throw new SemanticExtractionValidationException(
                    "semantic shardContext contains references outside the supplied bundle");
        }
        return new OwnershipContext(ownedFacts, ownedCandidates, overlap);
    }

    private Set<String> textSet(JsonNode context, String field) {
        JsonNode values = context.path(field);
        if (!values.isArray()) {
            throw new SemanticExtractionValidationException(
                    "semantic shardContext field must be an array: " + field);
        }
        Set<String> result = new LinkedHashSet<>();
        for (JsonNode value : values) {
            String reference = value.isTextual() ? value.asText() : "";
            if (reference.isBlank() || !result.add(reference)) {
                throw new SemanticExtractionValidationException(
                        "semantic shardContext contains an invalid or duplicate reference");
            }
        }
        return Set.copyOf(result);
    }

    private Set<String> itemIds(JsonNode bundle, List<String> sections) {
        Set<String> result = new LinkedHashSet<>();
        for (String section : sections) {
            JsonNode items = bundle.path(section);
            if (!items.isArray()) {
                throw new SemanticExtractionValidationException(
                        "semantic evidence bundle section must be an array: " + section);
            }
            for (JsonNode item : items) {
                String id = item.path("id").asText("");
                if (id.isBlank() || !result.add(id)) {
                    throw new SemanticExtractionValidationException(
                            "semantic evidence bundle contains a missing or duplicate item id");
                }
            }
        }
        return result;
    }

    private void requireDisjoint(Set<String> ownedFacts, Set<String> ownedCandidates, Set<String> overlap) {
        Set<String> seen = new LinkedHashSet<>();
        for (Set<String> refs : List.of(ownedFacts, ownedCandidates, overlap)) {
            for (String ref : refs) {
                if (!seen.add(ref)) {
                    throw new SemanticExtractionValidationException(
                            "semantic shardContext ownership sets must be disjoint");
                }
            }
        }
    }

    /**
     * CN: 对单个raw模型对象同时校验owned grounding、section-specific direct refs和审计evidence refs；
     * 成功无副作用，任一overlap-only、cross-owner或未知ref使整片在backfill前失败，不保留部分对象。
     *
     * EN: Validates one raw model item across owned grounding, section-specific direct references, and audit evidence
     * references. Success has no side effects; overlap-only, cross-owner, or unknown references abort the shard before
     * backfill without retaining a partial item.
     */
    private void validateItem(
            String section,
            JsonNode item,
            Set<String> owned,
            Set<String> overlap,
            Set<String> evidence
    ) {
        if (!item.isObject()) {
            throw new SemanticExtractionValidationException(
                    "semantic shard output item must be an object in section " + section);
        }
        JsonNode groundingRefs = item.path("ownedGroundingRefs");
        if (!groundingRefs.isArray() || groundingRefs.isEmpty()) {
            throw new SemanticExtractionValidationException(
                    "semantic shard output item requires ownedGroundingRefs in section " + section);
        }
        for (JsonNode node : groundingRefs) {
            if (!node.isTextual() || node.asText().isBlank() || !owned.contains(node.asText())) {
                throw new SemanticExtractionValidationException(
                        "semantic shard output grounding reference is not owned by the current shard");
            }
        }
        for (String field : DIRECT_REFERENCE_FIELDS) {
            String reference = item.path(field).asText("");
            if (reference.isBlank()) continue;
            if (!owned.contains(reference)) {
                throw new SemanticExtractionValidationException(
                        "semantic shard output directly references a non-owned candidate or fact");
            }
        }
        JsonNode evidenceRefs = item.path("evidenceRefs");
        if (!evidenceRefs.isArray()) {
            throw new SemanticExtractionValidationException(
                    "semantic shard output evidenceRefs must be an array in section " + section);
        }
        for (JsonNode node : evidenceRefs) {
            if (!node.isTextual() || node.asText().isBlank()) {
                throw new SemanticExtractionValidationException(
                        "semantic shard output evidence reference is invalid");
            }
            String reference = node.asText();
            if (!owned.contains(reference) && !overlap.contains(reference) && !evidence.contains(reference)) {
                throw new SemanticExtractionValidationException(
                        "semantic shard output contains an unknown evidence reference");
            }
        }
    }

    private Set<String> evidenceIds(JsonNode bundle) {
        Set<String> result = new LinkedHashSet<>();
        JsonNode evidence = bundle.path("evidence");
        if (!evidence.isArray()) {
            throw new SemanticExtractionValidationException(
                    "semantic evidence bundle section must be an array: evidence");
        }
        evidence.forEach(item -> {
            String id = item.path("id").asText("");
            if (!id.isBlank()) result.add(id);
        });
        return result;
    }

    private record OwnershipContext(
            Set<String> ownedFacts,
            Set<String> ownedCandidates,
            Set<String> overlap
    ) {
    }
}
