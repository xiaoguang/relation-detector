package com.relationdetector.semantic.reader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.graph.EvidenceGraphFact;
import com.relationdetector.semantic.kg.SemanticEdge;
import com.relationdetector.semantic.kg.SemanticKgRecordFactory;
import com.relationdetector.semantic.kg.SemanticNode;
import com.relationdetector.semantic.model.PhysicalEndpointRef;

/**
 * CN: 从全局 graph record store 外排聚合 endpoint evidence，并逐条生成完整 KG node/edge stores；输入已
 * 跨运输窗口归并，输出可直接流式写文件。本类不加载完整 graph，也不重新解释物理事实。
 * EN: Externally aggregates endpoint evidence from the global graph record store and materializes complete KG node
 * and edge stores one record at a time. Its input is already merged across transport windows, its output streams
 * directly to files, and it neither loads the full graph nor reinterprets physical facts.
 */
final class SemanticDiskBackedKgStore implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final SemanticGraphRecordStore graph;
    private final ExternalJsonRecordStore endpointEvidence;
    private final ExternalJsonRecordStore nodes;
    private final ExternalJsonRecordStore edges;
    private final SemanticKgRecordFactory factory = new SemanticKgRecordFactory();

    SemanticDiskBackedKgStore(SemanticGraphRecordStore graph, Path workspace) {
        if (graph == null || workspace == null) {
            throw new IllegalArgumentException("semantic graph records and KG workspace are required");
        }
        this.graph = graph;
        this.endpointEvidence = new ExternalJsonRecordStore(
                workspace.resolve("endpoint-evidence"), this::mergeEvidence);
        this.nodes = new ExternalJsonRecordStore(workspace.resolve("nodes"));
        this.edges = new ExternalJsonRecordStore(workspace.resolve("edges"));
        build();
    }

    long nodeCount() {
        return nodes.count();
    }

    long edgeCount() {
        return edges.count();
    }

    void writeNodes(JsonGenerator generator) throws IOException {
        nodes.writeArray(generator, "nodes");
    }

    void writeEdges(JsonGenerator generator) throws IOException {
        edges.writeArray(generator, "edges");
    }

    private void build() {
        graph.forEach(SemanticGraphRecordStore.Section.FACTS, value -> {
            EvidenceGraphFact fact = fact(value);
            requireEvidence(fact);
            ObjectNode refs = refs(fact.evidenceRefs());
            for (PhysicalEndpointRef endpoint : fact.endpoints()) {
                endpointEvidence.append(endpoint.displayName(), refs);
                endpointEvidence.append(endpoint.table(), refs);
            }
        });
        endpointEvidence.finish();

        graph.forEach(SemanticGraphRecordStore.Section.ENDPOINTS, value -> {
            PhysicalEndpointRef endpoint = endpoint(value);
            List<String> columnRefs = evidence(endpoint.displayName());
            List<String> tableRefs = evidence(endpoint.table());
            if (endpoint.isColumnLevel() && columnRefs.isEmpty() || tableRefs.isEmpty()) {
                throw new ScanResultContractException(
                        "semantic physical endpoint has no global evidence");
            }
            append(factory.endpoint(endpoint, columnRefs, tableRefs));
        });
        graph.forEach(SemanticGraphRecordStore.Section.FACTS, value -> append(factory.fact(fact(value))));
        nodes.finish();
        edges.finish();
    }

    private void requireEvidence(EvidenceGraphFact fact) {
        if (!"Diagnostic".equals(fact.type()) && fact.evidenceRefs().isEmpty()) {
            throw new ScanResultContractException(
                    "semantic graph fact requires evidence: " + fact.id());
        }
        for (String ref : fact.evidenceRefs()) {
            if (!graph.containsReference(ref)) {
                throw new ScanResultContractException(
                        "semantic graph fact has unresolved evidence: " + fact.id());
            }
        }
    }

    private void append(SemanticKgRecordFactory.Records records) {
        for (SemanticNode node : records.nodes()) {
            nodes.append(node.id(), JSON.valueToTree(node));
        }
        for (SemanticEdge edge : records.edges()) {
            if (edge.evidenceRefs().isEmpty()) {
                throw new ScanResultContractException(
                        "semantic KG edge requires evidence: " + edge.id());
            }
            for (String ref : edge.evidenceRefs()) {
                if (!graph.containsReference(ref)) {
                    throw new ScanResultContractException(
                            "semantic KG edge has unresolved evidence: " + edge.id());
                }
            }
            edges.append(edge.id(), JSON.valueToTree(edge));
        }
    }

    private List<String> evidence(String endpoint) {
        return endpointEvidence.get(endpoint)
                .map(record -> {
                    java.util.ArrayList<String> refs = new java.util.ArrayList<>();
                    record.value().path("refs").forEach(value -> refs.add(value.asText()));
                    return List.copyOf(refs);
                })
                .orElse(List.of());
    }

    private ObjectNode refs(List<String> values) {
        ObjectNode result = JSON.createObjectNode();
        values.stream().distinct().sorted().forEach(result.putArray("refs")::add);
        return result;
    }

    private JsonNode mergeEvidence(JsonNode left, JsonNode right) {
        TreeSet<String> refs = new TreeSet<>();
        left.path("refs").forEach(value -> refs.add(value.asText()));
        right.path("refs").forEach(value -> refs.add(value.asText()));
        return refs(List.copyOf(refs));
    }

    private EvidenceGraphFact fact(JsonNode value) {
        String id = value.path("id").asText("");
        String type = value.path("type").asText("");
        if (id.isBlank() || type.isBlank() || !value.path("endpoints").isArray()
                || !value.path("evidenceRefs").isArray()) {
            throw new ScanResultContractException("semantic graph fact is malformed");
        }
        List<PhysicalEndpointRef> endpoints = new ArrayList<>();
        value.path("endpoints").forEach(endpoint -> endpoints.add(endpoint(endpoint)));
        List<String> evidenceRefs = new ArrayList<>();
        value.path("evidenceRefs").forEach(ref -> evidenceRefs.add(ref.asText()));
        Map<String, Object> attributes = value.path("attributes").isObject()
                ? JSON.convertValue(value.path("attributes"), new com.fasterxml.jackson.core.type.TypeReference<>() {
                })
                : Map.of();
        return new EvidenceGraphFact(
                id,
                type,
                value.path("label").asText(id),
                endpoints,
                evidenceRefs,
                value.path("confidence").decimalValue(),
                value.get("payload"),
                attributes);
    }

    private PhysicalEndpointRef endpoint(JsonNode value) {
        String table = value.path("table").asText("");
        if (table.isBlank()) {
            throw new ScanResultContractException("semantic graph endpoint is malformed");
        }
        String column = value.path("column").isNull() ? null : value.path("column").asText(null);
        return new PhysicalEndpointRef(table, column);
    }

    @Override
    public void close() {
        RuntimeException failure = null;
        for (ExternalJsonRecordStore store : List.of(endpointEvidence, nodes, edges)) {
            try {
                store.close();
            } catch (RuntimeException error) {
                if (failure == null) {
                    failure = error;
                } else {
                    failure.addSuppressed(error);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
