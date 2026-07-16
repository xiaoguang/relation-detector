package com.relationdetector.core.derived;

import java.util.List;

import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.model.NamingEvidenceCandidate;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.core.identity.CanonicalEndpointKeyProvider;
import com.relationdetector.core.scan.ScanConfig;

/**
 *
 * Orchestrates derived inference over already merged, typed facts.
 */
public final class DerivedPathInferenceService {
    private final CanonicalEndpointKeyProvider endpointKeys;

    public DerivedPathInferenceService() {
        this(CanonicalEndpointKeyProvider.defaults());
    }

    public DerivedPathInferenceService(CanonicalEndpointKeyProvider endpointKeys) {
        this.endpointKeys = endpointKeys;
    }

    public List<NamingEvidenceCandidate> deriveNamingEvidence(
            List<NamingEvidenceCandidate> namingEvidence,
            ScanConfig config
    ) {
        if (!enabled(config) || !config.derivedNamingEvidenceEnabled) {
            return List.of();
        }
        DerivedPathGraphBuilder graphBuilder = new DerivedPathGraphBuilder(config, endpointKeys);
        return new DerivedNamingInference(config, graphBuilder).derive(namingEvidence);
    }

    public DerivedPathInferenceResult infer(
            List<RelationshipCandidate> relationships,
            List<DataLineageCandidate> dataLineages,
            List<NamingEvidenceCandidate> namingEvidence,
            ScanConfig config
    ) {
        if (!enabled(config)) {
            return DerivedPathInferenceResult.empty();
        }
        DerivedPathGraphBuilder graphBuilder = new DerivedPathGraphBuilder(config, endpointKeys);
        DerivedNamingInference namingInference = new DerivedNamingInference(config, graphBuilder);
        DerivedRelationshipInference.Result relationshipsResult = config.derivedRelationshipsEnabled
                ? new DerivedRelationshipInference(config, graphBuilder, namingInference)
                .infer(relationships, namingEvidence)
                : new DerivedRelationshipInference.Result(List.of(), List.of());
        var lineages = config.derivedDataLineageEnabled
                ? new DerivedLineageInference(config, graphBuilder).infer(dataLineages)
                : List.<com.relationdetector.contracts.model.DerivedPathCandidate>of();
        return new DerivedPathInferenceResult(
                relationshipsResult.derivedRelationships(),
                lineages,
                relationshipsResult.derivedNamingEvidence());
    }

    private boolean enabled(ScanConfig config) {
        return config != null && config.derivedPathsEnabled;
    }
}
