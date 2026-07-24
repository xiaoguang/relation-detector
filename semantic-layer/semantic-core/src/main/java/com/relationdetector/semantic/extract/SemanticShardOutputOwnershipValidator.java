package com.relationdetector.semantic.extract;

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
final class SemanticShardOutputOwnershipValidator {
    static final List<String> OUTPUT_SECTIONS = List.of(
            "entities", "events", "relations", "lineage", "metrics", "dimensions", "triplets", "reviewItems");
    private static final List<String> DIRECT_REFERENCE_FIELDS = List.of(
            "eventCandidateRef", "candidateRef", "factRef");

    void validate(JsonNode rawDocument, SemanticShard shard) {
        if (rawDocument == null || !rawDocument.isObject() || shard == null) {
            throw new SemanticExtractionValidationException("semantic shard output ownership input is invalid");
        }
        Set<String> owned = new LinkedHashSet<>(shard.ownedFactRefs());
        owned.addAll(shard.ownedCandidateRefs());
        Set<String> overlap = shard.overlapRefs();
        Set<String> evidence = evidenceIds(shard.trustedBundle());
        for (String section : OUTPUT_SECTIONS) {
            JsonNode items = rawDocument.path(section);
            if (!items.isArray()) {
                throw new SemanticExtractionValidationException(
                        "semantic shard output section must be an array: " + section);
            }
            for (JsonNode item : items) {
                validateItem(section, item, owned, overlap, evidence);
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
        bundle.path("evidence").forEach(item -> {
            String id = item.path("id").asText("");
            if (!id.isBlank()) result.add(id);
        });
        return result;
    }
}
