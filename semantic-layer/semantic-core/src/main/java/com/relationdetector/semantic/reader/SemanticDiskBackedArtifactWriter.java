package com.relationdetector.semantic.reader;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * CN: 将 SemanticEvidenceStore 中已完成全局聚合的 KG 与 EvidenceGraph 记录通过外排 stable-ID 校验并流式
 * 写成三个正式 artifact；下游是 semantic CLI build/e2e，本类不重新解释物理事实或完整物化 graph。
 * EN: Validates the globally aggregated KG and EvidenceGraph records in SemanticEvidenceStore through external
 * stable-ID stores and streams the three formal artifacts. It serves build/e2e without reinterpreting physical facts
 * or materializing the complete graph.
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
        try (SemanticDiskBackedKgStore kg =
                     new SemanticDiskBackedKgStore(evidenceStore.graphRecords(), workspace.resolve("kg"))) {
            Files.createDirectories(outputDirectory);
            Map<String, Object> buildRun = buildRun(evidenceStore.descriptor());
            writeKg(outputDirectory.resolve("semantic-kg.json"), kg, buildRun, evidenceStore);
            writeBuildRun(outputDirectory.resolve("semantic-build-run.json"), buildRun);
            writeEvidenceGraph(
                    outputDirectory.resolve("semantic-evidence-graph.json"),
                    evidenceStore);
        } catch (IOException failure) {
            throw new ScanResultContractException("failed to write disk-backed semantic artifacts", failure);
        } finally {
            deleteRecursivelyBestEffort(workspace);
        }
    }

    private void writeKg(
            Path target,
            SemanticDiskBackedKgStore kg,
            Map<String, Object> buildRun,
            SemanticEvidenceStore evidence
    ) throws IOException {
        try (OutputStream output = Files.newOutputStream(target);
             JsonGenerator generator = JSON.getFactory().createGenerator(output)) {
            generator.useDefaultPrettyPrinter();
            generator.writeStartObject();
            generator.writeObjectField("buildRun", buildRun);
            generator.writeObjectFieldStart("summary");
            generator.writeNumberField("nodeCount", kg.nodeCount());
            generator.writeNumberField("edgeCount", kg.edgeCount());
            generator.writeNumberField("evidenceRefCount",
                    evidence.graphRecords().count(SemanticGraphRecordStore.Section.EVIDENCE));
            generator.writeNumberField("diagnosticCount",
                    evidence.graphRecords().count(SemanticGraphRecordStore.Section.DIAGNOSTICS));
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
            kg.writeNodes(generator);
            kg.writeEdges(generator);
            evidence.graphRecords().writeArray(
                    SemanticGraphRecordStore.Section.EVIDENCE, generator, "evidenceRefs");
            evidence.graphRecords().writeArray(
                    SemanticGraphRecordStore.Section.DIAGNOSTICS, generator, "diagnostics");
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
            SemanticEvidenceStore evidence
    ) throws IOException {
        try (OutputStream output = Files.newOutputStream(target);
             JsonGenerator generator = JSON.getFactory().createGenerator(output)) {
            generator.useDefaultPrettyPrinter();
            generator.writeStartObject();
            writeScanBundleDescriptor(generator, evidence.descriptor());
            evidence.graphRecords().writeArray(
                    SemanticGraphRecordStore.Section.ENDPOINTS, generator, "endpoints");
            evidence.graphRecords().writeArray(
                    SemanticGraphRecordStore.Section.FACTS, generator, "facts");
            evidence.graphRecords().writeArray(
                    SemanticGraphRecordStore.Section.EVIDENCE, generator, "evidenceRefs");
            evidence.graphRecords().writeArray(
                    SemanticGraphRecordStore.Section.DIAGNOSTICS, generator, "diagnostics");
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

}
