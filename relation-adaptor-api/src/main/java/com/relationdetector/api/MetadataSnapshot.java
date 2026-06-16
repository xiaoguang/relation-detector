package com.relationdetector.api;

import java.util.ArrayList;
import java.util.List;

/** Raw metadata result returned by adaptors before core scoring and merging. */
public final class MetadataSnapshot {
    private final List<TableId> tables = new ArrayList<>();
    private final List<ColumnRef> columns = new ArrayList<>();
    private final List<RelationshipCandidate> relationships = new ArrayList<>();
    private final List<Evidence> auxiliaryEvidence = new ArrayList<>();
    private final List<WarningMessage> warnings = new ArrayList<>();

    public List<TableId> tables() {
        return tables;
    }

    public List<ColumnRef> columns() {
        return columns;
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
