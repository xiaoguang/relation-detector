package com.relationdetector.core.scan;

import java.util.List;

import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.model.NamingEvidenceCandidate;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.WarningMessage;

/**
 * Per-statement structured execution output before scan-level merge.
 */
public record StatementExecutionOutcome(
        List<RelationshipCandidate> relationshipCandidates,
        List<DataLineageCandidate> lineageCandidates,
        List<NamingEvidenceCandidate> namingEvidence,
        List<WarningMessage> warnings
) {
    public static StatementExecutionOutcome empty() {
        return new StatementExecutionOutcome(List.of(), List.of(), List.of(), List.of());
    }
}
