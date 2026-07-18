package com.relationdetector.semantic.extract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.extract.SemanticReferenceValidator.Session;
import com.relationdetector.semantic.extract.SemanticSectionNormalizer.NormalizationResult;
import com.relationdetector.semantic.extract.model.SemanticExtractionDocument;

/**
 * CN: 编排 typed document decode、candidate backfill、section normalization、ID/reference/physical validation、review 和 graph assembly；缺少 evidence bundle 或 closure 失败时原子拒绝。
 * EN: Orchestrates typed document decoding, candidate backfill, section normalization, id/reference/physical validation, review generation, and graph assembly. Missing evidence or failed closure rejects the result atomically.
 */
public final class SemanticExtractionDocumentNormalizer {
    private final SemanticExtractionDocumentCodec codec;
    private final SemanticCandidateBackfill candidateBackfill;
    private final SemanticSectionNormalizer sectionNormalizer;
    private final SemanticReviewGenerator reviewGenerator;
    private final SemanticReferenceValidator referenceValidator;

    public SemanticExtractionDocumentNormalizer() {
        this(new SemanticExtractionDocumentCodec(), new SemanticCandidateBackfill(),
                new SemanticSectionNormalizer(), new SemanticReviewGenerator(), new SemanticReferenceValidator());
    }

    SemanticExtractionDocumentNormalizer(
            SemanticExtractionDocumentCodec codec,
            SemanticCandidateBackfill candidateBackfill,
            SemanticSectionNormalizer sectionNormalizer,
            SemanticReviewGenerator reviewGenerator,
            SemanticReferenceValidator referenceValidator
    ) {
        this.codec = codec;
        this.candidateBackfill = candidateBackfill;
        this.sectionNormalizer = sectionNormalizer;
        this.reviewGenerator = reviewGenerator;
        this.referenceValidator = referenceValidator;
    }

    public ObjectNode normalize(JsonNode rawDocument) {
        throw new IllegalArgumentException("semantic evidence bundle is required for formal normalization");
    }

    public ObjectNode normalize(JsonNode rawDocument, JsonNode evidenceBundle) {
        SemanticReferenceIndex referenceIndex = SemanticReferenceIndex.from(evidenceBundle);
        SemanticPhysicalReferenceIndex physicalIndex = SemanticPhysicalReferenceIndex.from(evidenceBundle);
        SemanticExtractionDocument document = codec.read(rawDocument);
        candidateBackfill.apply(document, evidenceBundle);
        SemanticGraphAssembler graph = new SemanticGraphAssembler();
        Session validation = referenceValidator.newSession(referenceIndex, physicalIndex);
        NormalizationResult normalized = sectionNormalizer.normalizeFacts(document, graph, validation);
        validation.addGeneratedReviewItems(reviewGenerator.generate(document));
        sectionNormalizer.normalizeReviewItems(document.reviewItems, graph, validation);
        document.semanticGraph = graph.build();
        document.validation = validation.build(document.entities, normalized.linkedEntities());
        if (!document.validation.isRefClosed()) {
            throw new SemanticExtractionValidationException(
                    "semantic extraction contains unresolved references: " + document.validation);
        }
        return codec.write(document);
    }
}
