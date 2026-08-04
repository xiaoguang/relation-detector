package com.relationdetector.semantic.extraction.normalization;

import com.relationdetector.semantic.extraction.shard.SemanticShardOutputOwnershipValidator;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.extraction.normalization.SemanticReferenceValidator.Session;
import com.relationdetector.semantic.extraction.normalization.SemanticSectionNormalizer.NormalizationResult;
import com.relationdetector.semantic.extraction.model.SemanticExtractionDocument;
import com.relationdetector.semantic.extraction.model.SemanticItem;

/**
 * CN: 编排 typed document decode、candidate backfill、section normalization、ID/reference/physical validation、review 和 graph assembly；缺少 evidence bundle 或 closure 失败时原子拒绝。
 * EN: Orchestrates typed document decoding, candidate backfill, section normalization, id/reference/physical validation, review generation, and graph assembly. Missing evidence or failed closure rejects the result atomically.
 */
public final class SemanticExtractionDocumentNormalizer {
    private final SemanticExtractionDocumentCodec codec;
    private final SemanticCandidateBackfill candidateBackfill;
    private final SemanticFormalIdentityCanonicalizer formalIdentityCanonicalizer;
    private final SemanticSectionNormalizer sectionNormalizer;
    private final SemanticReviewGenerator reviewGenerator;
    private final SemanticReferenceValidator referenceValidator;
    private final SemanticShardOutputOwnershipValidator ownershipValidator;

    public SemanticExtractionDocumentNormalizer() {
        this(new SemanticExtractionDocumentCodec(), new SemanticCandidateBackfill(),
                new SemanticFormalIdentityCanonicalizer(), new SemanticSectionNormalizer(),
                new SemanticReviewGenerator(), new SemanticReferenceValidator(),
                new SemanticShardOutputOwnershipValidator());
    }

    public SemanticExtractionDocumentNormalizer(
            SemanticExtractionDocumentCodec codec,
            SemanticCandidateBackfill candidateBackfill,
            SemanticFormalIdentityCanonicalizer formalIdentityCanonicalizer,
            SemanticSectionNormalizer sectionNormalizer,
            SemanticReviewGenerator reviewGenerator,
            SemanticReferenceValidator referenceValidator,
            SemanticShardOutputOwnershipValidator ownershipValidator
    ) {
        this.codec = codec;
        this.candidateBackfill = candidateBackfill;
        this.formalIdentityCanonicalizer = formalIdentityCanonicalizer;
        this.sectionNormalizer = sectionNormalizer;
        this.reviewGenerator = reviewGenerator;
        this.referenceValidator = referenceValidator;
        this.ownershipValidator = ownershipValidator;
    }

    /**
     * CN: 对 standalone 与自动 shard 使用同一 owner-aware 入口；先验证 shardContext 和 raw output 所有权，
     * 再执行正式 normalization，任一越界或闭包失败都不返回部分文档。
     * EN: Uses one owner-aware entry point for standalone and planned shards. It validates shardContext and raw-output
     * ownership before formal normalization, returning no partial document after an ownership or closure failure.
     */
    public ObjectNode normalizeOwnedShard(JsonNode rawDocument, JsonNode evidenceBundle) {
        ownershipValidator.validate(rawDocument, evidenceBundle);
        return normalize(rawDocument, evidenceBundle);
    }

    ObjectNode normalize(JsonNode rawDocument, JsonNode evidenceBundle) {
        SemanticReferenceIndex referenceIndex = SemanticReferenceIndex.from(evidenceBundle);
        SemanticPhysicalReferenceIndex physicalIndex = SemanticPhysicalReferenceIndex.from(evidenceBundle);
        SemanticExtractionDocument document = codec.read(rawDocument);
        candidateBackfill.apply(document, evidenceBundle);
        applyReviewDefaults(document);
        SemanticFormalIdentityCanonicalizer.CanonicalizationState canonicalization =
                formalIdentityCanonicalizer.canonicalizeFacts(document);
        formalIdentityCanonicalizer.canonicalizeReviewItems(document.reviewItems, canonicalization);
        SemanticGraphAssembler graph = new SemanticGraphAssembler();
        Session validation = referenceValidator.newSession(referenceIndex, physicalIndex);
        NormalizationResult normalized = sectionNormalizer.normalizeFacts(document, graph, validation);
        validation.addGeneratedReviewItems(reviewGenerator.generate(document));
        formalIdentityCanonicalizer.canonicalizeReviewItems(document.reviewItems, canonicalization);
        sectionNormalizer.normalizeReviewItems(document.reviewItems, graph, validation);
        document.semanticGraph = graph.build();
        document.validation = validation.build(document.entities, normalized.linkedEntities());
        if (!document.validation.isRefClosed()) {
            throw new SemanticExtractionValidationException(
                    "semantic extraction contains unresolved references: " + document.validation);
        }
        return codec.write(document);
    }

    private void applyReviewDefaults(SemanticExtractionDocument document) {
        defaultReviewStatus(document.entities, "SYSTEM_PROPOSED");
        defaultReviewStatus(document.events, "SYSTEM_PROPOSED");
        defaultReviewStatus(document.relations, "SYSTEM_PROPOSED");
        defaultReviewStatus(document.lineage, "SYSTEM_PROPOSED");
        defaultReviewStatus(document.metrics, "SYSTEM_PROPOSED");
        defaultReviewStatus(document.dimensions, "SYSTEM_PROPOSED");
        defaultReviewStatus(document.triplets, "SYSTEM_PROPOSED");
        defaultReviewStatus(document.reviewItems, "REVIEW_NEEDED");
    }

    private void defaultReviewStatus(List<? extends SemanticItem> items, String value) {
        for (SemanticItem item : items) {
            if (item.reviewStatus == null || item.reviewStatus.isBlank()) {
                item.reviewStatus = value;
            }
        }
    }
}
