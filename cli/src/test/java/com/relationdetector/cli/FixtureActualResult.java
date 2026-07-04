package com.relationdetector.cli;

import java.util.List;

import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.model.NamingEvidenceCandidate;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.WarningMessage;

record FixtureActualResult(
        List<RelationshipCandidate> relationships,
        List<DataLineageCandidate> lineages,
        List<NamingEvidenceCandidate> namingEvidence,
        List<WarningMessage> warnings
) {
}
