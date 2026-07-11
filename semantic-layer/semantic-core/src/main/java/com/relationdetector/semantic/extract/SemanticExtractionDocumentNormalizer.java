package com.relationdetector.semantic.extract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.extract.SemanticReferenceValidator.Session;
import com.relationdetector.semantic.extract.SemanticSectionNormalizer.NormalizationResult;
import com.relationdetector.semantic.extract.model.SemanticExtractionDocument;

/** Coordinates typed, ref-closed normalization of LLM semantic extraction output. */
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
        return normalize(rawDocument, null);
    }

    public ObjectNode normalize(JsonNode rawDocument, JsonNode evidenceBundle) {
        SemanticExtractionDocument document = codec.read(rawDocument);
        candidateBackfill.apply(document, evidenceBundle);
        SemanticGraphAssembler graph = new SemanticGraphAssembler();
        Session validation = referenceValidator.newSession();
        NormalizationResult normalized = sectionNormalizer.normalizeFacts(document, graph, validation);
        validation.addGeneratedReviewItems(reviewGenerator.generate(document));
        sectionNormalizer.normalizeReviewItems(document.reviewItems, graph, validation);
        document.semanticGraph = graph.build();
        document.validation = validation.build(document.entities, normalized.linkedEntities());
        return codec.write(document);
    }
}
