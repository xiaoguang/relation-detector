package com.relationdetector.semantic.graph;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.StableSemanticId;
import com.relationdetector.semantic.event.SemanticEventCandidate;
import com.relationdetector.semantic.event.SemanticEventExtractor;
import com.relationdetector.semantic.model.PhysicalEndpointRef;
import com.relationdetector.semantic.reader.PhysicalEndpointJsonReader;
import com.relationdetector.semantic.reader.ScanBundle;
import com.relationdetector.semantic.reader.ScanDiagnosticFact;
import com.relationdetector.semantic.reader.ScanFact;
import com.relationdetector.semantic.reader.ScanLineageFact;
import com.relationdetector.semantic.reader.ScanNamingEvidenceFact;
import com.relationdetector.semantic.reader.ScanRelationshipFact;
import com.relationdetector.contracts.metadata.MetadataColumnFact;
import com.relationdetector.contracts.metadata.MetadataConstraintFact;
import com.relationdetector.contracts.metadata.MetadataIndexFact;
import com.relationdetector.contracts.metadata.MetadataTableFact;

/**
 * CN: 将一个有界 typed ScanBundle 输入窗口的 metadata inventory、direct/derived facts、naming、
 * diagnostics 和 event contributions 确定性装配为 EvidenceGraph 记录。生产链路在窗口外全局聚合 event
 * 和 stable-ID；本类不调用 LLM、修改物理事实或把输入窗口定义为语义边界。
 *
 * EN: Deterministically assembles metadata inventory, direct and derived facts, naming, diagnostics, and event
 * contributions from one bounded typed ScanBundle input window into EvidenceGraph records. Production globally
 * merges events and stable IDs outside the window; this class neither calls an LLM, changes physical facts, nor
 * treats a transport window as a semantic boundary.
 */
public final class SemanticEvidenceBuilder {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private final SemanticEventExtractor eventExtractor = new SemanticEventExtractor();

    /**
     * CN: 按固定 section 顺序将 typed facts 和 event candidates materialize 为 endpoint/fact/evidence inventories。
     * evidence 闭合由下游 SemanticKgBuilder/ReferenceIndex 验证；本方法不补造引用或返回 KG。
     * EN: Materializes typed facts and event candidates into endpoint, fact, and evidence inventories in stable
     * section order. Downstream SemanticKgBuilder/ReferenceIndex owns evidence-closure validation; this method never
     * invents references or returns a KG.
     */
    public EvidenceGraph build(ScanBundle scanBundle) {
        Map<String, PhysicalEndpointRef> endpoints = new LinkedHashMap<>();
        Map<String, EvidenceReference> evidenceRefs = new LinkedHashMap<>();
        List<EvidenceGraphFact> facts = new ArrayList<>();

        addMetadataFacts(scanBundle, endpoints, evidenceRefs, facts);

        for (ScanRelationshipFact relationship : scanBundle.relationships()) {
            PhysicalEndpointRef source = relationship.source();
            PhysicalEndpointRef target = relationship.target();
            String id = relationship.id();
            JsonNode document = relationship.document();
            List<String> refs = evidenceRefs(id, document, evidenceRefs);
            addEndpoint(endpoints, source);
            addEndpoint(endpoints, target);
            facts.add(new EvidenceGraphFact(id, "RelationshipFact", source.displayName() + " -> " + target.displayName(),
                    List.of(source, target), refs, BigDecimal.valueOf(relationship.confidence()), document,
                    Map.of("relationType", relationship.relationType(),
                            "relationSubType", relationship.relationSubType())));
        }

        for (ScanLineageFact lineage : scanBundle.dataLineages()) {
            List<PhysicalEndpointRef> factEndpoints = new ArrayList<>();
            for (PhysicalEndpointRef endpoint : lineage.sources()) {
                addEndpoint(endpoints, endpoint);
                factEndpoints.add(endpoint);
            }
            PhysicalEndpointRef target = lineage.target();
            addEndpoint(endpoints, target);
            factEndpoints.add(target);
            String sources = factEndpoints.subList(0, factEndpoints.size() - 1).stream()
                    .map(PhysicalEndpointRef::displayName)
                    .reduce((left, right) -> left + "+" + right)
                    .orElse("unknown");
            String id = lineage.id();
            JsonNode document = lineage.document();
            facts.add(new EvidenceGraphFact(id, "LineageFact", sources + " -> " + target.displayName(),
                    factEndpoints, evidenceRefs(id, document, evidenceRefs), BigDecimal.valueOf(lineage.confidence()),
                    document, Map.of("flowKind", lineage.flowKind(), "transformType", lineage.transformType())));
        }

        for (ScanNamingEvidenceFact naming : scanBundle.namingEvidence()) {
            PhysicalEndpointRef source = naming.source();
            PhysicalEndpointRef target = naming.target();
            addEndpoint(endpoints, source);
            addEndpoint(endpoints, target);
            String id = naming.id();
            facts.add(new EvidenceGraphFact(id, "NamingEvidenceFact", source.displayName() + " -> " + target.displayName(),
                    List.of(source, target), evidenceRefs(id, naming.document(), evidenceRefs),
                    BigDecimal.valueOf(naming.confidence()), naming.document(),
                    Map.of("rule", naming.rule(), "directionHint", naming.directionHint())));
        }

        for (ScanRelationshipFact derived : scanBundle.derivedRelationships()) {
            addDerivedFact("DerivedRelationshipFact", derived, derived.source(), derived.target(),
                    derived.confidence(), endpoints, evidenceRefs, facts);
        }
        for (ScanLineageFact derived : scanBundle.derivedDataLineages()) {
            PhysicalEndpointRef source = derived.sources().isEmpty() ? derived.target() : derived.sources().get(0);
            addDerivedFact("DerivedLineageFact", derived, source, derived.target(), derived.confidence(),
                    endpoints, evidenceRefs, facts);
        }

        for (SemanticEventCandidate event : eventExtractor.extract(scanBundle)) {
            addEventFact(event, endpoints, evidenceRefs, facts);
        }

        for (ScanDiagnosticFact diagnostic : scanBundle.diagnostics()) {
            String id = diagnostic.id();
            JsonNode document = diagnostic.document();
            EvidenceReference ref = diagnosticEvidenceRef(id, document);
            evidenceRefs.putIfAbsent(ref.id(), ref);
            facts.add(new EvidenceGraphFact(id, "Diagnostic", diagnostic.message().isBlank() ? id : diagnostic.message(),
                    List.of(), List.of(ref.id()), BigDecimal.ZERO, document,
                    Map.of("code", diagnostic.code(), "severity", diagnostic.severity())));
        }

        List<JsonNode> diagnosticDocuments = scanBundle.diagnostics().stream()
                .map(ScanDiagnosticFact::document)
                .toList();
        return new EvidenceGraph(scanBundle, List.copyOf(endpoints.values()), facts,
                List.copyOf(evidenceRefs.values()), diagnosticDocuments, scanBundle.summary());
    }

    /**
     * CN: 将 COMPLETE inventory 的表、列、约束和索引转换为带稳定证据引用的 typed graph facts；
     * 输入是已验证 ScanBundle，副作用仅限当前构建集合，非法 endpoint 会中止整次 graph 构建。
     * EN: Converts tables, columns, constraints, and indexes from a COMPLETE inventory into typed graph facts
     * with stable evidence references. It mutates only the current build collections and aborts the whole graph
     * build when an endpoint is invalid.
     */
    private void addMetadataFacts(
            ScanBundle scanBundle,
            Map<String, PhysicalEndpointRef> endpoints,
            Map<String, EvidenceReference> evidenceRefs,
            List<EvidenceGraphFact> facts
    ) {
        for (MetadataTableFact table : scanBundle.metadataInventory().tables()) {
            PhysicalEndpointRef endpoint = PhysicalEndpointRef.table(tableIdentity(
                    table.catalog(), table.schema(), table.tableName()));
            addMetadataFact("MetadataTableFact", endpoint.displayName(), List.of(endpoint),
                    JSON.valueToTree(table), endpoints, evidenceRefs, facts);
        }
        for (MetadataColumnFact column : scanBundle.metadataInventory().columns()) {
            PhysicalEndpointRef endpoint = new PhysicalEndpointRef(
                    tableIdentity(column.catalog(), column.schema(), column.tableName()), column.columnName());
            addMetadataFact("MetadataColumnFact", endpoint.displayName(), List.of(endpoint),
                    JSON.valueToTree(column), endpoints, evidenceRefs, facts);
        }
        for (MetadataConstraintFact constraint : scanBundle.metadataInventory().constraints()) {
            List<PhysicalEndpointRef> factEndpoints = new ArrayList<>();
            addColumns(factEndpoints, constraint.catalog(), constraint.schema(), constraint.tableName(),
                    constraint.columns());
            addColumns(factEndpoints, constraint.referencedCatalog(), constraint.referencedSchema(),
                    constraint.referencedTable(), constraint.referencedColumns());
            if (factEndpoints.isEmpty()) {
                factEndpoints.add(PhysicalEndpointRef.table(tableIdentity(
                        constraint.catalog(), constraint.schema(), constraint.tableName())));
            }
            String identity = tableIdentity(constraint.catalog(), constraint.schema(), constraint.tableName())
                    + ":" + constraint.constraintName();
            addMetadataFact("MetadataConstraintFact", identity, factEndpoints,
                    JSON.valueToTree(constraint), endpoints, evidenceRefs, facts);
        }
        for (MetadataIndexFact index : scanBundle.metadataInventory().indexes()) {
            List<PhysicalEndpointRef> factEndpoints = new ArrayList<>();
            addColumns(factEndpoints, index.catalog(), index.schema(), index.tableName(), index.columns());
            if (factEndpoints.isEmpty()) {
                factEndpoints.add(PhysicalEndpointRef.table(tableIdentity(
                        index.catalog(), index.schema(), index.tableName())));
            }
            String identity = tableIdentity(index.catalog(), index.schema(), index.tableName())
                    + ":" + index.indexName();
            addMetadataFact("MetadataIndexFact", identity, factEndpoints,
                    JSON.valueToTree(index), endpoints, evidenceRefs, facts);
        }
    }

    private void addMetadataFact(
            String type,
            String identity,
            List<PhysicalEndpointRef> factEndpoints,
            JsonNode payload,
            Map<String, PhysicalEndpointRef> endpoints,
            Map<String, EvidenceReference> evidenceRefs,
            List<EvidenceGraphFact> facts
    ) {
        String id = StableSemanticId.of(type, identity, StableSemanticId.canonicalJson(payload));
        String evidenceId = StableSemanticId.of("evidence:metadata", id);
        for (PhysicalEndpointRef endpoint : factEndpoints) {
            addEndpoint(endpoints, endpoint);
        }
        EvidenceReference reference = new EvidenceReference(
                evidenceId, type, "METADATA", BigDecimal.ONE,
                "metadataInventory", identity, Map.of("inventoryStatus", "COMPLETE"));
        evidenceRefs.put(evidenceId, reference);
        facts.add(new EvidenceGraphFact(
                id, type, identity, factEndpoints, List.of(evidenceId), BigDecimal.ONE, payload,
                Map.of("inventoryStatus", "COMPLETE")));
    }

    private void addColumns(
            List<PhysicalEndpointRef> endpoints,
            String catalog,
            String schema,
            String table,
            List<String> columns
    ) {
        if (table == null || table.isBlank()) {
            return;
        }
        String tableIdentity = tableIdentity(catalog, schema, table);
        for (String column : columns) {
            if (column != null && !column.isBlank()) {
                endpoints.add(new PhysicalEndpointRef(tableIdentity, column));
            }
        }
    }

    private String tableIdentity(String catalog, String schema, String table) {
        List<String> parts = new ArrayList<>();
        if (catalog != null && !catalog.isBlank()) {
            parts.add(catalog);
        }
        if (schema != null && !schema.isBlank()) {
            parts.add(schema);
        }
        if (table == null || table.isBlank()) {
            throw new IllegalArgumentException("metadata table name is required");
        }
        parts.add(table);
        return String.join(".", parts);
    }

    private void addEventFact(
            SemanticEventCandidate event,
            Map<String, PhysicalEndpointRef> endpoints,
            Map<String, EvidenceReference> evidenceRefs,
            List<EvidenceGraphFact> facts
    ) {
        EvidenceGraphFact fact = eventFact(event);
        fact.endpoints().forEach(endpoint -> addEndpoint(endpoints, endpoint));
        facts.add(fact);
    }

    /**
     * CN: 将一个已经完成全局聚合的 typed event 转换为独立 graph fact；磁盘链路在所有运输窗口结束后
     * 调用本方法，因此不会把 partial event 写入正式 graph。
     * EN: Converts one globally finalized typed event into an independent graph fact. The disk-backed path invokes
     * this only after every transport window has completed, so partial events never enter the formal graph.
     */
    public EvidenceGraphFact eventFact(SemanticEventCandidate event) {
        if (event == null) {
            throw new IllegalArgumentException("semantic event candidate is required");
        }
        List<PhysicalEndpointRef> factEndpoints = new ArrayList<>();
        for (String endpointName : event.inputEndpoints()) {
            factEndpoints.add(PhysicalEndpointRef.column(endpointName));
        }
        for (String endpointName : event.outputEndpoints()) {
            factEndpoints.add(PhysicalEndpointRef.column(endpointName));
        }
        return new EvidenceGraphFact(event.id(), "SemanticEventCandidate",
                event.readableNameHint().isBlank()
                        ? (event.sourceObject().isBlank() ? event.eventKind() : event.sourceObject())
                        : event.readableNameHint(),
                factEndpoints, event.evidenceRefs(), event.confidence(), eventPayload(event),
                Map.of("eventKind", event.eventKind(),
                        "sourceType", event.sourceType(),
                        "sourceObjectType", event.sourceObjectType(),
                        "sourceObject", event.sourceObject(),
                        "readableNameHint", event.readableNameHint(),
                        "businessActionHint", event.businessActionHint(),
                        "eventNameBasis", event.eventNameBasis(),
                        "inputEndpointCount", event.inputEndpoints().size()));
    }

    private void addDerivedFact(
            String type,
            ScanFact fact,
            PhysicalEndpointRef source,
            PhysicalEndpointRef target,
            double confidence,
            Map<String, PhysicalEndpointRef> endpoints,
            Map<String, EvidenceReference> evidenceRefs,
            List<EvidenceGraphFact> facts
    ) {
        JsonNode derived = fact.document();
        addEndpoint(endpoints, source);
        addEndpoint(endpoints, target);
        List<PhysicalEndpointRef> factEndpoints = new ArrayList<>();
        for (JsonNode pathNode : derived.path("path")) {
            PhysicalEndpointRef step = PhysicalEndpointJsonReader.read(pathNode);
            addEndpoint(endpoints, step);
            factEndpoints.add(step);
        }
        if (factEndpoints.isEmpty()) {
            factEndpoints.add(source);
            factEndpoints.add(target);
        }
        String id = fact.id();
        facts.add(new EvidenceGraphFact(id, type, source.displayName() + " -> " + target.displayName(),
                factEndpoints, evidenceRefs(id, derived, evidenceRefs), BigDecimal.valueOf(confidence), derived,
                Map.of("pathLength", derived.path("pathLength").asInt(0),
                        "kind", derived.path("kind").asText(""))));
    }

    private List<String> evidenceRefs(
            String ownerId,
            JsonNode record,
            Map<String, EvidenceReference> evidenceRefs
    ) {
        JsonNode sourceArray = record.path("rawEvidence").isArray() && !record.path("rawEvidence").isEmpty()
                ? record.path("rawEvidence")
                : record.path("evidence");
        List<String> result = new ArrayList<>();
        if (sourceArray.isArray()) {
            for (JsonNode evidence : sourceArray) {
                EvidenceReference ref = evidenceRef(ownerId, evidence);
                evidenceRefs.putIfAbsent(ref.id(), ref);
                result.add(ref.id());
            }
        }
        return result;
    }

    private EvidenceReference evidenceRef(String ownerId, JsonNode evidence) {
        String id = StableSemanticId.of("evidence", ownerId, StableSemanticId.canonicalJson(evidence));
        String evidenceType = evidence.path("type").asText(evidence.path("transformType").asText("UNKNOWN"));
        return new EvidenceReference(
                id,
                evidenceType,
                evidence.path("sourceType").asText("UNKNOWN"),
                evidence.path("score").decimalValue(),
                evidence.path("source").asText(""),
                evidence.path("detail").asText(""),
                attributes(evidence.path("attributes"))
        );
    }

    private EvidenceReference diagnosticEvidenceRef(String ownerId, JsonNode diagnostic) {
        return new EvidenceReference(
                "evidence:" + ownerId,
                diagnostic.path("type").asText("DIAGNOSTIC"),
                "DIAGNOSTIC",
                BigDecimal.ZERO,
                diagnostic.path("source").asText(""),
                diagnostic.path("message").asText(""),
                attributes(diagnostic.path("attributes"))
        );
    }

    private ObjectNode eventPayload(SemanticEventCandidate event) {
        ObjectNode payload = JSON.createObjectNode();
        payload.put("id", event.id());
        payload.put("eventKind", event.eventKind());
        payload.put("sourceType", event.sourceType());
        payload.put("sourceObject", event.sourceObject());
        payload.put("sourceObjectType", event.sourceObjectType());
        payload.put("sourceObjectName", event.sourceObjectName());
        payload.put("sourceFile", event.sourceFile());
        payload.put("sourceStatementId", event.sourceStatementId());
        payload.put("readableNameHint", event.readableNameHint());
        payload.put("businessActionHint", event.businessActionHint());
        payload.put("eventNameBasis", event.eventNameBasis());
        payload.set("operationKinds", strings(event.operationKinds()));
        payload.set("inputEndpoints", strings(event.inputEndpoints()));
        payload.set("outputEndpoints", strings(event.outputEndpoints()));
        payload.set("lineageRefs", strings(event.lineageRefs()));
        payload.set("supportingDerivedLineageRefs", strings(event.supportingDerivedLineageRefs()));
        payload.set("relationshipRefs", strings(event.relationshipRefs()));
        payload.set("evidenceRefs", strings(event.evidenceRefs()));
        payload.put("confidence", event.confidence());
        return payload;
    }

    private ArrayNode strings(List<String> values) {
        ArrayNode result = JSON.createArrayNode();
        for (String value : values) {
            result.add(value);
        }
        return result;
    }

    private void addEndpoint(Map<String, PhysicalEndpointRef> endpoints, PhysicalEndpointRef endpoint) {
        endpoints.putIfAbsent(endpoint.displayName(), endpoint);
        endpoints.putIfAbsent(endpoint.table(), PhysicalEndpointRef.table(endpoint.table()));
    }

    private Map<String, Object> attributes(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        return JSON.convertValue(node, MAP_TYPE);
    }
}
