package com.relationdetector.core.result;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.relationdetector.contracts.Enums.MetadataInventoryStatus;
import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.model.DerivedPathCandidate;
import com.relationdetector.contracts.model.NamingEvidenceCandidate;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.spi.ScanScope;

/**
 * CN: 承载交给 JSON/table writers 的最终 direct/derived facts、naming evidence、warnings 与 source 清单。
 * EN: Carries final direct/derived facts, naming evidence, warnings, and source inventory passed to JSON and table writers.
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
    private MetadataInventory metadataInventory;

    public ScanResult(String databaseType, String schema) {
        this(databaseType, null, schema);
    }

    public ScanResult(String databaseType, String catalog, String schema) {
        this(databaseType, catalog, schema, MetadataInventory.empty(
                MetadataInventoryStatus.NOT_REQUESTED,
                new ScanScope(catalog, schema, List.of(), List.of())));
    }

    public ScanResult(
            String databaseType,
            String catalog,
            String schema,
            MetadataInventory metadataInventory
    ) {
        this.databaseType = databaseType;
        this.catalog = catalog;
        this.schema = schema;
        this.generatedAt = Instant.now();
        if (metadataInventory == null) {
            throw new IllegalArgumentException("metadata inventory is required");
        }
        this.metadataInventory = metadataInventory;
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

    public MetadataInventory metadataInventory() {
        return metadataInventory;
    }

    public void metadataInventory(MetadataInventory value) {
        if (value == null) {
            throw new IllegalArgumentException("metadata inventory is required");
        }
        metadataInventory = value;
    }
}
