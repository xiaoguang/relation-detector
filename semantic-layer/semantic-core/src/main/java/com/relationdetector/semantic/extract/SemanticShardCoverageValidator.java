package com.relationdetector.semantic.extract;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * CN: 验证完整 bundle 的 facts/candidates 在 shard plan 中恰有一个 owner 且每片 evidence refs 闭合；输入 plan，成功无输出，失败原子拒绝。
 * EN: Validates unique fact/candidate ownership and evidence-reference closure for every shard. Success has no output; any violation rejects the plan atomically.
 */
public final class SemanticShardCoverageValidator {
    public void validate(ObjectNode fullBundle, SemanticShardPlan plan) {
        if (fullBundle == null || plan == null || plan.shards().isEmpty()) {
            throw new SemanticShardingException("semantic shard plan is incomplete");
        }
        Set<String> shardIds = new LinkedHashSet<>();
        Map<String, ObjectNode> bundleByShard = new LinkedHashMap<>();
        for (SemanticShard shard : plan.shards()) {
            if (!shardIds.add(shard.id())) {
                throw new SemanticShardingException("semantic shard ids must be unique");
            }
            validateOwnershipContext(shard);
            bundleByShard.put(shard.id(), shard.trustedBundle());
            validateEvidenceClosure(shard.trustedBundle());
        }
        requireOwners(fullBundle, SemanticShardBundleIndex.FACT_SECTIONS, plan.factOwners(), bundleByShard);
        requireOwners(fullBundle, SemanticShardBundleIndex.CANDIDATE_SECTIONS, plan.candidateOwners(), bundleByShard);
    }

    private void requireOwners(
            ObjectNode fullBundle,
            List<String> sections,
            Map<String, String> owners,
            Map<String, ObjectNode> bundleByShard
    ) {
        Set<String> expected = ids(fullBundle, sections);
        if (!owners.keySet().equals(expected)) {
            throw new SemanticShardingException("semantic shard ownership does not cover the complete bundle");
        }
        for (Map.Entry<String, String> owner : owners.entrySet()) {
            ObjectNode shard = bundleByShard.get(owner.getValue());
            if (shard == null || !containsId(shard, sections, owner.getKey())) {
                throw new SemanticShardingException("semantic shard owner does not contain owned reference");
            }
        }
    }

    private void validateEvidenceClosure(ObjectNode bundle) {
        SemanticShardBundleIndex index = new SemanticShardBundleIndex(bundle);
        Set<String> references = ids(bundle, List.of("evidence"));
        references.addAll(ids(bundle, SemanticShardBundleIndex.ITEM_SECTIONS));
        for (String section : SemanticShardBundleIndex.ITEM_SECTIONS) {
            for (JsonNode item : bundle.path(section)) {
                for (JsonNode ref : item.path("evidenceRefs")) {
                    if (ref.isTextual() && !references.contains(ref.asText())) {
                        throw new SemanticShardingException("semantic shard contains unresolved evidence reference");
                    }
                }
                for (String ref : index.dependencyRefs(item)) {
                    if (!references.contains(ref)) {
                        throw new SemanticShardingException("semantic shard contains unresolved candidate dependency");
                    }
                }
            }
        }
    }

    private void validateOwnershipContext(SemanticShard shard) {
        JsonNode context = shard.trustedBundle().path("shardContext");
        if (!context.isObject()
                || !shard.id().equals(context.path("shardId").asText())
                || !shard.ownerKey().equals(context.path("ownerKey").asText())
                || !context.path("outputOwnedReferencesOnly").asBoolean(false)
                || !textValues(context.path("ownedFactRefs")).equals(shard.ownedFactRefs())
                || !textValues(context.path("ownedCandidateRefs")).equals(shard.ownedCandidateRefs())
                || !textValues(context.path("overlapRefs")).equals(shard.overlapRefs())) {
            throw new SemanticShardingException("semantic shard ownership context is inconsistent");
        }
    }

    private Set<String> textValues(JsonNode values) {
        Set<String> result = new LinkedHashSet<>();
        values.forEach(value -> {
            if (value.isTextual() && !value.asText().isBlank()) {
                result.add(value.asText());
            }
        });
        return result;
    }

    private Set<String> ids(ObjectNode bundle, List<String> sections) {
        Set<String> result = new LinkedHashSet<>();
        for (String section : sections) {
            bundle.path(section).forEach(item -> {
                String id = item.path("id").asText("");
                if (!id.isBlank()) result.add(id);
            });
        }
        return result;
    }

    private boolean containsId(ObjectNode bundle, List<String> sections, String id) {
        return ids(bundle, sections).contains(id);
    }
}
