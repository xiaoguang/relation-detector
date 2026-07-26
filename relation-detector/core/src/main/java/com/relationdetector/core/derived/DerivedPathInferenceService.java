package com.relationdetector.core.derived;

import java.util.ArrayList;
import java.util.List;

import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.model.NamingEvidenceCandidate;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.core.identity.CanonicalEndpointKeyProvider;
import com.relationdetector.core.naming.NamingEvidenceMerger;
import com.relationdetector.core.scan.ScanConfig;

/**
 * CN: 在已合并的 typed direct facts 上编排 relationship、lineage 和 naming derived inference，不重新运行 parser 或 naming rules。
 * EN: Orchestrates relationship, lineage, and naming inference over merged typed facts without rerunning parsers or naming rules.
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
        return infer(List.of(), List.of(), namingEvidence, config).derivedNamingEvidence();
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
        List<NamingEvidenceCandidate> derivedNaming = new ArrayList<>(
                relationshipsResult.derivedNamingEvidence());
        if (config.derivedNamingEvidenceEnabled) {
            derivedNaming.addAll(namingInference.derive(namingEvidence));
        }
        List<NamingEvidenceCandidate> mergedNaming = new NamingEvidenceMerger(endpointKeys)
                .merge(derivedNaming);
        return new DerivedResultSelector(endpointKeys).select(
                relationshipsResult.derivedRelationships(),
                lineages,
                mergedNaming,
                namingEvidence,
                derivedNaming,
                config.derivedMaxFacts);
    }

    private boolean enabled(ScanConfig config) {
        return config != null && config.derivedPathsEnabled;
    }
}
