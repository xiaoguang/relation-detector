package com.relationdetector.core.derived;

import java.util.List;

import com.relationdetector.contracts.model.DerivedPathCandidate;
import com.relationdetector.contracts.model.NamingEvidenceCandidate;

public record DerivedPathInferenceResult(
        List<DerivedPathCandidate> derivedRelationships,
        List<DerivedPathCandidate> derivedDataLineages,
        List<NamingEvidenceCandidate> derivedNamingEvidence
) {
    public static DerivedPathInferenceResult empty() {
        return new DerivedPathInferenceResult(List.of(), List.of(), List.of());
    }
}
