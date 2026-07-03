package com.relationdetector.core.scan;

import java.util.List;

import com.relationdetector.contracts.model.NamingEvidenceCandidate;
import com.relationdetector.core.metadata.MetadataEvidenceEnhancer;
import com.relationdetector.core.relation.NamingEvidenceExtractor;
import com.relationdetector.core.relation.NamingMatchEvidenceEnhancer;

final class EvidenceEnhancementPipeline {
    private final MetadataEvidenceEnhancer metadataEvidenceEnhancer = new MetadataEvidenceEnhancer();
    private final NamingEvidenceExtractor namingEvidenceExtractor = new NamingEvidenceExtractor();
    private final NamingMatchEvidenceEnhancer namingMatchEvidenceEnhancer = new NamingMatchEvidenceEnhancer();

    void enhance(ScanPipelineContext ctx) {
        if (ctx.metadataSnapshot != null) {
            addNamingEvidence(ctx, namingEvidenceExtractor.extractFromMetadata(ctx.metadataSnapshot));
            metadataEvidenceEnhancer.enhance(ctx.relationshipCandidates, ctx.metadataSnapshot);
        }
        addNamingEvidence(ctx, namingEvidenceExtractor.extractFromRelationshipCandidates(ctx.relationshipCandidates));
        namingMatchEvidenceEnhancer.enhance(ctx.relationshipCandidates, ctx.result.namingEvidence());
    }

    private void addNamingEvidence(ScanPipelineContext ctx, List<NamingEvidenceCandidate> evidence) {
        for (NamingEvidenceCandidate candidate : evidence) {
            if (ctx.result.namingEvidence().stream().noneMatch(existing -> sameNamingEvidence(existing, candidate))) {
                ctx.result.namingEvidence().add(candidate);
            }
        }
    }

    private boolean sameNamingEvidence(NamingEvidenceCandidate left, NamingEvidenceCandidate right) {
        return left.source().normalizedKey().equals(right.source().normalizedKey())
                && left.target().normalizedKey().equals(right.target().normalizedKey())
                && left.rule().equals(right.rule());
    }
}
