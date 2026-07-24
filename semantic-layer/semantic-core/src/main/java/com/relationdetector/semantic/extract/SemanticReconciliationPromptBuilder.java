package com.relationdetector.semantic.extract;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * CN: 从 merged semantic 摘要、跨片冲突和 owner manifest 构造精简 reconciliation prompt；输出只允许 patch，不发送完整物理 KG 或允许改写事实。
 * EN: Builds a compact reconciliation prompt from merged semantic summaries, cross-shard conflicts, and ownership metadata. It requests a patch only and never sends or rewrites the full physical KG.
 */
public final class SemanticReconciliationPromptBuilder {
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public SemanticExtractionPrompt build(SemanticShardMergeResult merge, SemanticShardPlan plan) {
        if (merge == null || plan == null) {
            throw new IllegalArgumentException("semantic reconciliation inputs are required");
        }
        ObjectNode bundle = JSON.createObjectNode();
        bundle.put("kind", "SEMANTIC_RECONCILIATION");
        bundle.put("fullBundleHash", plan.fullBundleHash());
        ArrayNode shards = bundle.putArray("shards");
        plan.shards().forEach(shard -> shards.addObject()
                .put("id", shard.id())
                .put("ownerKey", shard.ownerKey())
                .put("estimatedInputTokens", shard.estimatedInputTokens()));
        bundle.set("semanticSummary", compact(merge.trustedMergedDocument()));
        bundle.set("conflicts", JSON.valueToTree(merge.conflicts()));
        bundle.putObject("instructions")
                .put("patchOnly", true)
                .put("newPhysicalFactsForbidden", true)
                .put("newEvidenceReferencesForbidden", true);
        return new SemanticExtractionPrompt(developerPrompt(), userPrompt(bundle), bundle);
    }

    public SemanticExtractionPrompt template(SemanticShardPlan plan) {
        ObjectNode empty = JSON.createObjectNode();
        for (String section : java.util.List.of(
                "entities", "events", "relations", "lineage", "metrics", "dimensions", "triplets", "reviewItems")) {
            empty.putArray(section);
        }
        SemanticExtractionPrompt prompt = build(new SemanticShardMergeResult(empty, java.util.List.of()), plan);
        ObjectNode bundle = ((ObjectNode) prompt.trustedEvidenceBundle()).deepCopy();
        bundle.put("template", true);
        return new SemanticExtractionPrompt(prompt.developerPrompt(), userPrompt(bundle), bundle);
    }

    private ObjectNode compact(ObjectNode merged) {
        ObjectNode compact = JSON.createObjectNode();
        for (String section : java.util.List.of(
                "entities", "events", "relations", "lineage", "metrics", "dimensions", "triplets", "reviewItems")) {
            ArrayNode output = compact.putArray(section);
            for (JsonNode item : merged.path(section)) {
                ObjectNode summary = output.addObject();
                copy(item, summary, "id");
                copy(item, summary, "name");
                copy(item, summary, "type");
                copy(item, summary, "machineType");
                copy(item, summary, "physicalName");
                copy(item, summary, "fromEntityRef");
                copy(item, summary, "toEntityRef");
                if (item.path("evidenceRefs").isArray()) {
                    summary.set("evidenceRefs", item.path("evidenceRefs").deepCopy());
                }
            }
        }
        return compact;
    }

    private void copy(JsonNode source, ObjectNode target, String field) {
        if (source.has(field)) target.set(field, source.path(field).deepCopy());
    }

    private String developerPrompt() {
        return """
                You reconcile already normalized evidence-grounded semantic shards.
                Return one JSON patch only with exactly these arrays:
                - resolutions: {section,id,selectedVariantHash} for every listed conflict.
                - renames: optional {section,id,name,description} display-only changes.
                - relations: optional semantic relations between existing entity ids with existing evidenceRefs.

                Never create physical facts, entity ids, candidate refs, or evidence refs.
                Never modify physical names, lineage, triplet candidate coverage, or governance status.
                Return JSON only.
                """;
    }

    private String userPrompt(ObjectNode bundle) {
        try {
            return "Reconcile this semantic shard summary and return the constrained patch:\n"
                    + JSON.writeValueAsString(bundle);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("failed to serialize reconciliation bundle", error);
        }
    }
}
