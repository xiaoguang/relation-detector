package com.relationdetector.semantic.extraction.prompt;

import java.util.LinkedHashSet;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * CN: 将 evidence-closed bundle 装配为严格 evidence-grounded developer/user prompts；prompt 声明引用闭包与禁止发明规则，但不执行模型请求。
 * EN: Assembles an evidence-closed bundle into developer and user prompts that enforce evidence grounding and reference closure. It does not execute a model request.
 */
public final class SemanticExtractionPromptBuilder {
    private static final ObjectMapper JSON = new ObjectMapper();

    public SemanticExtractionPrompt build(ObjectNode evidenceBundle) {
        if (evidenceBundle == null) {
            throw new IllegalArgumentException("semantic evidence bundle is required");
        }
        ObjectNode detached = evidenceBundle.deepCopy();
        return new SemanticExtractionPrompt(developerPrompt(), userPrompt(modelProjection(detached)), detached);
    }

    /**
     * CN: 返回固定 developer contract，约束模型只使用 bundle、保留 lineage、引用 event/triplet candidates 并输出 ref-closed document；无输入和副作用。
     * EN: Returns the fixed developer contract requiring bundle-only reasoning, preserved lineage, candidate references, and ref-closed output. It has no input or side effects.
     */
    private String developerPrompt() {
        return """
                You are an Evidence-Grounded Semantic Extractor for enterprise database metadata.

                Hard rules:
                - Only use the provided evidence bundle.
                - Do not invent database facts, physical tables, columns, joins, metrics, or lineage.
                - When shardContext is present, create output only for ownedFactRefs and ownedCandidateRefs.
                  overlapRefs are read-only context and must not become independently owned output.
                  Fact or candidate audit-reference arrays may be projected as *RefCount and *RefsSha256 fields.
                  Their exact IDs remain in the complete audit store and shard sidecar, not in this model context.
                  shardContext records the aggregate count and digest. Do not recreate or copy summarized refs into
                  model output; use the owned fact or candidate id as output evidence instead.
                - Every model-authored output item must include a non-empty ownedGroundingRefs array containing
                  current-shard ownedFactRefs or ownedCandidateRefs. evidenceRefs are audit context only and never
                  establish output ownership.
                - Do not mark anything as BUSINESS_APPROVED.
                - Every output item must include evidenceRefs that point back to stable ids in the input bundle.
                - If a business meaning or metric formula is uncertain, use reviewStatus=REVIEW_NEEDED.
                - Use human-readable Chinese labels for type and meaning; internal enum-like values may appear only in machineType.
                - Keep lineage as a first-class section. Triplets are summaries and must not replace lineage.
                - Produce a ref-closed semantic document: every entity, event, relation, lineage, metric, dimension,
                  triplet, and review item must have a stable id.
                - Leave event inputs, outputs, inputEntityRefs, and outputEntityRefs empty. The core rebuilds those
                  deterministic endpoint fields from the owned eventCandidate after validating this response.
                - Events must be grounded in eventCandidates. Include eventCandidateRef on each event and do not
                  create events that have no eventCandidate.
                - Enrich only eventCandidates for which you can add useful business meaning. The core deterministically
                  backfills every omitted owned event candidate after validating this response.
                - Prefer eventCandidates[].readableNameHint and businessActionHint when naming events, but keep
                  eventCandidateRef unchanged and use that owned candidate id as evidence.
                - Never create an event only from derivedLineage. Derived lineage may only explain a candidate through
                  supportingDerivedLineageRefs already present on that eventCandidate.
                - Relations must include fromEntityRef/toEntityRef when their endpoints match entities.
                - Lineage must include sourceEntityRefs/targetEntityRef when physical endpoints match entities.
                - Metrics and dimensions must include ownerEntityRef and, when applicable, sourceEntityRefs or
                  dimensionEntityRef.
                - Deterministic triplet candidates are intentionally omitted from the model projection. Leave triplets
                  empty unless an explicit tripletCandidate is present; the core deterministically backfills all omitted
                  owned triplets and rebuilds semanticGraph and validation after this response.
                - Review items should come from reviewItemCandidates or from unresolved/uncertain output items.
                  Preserve targetRef and targetSection whenever present.
                - Set semanticGraph and validation to null. The core rebuilds both after deterministic backfill,
                  normalization, ownership checks, and evidence closure.

                Required JSON output sections:
                - entities: business objects, master data, business documents, document lines, facts, dimensions.
                - events: business or data-processing actions derived from eventCandidates; use readable names and
                  descriptions, preserve eventCandidateRef, and ground evidence in that owned candidate id.
                - relations: human-readable business relationships between entities.
                - lineage: field-level data flow explanations.
                - metrics: metric candidates, aggregation suggestions, source fields, and review status.
                - dimensions: dimension candidates useful for analysis filters/grouping.
                - triplets: subject-predicate-object summaries with readable Chinese text.
                - reviewItems: uncertain items that need business or data owner review.
                - semanticGraph: null; the core rebuilds the graph from normalized sections.
                - validation: null; the core runs final reference and evidence closure validation.

                Return JSON only. Do not wrap it in Markdown.
                """;
    }

    private String userPrompt(ObjectNode evidenceBundle) {
        try {
            return """
                    Extract semantic enrichments from this relation-detector model projection. The complete evidence
                    bundle remains outside the prompt and is used by the core for deterministic candidate backfill,
                    owner validation, reference closure, and final KG construction.

                    Use these deterministic candidate sections as anchors:
                    - eventCandidates: allowed event facts; selectively enrich useful candidates and preserve eventCandidateRef.
                    - reviewItemCandidates: suggested audit items for diagnostics or uncertain facts.
                    - tripletCandidates are omitted from this projection and are materialized by the core.

                    Model projection:
                    %s
                    """.formatted(JSON.writeValueAsString(evidenceBundle));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("failed to serialize evidence bundle", e);
        }
    }

    private ObjectNode modelProjection(ObjectNode completeBundle) {
        ObjectNode projection = completeBundle.deepCopy();
        Set<String> deterministicTripletRefs = new LinkedHashSet<>();
        JsonNode tripletCandidates = projection.path("tripletCandidates");
        if (tripletCandidates.isArray()) {
            tripletCandidates.forEach(candidate -> {
                String id = candidate.path("id").asText("");
                if (!id.isBlank()) {
                    deterministicTripletRefs.add(id);
                }
            });
        }
        projection.remove("tripletCandidates");
        JsonNode shardContext = projection.path("shardContext");
        if (shardContext instanceof ObjectNode context && context.path("ownedCandidateRefs").isArray()) {
            ArrayNode modelOwnedCandidates = JSON.createArrayNode();
            context.path("ownedCandidateRefs").forEach(ref -> {
                if (ref.isTextual() && !deterministicTripletRefs.contains(ref.asText())) {
                    modelOwnedCandidates.add(ref.asText());
                }
            });
            context.set("ownedCandidateRefs", modelOwnedCandidates);
            context.put("deterministicBackfillCandidateCount", deterministicTripletRefs.size());
        }
        return projection;
    }
}
