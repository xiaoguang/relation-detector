package com.relationdetector.semantic.reader;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * CN: 将bounded component产生的KG与EvidenceGraph通过外排stable-ID合并并流式写成三个正式artifact；
 * 上游是SemanticEvidenceStore，下游是semantic CLI build/e2e，本类不重新解释物理事实或完整物化graph。
 * EN: Externally merges KG and EvidenceGraph records emitted by bounded components and streams the three formal
 * artifacts. It serves build/e2e without reinterpreting physical facts or materializing the complete graph.
 */
public final class SemanticDiskBackedArtifactWriter {
    private static final ObjectMapper JSON = new ObjectMapper();

    public void writeArtifacts(SemanticEvidenceStore evidenceStore, Path outputDirectory) {
        if (evidenceStore == null || outputDirectory == null) {
            throw new IllegalArgumentException("semantic evidence store and output directory are required");
        }
        Path workspace = outputDirectory.resolve(".artifact-merge-work");
        if (Files.exists(workspace)) {
            throw new ScanResultContractException("semantic artifact merge workspace already exists");
        }
        Map<ArtifactSection, ExternalJsonRecordStore> stores = new EnumMap<>(ArtifactSection.class);
        try {
            Files.createDirectories(outputDirectory);
            for (ArtifactSection section : ArtifactSection.values()) {
                stores.put(section, new ExternalJsonRecordStore(
                        workspace.resolve(section.name().toLowerCase(java.util.Locale.ROOT))));
            }
            evidenceStore.forEachComponent(component -> appendComponent(component, stores));
            stores.values().forEach(ExternalJsonRecordStore::finish);
            Map<String, Object> buildRun = buildRun(evidenceStore.descriptor());
            writeKg(outputDirectory.resolve("semantic-kg.json"), stores, buildRun, evidenceStore);
            writeBuildRun(outputDirectory.resolve("semantic-build-run.json"), buildRun);
            writeEvidenceGraph(
                    outputDirectory.resolve("semantic-evidence-graph.json"),
                    stores,
                    evidenceStore);
        } catch (IOException failure) {
            throw new ScanResultContractException("failed to write disk-backed semantic artifacts", failure);
        } finally {
            stores.values().forEach(store -> {
                try {
                    store.close();
                } catch (RuntimeException ignored) {
                    // The primary write failure is more useful than cleanup noise.
                }
            });
            deleteRecursivelyBestEffort(workspace);
        }
    }

    private void appendComponent(
            SemanticEvidenceStore.ComponentBundle component,
            Map<ArtifactSection, ExternalJsonRecordStore> stores
    ) {
        try {
            JsonNode kg = JSON.readTree(component.kgPath().toFile());
            appendArray(stores.get(ArtifactSection.KG_NODES), kg.path("nodes"));
            appendArray(stores.get(ArtifactSection.KG_EDGES), kg.path("edges"));
            appendArray(stores.get(ArtifactSection.KG_EVIDENCE), kg.path("evidenceRefs"));
            appendDiagnostics(stores.get(ArtifactSection.KG_DIAGNOSTICS), kg.path("diagnostics"));

            JsonNode graph = JSON.readTree(component.evidenceGraphPath().toFile());
            appendEndpoints(stores.get(ArtifactSection.GRAPH_ENDPOINTS), graph.path("endpoints"));
            appendArray(stores.get(ArtifactSection.GRAPH_FACTS), graph.path("facts"));
            appendArray(stores.get(ArtifactSection.GRAPH_EVIDENCE), graph.path("evidenceRefs"));
            appendDiagnostics(stores.get(ArtifactSection.GRAPH_DIAGNOSTICS), graph.path("diagnostics"));
        } catch (IOException failure) {
            throw new ScanResultContractException("failed to merge bounded semantic component artifact", failure);
        }
    }

    private void appendArray(ExternalJsonRecordStore store, JsonNode values) {
        if (!values.isArray()) {
            throw new ScanResultContractException("semantic component artifact section must be an array");
        }
        for (JsonNode value : values) {
            store.append(requiredId(value), value);
        }
    }

    private void appendEndpoints(ExternalJsonRecordStore store, JsonNode values) {
        if (!values.isArray()) {
            throw new ScanResultContractException("semantic graph endpoints must be an array");
        }
        for (JsonNode value : values) {
            String table = value.path("table").asText("");
            String column = value.path("column").asText("");
            String key = column.isBlank() ? table : table + "." + column;
            store.append(key, value);
        }
    }

    private void appendDiagnostics(ExternalJsonRecordStore store, JsonNode values) {
        if (!values.isArray()) {
            throw new ScanResultContractException("semantic diagnostics must be an array");
        }
        int position = 0;
        for (JsonNode value : values) {
            String id = value.path("id").asText("");
            store.append(id.isBlank()
                    ? com.relationdetector.semantic.StableSemanticId.of(
                            "semantic-diagnostic", Integer.toString(position), value.toString())
                    : id, value);
            position++;
        }
    }

    private String requiredId(JsonNode value) {
        String id = value.path("id").asText("");
        if (id.isBlank()) {
            throw new ScanResultContractException("semantic artifact record id is required");
        }
        return id;
    }

    private void writeKg(
            Path target,
            Map<ArtifactSection, ExternalJsonRecordStore> stores,
            Map<String, Object> buildRun,
            SemanticEvidenceStore evidence
    ) throws IOException {
        try (OutputStream output = Files.newOutputStream(target);
             JsonGenerator generator = JSON.getFactory().createGenerator(output)) {
            generator.useDefaultPrettyPrinter();
            generator.writeStartObject();
            generator.writeObjectField("buildRun", buildRun);
            generator.writeObjectFieldStart("summary");
            generator.writeNumberField("nodeCount", stores.get(ArtifactSection.KG_NODES).count());
            generator.writeNumberField("edgeCount", stores.get(ArtifactSection.KG_EDGES).count());
            generator.writeNumberField("evidenceRefCount", stores.get(ArtifactSection.KG_EVIDENCE).count());
            generator.writeNumberField("diagnosticCount", stores.get(ArtifactSection.KG_DIAGNOSTICS).count());
            generator.writeNumberField("inputRelationshipCount",
                    evidence.count(SemanticEvidenceStore.Section.RELATIONSHIPS));
            generator.writeNumberField("inputDataLineageCount",
                    evidence.count(SemanticEvidenceStore.Section.LINEAGE));
            generator.writeNumberField("inputNamingEvidenceCount",
                    evidence.count(SemanticEvidenceStore.Section.NAMING_EVIDENCE));
            generator.writeNumberField("inputDerivedRelationshipCount",
                    evidence.count(SemanticEvidenceStore.Section.DERIVED_RELATIONSHIPS));
            generator.writeNumberField("inputDerivedDataLineageCount",
                    evidence.count(SemanticEvidenceStore.Section.DERIVED_LINEAGE));
            generator.writeNumberField("eventCandidateCount",
                    evidence.count(SemanticEvidenceStore.Section.EVENT_CANDIDATES));
            generator.writeEndObject();
            stores.get(ArtifactSection.KG_NODES).writeArray(generator, "nodes");
            stores.get(ArtifactSection.KG_EDGES).writeArray(generator, "edges");
            stores.get(ArtifactSection.KG_EVIDENCE).writeArray(generator, "evidenceRefs");
            stores.get(ArtifactSection.KG_DIAGNOSTICS).writeArray(generator, "diagnostics");
            generator.writeEndObject();
            generator.writeRaw('\n');
        }
    }

    private void writeBuildRun(Path target, Map<String, Object> buildRun) throws IOException {
        try (OutputStream output = Files.newOutputStream(target);
             JsonGenerator generator = JSON.getFactory().createGenerator(output)) {
            generator.useDefaultPrettyPrinter();
            generator.writeObject(buildRun);
            generator.writeRaw('\n');
        }
    }

    private void writeEvidenceGraph(
            Path target,
            Map<ArtifactSection, ExternalJsonRecordStore> stores,
            SemanticEvidenceStore evidence
    ) throws IOException {
        try (OutputStream output = Files.newOutputStream(target);
             JsonGenerator generator = JSON.getFactory().createGenerator(output)) {
            generator.useDefaultPrettyPrinter();
            generator.writeStartObject();
            writeScanBundleDescriptor(generator, evidence.descriptor());
            stores.get(ArtifactSection.GRAPH_ENDPOINTS).writeArray(generator, "endpoints");
            stores.get(ArtifactSection.GRAPH_FACTS).writeArray(generator, "facts");
            stores.get(ArtifactSection.GRAPH_EVIDENCE).writeArray(generator, "evidenceRefs");
            stores.get(ArtifactSection.GRAPH_DIAGNOSTICS).writeArray(generator, "diagnostics");
            generator.writeObjectFieldStart("summary");
            generator.writeNumberField("relationshipCount",
                    evidence.count(SemanticEvidenceStore.Section.RELATIONSHIPS));
            generator.writeNumberField("lineageCount",
                    evidence.count(SemanticEvidenceStore.Section.LINEAGE));
            generator.writeNumberField("namingEvidenceCount",
                    evidence.count(SemanticEvidenceStore.Section.NAMING_EVIDENCE));
            generator.writeNumberField("metadataTableCount",
                    evidence.count(SemanticEvidenceStore.Section.METADATA_TABLES));
            generator.writeNumberField("metadataColumnCount",
                    evidence.count(SemanticEvidenceStore.Section.METADATA_COLUMNS));
            generator.writeEndObject();
            generator.writeEndObject();
            generator.writeRaw('\n');
        }
    }

    private void writeScanBundleDescriptor(
            JsonGenerator generator,
            SemanticInputStore.Descriptor descriptor
    ) throws IOException {
        generator.writeObjectFieldStart("scanBundle");
        generator.writeStringField("databaseType", descriptor.databaseType());
        generator.writeStringField("catalog", descriptor.catalog());
        generator.writeStringField("schema", descriptor.schema());
        generator.writeStringField("generatedAt", descriptor.generatedAt());
        writeStrings(generator, "sources", descriptor.sources());
        writeStrings(generator, "inputFiles", descriptor.inputFiles());
        generator.writeObjectFieldStart("summary");
        generator.writeNumberField("metadataTableCount", descriptor.inventory().tableCount());
        generator.writeNumberField("metadataColumnCount", descriptor.inventory().columnCount());
        generator.writeNumberField("metadataConstraintCount", descriptor.inventory().constraintCount());
        generator.writeNumberField("metadataIndexCount", descriptor.inventory().indexCount());
        generator.writeEndObject();
        generator.writeEndObject();
    }

    private void writeStrings(JsonGenerator generator, String field, List<String> values) throws IOException {
        generator.writeArrayFieldStart(field);
        for (String value : values) {
            generator.writeString(value);
        }
        generator.writeEndArray();
    }

    private Map<String, Object> buildRun(SemanticInputStore.Descriptor descriptor) {
        Map<String, Object> database = new java.util.LinkedHashMap<>();
        database.put("type", descriptor.databaseType());
        database.put("catalog", descriptor.catalog());
        database.put("schema", descriptor.schema());
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("builtAt", Instant.now().toString());
        result.put("database", database);
        result.put("generatedAt", descriptor.generatedAt());
        result.put("sources", descriptor.sources());
        result.put("inputFiles", descriptor.inputFiles());
        return Map.copyOf(result);
    }

    private void deleteRecursivelyBestEffort(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
            // The finished artifacts remain valid; workspace cleanup is best effort here.
        }
    }

    private enum ArtifactSection {
        KG_NODES,
        KG_EDGES,
        KG_EVIDENCE,
        KG_DIAGNOSTICS,
        GRAPH_ENDPOINTS,
        GRAPH_FACTS,
        GRAPH_EVIDENCE,
        GRAPH_DIAGNOSTICS
    }
}
