package com.relationdetector.semantic.kg;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.relationdetector.semantic.graph.EvidenceGraph;
import com.relationdetector.semantic.graph.EvidenceGraphFact;
import com.relationdetector.semantic.reader.EndpointRef;
import com.relationdetector.semantic.reader.ScanBundle;

/** Materializes an evidence graph into a JSON-friendly KG. */
public final class SemanticKgBuilder {
    public SemanticKnowledgeGraph build(EvidenceGraph graph) {
        Map<String, SemanticNode> nodes = new LinkedHashMap<>();
        Map<String, SemanticEdge> edges = new LinkedHashMap<>();
        Map<String, List<String>> endpointEvidence = new LinkedHashMap<>();

        for (EvidenceGraphFact fact : graph.facts()) {
            for (EndpointRef endpoint : fact.endpoints()) {
                String endpointKey = endpoint.displayName();
                endpointEvidence.computeIfAbsent(endpointKey, ignored -> new ArrayList<>()).addAll(fact.evidenceRefs());
                endpointEvidence.computeIfAbsent(endpoint.table(), ignored -> new ArrayList<>()).addAll(fact.evidenceRefs());
            }
        }

        for (EndpointRef endpoint : graph.endpoints()) {
            if (endpoint.isColumnLevel()) {
                addNode(nodes, new SemanticNode(columnNodeId(endpoint), "PhysicalColumn", endpoint.displayName(),
                        BigDecimal.ONE, "EVIDENCE_SUPPORTED", refs(endpointEvidence, endpoint.displayName()),
                        Map.of("table", endpoint.table(), "column", endpoint.column())));
                addNode(nodes, new SemanticNode(tableNodeId(endpoint.table()), "PhysicalTable", endpoint.table(),
                        BigDecimal.ONE, "EVIDENCE_SUPPORTED", refs(endpointEvidence, endpoint.table()),
                        Map.of("table", endpoint.table())));
                addEdge(edges, new SemanticEdge("edge:table-column:" + endpoint.displayName(), "TABLE_COLUMN",
                        tableNodeId(endpoint.table()), columnNodeId(endpoint), BigDecimal.ONE,
                        refs(endpointEvidence, endpoint.displayName()), Map.of()));
            } else {
                addNode(nodes, new SemanticNode(tableNodeId(endpoint.table()), "PhysicalTable", endpoint.table(),
                        BigDecimal.ONE, "EVIDENCE_SUPPORTED", refs(endpointEvidence, endpoint.table()),
                        Map.of("table", endpoint.table())));
            }
        }

        for (EvidenceGraphFact fact : graph.facts()) {
            String nodeType = switch (fact.type()) {
                case "RelationshipFact", "DerivedRelationshipFact" -> "RelationshipFact";
                case "LineageFact", "DerivedLineageFact" -> "LineageFact";
                case "NamingEvidenceFact" -> "NamingEvidenceFact";
                case "Diagnostic" -> "Diagnostic";
                default -> fact.type();
            };
            addNode(nodes, new SemanticNode(fact.id(), nodeType, fact.label(), fact.confidence(),
                    reviewStatus(fact), fact.evidenceRefs(), fact.attributes()));
            connectFact(nodes, edges, fact);
            if ("RelationshipFact".equals(fact.type()) || "DerivedRelationshipFact".equals(fact.type())) {
                addJoinPath(nodes, edges, fact);
            }
        }

        Map<String, Integer> summary = new LinkedHashMap<>();
        summary.put("nodeCount", nodes.size());
        summary.put("edgeCount", edges.size());
        summary.put("evidenceRefCount", graph.evidenceRefs().size());
        summary.put("diagnosticCount", graph.diagnostics().size());
        summary.put("inputRelationshipCount", graph.scanBundle().relationships().size());
        summary.put("inputDataLineageCount", graph.scanBundle().dataLineages().size());
        summary.put("inputNamingEvidenceCount", graph.scanBundle().namingEvidence().size());
        summary.put("inputDerivedRelationshipCount", graph.scanBundle().derivedRelationships().size());
        summary.put("inputDerivedDataLineageCount", graph.scanBundle().derivedDataLineages().size());

        return new SemanticKnowledgeGraph(buildRun(graph.scanBundle()), summary, List.copyOf(nodes.values()),
                List.copyOf(edges.values()), graph.evidenceRefs(), graph.diagnostics());
    }

    private void connectFact(Map<String, SemanticNode> nodes, Map<String, SemanticEdge> edges, EvidenceGraphFact fact) {
        List<EndpointRef> endpoints = fact.endpoints();
        for (int i = 0; i < endpoints.size(); i++) {
            EndpointRef endpoint = endpoints.get(i);
            String endpointNode = endpoint.isColumnLevel() ? columnNodeId(endpoint) : tableNodeId(endpoint.table());
            String type = switch (fact.type()) {
                case "RelationshipFact", "DerivedRelationshipFact" -> i == 0 ? "RELATIONSHIP_SOURCE" : "RELATIONSHIP_TARGET";
                case "LineageFact", "DerivedLineageFact" -> i == endpoints.size() - 1 ? "LINEAGE_TARGET" : "LINEAGE_SOURCE";
                case "NamingEvidenceFact" -> i == 0 ? "NAMING_SOURCE" : "NAMING_TARGET";
                default -> "FACT_ENDPOINT";
            };
            addEdge(edges, new SemanticEdge("edge:" + type + ":" + fact.id() + ":" + endpoint.displayName() + ":" + i,
                    type, fact.id(), endpointNode, fact.confidence(), fact.evidenceRefs(), Map.of("ordinal", i)));
        }
        for (String evidenceRef : fact.evidenceRefs()) {
            addEdge(edges, new SemanticEdge("edge:supported-by:" + fact.id() + ":" + evidenceRef,
                    "SUPPORTED_BY_EVIDENCE", fact.id(), evidenceRef, fact.confidence(), List.of(evidenceRef), Map.of()));
        }
    }

    private void addJoinPath(Map<String, SemanticNode> nodes, Map<String, SemanticEdge> edges, EvidenceGraphFact fact) {
        List<EndpointRef> endpoints = fact.endpoints();
        if (endpoints.size() < 2) {
            return;
        }
        String pathId = "joinpath:" + fact.id().replaceFirst("^(derived-relationship:|relationship:)", "");
        addNode(nodes, new SemanticNode(pathId, "JoinPath", fact.label(), fact.confidence(), "EVIDENCE_SUPPORTED",
                fact.evidenceRefs(), Map.of("sourceFact", fact.id(), "hopCount", Math.max(1, endpoints.size() - 1))));
        addEdge(edges, new SemanticEdge("edge:joinpath-source:" + pathId, "JOIN_PATH_SOURCE",
                pathId, endpointNodeId(endpoints.get(0)), fact.confidence(), fact.evidenceRefs(), Map.of()));
        addEdge(edges, new SemanticEdge("edge:joinpath-target:" + pathId, "JOIN_PATH_TARGET",
                pathId, endpointNodeId(endpoints.get(endpoints.size() - 1)), fact.confidence(), fact.evidenceRefs(), Map.of()));
        for (int i = 0; i < endpoints.size() - 1; i++) {
            addEdge(edges, new SemanticEdge("edge:joinpath-step:" + pathId + ":" + i,
                    "JOIN_PATH_STEP", endpointNodeId(endpoints.get(i)), endpointNodeId(endpoints.get(i + 1)),
                    fact.confidence(), fact.evidenceRefs(), Map.of("joinPath", pathId, "ordinal", i)));
        }
    }

    private String endpointNodeId(EndpointRef endpoint) {
        return endpoint.isColumnLevel() ? columnNodeId(endpoint) : tableNodeId(endpoint.table());
    }

    private void addNode(Map<String, SemanticNode> nodes, SemanticNode node) {
        nodes.putIfAbsent(node.id(), node);
    }

    private void addEdge(Map<String, SemanticEdge> edges, SemanticEdge edge) {
        if (!edge.evidenceRefs().isEmpty()) {
            edges.putIfAbsent(edge.id(), edge);
        }
    }

    private List<String> refs(Map<String, List<String>> refsByEndpoint, String key) {
        return refsByEndpoint.getOrDefault(key, List.of()).stream().distinct().toList();
    }

    private String tableNodeId(String table) {
        return "table:" + table;
    }

    private String columnNodeId(EndpointRef endpoint) {
        return "column:" + endpoint.displayName();
    }

    private String reviewStatus(EvidenceGraphFact fact) {
        return "Diagnostic".equals(fact.type()) ? "NEEDS_MORE_EVIDENCE" : "EVIDENCE_SUPPORTED";
    }

    private Map<String, Object> buildRun(ScanBundle bundle) {
        return Map.of(
                "builtAt", Instant.now().toString(),
                "database", Map.of("type", bundle.databaseType(), "schema", bundle.schema()),
                "generatedAt", bundle.generatedAt(),
                "sources", bundle.sources(),
                "inputFiles", bundle.inputFiles().stream().map(path -> path.toAbsolutePath().toString()).toList()
        );
    }
}
