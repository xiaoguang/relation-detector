package com.relationdetector.semantic.kg.store;

import com.relationdetector.semantic.evidence.SemanticGraphRecordStore;

import com.relationdetector.semantic.evidence.SemanticEvidenceStore;

import com.relationdetector.semantic.ingest.SemanticInputStore;

import com.relationdetector.semantic.ingest.ScanResultContractException;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relationdetector.semantic.internal.io.SemanticFileTreeOperations;
import com.relationdetector.semantic.internal.io.SemanticDigestingOutputStream;
import com.relationdetector.semantic.kg.SemanticKgEvidenceGraphReference;

/**
 * CN: 将 SemanticEvidenceStore 中已完成全局聚合的 KG 与 EvidenceGraph 记录通过外排 stable-ID 校验并流式
 * 写成三个正式 artifact；下游是 semantic CLI build/e2e，本类不重新解释物理事实或完整物化 graph。
 * EN: Validates the globally aggregated KG and EvidenceGraph records in SemanticEvidenceStore through external
 * stable-ID stores and streams the three formal artifacts. It serves build/e2e without reinterpreting physical facts
 * or materializing the complete graph.
 */
public final class SemanticKgArtifactWriter {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Clock clock;

    public SemanticKgArtifactWriter() {
        this(Clock.systemUTC());
    }

    public SemanticKgArtifactWriter(Clock clock) {
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public SemanticKgArtifactReport writeArtifacts(
            SemanticEvidenceStore evidenceStore,
            Path outputDirectory
    ) {
        return writeArtifacts(evidenceStore, outputDirectory, SemanticKgArtifactMode.FULL);
    }

    /**
     * CN: 将完整磁盘evidence store按同一renderer写为FULL文件或DIGEST_ONLY空sink，并返回三份逻辑artifact
     * 的字节、SHA和closure摘要；输入或I/O失败时清理merge workspace，不保留部分逻辑报告。
     * EN: Renders a complete disk evidence store through the same serializer to FULL files or a DIGEST_ONLY null sink
     * and returns byte, SHA, and closure summaries for all three logical artifacts. Input or I/O failure cleans the
     * merge workspace and never returns a partial logical report.
     */
    public SemanticKgArtifactReport writeArtifacts(
            SemanticEvidenceStore evidenceStore,
            Path outputDirectory,
            SemanticKgArtifactMode mode
    ) {
        if (evidenceStore == null || outputDirectory == null) {
            throw new IllegalArgumentException("semantic evidence store and output directory are required");
        }
        SemanticKgArtifactMode resolved = mode == null ? SemanticKgArtifactMode.FULL : mode;
        Path workspace = outputDirectory.resolve(".artifact-merge-work");
        if (Files.exists(workspace)) {
            throw new ScanResultContractException("semantic artifact merge workspace already exists");
        }
        try (SemanticKgStore kg =
                     new SemanticKgStore(evidenceStore.graphRecords(), workspace.resolve("kg"))) {
            Files.createDirectories(outputDirectory);
            Map<String, Object> buildRun = buildRun(evidenceStore.descriptor());
            SemanticKgArtifactReport.ArtifactDigest evidenceGraph = render(
                    "semantic-evidence-graph.json", outputDirectory, resolved,
                    generator -> writeEvidenceGraph(generator, evidenceStore));
            SemanticKgEvidenceGraphReference evidenceGraphReference = new SemanticKgEvidenceGraphReference(
                    evidenceGraph.path(),
                    evidenceGraph.sha256(),
                    evidenceStore.graphRecords().count(SemanticGraphRecordStore.Section.EVIDENCE),
                    evidenceStore.graphRecords().count(SemanticGraphRecordStore.Section.DIAGNOSTICS));
            SemanticKgArtifactReport.ArtifactDigest kgArtifact = render(
                    "semantic-kg.json", outputDirectory, resolved,
                    generator -> writeKg(generator, kg, buildRun, evidenceStore, evidenceGraphReference));
            SemanticKgArtifactReport.ArtifactDigest buildArtifact = render(
                    "semantic-build-run.json", outputDirectory, resolved,
                    generator -> writeBuildRun(generator, buildRun));
            List<SemanticKgArtifactReport.ArtifactDigest> artifacts =
                    List.of(kgArtifact, buildArtifact, evidenceGraph);
            SemanticKgArtifactReport report = new SemanticKgArtifactReport(
                    artifacts,
                    new SemanticKgArtifactReport.Summary(
                            kg.nodeCount(),
                            kg.edgeCount(),
                            evidenceStore.graphRecords().count(
                                    SemanticGraphRecordStore.Section.EVIDENCE),
                            evidenceStore.graphRecords().count(
                                    SemanticGraphRecordStore.Section.DIAGNOSTICS),
                            "PASS"));
            writeReport(outputDirectory.resolve("semantic-kg-digests.json"), resolved, report);
            return report;
        } catch (IOException failure) {
            throw new ScanResultContractException("failed to write disk-backed semantic artifacts", failure);
        } finally {
            deleteRecursivelyBestEffort(workspace);
        }
    }

    private void writeKg(
            JsonGenerator generator,
            SemanticKgStore kg,
            Map<String, Object> buildRun,
            SemanticEvidenceStore evidence,
            SemanticKgEvidenceGraphReference evidenceGraph
    ) throws IOException {
        generator.writeStartObject();
        generator.writeNumberField("artifactSchemaVersion", 2);
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
        generator.writeObjectField("evidenceGraph", evidenceGraph);
        generator.writeEndObject();
        generator.writeRaw('\n');
    }

    private void writeBuildRun(JsonGenerator generator, Map<String, Object> buildRun) throws IOException {
        generator.writeObject(buildRun);
        generator.writeRaw('\n');
    }

    private void writeEvidenceGraph(
            JsonGenerator generator,
            SemanticEvidenceStore evidence
    ) throws IOException {
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
        result.put("builtAt", Instant.now(clock).toString());
        result.put("database", database);
        result.put("generatedAt", descriptor.generatedAt());
        result.put("sources", descriptor.sources());
        result.put("inputFiles", descriptor.inputFiles());
        return Map.copyOf(result);
    }

    private void deleteRecursivelyBestEffort(Path root) {
        SemanticFileTreeOperations.deleteRecursivelyBestEffort(root);
    }

    private SemanticKgArtifactReport.ArtifactDigest render(
            String logicalPath,
            Path outputDirectory,
            SemanticKgArtifactMode mode,
            JsonRenderer renderer
    ) throws IOException {
        OutputStream sink = mode == SemanticKgArtifactMode.FULL
                ? Files.newOutputStream(outputDirectory.resolve(logicalPath))
                : OutputStream.nullOutputStream();
        SemanticDigestingOutputStream digest = new SemanticDigestingOutputStream(sink);
        try (JsonGenerator generator = JSON.getFactory().createGenerator(digest)) {
            generator.useDefaultPrettyPrinter();
            renderer.write(generator);
        }
        return new SemanticKgArtifactReport.ArtifactDigest(
                logicalPath, digest.bytes(), digest.sha256());
    }

    private void writeReport(
            Path target,
            SemanticKgArtifactMode mode,
            SemanticKgArtifactReport report
    ) throws IOException {
        try (OutputStream output = Files.newOutputStream(target);
             JsonGenerator generator = JSON.getFactory().createGenerator(output)) {
            generator.useDefaultPrettyPrinter();
            generator.writeStartObject();
            generator.writeNumberField("artifactSchemaVersion", 1);
            generator.writeStringField("mode", mode.name());
            generator.writeObjectField("summary", report.summary());
            generator.writeObjectFieldStart("validation");
            generator.writeStringField("referenceClosure", report.summary().referenceClosure());
            generator.writeEndObject();
            generator.writeObjectField("artifacts", report.artifacts());
            generator.writeEndObject();
            generator.writeRaw('\n');
        }
    }

    @FunctionalInterface
    private interface JsonRenderer {
        void write(JsonGenerator generator) throws IOException;
    }

}
