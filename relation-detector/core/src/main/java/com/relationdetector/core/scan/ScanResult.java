package com.relationdetector.core.scan;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.model.DerivedPathCandidate;
import com.relationdetector.contracts.model.NamingEvidenceCandidate;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.WarningMessage;

/**
 *
 * Public scan result passed to JSON/table writers.
 */
public final class ScanResult {
    private final String databaseType;
    private final String catalog;
    private final String schema;
    private final Instant generatedAt;
    private final List<RelationshipCandidate> relationships = new ArrayList<>();
    private final List<DataLineageCandidate> dataLineages = new ArrayList<>();
    private final List<DerivedPathCandidate> derivedRelationships = new ArrayList<>();
    private final List<DerivedPathCandidate> derivedDataLineages = new ArrayList<>();
    private final List<NamingEvidenceCandidate> namingEvidence = new ArrayList<>();
    private final List<WarningMessage> warnings = new ArrayList<>();
    private final List<String> sources = new ArrayList<>();

    public ScanResult(String databaseType, String schema) {
        this(databaseType, null, schema);
    }

    public ScanResult(String databaseType, String catalog, String schema) {
        this.databaseType = databaseType;
        this.catalog = catalog;
        this.schema = schema;
        this.generatedAt = Instant.now();
    }

    public String databaseType() {
        return databaseType;
    }

    public String schema() {
        return schema;
    }

    public String catalog() {
        return catalog;
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

    public List<DerivedPathCandidate> derivedRelationships() {
        return derivedRelationships;
    }

    public List<DerivedPathCandidate> derivedDataLineages() {
        return derivedDataLineages;
    }

    public List<NamingEvidenceCandidate> namingEvidence() {
        return namingEvidence;
    }

    public List<WarningMessage> warnings() {
        return warnings;
    }

    public List<String> sources() {
        return sources;
    }
}
