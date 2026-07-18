package com.relationdetector.core.derived;

import java.util.List;

import com.relationdetector.contracts.model.DerivedPathCandidate;
import com.relationdetector.contracts.model.NamingEvidenceCandidate;

/**
 * CN: 承载一次 derived inference 的 relationship paths、lineage paths 与 naming evidence 结果。
 * EN: Carries relationship paths, lineage paths, and naming evidence from one derived-inference run.
 */
public record DerivedPathInferenceResult(
        List<DerivedPathCandidate> derivedRelationships,
        List<DerivedPathCandidate> derivedDataLineages,
        List<NamingEvidenceCandidate> derivedNamingEvidence
) {
    public static DerivedPathInferenceResult empty() {
        return new DerivedPathInferenceResult(List.of(), List.of(), List.of());
    }
}
