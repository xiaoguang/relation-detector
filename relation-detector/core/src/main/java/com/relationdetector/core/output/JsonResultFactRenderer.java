package com.relationdetector.core.output;

import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.model.DataLineageEvidence;
import com.relationdetector.contracts.model.DerivedPathCandidate;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.NamingEvidenceCandidate;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.core.evidence.EvidenceObservationAggregator;
import com.relationdetector.core.scan.MetadataInventory;

/**
 * CN: 将已合并的 relationship、lineage、naming、derived、warning 与 inventory 转为 writer wire node，
 * 并计算对应 observation 数；输入是不可变事实，输出是新建 JSON node。本类不选择 direct/derived 视图、
 * 不写顶层 document，也不修改、评分或合并事实。
 * EN: Renders merged relationship, lineage, naming, derived, warning, and inventory facts into fresh writer wire
 * nodes and computes their observation counts. It neither selects the direct/derived view nor writes the top-level
 * document, and it never mutates, scores, or merges facts.
 */
final class JsonResultFactRenderer {
    private final ObjectMapper json;

    JsonResultFactRenderer(ObjectMapper json) {
        this.json = json;
    }

    ObjectNode metadataInventoryNode(MetadataInventory inventory) {
        ObjectNode node = json.createObjectNode();
        node.put("status", inventory.status().name());
        ObjectNode scope = node.putObject("scope");
        scope.put("catalog", safe(inventory.scope().catalog()));
        scope.put("schema", safe(inventory.scope().schema()));
        ArrayNode includeTables = scope.putArray("includeTables");
        inventory.scope().includeTables().forEach(includeTables::add);
        ArrayNode excludeTables = scope.putArray("excludeTables");
        inventory.scope().excludeTables().forEach(excludeTables::add);

        ObjectNode counts = node.putObject("counts");
        counts.put("tables", inventory.tables().size());
        counts.put("columns", inventory.columns().size());
        counts.put("constraints", inventory.constraints().size());
        counts.put("indexes", inventory.indexes().size());
        node.set("tables", json.valueToTree(inventory.tables()));
        node.set("columns", json.valueToTree(inventory.columns()));
        node.set("constraints", json.valueToTree(inventory.constraints()));
        node.set("indexes", json.valueToTree(inventory.indexes()));
        return node;
    }

    ObjectNode relationshipNode(
            RelationshipCandidate relation,
            boolean includeEvidence,
            boolean includeWarnings
    ) {
        ObjectNode node = json.createObjectNode();
        node.set("source", endpointNode(relation.source()));
        node.set("target", endpointNode(relation.target()));
        node.put("relationType", relation.relationType().name());
        node.put("relationSubType", relation.relationSubType().name());
        node.put("confidence", relation.confidence().setScale(4, RoundingMode.HALF_UP));
        node.set("rawEvidence", includeEvidence
                ? evidenceNode(relation.rawEvidence().isEmpty() ? relation.evidence() : relation.rawEvidence())
                : json.createArrayNode());
        node.set("evidence", includeEvidence
                ? evidenceNode(relation.evidence())
                : json.createArrayNode());
        node.set("warnings", includeWarnings ? warningsNode(relation.warnings()) : json.createArrayNode());
        if (!relation.attributes().isEmpty()) {
            node.set("attributes", attributesNode(relation.attributes()));
        }
        return node;
    }

    ObjectNode dataLineageNode(
            DataLineageCandidate lineage,
            boolean includeEvidence,
            boolean includeWarnings
    ) {
        ObjectNode node = json.createObjectNode();
        ArrayNode sources = node.putArray("sources");
        lineage.sources().forEach(source -> sources.add(endpointNode(source)));
        node.set("target", endpointNode(lineage.target()));
        node.put("flowKind", lineage.flowKind().name());
        node.put("transformType", lineage.transformType().name());
        node.put("confidence", lineage.confidence().setScale(4, RoundingMode.HALF_UP));
        node.set("rawEvidence", includeEvidence
                ? dataLineageEvidenceNode(lineage.rawEvidence().isEmpty()
                        ? lineage.evidence() : lineage.rawEvidence())
                : json.createArrayNode());
        node.set("evidence", includeEvidence
                ? dataLineageEvidenceNode(lineage.evidence())
                : json.createArrayNode());
        node.set("warnings", includeWarnings ? warningsNode(lineage.warnings()) : json.createArrayNode());
        node.set("attributes", attributesNode(lineage.attributes()));
        return node;
    }

    ObjectNode namingEvidenceNode(NamingEvidenceCandidate naming, boolean includeEvidence) {
        ObjectNode node = lightweightNamingEvidenceNode(naming);
        node.set("evidence", includeEvidence
                ? evidenceNode(List.of(naming.evidence()))
                : json.createArrayNode());
        node.set("rawEvidence", includeEvidence
                ? evidenceNode(naming.rawEvidence())
                : json.createArrayNode());
        return node;
    }

    ObjectNode lightweightNamingEvidenceNode(NamingEvidenceCandidate naming) {
        ObjectNode node = json.createObjectNode();
        node.put("id", naming.id());
        node.set("source", endpointNode(naming.source()));
        node.set("target", endpointNode(naming.target()));
        node.put("rule", safe(naming.rule()));
        node.put("directionHint", naming.directionHint());
        return node;
    }

    ObjectNode derivedPathNode(DerivedPathCandidate candidate, boolean includeEvidence) {
        ObjectNode node = json.createObjectNode();
        node.put("kind", candidate.kind().name());
        node.set("source", endpointNode(candidate.source()));
        node.set("target", endpointNode(candidate.target()));
        node.put("pathLength", candidate.pathLength());
        node.put("confidence", candidate.confidence().setScale(4, RoundingMode.HALF_UP));
        ArrayNode path = node.putArray("path");
        candidate.path().forEach(endpoint -> path.add(endpointNode(endpoint)));
        node.set("rawEvidence", includeEvidence
                ? evidenceNode(candidate.rawEvidence().isEmpty()
                        ? candidate.evidence() : candidate.rawEvidence())
                : json.createArrayNode());
        node.set("evidence", includeEvidence
                ? evidenceNode(candidate.evidence())
                : json.createArrayNode());
        node.set("attributes", attributesNode(candidate.attributes()));
        return node;
    }

    ArrayNode warningsNode(List<WarningMessage> warnings) {
        ArrayNode array = json.createArrayNode();
        warnings.forEach(warning -> array.add(warningNode(warning)));
        return array;
    }

    ObjectNode warningNode(WarningMessage warning) {
        ObjectNode node = json.createObjectNode();
        node.put("type", warning.type().name());
        node.put("severity", warning.severity().name());
        node.put("code", safe(warning.code()));
        node.put("message", safe(warning.message()));
        node.put("source", safe(warning.source()));
        node.put("line", warning.line());
        node.set("attributes", attributesNode(warning.attributes()));
        return node;
    }

    int relationshipObservationCount(List<RelationshipCandidate> relationships) {
        return relationships.stream()
                .flatMap(relation -> (relation.rawEvidence().isEmpty()
                        ? relation.evidence() : relation.rawEvidence()).stream())
                .mapToInt(evidence -> EvidenceObservationAggregator.occurrenceCount(evidence.attributes()))
                .sum();
    }

    int dataLineageObservationCount(List<DataLineageCandidate> lineages) {
        return lineages.stream()
                .flatMap(lineage -> (lineage.rawEvidence().isEmpty()
                        ? lineage.evidence() : lineage.rawEvidence()).stream())
                .mapToInt(evidence -> EvidenceObservationAggregator.occurrenceCount(evidence.attributes()))
                .sum();
    }

    int namingEvidenceObservationCount(List<NamingEvidenceCandidate> namingEvidence) {
        return namingEvidence.stream()
                .flatMap(candidate -> candidate.rawEvidence().stream())
                .mapToInt(evidence -> EvidenceObservationAggregator.occurrenceCount(evidence.attributes()))
                .sum();
    }

    int derivedPathObservationCount(List<DerivedPathCandidate> candidates) {
        return candidates.stream()
                .flatMap(candidate -> (candidate.rawEvidence().isEmpty()
                        ? candidate.evidence() : candidate.rawEvidence()).stream())
                .mapToInt(evidence -> EvidenceObservationAggregator.occurrenceCount(evidence.attributes()))
                .sum();
    }

    private ObjectNode endpointNode(Endpoint endpoint) {
        ObjectNode node = json.createObjectNode();
        node.put("table", endpoint.table().displayName());
        if (endpoint.isColumnLevel()) {
            node.put("column", endpoint.column().columnName());
        } else {
            node.putNull("column");
        }
        return node;
    }

    private ArrayNode evidenceNode(List<Evidence> evidence) {
        ArrayNode array = json.createArrayNode();
        evidence.forEach(item -> {
            ObjectNode node = array.addObject();
            node.put("type", item.type().name());
            node.put("sourceType", item.sourceType().name());
            node.put("score", item.score());
            node.put("source", safe(item.source()));
            node.put("detail", safe(item.detail()));
            if (item.attributes().containsKey("evidenceRef")) {
                node.put("evidenceRef", String.valueOf(item.attributes().get("evidenceRef")));
            }
            node.set("attributes", attributesNode(item.attributes()));
        });
        return array;
    }

    private ArrayNode dataLineageEvidenceNode(List<DataLineageEvidence> evidence) {
        ArrayNode array = json.createArrayNode();
        evidence.forEach(item -> {
            ObjectNode node = array.addObject();
            node.put("type", "DATA_LINEAGE");
            node.put("transformType", item.transformType().name());
            node.put("sourceType", item.sourceType().name());
            node.put("score", item.score());
            node.put("source", safe(item.source()));
            node.put("detail", safe(item.detail()));
            node.set("attributes", attributesNode(item.attributes()));
        });
        return array;
    }

    private ObjectNode attributesNode(Map<String, Object> attributes) {
        ObjectNode node = json.createObjectNode();
        attributes.forEach((key, value) -> node.set(key, json.valueToTree(value)));
        return node;
    }

    static String safe(String value) {
        return value == null ? "" : value;
    }
}
