package com.relationdetector.core.scan;

import java.util.List;

import com.relationdetector.contracts.metadata.MetadataSnapshot;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.core.derived.DerivedPathInferenceService;
import com.relationdetector.core.metadata.MetadataEvidenceEnhancer;
import com.relationdetector.core.relation.NamingEvidenceExtractor;
import com.relationdetector.core.relation.NamingEvidencePool;
import com.relationdetector.core.relation.NamingMatchEvidenceEnhancer;

/**
 * Scan-level evidence enhancement shared by production scans and correctness.
 */
public final class EvidenceEnhancementService {
    private final MetadataEvidenceEnhancer metadataEvidenceEnhancer = new MetadataEvidenceEnhancer();
    private final NamingEvidenceExtractor namingEvidenceExtractor = new NamingEvidenceExtractor();
    private final NamingMatchEvidenceEnhancer namingMatchEvidenceEnhancer = new NamingMatchEvidenceEnhancer();
    private final DerivedPathInferenceService derivedPathInferenceService = new DerivedPathInferenceService();

    public void enhance(List<RelationshipCandidate> relationshipCandidates, NamingEvidencePool namingEvidencePool) {
        enhance(relationshipCandidates, namingEvidencePool, null, null);
    }

    public void enhance(
            List<RelationshipCandidate> relationshipCandidates,
            NamingEvidencePool namingEvidencePool,
            MetadataSnapshot metadataSnapshot
    ) {
        enhance(relationshipCandidates, namingEvidencePool, metadataSnapshot, null);
    }

    public void enhance(
            List<RelationshipCandidate> relationshipCandidates,
            NamingEvidencePool namingEvidencePool,
            MetadataSnapshot metadataSnapshot,
            ScanConfig config
    ) {
        if (metadataSnapshot != null) {
            namingEvidencePool.addAll(namingEvidenceExtractor.extractFromMetadata(metadataSnapshot, config));
            metadataEvidenceEnhancer.enhance(relationshipCandidates, metadataSnapshot);
        }
        namingEvidencePool.addAll(namingEvidenceExtractor.extractFromRelationshipCandidates(relationshipCandidates,
                config));
        namingEvidencePool.addAll(derivedPathInferenceService.deriveNamingEvidence(namingEvidencePool.merged(), config));
        namingMatchEvidenceEnhancer.enhance(relationshipCandidates, namingEvidencePool);
    }
}
