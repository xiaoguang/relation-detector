package com.relationdetector.semantic.extraction.prompt;

import com.relationdetector.semantic.extraction.shard.SemanticRunPlan;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * CN: 从跨片冲突及 owner manifest 构造有界 reconciliation prompt；输入只包含可选择的完整冲突变体，
 * 输出只允许 patch，禁止把无关 merged semantic 内容、完整物理 KG 或可改写事实的上下文发送给模型。
 * EN: Builds a bounded reconciliation prompt from cross-shard conflicts and ownership metadata. The input contains
 * only complete selectable conflict variants; unrelated merged semantic content and the full physical KG stay out of
 * the model context, and the output remains a constrained patch.
 */
public final class SemanticReconciliationPromptBuilder {
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public SemanticExtractionPrompt template(SemanticRunPlan plan) {
        ObjectNode bundle = JSON.createObjectNode();
        bundle.put("kind", "SEMANTIC_RECONCILIATION");
        bundle.put("fullBundleHash", plan.fullBundle().sha256());
        ArrayNode shards = bundle.putArray("shards");
        plan.shards().forEach(shard -> shards.addObject()
                .put("id", shard.id())
                .put("ownerKey", shard.ownerKey())
                .put("estimatedInputTokens", shard.estimatedInputTokens()));
        bundle.putArray("conflicts");
        bundle.put("template", true);
        bundle.putObject("instructions")
                .put("patchOnly", true)
                .put("newPhysicalFactsForbidden", true)
                .put("newEvidenceReferencesForbidden", true);
        return new SemanticExtractionPrompt(developerPrompt(), userPrompt(bundle), bundle);
    }

    private String developerPrompt() {
        return """
                You reconcile already normalized evidence-grounded semantic shards.
                Return one JSON patch only with exactly these arrays:
                - resolutions: {section,id,selectedVariantHash} for every listed conflict.
                - renames: optional {section,id,name,description} display-only changes.

                Name changes must preserve the existing canonical identity; descriptions may change display metadata only.
                Never create semantic objects or relations, physical facts, entity ids, candidate refs, or evidence refs.
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
