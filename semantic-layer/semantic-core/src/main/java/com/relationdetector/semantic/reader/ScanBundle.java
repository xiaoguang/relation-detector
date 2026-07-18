package com.relationdetector.semantic.reader;

import java.nio.file.Path;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonValue;

/** Immutable typed relation-detector scan result consumed by semantic-layer. */
public final class ScanBundle {
    private final String databaseType;
    private final String catalog;
    private final String schema;
    private final String generatedAt;
    private final List<String> sources;
    private final List<Path> inputFiles;
    private final Map<String, Integer> summary;
    private final List<ScanRelationshipFact> relationships;
    private final List<ScanLineageFact> dataLineages;
    private final List<ScanRelationshipFact> derivedRelationships;
    private final List<ScanLineageFact> derivedDataLineages;
    private final List<ScanNamingEvidenceFact> namingEvidence;
    private final List<ScanDiagnosticFact> diagnostics;

    public ScanBundle(
            String databaseType,
            String schema,
            String generatedAt,
            List<String> sources,
            List<Path> inputFiles,
            Map<String, Integer> summary,
            List<?> relationships,
            List<?> dataLineages,
            List<?> derivedRelationships,
            List<?> derivedDataLineages,
            List<?> namingEvidence,
            List<?> diagnostics
    ) {
        this(databaseType, "", schema, generatedAt, sources, inputFiles, summary, relationships, dataLineages,
                derivedRelationships, derivedDataLineages, namingEvidence, diagnostics);
    }

    public ScanBundle(
            String databaseType,
            String catalog,
            String schema,
            String generatedAt,
            List<String> sources,
            List<Path> inputFiles,
            Map<String, Integer> summary,
            List<?> relationships,
            List<?> dataLineages,
            List<?> derivedRelationships,
            List<?> derivedDataLineages,
            List<?> namingEvidence,
            List<?> diagnostics
    ) {
        if (databaseType == null || databaseType.isBlank()) {
            throw new IllegalArgumentException("database type is required");
        }
        this.databaseType = databaseType;
        this.catalog = catalog == null ? "" : catalog;
        this.schema = schema == null ? "" : schema;
        this.generatedAt = generatedAt == null ? "" : generatedAt;
        this.sources = List.copyOf(sources == null ? List.of() : sources);
        this.inputFiles = List.copyOf(inputFiles == null ? List.of() : inputFiles);
        this.summary = Map.copyOf(summary == null ? Map.of() : summary);
        this.relationships = ScanFactFactory.relationships(relationships, false);
        this.dataLineages = ScanFactFactory.lineages(dataLineages, false);
        this.derivedRelationships = ScanFactFactory.relationships(derivedRelationships, true);
        this.derivedDataLineages = ScanFactFactory.lineages(derivedDataLineages, true);
        this.namingEvidence = ScanFactFactory.naming(namingEvidence);
        this.diagnostics = ScanFactFactory.diagnostics(diagnostics);
        requireUniqueIds(this.relationships, "relationships");
        requireUniqueIds(this.dataLineages, "dataLineages");
        requireUniqueIds(this.derivedRelationships, "derivedRelationships");
        requireUniqueIds(this.derivedDataLineages, "derivedDataLineages");
        requireUniqueIds(this.namingEvidence, "namingEvidence");
        requireUniqueIds(this.diagnostics, "diagnostics");
    }

    public String databaseType() { return databaseType; }
    public String catalog() { return catalog; }
    public String schema() { return schema; }
    public String generatedAt() { return generatedAt; }
    public List<String> sources() { return sources; }
    public List<Path> inputFiles() { return inputFiles; }
    public Map<String, Integer> summary() { return summary; }
    public List<ScanRelationshipFact> relationships() { return relationships; }
    public List<ScanLineageFact> dataLineages() { return dataLineages; }
    public List<ScanRelationshipFact> derivedRelationships() { return derivedRelationships; }
    public List<ScanLineageFact> derivedDataLineages() { return derivedDataLineages; }
    public List<ScanNamingEvidenceFact> namingEvidence() { return namingEvidence; }
    public List<ScanDiagnosticFact> diagnostics() { return diagnostics; }

    /** Keeps the evidence-graph artifact compatible while runtime consumers use typed facts. */
    @JsonValue
    public Map<String, Object> jsonView() {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("databaseType", databaseType);
        view.put("catalog", catalog);
        view.put("schema", schema);
        view.put("generatedAt", generatedAt);
        view.put("sources", sources);
        view.put("inputFiles", inputFiles.stream().map(SemanticInputPathCanonicalizer::canonicalize).toList());
        view.put("summary", summary);
        view.put("relationships", relationships.stream().map(ScanFact::document).toList());
        view.put("dataLineages", dataLineages.stream().map(ScanFact::document).toList());
        view.put("derivedRelationships", derivedRelationships.stream().map(ScanFact::document).toList());
        view.put("derivedDataLineages", derivedDataLineages.stream().map(ScanFact::document).toList());
        view.put("namingEvidence", namingEvidence.stream().map(ScanFact::document).toList());
        view.put("diagnostics", diagnostics.stream().map(ScanFact::document).toList());
        return java.util.Collections.unmodifiableMap(view);
    }

    private void requireUniqueIds(List<? extends ScanFact> facts, String section) {
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (ScanFact fact : facts) {
            if (!ids.add(fact.id())) {
                throw new ScanResultContractException(
                        section + " contains duplicate semantic fact identity: " + fact.id());
            }
        }
    }
}
