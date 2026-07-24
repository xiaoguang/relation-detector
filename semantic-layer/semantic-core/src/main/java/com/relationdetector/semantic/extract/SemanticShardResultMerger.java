package com.relationdetector.semantic.extract;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.StableSemanticId;

/**
 * CN: 按 section/stable ID 幂等合并已归一化 shard，并显式保留内容冲突；输入完整 shard results，输出 draft/conflicts，禁止 last-write-wins。
 * EN: Idempotently merges normalized shards by section and stable id while preserving content conflicts explicitly. It never uses last-write-wins behavior.
 */
public final class SemanticShardResultMerger {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> SECTIONS = List.of(
            "entities", "events", "relations", "lineage", "metrics", "dimensions", "triplets", "reviewItems");
    private final SemanticShardIdentityCanonicalizer identityCanonicalizer =
            new SemanticShardIdentityCanonicalizer();

    public SemanticShardMergeResult merge(List<SemanticShardNormalizedResult> results) {
        return mergeCanonicalized(results, List.of());
    }

    public SemanticShardMergeResult merge(
            List<SemanticShardNormalizedResult> results,
            SemanticShardPlan plan
    ) {
        SemanticShardIdentityCanonicalizer.CanonicalizedShardResults canonicalized =
                identityCanonicalizer.canonicalize(results, plan);
        return mergeCanonicalized(canonicalized.results(), canonicalized.generatedReviews());
    }

    private SemanticShardMergeResult mergeCanonicalized(
            List<SemanticShardNormalizedResult> results,
            List<ObjectNode> generatedReviews
    ) {
        if (results == null || results.isEmpty()) {
            throw new IllegalArgumentException("normalized shard results are required");
        }
        ObjectNode merged = JSON.createObjectNode();
        List<SemanticShardConflict> conflicts = new ArrayList<>();
        for (String section : SECTIONS) {
            Map<String, List<SemanticShardVariant>> variants = variants(results, section);
            ArrayNode output = merged.putArray(section);
            for (Map.Entry<String, List<SemanticShardVariant>> entry : variants.entrySet()) {
                List<SemanticShardVariant> distinct = distinct(entry.getValue());
                output.add(distinct.get(0).trustedDocument().deepCopy());
                if (distinct.size() > 1) {
                    conflicts.add(new SemanticShardConflict(section, entry.getKey(), distinct));
                }
            }
        }
        appendGeneratedReviews(merged.withArray("reviewItems"), generatedReviews);
        return new SemanticShardMergeResult(merged, conflicts);
    }

    private void appendGeneratedReviews(ArrayNode output, List<ObjectNode> generatedReviews) {
        Map<String, JsonNode> byId = new LinkedHashMap<>();
        output.forEach(item -> byId.put(item.path("id").asText(""), item));
        for (ObjectNode review : generatedReviews) {
            JsonNode previous = byId.putIfAbsent(review.path("id").asText(""), review);
            if (previous == null) {
                output.add(review.deepCopy());
            } else if (!previous.equals(review)) {
                throw new SemanticExtractionValidationException(
                        "generated semantic duplicate review conflicts with model output");
            }
        }
    }

    private Map<String, List<SemanticShardVariant>> variants(
            List<SemanticShardNormalizedResult> results,
            String section
    ) {
        Map<String, List<SemanticShardVariant>> variants = new LinkedHashMap<>();
        for (SemanticShardNormalizedResult result : results) {
            for (JsonNode item : result.trustedDocument().path(section)) {
                String id = item.path("id").asText("");
                if (id.isBlank()) {
                    throw new SemanticExtractionValidationException(
                            "normalized shard item is missing stable id in section " + section);
                }
                String hash = StableSemanticId.of("semantic-shard-variant", StableSemanticId.canonicalJson(item));
                variants.computeIfAbsent(id, ignored -> new ArrayList<>())
                        .add(new SemanticShardVariant(result.shardId(), hash, item));
            }
        }
        return variants;
    }

    private List<SemanticShardVariant> distinct(List<SemanticShardVariant> variants) {
        Map<String, SemanticShardVariant> result = new LinkedHashMap<>();
        variants.forEach(variant -> result.putIfAbsent(variant.hash(), variant));
        return List.copyOf(result.values());
    }
}
