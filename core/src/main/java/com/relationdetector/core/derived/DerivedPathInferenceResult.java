package com.relationdetector.core.derived;

import java.util.List;

import com.relationdetector.contracts.model.DerivedPathCandidate;

public record DerivedPathInferenceResult(
        List<DerivedPathCandidate> derivedRelationships,
        List<DerivedPathCandidate> derivedDataLineages
) {
    public static DerivedPathInferenceResult empty() {
        return new DerivedPathInferenceResult(List.of(), List.of());
    }
}
