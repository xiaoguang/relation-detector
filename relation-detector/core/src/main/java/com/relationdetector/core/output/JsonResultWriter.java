package com.relationdetector.core.output;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.contracts.model.DerivedPathCandidate;
import com.relationdetector.contracts.model.NamingEvidenceCandidate;
import com.relationdetector.core.scan.MetadataInventory;
import com.relationdetector.core.scan.ScanResult;

/**
 * JSON output writer for bounded string rendering and streaming file output.
 *
 * <p>CN: ObjectMapper 在静态初始化阶段完成配置，之后只读复用。有界字符串入口创建独立 JSON 树，
 * 文件和流入口通过 JsonGenerator 逐段写出，因此并发调用不会共享可变输出状态，也不会为完整文件构造根树。
 *
 * <p>EN: The ObjectMapper is configured during static initialization and reused read-only. Bounded string calls
 * create independent JSON trees, while file and stream calls emit sections through a JsonGenerator, so concurrent
 * calls share no mutable output state and complete files need no root object tree.
 */
public final class JsonResultWriter {
    private static final String TRANSITIVE_NAMING_PATH = "TRANSITIVE_NAMING_PATH";
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private final JsonResultFactRenderer renderer = new JsonResultFactRenderer(JSON);

    /**
     *
     * 将 ScanResult 渲染成最终 JSON 字符串。
     *
     * <p>EN: Renders ScanResult into the final JSON string.
     */
    public String write(ScanResult result, boolean includeEvidence, boolean includeWarnings) {
        return write(result, includeEvidence, includeWarnings, true);
    }

    /**
     *
     * 将 ScanResult 渲染成最终 JSON 字符串。
     *
     * <p>Observation counts are debug-only counters derived from rawEvidence.
     * They help compare merged facts with their raw observations and can be
     * disabled by output.includeObservationCounts.
     *
     * <p>EN: Renders ScanResult into the final JSON string.
     */
    public String write(
            ScanResult result,
            boolean includeEvidence,
            boolean includeWarnings,
            boolean includeObservationCounts
    ) {
        return serialize(document(result, includeEvidence, includeWarnings, includeObservationCounts, true));
    }

    /**
     *
     * Renders the direct-fact view of a completed scan without reparsing the input.
     */
    public String writeDirect(
            ScanResult result,
            boolean includeEvidence,
            boolean includeWarnings,
            boolean includeObservationCounts
    ) {
        return serialize(document(result, includeEvidence, includeWarnings, includeObservationCounts, false));
    }

    public void write(
            ScanResult result,
            OutputStream output,
            boolean includeEvidence,
            boolean includeWarnings,
            boolean includeObservationCounts
    ) throws IOException {
        writeStreaming(result, output, includeEvidence, includeWarnings, includeObservationCounts, true);
    }

    public void writeDirect(
            ScanResult result,
            OutputStream output,
            boolean includeEvidence,
            boolean includeWarnings,
            boolean includeObservationCounts
    ) throws IOException {
        writeStreaming(result, output, includeEvidence, includeWarnings, includeObservationCounts, false);
    }

    public void write(
            ScanResult result,
            Path output,
            boolean includeEvidence,
            boolean includeWarnings,
            boolean includeObservationCounts
    ) throws IOException {
        try (OutputStream stream = Files.newOutputStream(output)) {
            write(result, stream, includeEvidence, includeWarnings, includeObservationCounts);
        }
    }

    /**
     * CN: 将已合并的扫描事实装配为稳定 JSON 树，并按调用参数选择 direct 或 direct+derived 视图；
     * 本方法只序列化现有事实，不重新评分、推导或合并证据。
     * EN: Assembles merged scan facts into a stable JSON tree and selects the direct or direct-plus-derived view;
     * it serializes existing facts without rescoring, deriving, or merging evidence.
     */
    private ObjectNode document(
            ScanResult result,
            boolean includeEvidence,
            boolean includeWarnings,
            boolean includeObservationCounts,
            boolean includeDerived
    ) {
        List<NamingEvidenceCandidate> allNamingEvidence = result.namingEvidence();
        List<NamingEvidenceCandidate> derivedNamingEvidence = allNamingEvidence.stream()
                .filter(this::isDerivedNamingEvidence)
                .toList();
        List<NamingEvidenceCandidate> directNamingEvidence = allNamingEvidence.stream()
                .filter(candidate -> !isDerivedNamingEvidence(candidate))
                .toList();
        List<NamingEvidenceCandidate> namingEvidence = includeDerived
                ? allNamingEvidence
                : directNamingEvidence;
        List<DerivedPathCandidate> derivedRelationshipFacts = includeDerived
                ? result.derivedRelationships()
                : List.of();
        List<DerivedPathCandidate> derivedDataLineageFacts = includeDerived
                ? result.derivedDataLineages()
                : List.of();
        List<NamingEvidenceCandidate> derivedNamingOutput = includeDerived
                ? derivedNamingEvidence
                : List.of();
        int directRelationshipCount = result.relationships().size();
        int derivedRelationshipCount = derivedRelationshipFacts.size();
        int directDataLineageCount = result.dataLineages().size();
        int derivedDataLineageCount = derivedDataLineageFacts.size();
        int directNamingEvidenceCount = directNamingEvidence.size();
        int derivedNamingEvidenceCount = derivedNamingOutput.size();

        ObjectNode root = JSON.createObjectNode();
        ObjectNode database = root.putObject("database");
        database.put("type", JsonResultFactRenderer.safe(result.databaseType()));
        if (result.catalog() != null && !result.catalog().isBlank()) {
            database.put("catalog", result.catalog());
        }
        database.put("schema", JsonResultFactRenderer.safe(result.schema()));
        root.put("generatedAt", String.valueOf(result.generatedAt()));

        ObjectNode summary = root.putObject("summary");
        summary.put("directRelationshipCount", directRelationshipCount);
        summary.put("derivedRelationshipCount", derivedRelationshipCount);
        summary.put("totalRelationshipCount", directRelationshipCount + derivedRelationshipCount);
        summary.put("directDataLineageCount", directDataLineageCount);
        summary.put("derivedDataLineageCount", derivedDataLineageCount);
        summary.put("totalDataLineageCount", directDataLineageCount + derivedDataLineageCount);
        summary.put("directNamingEvidenceCount", directNamingEvidenceCount);
        summary.put("derivedNamingEvidenceCount", derivedNamingEvidenceCount);
        summary.put("totalNamingEvidenceCount", directNamingEvidenceCount + derivedNamingEvidenceCount);
        if (includeObservationCounts) {
            int directRelationshipObservations = renderer.relationshipObservationCount(result.relationships());
            int derivedRelationshipEvidenceSets = renderer.derivedPathEvidenceSetCount(derivedRelationshipFacts);
            java.math.BigInteger derivedRelationshipCombinations =
                    renderer.derivedPathSupportCombinationCount(derivedRelationshipFacts);
            int directDataLineageObservations = renderer.dataLineageObservationCount(result.dataLineages());
            int derivedDataLineageEvidenceSets = renderer.derivedPathEvidenceSetCount(derivedDataLineageFacts);
            java.math.BigInteger derivedDataLineageCombinations =
                    renderer.derivedPathSupportCombinationCount(derivedDataLineageFacts);
            int directNamingObservations = renderer.namingEvidenceObservationCount(directNamingEvidence);
            int derivedNamingEvidenceSets = renderer.derivedNamingEvidenceSetCount(derivedNamingOutput);
            java.math.BigInteger derivedNamingCombinations =
                    renderer.derivedNamingSupportCombinationCount(derivedNamingOutput);
            summary.put("directRelationshipObservationCount", directRelationshipObservations);
            summary.put("derivedRelationshipEvidenceSetCount", derivedRelationshipEvidenceSets);
            summary.put("derivedRelationshipSupportCombinationCount", derivedRelationshipCombinations);
            summary.put("directDataLineageObservationCount", directDataLineageObservations);
            summary.put("derivedDataLineageEvidenceSetCount", derivedDataLineageEvidenceSets);
            summary.put("derivedDataLineageSupportCombinationCount", derivedDataLineageCombinations);
            summary.put("directNamingEvidenceObservationCount", directNamingObservations);
            summary.put("derivedNamingEvidenceSetCount", derivedNamingEvidenceSets);
            summary.put("derivedNamingSupportCombinationCount", derivedNamingCombinations);
        }
        summary.put("warningCount", includeWarnings ? result.warnings().size() : 0);
        ArrayNode sources = summary.putArray("sources");
        result.sources().forEach(sources::add);

        root.set("metadataInventory", renderer.metadataInventoryNode(result.metadataInventory()));

        ArrayNode relationships = root.putArray("relationships");
        result.relationships().forEach(relation ->
                relationships.add(renderer.relationshipNode(relation, includeEvidence, includeWarnings)));

        ArrayNode dataLineages = root.putArray("dataLineages");
        result.dataLineages().forEach(lineage ->
                dataLineages.add(renderer.dataLineageNode(lineage, includeEvidence, includeWarnings)));

        ArrayNode derivedRelationships = root.putArray("derivedRelationships");
        derivedRelationshipFacts.forEach(candidate ->
                derivedRelationships.add(renderer.derivedPathNode(candidate, includeEvidence)));

        ArrayNode derivedDataLineages = root.putArray("derivedDataLineages");
        derivedDataLineageFacts.forEach(candidate ->
                derivedDataLineages.add(renderer.derivedPathNode(candidate, includeEvidence)));

        ArrayNode naming = root.putArray("namingEvidence");
        namingEvidence.forEach(candidate ->
                naming.add(renderer.namingEvidenceNode(candidate, includeEvidence)));

        ArrayNode derivedNaming = root.putArray("derivedNamingEvidence");
        derivedNamingOutput.forEach(candidate ->
                derivedNaming.add(renderer.lightweightNamingEvidenceNode(candidate)));

        if (includeWarnings) {
            root.set("warnings", renderer.warningsNode(result.warnings()));
        } else {
            root.set("warnings", JSON.createArrayNode());
        }

        return root;
    }

    private String serialize(ObjectNode root) {
        try {
            return JSON.writeValueAsString(root) + "\n";
        } catch (IOException e) {
            throw new IllegalStateException("Failed to render scan result JSON", e);
        }
    }

    /**
     * CN: 将已完成的扫描结果按 direct 或 direct+derived 视图流式写入调用方提供的输出流；输入开关控制
     * evidence、warning 和 observation count，可见副作用仅为写入并刷新该流。JSON 生成或底层写入失败时
     * 抛出 IOException，不修改 ScanResult，也不关闭调用方输出流。
     *
     * EN: Streams a completed scan as either the direct or direct-plus-derived view to the supplied output stream.
     * Input flags control evidence, warnings, and observation counts; the only side effect is writing and flushing
     * that stream. JSON generation or I/O failure raises IOException without mutating ScanResult or closing the
     * caller-owned stream.
     */
    private void writeStreaming(
            ScanResult result,
            OutputStream output,
            boolean includeEvidence,
            boolean includeWarnings,
            boolean includeObservationCounts,
            boolean includeDerived
    ) throws IOException {
        List<NamingEvidenceCandidate> allNamingEvidence = result.namingEvidence();
        List<NamingEvidenceCandidate> derivedNamingEvidence = allNamingEvidence.stream()
                .filter(this::isDerivedNamingEvidence)
                .toList();
        List<NamingEvidenceCandidate> directNamingEvidence = allNamingEvidence.stream()
                .filter(candidate -> !isDerivedNamingEvidence(candidate))
                .toList();
        List<NamingEvidenceCandidate> namingEvidence = includeDerived ? allNamingEvidence : directNamingEvidence;
        List<DerivedPathCandidate> derivedRelationshipFacts = includeDerived
                ? result.derivedRelationships() : List.of();
        List<DerivedPathCandidate> derivedDataLineageFacts = includeDerived
                ? result.derivedDataLineages() : List.of();
        List<NamingEvidenceCandidate> derivedNamingOutput = includeDerived
                ? derivedNamingEvidence : List.of();

        try (JsonGenerator generator = JSON.getFactory().createGenerator(output)) {
            generator.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
            generator.useDefaultPrettyPrinter();
            generator.writeStartObject();
            generator.writeObjectFieldStart("database");
            generator.writeStringField("type", JsonResultFactRenderer.safe(result.databaseType()));
            if (result.catalog() != null && !result.catalog().isBlank()) {
                generator.writeStringField("catalog", result.catalog());
            }
            generator.writeStringField("schema", JsonResultFactRenderer.safe(result.schema()));
            generator.writeEndObject();
            generator.writeStringField("generatedAt", String.valueOf(result.generatedAt()));
            writeSummary(generator, result, directNamingEvidence, derivedRelationshipFacts,
                    derivedDataLineageFacts, derivedNamingOutput, includeWarnings, includeObservationCounts);
            writeMetadataInventory(generator, result.metadataInventory());
            writeArray(generator, "relationships", result.relationships().stream()
                    .map(relation -> renderer.relationshipNode(
                            relation, includeEvidence, includeWarnings)).iterator());
            writeArray(generator, "dataLineages", result.dataLineages().stream()
                    .map(lineage -> renderer.dataLineageNode(
                            lineage, includeEvidence, includeWarnings)).iterator());
            writeArray(generator, "derivedRelationships", derivedRelationshipFacts.stream()
                    .map(candidate -> renderer.derivedPathNode(candidate, includeEvidence)).iterator());
            writeArray(generator, "derivedDataLineages", derivedDataLineageFacts.stream()
                    .map(candidate -> renderer.derivedPathNode(candidate, includeEvidence)).iterator());
            writeArray(generator, "namingEvidence", namingEvidence.stream()
                    .map(candidate -> renderer.namingEvidenceNode(candidate, includeEvidence)).iterator());
            writeArray(generator, "derivedNamingEvidence", derivedNamingOutput.stream()
                    .map(renderer::lightweightNamingEvidenceNode).iterator());
            writeArray(generator, "warnings", includeWarnings
                    ? result.warnings().stream().map(renderer::warningNode).iterator()
                    : java.util.Collections.emptyIterator());
            generator.writeEndObject();
            generator.writeRaw('\n');
            generator.flush();
        }
    }

    /**
     * CN: 根据本次已选 direct/derived 集合计算并写出 summary；输入集合决定所有计数，warning 与 observation
     * 开关决定可选计数。方法只向当前 JsonGenerator 写字段，不修改事实；生成器写入失败时抛出 IOException。
     *
     * EN: Computes and writes the summary from the selected direct and derived inputs, with warning and observation
     * flags controlling optional counts. It only appends fields to the active JsonGenerator, never mutates facts,
     * and propagates IOException when generator output fails.
     */
    private void writeSummary(
            JsonGenerator generator,
            ScanResult result,
            List<NamingEvidenceCandidate> directNamingEvidence,
            List<DerivedPathCandidate> derivedRelationshipFacts,
            List<DerivedPathCandidate> derivedDataLineageFacts,
            List<NamingEvidenceCandidate> derivedNamingOutput,
            boolean includeWarnings,
            boolean includeObservationCounts
    ) throws IOException {
        int directRelationshipCount = result.relationships().size();
        int derivedRelationshipCount = derivedRelationshipFacts.size();
        int directDataLineageCount = result.dataLineages().size();
        int derivedDataLineageCount = derivedDataLineageFacts.size();
        int directNamingEvidenceCount = directNamingEvidence.size();
        int derivedNamingEvidenceCount = derivedNamingOutput.size();
        generator.writeObjectFieldStart("summary");
        generator.writeNumberField("directRelationshipCount", directRelationshipCount);
        generator.writeNumberField("derivedRelationshipCount", derivedRelationshipCount);
        generator.writeNumberField("totalRelationshipCount", directRelationshipCount + derivedRelationshipCount);
        generator.writeNumberField("directDataLineageCount", directDataLineageCount);
        generator.writeNumberField("derivedDataLineageCount", derivedDataLineageCount);
        generator.writeNumberField("totalDataLineageCount", directDataLineageCount + derivedDataLineageCount);
        generator.writeNumberField("directNamingEvidenceCount", directNamingEvidenceCount);
        generator.writeNumberField("derivedNamingEvidenceCount", derivedNamingEvidenceCount);
        generator.writeNumberField("totalNamingEvidenceCount",
                directNamingEvidenceCount + derivedNamingEvidenceCount);
        if (includeObservationCounts) {
            int directRelationshipObservations = renderer.relationshipObservationCount(result.relationships());
            int derivedRelationshipEvidenceSets = renderer.derivedPathEvidenceSetCount(derivedRelationshipFacts);
            java.math.BigInteger derivedRelationshipCombinations =
                    renderer.derivedPathSupportCombinationCount(derivedRelationshipFacts);
            int directDataLineageObservations = renderer.dataLineageObservationCount(result.dataLineages());
            int derivedDataLineageEvidenceSets = renderer.derivedPathEvidenceSetCount(derivedDataLineageFacts);
            java.math.BigInteger derivedDataLineageCombinations =
                    renderer.derivedPathSupportCombinationCount(derivedDataLineageFacts);
            int directNamingObservations = renderer.namingEvidenceObservationCount(directNamingEvidence);
            int derivedNamingEvidenceSets = renderer.derivedNamingEvidenceSetCount(derivedNamingOutput);
            java.math.BigInteger derivedNamingCombinations =
                    renderer.derivedNamingSupportCombinationCount(derivedNamingOutput);
            generator.writeNumberField("directRelationshipObservationCount", directRelationshipObservations);
            generator.writeNumberField("derivedRelationshipEvidenceSetCount", derivedRelationshipEvidenceSets);
            generator.writeNumberField("derivedRelationshipSupportCombinationCount", derivedRelationshipCombinations);
            generator.writeNumberField("directDataLineageObservationCount", directDataLineageObservations);
            generator.writeNumberField("derivedDataLineageEvidenceSetCount", derivedDataLineageEvidenceSets);
            generator.writeNumberField("derivedDataLineageSupportCombinationCount", derivedDataLineageCombinations);
            generator.writeNumberField("directNamingEvidenceObservationCount", directNamingObservations);
            generator.writeNumberField("derivedNamingEvidenceSetCount", derivedNamingEvidenceSets);
            generator.writeNumberField("derivedNamingSupportCombinationCount", derivedNamingCombinations);
        }
        generator.writeNumberField("warningCount", includeWarnings ? result.warnings().size() : 0);
        generator.writeArrayFieldStart("sources");
        for (String source : result.sources()) {
            generator.writeString(source);
        }
        generator.writeEndArray();
        generator.writeEndObject();
    }

    private void writeMetadataInventory(JsonGenerator generator, MetadataInventory inventory) throws IOException {
        generator.writeObjectFieldStart("metadataInventory");
        generator.writeStringField("status", inventory.status().name());
        generator.writeStringField("basis", inventory.basis().name());
        generator.writeObjectFieldStart("scope");
        generator.writeStringField("catalog", JsonResultFactRenderer.safe(inventory.scope().catalog()));
        generator.writeStringField("schema", JsonResultFactRenderer.safe(inventory.scope().schema()));
        writeStringArray(generator, "includeTables", inventory.scope().includeTables());
        writeStringArray(generator, "excludeTables", inventory.scope().excludeTables());
        generator.writeEndObject();
        generator.writeObjectFieldStart("counts");
        generator.writeNumberField("tables", inventory.tables().size());
        generator.writeNumberField("columns", inventory.columns().size());
        generator.writeNumberField("constraints", inventory.constraints().size());
        generator.writeNumberField("indexes", inventory.indexes().size());
        generator.writeEndObject();
        writeObjects(generator, "tables", inventory.tables());
        writeObjects(generator, "columns", inventory.columns());
        writeObjects(generator, "constraints", inventory.constraints());
        writeObjects(generator, "indexes", inventory.indexes());
        generator.writeEndObject();
    }

    private void writeArray(
            JsonGenerator generator,
            String field,
            java.util.Iterator<? extends ObjectNode> values
    )
            throws IOException {
        generator.writeArrayFieldStart(field);
        while (values.hasNext()) {
            generator.writeTree(values.next());
        }
        generator.writeEndArray();
    }

    private void writeObjects(JsonGenerator generator, String field, List<?> values) throws IOException {
        generator.writeArrayFieldStart(field);
        for (Object value : values) {
            generator.writeObject(value);
        }
        generator.writeEndArray();
    }

    private void writeStringArray(JsonGenerator generator, String field, List<String> values) throws IOException {
        generator.writeArrayFieldStart(field);
        for (String value : values) {
            generator.writeString(value);
        }
        generator.writeEndArray();
    }

    private boolean isDerivedNamingEvidence(NamingEvidenceCandidate candidate) {
        return TRANSITIVE_NAMING_PATH.equals(candidate.rule());
    }
}
