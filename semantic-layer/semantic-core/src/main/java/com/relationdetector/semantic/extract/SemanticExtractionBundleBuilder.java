package com.relationdetector.semantic.extract;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
            ObjectNode item = result.addObject();
            item.put("id", fact.id());
            item.put(identityField, fact.endpoints().get(0).displayName());
            item.set("endpoints", strings(fact.endpoints().stream()
                    .map(PhysicalEndpointRef::displayName)
                    .toList()));
            item.set("evidenceRefs", strings(fact.evidenceRefs()));
            item.set("catalogFact", fact.payload());
        }
        return result;
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
            PhysicalEndpointRef source = relationship.source();
            PhysicalEndpointRef target = relationship.target();
            JsonNode document = relationship.document();
            ObjectNode item = result.addObject();
            item.put("id", relationship.id());
            item.put("source", source.displayName());
            item.put("target", target.displayName());
            item.put("type", relationship.relationType());
            item.put("subType", relationship.relationSubType());
            item.put("confidence", relationship.confidence());
            item.set("evidenceRefs", strings(evidenceRefsByFact.getOrDefault(relationship.id(), List.of())));
            item.set("evidenceTypes", evidenceTypes(document.path("evidence")));
        }
        return result;
    }

    private ArrayNode lineages(
            List<ScanLineageFact> lineages,
            Map<String, List<String>> evidenceRefsByFact
    ) {
        ArrayNode result = JSON.createArrayNode();
        for (ScanLineageFact lineage : lineages) {
            List<PhysicalEndpointRef> sources = new ArrayList<>(lineage.sources());
            PhysicalEndpointRef target = lineage.target();
            JsonNode document = lineage.document();
            ObjectNode item = result.addObject();
            item.put("id", lineage.id());
            item.set("sources", strings(sources.stream().map(PhysicalEndpointRef::displayName).toList()));
            item.put("target", target.displayName());
            item.put("flowKind", lineage.flowKind());
            item.put("transformType", lineage.transformType());
            item.put("confidence", lineage.confidence());
            item.set("evidenceRefs", strings(evidenceRefsByFact.getOrDefault(lineage.id(), List.of())));
            item.set("evidenceSources", evidenceSources(document.path("evidence")));
        }
        return result;
    }

    private ArrayNode eventCandidates(List<SemanticEventCandidate> events) {
        ArrayNode result = JSON.createArrayNode();
        for (SemanticEventCandidate event : events) {
            ObjectNode item = result.addObject();
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
        }
        return result;
    }

    private ArrayNode namingEvidence(
            List<ScanNamingEvidenceFact> namingEvidence,
            Map<String, List<String>> evidenceRefsByFact
    ) {
        ArrayNode result = JSON.createArrayNode();
        for (ScanNamingEvidenceFact naming : namingEvidence) {
            PhysicalEndpointRef source = naming.source();
            PhysicalEndpointRef target = naming.target();
            ObjectNode item = result.addObject();
            item.put("id", naming.id());
            item.put("source", source.displayName());
            item.put("target", target.displayName());
            item.put("rule", naming.rule());
            item.put("directionHint", naming.directionHint());
            item.set("evidenceRefs", strings(evidenceRefsByFact.getOrDefault(naming.id(), List.of())));
        }
        return result;
    }

    private ArrayNode diagnostics(List<ScanDiagnosticFact> diagnostics) {
        ArrayNode result = JSON.createArrayNode();
        for (ScanDiagnosticFact diagnostic : diagnostics) {
            ObjectNode item = result.addObject();
            item.put("id", diagnostic.id());
            item.put("code", diagnostic.code());
            item.put("severity", diagnostic.severity());
            item.put("message", diagnostic.message());
            item.put("source", diagnostic.source());
        }
        return result;
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
            ObjectNode item = result.addObject();
            item.put("id", reference.id());
            item.put("type", reference.evidenceType());
            item.put("sourceType", reference.sourceType());
            item.put("score", reference.score());
            item.put("source", reference.source());
            item.put("detail", reference.detail());
            item.set("attributes", JSON.valueToTree(reference.attributes()));
        }
        return result;
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
