package com.relationdetector.semantic.reader;

import com.fasterxml.jackson.databind.JsonNode;

/** Typed semantic-layer view of one relation-detector output fact. */
public sealed interface ScanFact permits ScanRelationshipFact, ScanLineageFact, ScanNamingEvidenceFact,
        ScanDiagnosticFact {
    String id();

    JsonNode document();
}
