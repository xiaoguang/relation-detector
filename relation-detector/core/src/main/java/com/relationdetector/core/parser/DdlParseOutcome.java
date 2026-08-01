package com.relationdetector.core.parser;

import java.util.List;

import com.relationdetector.contracts.model.NamingEvidenceCandidate;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.core.ddl.DdlEvidenceInventory;
import com.relationdetector.core.ddl.DdlCatalogInventory;

/**
 * CN: 承载一批 typed DDL 产生的 relationship candidates、naming evidence 与 scan-level evidence inventory。
 * EN: Carries relationship candidates, naming evidence, and scan-level inventory produced by typed DDL parsing.
 */
public record DdlParseOutcome(
        List<RelationshipCandidate> relationships,
        List<NamingEvidenceCandidate> namingEvidence,
        DdlEvidenceInventory inventory,
        DdlCatalogInventory catalogInventory
) {
    public DdlParseOutcome {
        relationships = relationships == null ? List.of() : List.copyOf(relationships);
        namingEvidence = namingEvidence == null ? List.of() : List.copyOf(namingEvidence);
        inventory = inventory == null ? new DdlEvidenceInventory() : inventory;
        catalogInventory = catalogInventory == null ? new DdlCatalogInventory() : catalogInventory;
    }

    public DdlParseOutcome(
            List<RelationshipCandidate> relationships,
            List<NamingEvidenceCandidate> namingEvidence
    ) {
        this(relationships, namingEvidence, new DdlEvidenceInventory(), new DdlCatalogInventory());
    }

    public DdlParseOutcome(
            List<RelationshipCandidate> relationships,
            List<NamingEvidenceCandidate> namingEvidence,
            DdlEvidenceInventory inventory
    ) {
        this(relationships, namingEvidence, inventory, new DdlCatalogInventory());
    }
}
