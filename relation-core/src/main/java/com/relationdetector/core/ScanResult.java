package com.relationdetector.core;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.relationdetector.api.DataLineageCandidate;
import com.relationdetector.api.RelationshipCandidate;
import com.relationdetector.api.WarningMessage;

/** Public scan result passed to JSON/table writers. */
public final class ScanResult {
    private final String databaseType;
    private final String schema;
    private final Instant generatedAt;
    private final List<RelationshipCandidate> relationships = new ArrayList<>();
    private final List<DataLineageCandidate> dataLineages = new ArrayList<>();
    private final List<WarningMessage> warnings = new ArrayList<>();
    private final List<String> sources = new ArrayList<>();

    public ScanResult(String databaseType, String schema) {
        this.databaseType = databaseType;
        this.schema = schema;
        this.generatedAt = Instant.now();
    }

    public String databaseType() {
        return databaseType;
    }

    public String schema() {
        return schema;
    }

    public Instant generatedAt() {
        return generatedAt;
    }

    public List<RelationshipCandidate> relationships() {
        return relationships;
    }

    public List<DataLineageCandidate> dataLineages() {
        return dataLineages;
    }

    public List<WarningMessage> warnings() {
        return warnings;
    }

    public List<String> sources() {
        return sources;
    }
}
