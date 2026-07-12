package com.relationdetector.core.parser;

import java.util.List;

import com.relationdetector.contracts.model.NamingEvidenceCandidate;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.core.ddl.DdlEvidenceInventory;

public record DdlParseOutcome(
        List<RelationshipCandidate> relationships,
        List<NamingEvidenceCandidate> namingEvidence,
        DdlEvidenceInventory inventory
) {
    public DdlParseOutcome {
        relationships = relationships == null ? List.of() : List.copyOf(relationships);
        namingEvidence = namingEvidence == null ? List.of() : List.copyOf(namingEvidence);
        inventory = inventory == null ? new DdlEvidenceInventory() : inventory;
    }

    public DdlParseOutcome(
            List<RelationshipCandidate> relationships,
            List<NamingEvidenceCandidate> namingEvidence
    ) {
        this(relationships, namingEvidence, new DdlEvidenceInventory());
    }
}
