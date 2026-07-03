package com.relationdetector.core.output;

import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.model.DataLineageEvidence;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.NamingEvidenceCandidate;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.core.relation.NamingEvidenceMerger;
import com.relationdetector.core.scan.ScanResult;

/**
 * JSON output writer backed by Jackson's object model.
 *
 * <p>CN: ObjectMapper 在静态初始化阶段完成配置，之后只读复用；每次 write 都创建新的
 * ObjectNode/ArrayNode，因此服务端和测试并发调用不会共享可变输出状态。
 *
 * <p>EN: The ObjectMapper is fully configured during static initialization and
 * reused read-only. Each write call creates fresh ObjectNode/ArrayNode state,
 * so concurrent service or test calls do not share mutable output state.
 */
public final class JsonResultWriter {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final NamingEvidenceMerger namingEvidenceMerger = new NamingEvidenceMerger();

    /**
     * 将 ScanResult 渲染成最终 JSON 字符串。
     *
     * <p>EN: Renders ScanResult into the final JSON string.
     */
    public String write(ScanResult result, boolean includeEvidence, boolean includeWarnings) {
        return write(result, includeEvidence, includeWarnings, true);
    }

    /**
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
        List<NamingEvidenceCandidate> namingEvidence = namingEvidenceMerger.merge(result.namingEvidence());

        ObjectNode root = JSON.createObjectNode();
        ObjectNode database = root.putObject("database");
        database.put("type", safe(result.databaseType()));
        database.put("schema", safe(result.schema()));
        root.put("generatedAt", String.valueOf(result.generatedAt()));

        ObjectNode summary = root.putObject("summary");
        summary.put("relationshipCount", result.relationships().size());
        summary.put("dataLineageCount", result.dataLineages().size());
        summary.put("namingEvidenceCount", namingEvidence.size());
        if (includeObservationCounts) {
            summary.put("relationshipObservationCount", relationshipObservationCount(result.relationships()));
            summary.put("dataLineageObservationCount", dataLineageObservationCount(result.dataLineages()));
            summary.put("namingEvidenceObservationCount", namingEvidenceObservationCount(namingEvidence));
        }
        summary.put("warningCount", result.warnings().size());
        ArrayNode sources = summary.putArray("sources");
        result.sources().forEach(sources::add);

        ArrayNode relationships = root.putArray("relationships");
        result.relationships().forEach(relation ->
                relationships.add(relationshipNode(relation, includeEvidence)));

        ArrayNode dataLineages = root.putArray("dataLineages");
        result.dataLineages().forEach(lineage ->
                dataLineages.add(dataLineageNode(lineage, includeEvidence)));

        ArrayNode naming = root.putArray("namingEvidence");
        namingEvidence.forEach(candidate ->
                naming.add(namingEvidenceNode(candidate, includeEvidence)));

        if (includeWarnings) {
            root.set("warnings", warningsNode(result.warnings()));
        } else {
            root.set("warnings", JSON.createArrayNode());
        }

        try {
            return JSON.writeValueAsString(root) + "\n";
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to render scan result JSON", e);
        }
    }

    private int relationshipObservationCount(List<RelationshipCandidate> relationships) {
        return relationships.stream()
                .mapToInt(relation -> relation.rawEvidence().isEmpty()
                        ? relation.evidence().size()
                        : relation.rawEvidence().size())
                .sum();
    }

    private int dataLineageObservationCount(List<DataLineageCandidate> lineages) {
        return lineages.stream()
                .mapToInt(lineage -> lineage.rawEvidence().isEmpty()
                        ? lineage.evidence().size()
                        : lineage.rawEvidence().size())
                .sum();
    }

    private int namingEvidenceObservationCount(List<NamingEvidenceCandidate> namingEvidence) {
        return namingEvidence.stream()
                .mapToInt(candidate -> candidate.rawEvidence().size())
                .sum();
    }

    private ObjectNode relationshipNode(RelationshipCandidate relation, boolean includeEvidence) {
        ObjectNode node = JSON.createObjectNode();
        node.set("source", endpointNode(relation.source()));
        node.set("target", endpointNode(relation.target()));
        node.put("relationType", relation.relationType().name());
        node.put("relationSubType", relation.relationSubType().name());
        node.put("confidence", relation.confidence().setScale(4, RoundingMode.HALF_UP));
        node.set("rawEvidence", includeEvidence
                ? evidenceNode(relation.rawEvidence().isEmpty() ? relation.evidence() : relation.rawEvidence())
                : JSON.createArrayNode());
        node.set("evidence", includeEvidence
                ? evidenceNode(relation.evidence())
                : JSON.createArrayNode());
        node.set("warnings", warningsNode(relation.warnings()));
        return node;
    }

    private ObjectNode dataLineageNode(DataLineageCandidate lineage, boolean includeEvidence) {
        ObjectNode node = JSON.createObjectNode();
        ArrayNode sources = node.putArray("sources");
        lineage.sources().forEach(source -> sources.add(endpointNode(source)));
        node.set("target", endpointNode(lineage.target()));
        node.put("flowKind", lineage.flowKind().name());
        node.put("transformType", lineage.transformType().name());
        node.put("confidence", lineage.confidence().setScale(4, RoundingMode.HALF_UP));
        node.set("rawEvidence", includeEvidence
                ? dataLineageEvidenceNode(lineage.rawEvidence().isEmpty() ? lineage.evidence() : lineage.rawEvidence())
                : JSON.createArrayNode());
        node.set("evidence", includeEvidence
                ? dataLineageEvidenceNode(lineage.evidence())
                : JSON.createArrayNode());
        node.set("warnings", warningsNode(lineage.warnings()));
        node.set("attributes", attributesNode(lineage.attributes()));
        return node;
    }

    private ObjectNode namingEvidenceNode(NamingEvidenceCandidate naming, boolean includeEvidence) {
        ObjectNode node = JSON.createObjectNode();
        node.put("id", naming.id());
        node.set("source", endpointNode(naming.source()));
        node.set("target", endpointNode(naming.target()));
        node.put("rule", safe(naming.rule()));
        node.put("directionHint", naming.directionHint());
        node.set("evidence", includeEvidence
                ? evidenceNode(List.of(naming.evidence()))
                : JSON.createArrayNode());
        node.set("rawEvidence", includeEvidence
                ? evidenceNode(naming.rawEvidence())
                : JSON.createArrayNode());
        return node;
    }

    private ObjectNode endpointNode(Endpoint endpoint) {
        ObjectNode node = JSON.createObjectNode();
        node.put("table", endpoint.table().displayName());
        if (endpoint.isColumnLevel()) {
            node.put("column", endpoint.column().columnName());
        } else {
            node.putNull("column");
        }
        return node;
    }

    private ArrayNode evidenceNode(List<Evidence> evidence) {
        ArrayNode array = JSON.createArrayNode();
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
        ArrayNode array = JSON.createArrayNode();
        evidence.forEach(item -> {
            ObjectNode node = array.addObject();
            node.put("transformType", item.transformType().name());
            node.put("sourceType", item.sourceType().name());
            node.put("score", item.score());
            node.put("source", safe(item.source()));
            node.put("detail", safe(item.detail()));
            node.set("attributes", attributesNode(item.attributes()));
        });
        return array;
    }

    private ArrayNode warningsNode(List<WarningMessage> warnings) {
        ArrayNode array = JSON.createArrayNode();
        warnings.forEach(warning -> {
            ObjectNode node = array.addObject();
            node.put("type", warning.type().name());
            node.put("severity", warning.severity().name());
            node.put("code", safe(warning.code()));
            node.put("message", safe(warning.message()));
            node.put("source", safe(warning.source()));
            node.put("line", warning.line());
            node.set("attributes", attributesNode(warning.attributes()));
        });
        return array;
    }

    private ObjectNode attributesNode(Map<String, Object> attributes) {
        ObjectNode node = JSON.createObjectNode();
        attributes.forEach((key, value) -> node.set(key, JSON.valueToTree(value)));
        return node;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
