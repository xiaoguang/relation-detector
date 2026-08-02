package com.relationdetector.semantic.kg;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.relationdetector.semantic.graph.EvidenceGraphFact;
import com.relationdetector.semantic.model.PhysicalEndpointRef;

/**
 * CN: 将一个已验证 endpoint 或 graph fact 确定性转换为 KG node/edge records；内存 builder 与磁盘
 * assembler 共用该映射，调用方负责 evidence closure 和 stable-ID 冲突校验。本类不持有全局图。
 * EN: Deterministically maps one validated endpoint or graph fact to KG node and edge records. The in-memory
 * builder and disk-backed assembler share this mapping; callers own evidence closure and stable-ID conflict
 * validation. This factory retains no global graph.
 */
public final class SemanticKgRecordFactory {
    public Records endpoint(
            PhysicalEndpointRef endpoint,
            List<String> columnEvidence,
            List<String> tableEvidence
    ) {
        List<SemanticNode> nodes = new ArrayList<>();
        List<SemanticEdge> edges = new ArrayList<>();
        if (endpoint.isColumnLevel()) {
            nodes.add(new SemanticNode(
                    columnNodeId(endpoint),
                    "PhysicalColumn",
                    endpoint.displayName(),
                    java.math.BigDecimal.ONE,
                    "EVIDENCE_SUPPORTED",
                    columnEvidence,
                    Map.of("table", endpoint.table(), "column", endpoint.column())));
            nodes.add(tableNode(endpoint.table(), tableEvidence));
            edges.add(new SemanticEdge(
                    "edge:table-column:" + endpoint.displayName(),
                    "TABLE_COLUMN",
                    tableNodeId(endpoint.table()),
                    columnNodeId(endpoint),
                    java.math.BigDecimal.ONE,
                    columnEvidence,
                    Map.of()));
        } else {
            nodes.add(tableNode(endpoint.table(), tableEvidence));
        }
        return new Records(nodes, edges);
    }

    public Records fact(EvidenceGraphFact fact) {
        List<SemanticNode> nodes = new ArrayList<>();
        List<SemanticEdge> edges = new ArrayList<>();
        String nodeType = switch (fact.type()) {
            case "RelationshipFact", "DerivedRelationshipFact" -> "RelationshipFact";
            case "LineageFact", "DerivedLineageFact" -> "LineageFact";
            case "NamingEvidenceFact" -> "NamingEvidenceFact";
            case "SemanticEventCandidate" -> "Event";
            case "Diagnostic" -> "Diagnostic";
            default -> fact.type();
        };
        nodes.add(new SemanticNode(
                fact.id(),
                nodeType,
                fact.label(),
                fact.confidence(),
                "Diagnostic".equals(fact.type()) ? "NEEDS_MORE_EVIDENCE" : "EVIDENCE_SUPPORTED",
                fact.evidenceRefs(),
                fact.attributes()));
        connectFact(fact, edges);
        if ("RelationshipFact".equals(fact.type()) || "DerivedRelationshipFact".equals(fact.type())) {
            addJoinPath(fact, nodes, edges);
        }
        return new Records(nodes, edges);
    }

    private void connectFact(EvidenceGraphFact fact, List<SemanticEdge> edges) {
        List<PhysicalEndpointRef> endpoints = fact.endpoints();
        for (int index = 0; index < endpoints.size(); index++) {
            PhysicalEndpointRef endpoint = endpoints.get(index);
            String type = switch (fact.type()) {
                case "RelationshipFact", "DerivedRelationshipFact" ->
                        index == 0 ? "RELATIONSHIP_SOURCE" : "RELATIONSHIP_TARGET";
                case "LineageFact", "DerivedLineageFact" ->
                        index == endpoints.size() - 1 ? "LINEAGE_TARGET" : "LINEAGE_SOURCE";
                case "NamingEvidenceFact" -> index == 0 ? "NAMING_SOURCE" : "NAMING_TARGET";
                case "SemanticEventCandidate" ->
                        index < eventInputEndpointCount(fact) ? "EVENT_INPUT" : "EVENT_OUTPUT";
                default -> "FACT_ENDPOINT";
            };
            edges.add(new SemanticEdge(
                    "edge:" + type + ":" + fact.id() + ":" + endpoint.displayName() + ":" + index,
                    type,
                    fact.id(),
                    endpointNodeId(endpoint),
                    fact.confidence(),
                    fact.evidenceRefs(),
                    Map.of("ordinal", index)));
        }
    }

    /**
     * CN: 将一个已验证的relationship path转换为单个JoinPath节点、首尾连接和有序step边，并把原fact
     * evidence完整复制到每条图记录。少于两个endpoint时无副作用；本方法不解析SQL、不选择路径，也不
     * 负责重复ID校验，冲突由下游KG store处理。
     *
     * EN: Converts one validated relationship path into a JoinPath node, source/target links, and ordered step edges,
     * copying the fact evidence onto every graph record. Paths with fewer than two endpoints have no effect. This
     * method neither parses SQL nor selects paths; downstream KG stores reject conflicting identities.
     */
    private void addJoinPath(
            EvidenceGraphFact fact,
            List<SemanticNode> nodes,
            List<SemanticEdge> edges
    ) {
        List<PhysicalEndpointRef> endpoints = fact.endpoints();
        if (endpoints.size() < 2) {
            return;
        }
        String pathId = "joinpath:" + stripRelationshipPrefix(fact.id());
        nodes.add(new SemanticNode(
                pathId,
                "JoinPath",
                fact.label(),
                fact.confidence(),
                "EVIDENCE_SUPPORTED",
                fact.evidenceRefs(),
                Map.of("sourceFact", fact.id(), "hopCount", Math.max(1, endpoints.size() - 1))));
        edges.add(new SemanticEdge(
                "edge:joinpath-source:" + pathId,
                "JOIN_PATH_SOURCE",
                pathId,
                endpointNodeId(endpoints.get(0)),
                fact.confidence(),
                fact.evidenceRefs(),
                Map.of()));
        edges.add(new SemanticEdge(
                "edge:joinpath-target:" + pathId,
                "JOIN_PATH_TARGET",
                pathId,
                endpointNodeId(endpoints.get(endpoints.size() - 1)),
                fact.confidence(),
                fact.evidenceRefs(),
                Map.of()));
        for (int index = 0; index < endpoints.size() - 1; index++) {
            edges.add(new SemanticEdge(
                    "edge:joinpath-step:" + pathId + ":" + index,
                    "JOIN_PATH_STEP",
                    endpointNodeId(endpoints.get(index)),
                    endpointNodeId(endpoints.get(index + 1)),
                    fact.confidence(),
                    fact.evidenceRefs(),
                    Map.of("joinPath", pathId, "ordinal", index)));
        }
    }

    private String stripRelationshipPrefix(String id) {
        if (id.startsWith("derived-relationship:")) {
            return id.substring("derived-relationship:".length());
        }
        if (id.startsWith("relationship:")) {
            return id.substring("relationship:".length());
        }
        return id;
    }

    private int eventInputEndpointCount(EvidenceGraphFact fact) {
        Object value = fact.attributes().get("inputEndpointCount");
        return value instanceof Number number ? number.intValue() : 0;
    }

    private SemanticNode tableNode(String table, List<String> evidence) {
        return new SemanticNode(
                tableNodeId(table),
                "PhysicalTable",
                table,
                java.math.BigDecimal.ONE,
                "EVIDENCE_SUPPORTED",
                evidence,
                Map.of("table", table));
    }

    private String endpointNodeId(PhysicalEndpointRef endpoint) {
        return endpoint.isColumnLevel() ? columnNodeId(endpoint) : tableNodeId(endpoint.table());
    }

    private String tableNodeId(String table) {
        return "table:" + table;
    }

    private String columnNodeId(PhysicalEndpointRef endpoint) {
        return "column:" + endpoint.displayName();
    }

    public record Records(List<SemanticNode> nodes, List<SemanticEdge> edges) {
        public Records {
            nodes = List.copyOf(nodes == null ? List.of() : nodes);
            edges = List.copyOf(edges == null ? List.of() : edges);
        }
    }
}
