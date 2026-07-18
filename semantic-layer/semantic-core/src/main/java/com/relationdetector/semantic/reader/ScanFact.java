package com.relationdetector.semantic.reader;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * CN: relationship、lineage、naming 或 diagnostic 的 sealed typed reader view，保留 stable id 和原始 JSON document 供 evidence audit。
 * EN: Sealed typed reader view of one relationship, lineage, naming, or diagnostic fact, preserving stable identity and source JSON for evidence audit.
 */
public sealed interface ScanFact permits ScanRelationshipFact, ScanLineageFact, ScanNamingEvidenceFact,
        ScanDiagnosticFact {
    String id();

    JsonNode document();
}
