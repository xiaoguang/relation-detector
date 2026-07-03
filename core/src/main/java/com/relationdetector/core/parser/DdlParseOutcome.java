package com.relationdetector.core.parser;

import java.util.List;

import com.relationdetector.contracts.model.NamingEvidenceCandidate;
import com.relationdetector.contracts.model.RelationshipCandidate;

public record DdlParseOutcome(
        List<RelationshipCandidate> relationships,
        List<NamingEvidenceCandidate> namingEvidence
) {
    public DdlParseOutcome {
        relationships = relationships == null ? List.of() : List.copyOf(relationships);
        namingEvidence = namingEvidence == null ? List.of() : List.copyOf(namingEvidence);
    }
}
