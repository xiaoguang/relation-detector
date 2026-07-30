package com.relationdetector.semantic.extract;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.event.SemanticEventCandidate;
import com.relationdetector.semantic.event.SemanticEventExtractor;
import com.relationdetector.semantic.graph.EvidenceGraph;
import com.relationdetector.semantic.graph.EvidenceGraphFact;
import com.relationdetector.semantic.graph.EvidenceReference;
import com.relationdetector.semantic.model.PhysicalEndpointRef;
import com.relationdetector.semantic.graph.SemanticEvidenceBuilder;
import com.relationdetector.semantic.reader.ScanBundle;
import com.relationdetector.semantic.reader.SemanticInputPathCanonicalizer;
import com.relationdetector.semantic.reader.ScanDiagnosticFact;
import com.relationdetector.semantic.reader.ScanLineageFact;
import com.relationdetector.semantic.reader.ScanNamingEvidenceFact;
import com.relationdetector.semantic.reader.ScanRelationshipFact;
import com.relationdetector.semantic.reader.SemanticMetadataInventoryEnvelope;

/**
 * CN: 从完整 typed ScanBundle 构造 evidence-closed bundle，保留全部 physical facts、deterministic candidates
 * 与引用 registry；上下文规模只由下游 typed sharding 控制，本类不做 focus 或数量裁剪。
 * EN: Builds an evidence-closed bundle from the complete typed ScanBundle, retaining every physical fact,
 * deterministic candidate, and reference registry. Downstream typed sharding alone controls context size.
 */
public final class SemanticExtractionBundleBuilder {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final SemanticEventExtractor eventExtractor = new SemanticEventExtractor();
    private final SemanticEvidenceBuilder evidenceBuilder = new SemanticEvidenceBuilder();
    private final ReviewItemCandidateGenerator reviewItemCandidateGenerator = new ReviewItemCandidateGenerator();
    private final TripletCandidateBuilder tripletCandidateBuilder = new TripletCandidateBuilder();

    /**
     * CN: 先建立完整 evidence graph 与物理 table registry，再按稳定 section 顺序输出全部事实和候选；
     * 输入为空直接失败，返回前不调用模型或执行任何预分片裁剪。
     * EN: Builds the complete evidence graph and physical-table registry before emitting every fact and candidate
     * in stable section order. Null input fails, and no model call or pre-sharding truncation occurs.
     */
    public ObjectNode build(ScanBundle bundle) {
        if (bundle == null) {
            throw new IllegalArgumentException("scan bundle is required");
        }
        Set<String> physicalTables = physicalTables(bundle);
        EvidenceGraph evidenceGraph = evidenceBuilder.build(bundle);
        Map<String, List<String>> evidenceRefsByFact = evidenceRefsByFact(evidenceGraph);
        ObjectNode root = JSON.createObjectNode();
        ObjectNode database = root.putObject("database");
        database.put("type", bundle.databaseType());
        database.put("catalog", bundle.catalog());
        database.put("schema", bundle.schema());
        root.set("metadataInventory", SemanticMetadataInventoryEnvelope.from(bundle.metadataInventory()));
        List<SemanticEventCandidate> events = eventExtractor.extract(bundle);
        root.set("inputFiles", strings(bundle.inputFiles().stream()
                .map(SemanticInputPathCanonicalizer::canonicalize)
                .toList()));
        root.set("sources", strings(bundle.sources()));
        root.set("tables", strings(new ArrayList<>(physicalTables)));
        root.set("evidence", evidence(evidenceGraph.evidenceRefs()));
        root.set("metadataTables", metadataFacts(evidenceGraph, "MetadataTableFact", "table"));
        root.set("metadataColumns", metadataFacts(evidenceGraph, "MetadataColumnFact", "column"));
        root.set("metadataConstraints", metadataFacts(evidenceGraph, "MetadataConstraintFact", "constraint"));
        root.set("metadataIndexes", metadataFacts(evidenceGraph, "MetadataIndexFact", "index"));
        root.set("relationships", relationships(bundle.relationships(), evidenceRefsByFact));
        root.set("lineage", lineages(bundle.dataLineages(), evidenceRefsByFact));
        root.set("eventCandidates", eventCandidates(events));
        root.set("derivedRelationships", relationships(bundle.derivedRelationships(), evidenceRefsByFact));
        root.set("derivedLineage", lineages(bundle.derivedDataLineages(), evidenceRefsByFact));
        root.set("namingEvidence", namingEvidence(bundle.namingEvidence(), evidenceRefsByFact));
        root.set("reviewItemCandidates", reviewItemCandidateGenerator.build(bundle));
        root.set("tripletCandidates", tripletCandidateBuilder.build(bundle, events));
        root.set("diagnostics", diagnostics(bundle.diagnostics()));
        root.putObject("instructions")
                .put("allOutputsMustUseEvidenceRefs", true)
                .put("llmCannotCreateDatabaseFacts", true)
                .put("businessApprovedIsForbidden", true)
                .put("markUncertainItemsReviewNeeded", true);
        return root;
    }

    /**
     * CN: 将一个磁盘运输窗口直接流式写成临时bundle，并返回同一次构建得到的physical evidence graph；
     * event只输出direct typed contribution，relationship/derived关联留给全局外排阶段。本方法不构造根级JSON树。
     * EN: Streams one disk transport window directly to a temporary bundle and returns the physical evidence graph
     * built in the same pass. Events contain direct typed contributions only; global external joins attach
     * relationships and derived lineage. This method never constructs a root JSON tree.
     */
    public EvidenceGraph writeTransportWindow(ScanBundle bundle, Path target) {
        if (bundle == null || target == null) {
            throw new IllegalArgumentException("scan bundle and transport target are required");
        }
        Set<String> physicalTables = physicalTables(bundle);
        EvidenceGraph evidenceGraph = evidenceBuilder.buildTransportWindow(bundle);
        Map<String, List<String>> evidenceRefsByFact = evidenceRefsByFact(evidenceGraph);
        List<SemanticEventCandidate> events = eventExtractor.extractTransportContributions(bundle);
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream output = Files.newOutputStream(target);
                 JsonGenerator generator = JSON.getFactory().createGenerator(output)) {
                generator.writeStartObject();
                generator.writeObjectFieldStart("database");
                generator.writeStringField("type", bundle.databaseType());
                generator.writeStringField("catalog", bundle.catalog());
                generator.writeStringField("schema", bundle.schema());
                generator.writeEndObject();
                generator.writeObjectField(
                        "metadataInventory",
                        SemanticMetadataInventoryEnvelope.from(bundle.metadataInventory()));
                writeStrings(generator, "inputFiles", bundle.inputFiles().stream()
                        .map(SemanticInputPathCanonicalizer::canonicalize)
                        .toList());
                writeStrings(generator, "sources", bundle.sources());
                writeStrings(generator, "tables", physicalTables);
                writeEvidence(generator, evidenceGraph);
                writeMetadataFacts(generator, evidenceGraph, "metadataTables", "MetadataTableFact", "table");
                writeMetadataFacts(generator, evidenceGraph, "metadataColumns", "MetadataColumnFact", "column");
                writeMetadataFacts(
                        generator, evidenceGraph, "metadataConstraints", "MetadataConstraintFact", "constraint");
                writeMetadataFacts(generator, evidenceGraph, "metadataIndexes", "MetadataIndexFact", "index");
                writeRelationships(generator, "relationships", bundle.relationships(), evidenceRefsByFact);
                writeLineages(generator, "lineage", bundle.dataLineages(), evidenceRefsByFact);
                writeEvents(generator, events);
                writeRelationships(
                        generator, "derivedRelationships", bundle.derivedRelationships(), evidenceRefsByFact);
                writeLineages(generator, "derivedLineage", bundle.derivedDataLineages(), evidenceRefsByFact);
                writeNaming(generator, bundle.namingEvidence(), evidenceRefsByFact);
                writeReviews(generator, bundle.diagnostics());
                writeTriplets(generator, bundle);
                writeDiagnostics(generator, bundle.diagnostics());
                generator.writeObjectFieldStart("instructions");
                generator.writeBooleanField("allOutputsMustUseEvidenceRefs", true);
                generator.writeBooleanField("llmCannotCreateDatabaseFacts", true);
                generator.writeBooleanField("businessApprovedIsForbidden", true);
                generator.writeBooleanField("markUncertainItemsReviewNeeded", true);
                generator.writeEndObject();
                generator.writeEndObject();
            }
            return evidenceGraph;
        } catch (IOException failure) {
            throw new SemanticExtractionValidationException(
                    "failed to stream semantic transport window");
        }
    }

    private Set<String> physicalTables(ScanBundle bundle) {
        Set<String> tables = new LinkedHashSet<>();
        bundle.metadataInventory().tables().forEach(table -> tables.add(tableIdentity(
                table.catalog(), table.schema(), table.tableName())));
        bundle.metadataInventory().columns().forEach(column -> tables.add(tableIdentity(
                column.catalog(), column.schema(), column.tableName())));
        bundle.metadataInventory().constraints().forEach(constraint -> {
            tables.add(tableIdentity(
                    constraint.catalog(), constraint.schema(), constraint.tableName()));
            if (constraint.referencedTable() != null && !constraint.referencedTable().isBlank()) {
                tables.add(tableIdentity(
                        constraint.referencedCatalog(),
                        constraint.referencedSchema(),
                        constraint.referencedTable()));
            }
        });
        bundle.metadataInventory().indexes().forEach(index -> tables.add(tableIdentity(
                index.catalog(), index.schema(), index.tableName())));
        for (ScanRelationshipFact relationship : bundle.relationships()) {
            addTable(tables, relationship.source());
            addTable(tables, relationship.target());
        }
        for (ScanRelationshipFact relationship : bundle.derivedRelationships()) {
            addTable(tables, relationship.source());
            addTable(tables, relationship.target());
        }
        for (ScanLineageFact lineage : bundle.dataLineages()) {
            lineage.sources().forEach(source -> addTable(tables, source));
            addTable(tables, lineage.target());
        }
        for (ScanLineageFact lineage : bundle.derivedDataLineages()) {
            lineage.sources().forEach(source -> addTable(tables, source));
            addTable(tables, lineage.target());
        }
        for (ScanNamingEvidenceFact naming : bundle.namingEvidence()) {
            addTable(tables, naming.source());
            addTable(tables, naming.target());
        }
        return tables;
    }

    private ArrayNode metadataFacts(EvidenceGraph graph, String type, String identityField) {
        ArrayNode result = JSON.createArrayNode();
        for (EvidenceGraphFact fact : graph.facts()) {
            if (!type.equals(fact.type())) {
                continue;
            }
            result.add(metadataFact(fact, identityField));
        }
        return result;
    }

    private ObjectNode metadataFact(EvidenceGraphFact fact, String identityField) {
        ObjectNode item = JSON.createObjectNode();
        item.put("id", fact.id());
        item.put(identityField, fact.endpoints().get(0).displayName());
        item.set("endpoints", strings(fact.endpoints().stream()
                .map(PhysicalEndpointRef::displayName)
                .toList()));
        item.set("evidenceRefs", strings(fact.evidenceRefs()));
        item.set("catalogFact", fact.payload());
        return item;
    }

    private String tableIdentity(String catalog, String schema, String table) {
        List<String> parts = new ArrayList<>();
        if (catalog != null && !catalog.isBlank()) {
            parts.add(catalog);
        }
        if (schema != null && !schema.isBlank()) {
            parts.add(schema);
        }
        parts.add(table);
        return String.join(".", parts);
    }

    private ArrayNode relationships(
            List<ScanRelationshipFact> relationships,
            Map<String, List<String>> evidenceRefsByFact
    ) {
        ArrayNode result = JSON.createArrayNode();
        for (ScanRelationshipFact relationship : relationships) {
            result.add(relationship(relationship, evidenceRefsByFact));
        }
        return result;
    }

    private ObjectNode relationship(
            ScanRelationshipFact relationship,
            Map<String, List<String>> evidenceRefsByFact
    ) {
        PhysicalEndpointRef source = relationship.source();
        PhysicalEndpointRef target = relationship.target();
        JsonNode document = relationship.document();
        ObjectNode item = JSON.createObjectNode();
        item.put("id", relationship.id());
        item.put("source", source.displayName());
        item.put("target", target.displayName());
        item.put("type", relationship.relationType());
        item.put("subType", relationship.relationSubType());
        item.put("confidence", relationship.confidence());
        item.set("evidenceRefs", strings(evidenceRefsByFact.getOrDefault(relationship.id(), List.of())));
        item.set("evidenceTypes", evidenceTypes(document.path("evidence")));
        return item;
    }

    private ArrayNode lineages(
            List<ScanLineageFact> lineages,
            Map<String, List<String>> evidenceRefsByFact
    ) {
        ArrayNode result = JSON.createArrayNode();
        for (ScanLineageFact lineage : lineages) {
            result.add(lineage(lineage, evidenceRefsByFact));
        }
        return result;
    }

    private ObjectNode lineage(
            ScanLineageFact lineage,
            Map<String, List<String>> evidenceRefsByFact
    ) {
        List<PhysicalEndpointRef> sources = new ArrayList<>(lineage.sources());
        PhysicalEndpointRef target = lineage.target();
        JsonNode document = lineage.document();
        ObjectNode item = JSON.createObjectNode();
        item.put("id", lineage.id());
        item.set("sources", strings(sources.stream().map(PhysicalEndpointRef::displayName).toList()));
        item.put("target", target.displayName());
        item.put("flowKind", lineage.flowKind());
        item.put("transformType", lineage.transformType());
        item.put("confidence", lineage.confidence());
        item.set("evidenceRefs", strings(evidenceRefsByFact.getOrDefault(lineage.id(), List.of())));
        item.set("evidenceSources", evidenceSources(document.path("evidence")));
        return item;
    }

    private ArrayNode eventCandidates(List<SemanticEventCandidate> events) {
        ArrayNode result = JSON.createArrayNode();
        for (SemanticEventCandidate event : events) {
            result.add(eventCandidate(event));
        }
        return result;
    }

    private ObjectNode eventCandidate(SemanticEventCandidate event) {
        ObjectNode item = JSON.createObjectNode();
        item.put("id", event.id());
        item.put("eventKind", event.eventKind());
        item.put("sourceType", event.sourceType());
        item.put("sourceObject", event.sourceObject());
        item.put("sourceObjectType", event.sourceObjectType());
        item.put("sourceObjectName", event.sourceObjectName());
        item.put("sourceFile", event.sourceFile());
        item.put("sourceStatementId", event.sourceStatementId());
        item.put("readableNameHint", event.readableNameHint());
        item.put("businessActionHint", event.businessActionHint());
        item.put("eventNameBasis", event.eventNameBasis());
        item.set("operationKinds", strings(event.operationKinds()));
        item.set("inputEndpoints", strings(event.inputEndpoints()));
        item.set("outputEndpoints", strings(event.outputEndpoints()));
        item.set("lineageRefs", strings(event.lineageRefs()));
        item.set("supportingDerivedLineageRefs", strings(event.supportingDerivedLineageRefs()));
        item.set("relationshipRefs", strings(event.relationshipRefs()));
        item.set("evidenceRefs", strings(event.evidenceRefs()));
        item.set("attributes", JSON.valueToTree(event.attributes()));
        item.put("confidence", event.confidence().doubleValue());
        return item;
    }

    private ArrayNode namingEvidence(
            List<ScanNamingEvidenceFact> namingEvidence,
            Map<String, List<String>> evidenceRefsByFact
    ) {
        ArrayNode result = JSON.createArrayNode();
        for (ScanNamingEvidenceFact naming : namingEvidence) {
            result.add(namingEvidence(naming, evidenceRefsByFact));
        }
        return result;
    }

    private ObjectNode namingEvidence(
            ScanNamingEvidenceFact naming,
            Map<String, List<String>> evidenceRefsByFact
    ) {
        PhysicalEndpointRef source = naming.source();
        PhysicalEndpointRef target = naming.target();
        ObjectNode item = JSON.createObjectNode();
        item.put("id", naming.id());
        item.put("source", source.displayName());
        item.put("target", target.displayName());
        item.put("rule", naming.rule());
        item.put("directionHint", naming.directionHint());
        item.set("evidenceRefs", strings(evidenceRefsByFact.getOrDefault(naming.id(), List.of())));
        return item;
    }

    private ArrayNode diagnostics(List<ScanDiagnosticFact> diagnostics) {
        ArrayNode result = JSON.createArrayNode();
        for (ScanDiagnosticFact diagnostic : diagnostics) {
            result.add(diagnostic(diagnostic));
        }
        return result;
    }

    private ObjectNode diagnostic(ScanDiagnosticFact diagnostic) {
        ObjectNode item = JSON.createObjectNode();
        item.put("id", diagnostic.id());
        item.put("code", diagnostic.code());
        item.put("severity", diagnostic.severity());
        item.put("message", diagnostic.message());
        item.put("source", diagnostic.source());
        return item;
    }

    private void addTable(Set<String> tables, PhysicalEndpointRef endpoint) {
        if (endpoint != null && !endpoint.table().isBlank()) {
            tables.add(endpoint.table());
        }
    }

    private Map<String, List<String>> evidenceRefsByFact(EvidenceGraph graph) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (EvidenceGraphFact fact : graph.facts()) {
            result.put(fact.id(), fact.evidenceRefs());
        }
        return result;
    }

    private ArrayNode evidence(List<EvidenceReference> references) {
        ArrayNode result = JSON.createArrayNode();
        for (EvidenceReference reference : references) {
            result.add(evidence(reference));
        }
        return result;
    }

    private ObjectNode evidence(EvidenceReference reference) {
        ObjectNode item = JSON.createObjectNode();
        item.put("id", reference.id());
        item.put("type", reference.evidenceType());
        item.put("sourceType", reference.sourceType());
        item.put("score", reference.score());
        item.put("source", reference.source());
        item.put("detail", reference.detail());
        item.set("attributes", JSON.valueToTree(reference.attributes()));
        return item;
    }

    private void writeStrings(JsonGenerator generator, String field, Iterable<String> values)
            throws IOException {
        generator.writeArrayFieldStart(field);
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                generator.writeString(value);
            }
        }
        generator.writeEndArray();
    }

    private void writeEvidence(JsonGenerator generator, EvidenceGraph graph) throws IOException {
        generator.writeArrayFieldStart("evidence");
        for (EvidenceReference reference : graph.evidenceRefs()) {
            generator.writeTree(evidence(reference));
        }
        generator.writeEndArray();
    }

    private void writeMetadataFacts(
            JsonGenerator generator,
            EvidenceGraph graph,
            String field,
            String type,
            String identityField
    ) throws IOException {
        generator.writeArrayFieldStart(field);
        for (EvidenceGraphFact fact : graph.facts()) {
            if (type.equals(fact.type())) {
                generator.writeTree(metadataFact(fact, identityField));
            }
        }
        generator.writeEndArray();
    }

    private void writeRelationships(
            JsonGenerator generator,
            String field,
            List<ScanRelationshipFact> relationships,
            Map<String, List<String>> evidenceRefsByFact
    ) throws IOException {
        generator.writeArrayFieldStart(field);
        for (ScanRelationshipFact relationship : relationships) {
            generator.writeTree(relationship(relationship, evidenceRefsByFact));
        }
        generator.writeEndArray();
    }

    private void writeLineages(
            JsonGenerator generator,
            String field,
            List<ScanLineageFact> lineages,
            Map<String, List<String>> evidenceRefsByFact
    ) throws IOException {
        generator.writeArrayFieldStart(field);
        for (ScanLineageFact lineage : lineages) {
            generator.writeTree(lineage(lineage, evidenceRefsByFact));
        }
        generator.writeEndArray();
    }

    private void writeEvents(
            JsonGenerator generator,
            List<SemanticEventCandidate> events
    ) throws IOException {
        generator.writeArrayFieldStart("eventCandidates");
        for (SemanticEventCandidate event : events) {
            generator.writeTree(eventCandidate(event));
        }
        generator.writeEndArray();
    }

    private void writeNaming(
            JsonGenerator generator,
            List<ScanNamingEvidenceFact> naming,
            Map<String, List<String>> evidenceRefsByFact
    ) throws IOException {
        generator.writeArrayFieldStart("namingEvidence");
        for (ScanNamingEvidenceFact fact : naming) {
            generator.writeTree(namingEvidence(fact, evidenceRefsByFact));
        }
        generator.writeEndArray();
    }

    private void writeReviews(
            JsonGenerator generator,
            List<ScanDiagnosticFact> diagnostics
    ) throws IOException {
        generator.writeArrayFieldStart("reviewItemCandidates");
        for (ScanDiagnosticFact diagnostic : diagnostics) {
            generator.writeTree(reviewItemCandidateGenerator.candidate(diagnostic));
        }
        generator.writeEndArray();
    }

    private void writeTriplets(JsonGenerator generator, ScanBundle bundle) throws IOException {
        generator.writeArrayFieldStart("tripletCandidates");
        try {
            tripletCandidateBuilder.forEachNonEvent(bundle, item -> {
                try {
                    generator.writeTree(item);
                } catch (IOException failure) {
                    throw new UncheckedIOException(failure);
                }
            });
        } catch (UncheckedIOException failure) {
            throw failure.getCause();
        }
        generator.writeEndArray();
    }

    private void writeDiagnostics(
            JsonGenerator generator,
            List<ScanDiagnosticFact> diagnostics
    ) throws IOException {
        generator.writeArrayFieldStart("diagnostics");
        for (ScanDiagnosticFact diagnostic : diagnostics) {
            generator.writeTree(diagnostic(diagnostic));
        }
        generator.writeEndArray();
    }

    private ArrayNode evidenceTypes(JsonNode evidenceArray) {
        ArrayNode result = JSON.createArrayNode();
        for (JsonNode evidence : evidenceArray) {
            String type = evidence.path("type").asText("");
            if (!type.isBlank()) {
                result.add(type);
            }
        }
        return result;
    }

    private ArrayNode evidenceSources(JsonNode evidenceArray) {
        ArrayNode result = JSON.createArrayNode();
        for (JsonNode evidence : evidenceArray) {
            String source = evidence.path("source").asText("");
            if (!source.isBlank()) {
                result.add(source);
            }
        }
        return result;
    }

    private ArrayNode strings(List<String> values) {
        ArrayNode result = JSON.createArrayNode();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                result.add(value);
            }
        }
        return result;
    }
}
