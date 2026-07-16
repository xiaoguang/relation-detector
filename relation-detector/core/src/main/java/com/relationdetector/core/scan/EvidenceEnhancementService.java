package com.relationdetector.core.scan;

import java.util.List;

import com.relationdetector.contracts.metadata.MetadataSnapshot;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.core.derived.DerivedPathInferenceService;
import com.relationdetector.core.metadata.MetadataEvidenceEnhancer;
import com.relationdetector.core.naming.NamingEvidenceExtractor;
import com.relationdetector.core.naming.NamingEvidencePool;
import com.relationdetector.core.naming.NamingMatchEvidenceEnhancer;
import com.relationdetector.core.identity.NamespaceContext;
import com.relationdetector.core.identity.CanonicalEndpointKeyProvider;
import com.relationdetector.contracts.spi.IdentifierRules;

/**
 *
 * Scan-level evidence enhancement shared by production scans and correctness.
 */
public final class EvidenceEnhancementService {
    private final MetadataEvidenceEnhancer metadataEvidenceEnhancer = new MetadataEvidenceEnhancer();
    private final NamingMatchEvidenceEnhancer namingMatchEvidenceEnhancer = new NamingMatchEvidenceEnhancer();

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
        enhance(relationshipCandidates, namingEvidencePool, metadataSnapshot, config,
                value -> value == null ? "" : value.strip().toLowerCase(java.util.Locale.ROOT),
                NamespaceContext.empty());
    }

    public void enhance(
            List<RelationshipCandidate> relationshipCandidates,
            NamingEvidencePool namingEvidencePool,
            MetadataSnapshot metadataSnapshot,
            ScanConfig config,
            IdentifierRules identifierRules,
            NamespaceContext namespace
    ) {
        NamingEvidenceExtractor namingEvidenceExtractor = new NamingEvidenceExtractor(
                new CanonicalEndpointKeyProvider(identifierRules, namespace));
        if (metadataSnapshot != null) {
            namingEvidencePool.addAll(namingEvidenceExtractor.extractFromMetadata(metadataSnapshot, config));
            metadataEvidenceEnhancer.enhance(
                    relationshipCandidates, metadataSnapshot, identifierRules, namespace);
        }
        namingEvidencePool.addAll(namingEvidenceExtractor.extractFromRelationshipCandidates(relationshipCandidates,
                config));
        namingEvidencePool.addAll(new DerivedPathInferenceService(
                new CanonicalEndpointKeyProvider(identifierRules, namespace))
                .deriveNamingEvidence(namingEvidencePool.merged(), config));
        namingMatchEvidenceEnhancer.enhance(relationshipCandidates, namingEvidencePool);
    }

    public void enhanceProfiledCandidates(
            List<RelationshipCandidate> profiledCandidates,
            NamingEvidencePool namingEvidencePool,
            ScanConfig config
    ) {
        enhanceProfiledCandidates(profiledCandidates, namingEvidencePool, config,
                value -> value == null ? "" : value.strip().toLowerCase(java.util.Locale.ROOT),
                NamespaceContext.empty());
    }

    public void enhanceProfiledCandidates(
            List<RelationshipCandidate> profiledCandidates,
            NamingEvidencePool namingEvidencePool,
            ScanConfig config,
            IdentifierRules identifierRules,
            NamespaceContext namespace
    ) {
        if (profiledCandidates == null || profiledCandidates.isEmpty()) {
            return;
        }
        NamingEvidenceExtractor namingEvidenceExtractor = new NamingEvidenceExtractor(
                new CanonicalEndpointKeyProvider(identifierRules, namespace));
        namingEvidencePool.addAll(namingEvidenceExtractor.extractFromRelationshipCandidates(
                profiledCandidates, config));
        namingMatchEvidenceEnhancer.enhance(profiledCandidates, namingEvidencePool);
    }
}
