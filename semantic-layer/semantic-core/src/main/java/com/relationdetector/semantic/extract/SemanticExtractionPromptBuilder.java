package com.relationdetector.semantic.extract;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.reader.ScanBundle;

/** Creates the prompt used for LLM semantic extraction. */
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

    private String developerPrompt() {
        return """
                You are an Evidence-Grounded Semantic Extractor for enterprise database metadata.

                Hard rules:
                - Only use the provided evidence bundle.
                - Do not invent database facts, physical tables, columns, joins, metrics, or lineage.
                - Do not mark anything as BUSINESS_APPROVED.
                - Every output item must include evidenceRefs that point back to the input evidence.
                - If a business meaning or metric formula is uncertain, use reviewStatus=REVIEW_NEEDED.
                - Use human-readable Chinese labels for type and meaning; internal enum-like values may appear only in machineType.
                - Keep lineage as a first-class section. Triplets are summaries and must not replace lineage.
                - Produce a ref-closed semantic document: every entity, event, relation, lineage, metric, dimension,
                  triplet, and review item must have a stable id.
                - Events must reference entities through inputEntityRefs/outputEntityRefs when possible.
                - Relations must include fromEntityRef/toEntityRef when their endpoints match entities.
                - Lineage must include sourceEntityRefs/targetEntityRef when physical endpoints match entities.
                - Metrics and dimensions must include ownerEntityRef and, when applicable, sourceEntityRefs or
                  dimensionEntityRef.
                - Include semanticGraph with nodes and edges, using the same ids as the top-level sections.
                - Include validation with isRefClosed and isolatedEntities. Do not hide isolated entities; report them.

                Required JSON output sections:
                - entities: business objects, master data, business documents, document lines, facts, dimensions.
                - events: business or data-processing actions inferred from routines, SQL writes, or source locations.
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

                    Evidence bundle:
                    %s
                    """.formatted(JSON.writeValueAsString(evidenceBundle));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("failed to serialize evidence bundle", e);
        }
    }
}
