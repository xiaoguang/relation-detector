package com.relationdetector.api;

import java.util.ArrayList;
import java.util.List;

/** Raw metadata result returned by adaptors before core scoring and merging. */
public final class MetadataSnapshot {
    private final List<TableId> tables = new ArrayList<>();
    private final List<ColumnRef> columns = new ArrayList<>();
    private final List<MetadataTableFact> tableFacts = new ArrayList<>();
    private final List<MetadataColumnFact> columnFacts = new ArrayList<>();
    private final List<MetadataIndexFact> indexFacts = new ArrayList<>();
    private final List<MetadataConstraintFact> constraintFacts = new ArrayList<>();
    private final List<RelationshipCandidate> relationships = new ArrayList<>();
    private final List<Evidence> auxiliaryEvidence = new ArrayList<>();
    private final List<WarningMessage> warnings = new ArrayList<>();

    public List<TableId> tables() {
        return tables;
    }

    public List<ColumnRef> columns() {
        return columns;
    }

    public List<MetadataTableFact> tableFacts() {
        return tableFacts;
    }

    public List<MetadataColumnFact> columnFacts() {
        return columnFacts;
    }

    public List<MetadataIndexFact> indexFacts() {
        return indexFacts;
    }

    public List<MetadataConstraintFact> constraintFacts() {
        return constraintFacts;
    }

    public List<RelationshipCandidate> relationships() {
        return relationships;
    }

    public List<Evidence> auxiliaryEvidence() {
        return auxiliaryEvidence;
    }

    public List<WarningMessage> warnings() {
        return warnings;
    }
}
