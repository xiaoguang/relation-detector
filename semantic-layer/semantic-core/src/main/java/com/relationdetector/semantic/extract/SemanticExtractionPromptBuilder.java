package com.relationdetector.semantic.extract;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.reader.ScanBundle;

/**
 * CN: 将 compact evidence bundle 装配为严格 evidence-grounded developer/user prompts；prompt 声明引用闭包与禁止发明规则，但不执行模型请求。
 * EN: Assembles a compact evidence bundle into developer and user prompts that enforce evidence grounding and reference closure. It does not execute a model request.
 */
public final class SemanticExtractionPromptBuilder {
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final SemanticExtractionBundleBuilder bundleBuilder;

    public SemanticExtractionPromptBuilder() {
        this(new SemanticExtractionBundleBuilder());
    }

    SemanticExtractionPromptBuilder(SemanticExtractionBundleBuilder bundleBuilder) {
        this.bundleBuilder = bundleBuilder;
    }

    public SemanticExtractionPrompt build(
            ScanBundle bundle,
            String focus,
            int maxRelationships,
            int maxLineage,
            int maxNamingEvidence
    ) {
        ObjectNode evidenceBundle = bundleBuilder.build(bundle, focus, maxRelationships, maxLineage, maxNamingEvidence);
        return new SemanticExtractionPrompt(developerPrompt(), userPrompt(evidenceBundle), evidenceBundle);
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
                - Do not mark anything as BUSINESS_APPROVED.
                - Every output item must include evidenceRefs that point back to stable ids in the input bundle.
                - If a business meaning or metric formula is uncertain, use reviewStatus=REVIEW_NEEDED.
                - Use human-readable Chinese labels for type and meaning; internal enum-like values may appear only in machineType.
                - Keep lineage as a first-class section. Triplets are summaries and must not replace lineage.
                - Produce a ref-closed semantic document: every entity, event, relation, lineage, metric, dimension,
                  triplet, and review item must have a stable id.
                - Events must reference entities through inputEntityRefs/outputEntityRefs when possible.
                - Events must be grounded in eventCandidates. Include eventCandidateRef on each event and do not
                  create events that have no eventCandidate.
                - Do not omit eventCandidates. If you cannot improve one, emit a conservative event using its
                  readableNameHint, input/output tables, and evidenceRefs.
                - Prefer eventCandidates[].readableNameHint and businessActionHint when naming events, but keep
                  eventCandidateRef and evidenceRefs unchanged.
                - Never create an event only from derivedLineage. Derived lineage may only explain a candidate through
                  supportingDerivedLineageRefs already present on that eventCandidate.
                - Relations must include fromEntityRef/toEntityRef when their endpoints match entities.
                - Lineage must include sourceEntityRefs/targetEntityRef when physical endpoints match entities.
                - Metrics and dimensions must include ownerEntityRef and, when applicable, sourceEntityRefs or
                  dimensionEntityRef.
                - Triplets must be grounded in tripletCandidates. Include candidateRef on every triplet. Use triplets
                  as readable summaries across entity relations, event input/output, metrics, dimensions, lineage
                  transforms, and naming aliases; do not use triplets as a replacement for lineage.
                - Do not omit tripletCandidates. If a candidate is repetitive, still emit the triplet with its
                  candidateRef and evidenceRefs so downstream KG construction remains complete.
                - Review items should come from reviewItemCandidates or from unresolved/uncertain output items.
                  Preserve targetRef and targetSection whenever present.
                - Include semanticGraph with nodes and edges, using the same ids as the top-level sections.
                - Include validation with isRefClosed and isolatedEntities. Do not hide isolated entities; report them.

                Required JSON output sections:
                - entities: business objects, master data, business documents, document lines, facts, dimensions.
                - events: business or data-processing actions derived from eventCandidates; use readable names and
                  descriptions, but preserve eventCandidateRef and evidenceRefs.
                - relations: human-readable business relationships between entities.
                - lineage: field-level data flow explanations.
                - metrics: metric candidates, aggregation suggestions, source fields, and review status.
                - dimensions: dimension candidates useful for analysis filters/grouping.
                - triplets: subject-predicate-object summaries with readable Chinese text.
                - reviewItems: uncertain items that need business or data owner review.
                - semanticGraph: id-based nodes and edges linking the sections above.
                - validation: ref-closure and review diagnostics for the generated semantic document.

                Return JSON only. Do not wrap it in Markdown.
                """;
    }

    private String userPrompt(ObjectNode evidenceBundle) {
        try {
            return """
                    Extract semantic candidates from this relation-detector evidence bundle.

                    Use these deterministic candidate sections as anchors:
                    - eventCandidates: allowed event facts; event output must preserve eventCandidateRef.
                    - reviewItemCandidates: suggested audit items for diagnostics or uncertain facts.
                    - tripletCandidates: allowed triplet summaries; triplet output must preserve candidateRef.

                    Evidence bundle:
                    %s
                    """.formatted(JSON.writeValueAsString(evidenceBundle));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("failed to serialize evidence bundle", e);
        }
    }
}
