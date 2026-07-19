package com.relationdetector.semantic.kg;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.relationdetector.semantic.graph.EvidenceGraph;
import com.relationdetector.semantic.graph.EvidenceGraphFact;
import com.relationdetector.semantic.graph.ReferenceIndex;
import com.relationdetector.semantic.model.PhysicalEndpointRef;
import com.relationdetector.semantic.reader.ScanBundle;
import com.relationdetector.semantic.reader.SemanticInputPathCanonicalizer;

/**
 * CN: 将 EvidenceGraph 的 physical endpoints、facts 与 event candidates 确定性 materialize 为 KG nodes/edges，并验证 evidence refs；Clock 只产生 build metadata，不参与 id。
 * EN: Deterministically materializes physical endpoints, facts, and event candidates from EvidenceGraph into KG nodes and edges while validating evidence. Clock affects metadata only, never ids.
 */
public final class SemanticKgBuilder {
    private final Clock clock;

    public SemanticKgBuilder() {
        this(Clock.systemUTC());
    }

    public SemanticKgBuilder(Clock clock) {
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
    }

    /**
     * CN: 先建立 endpoint evidence inventory，再创建 table/column/fact/event nodes 和 edges；重复 node 或冲突 edge 明确失败，返回不可变 KG，不覆盖先前对象。
     * EN: Builds endpoint-evidence inventory before table, column, fact, and event nodes and edges. Duplicate nodes or conflicting edges fail; immutable assembly never overwrites objects.
     */
    public SemanticKnowledgeGraph build(EvidenceGraph graph) {
        SemanticKgIdentityRegistry identity = new SemanticKgIdentityRegistry();
        Map<String, List<String>> endpointEvidence = new LinkedHashMap<>();
        ReferenceIndex referenceIndex = ReferenceIndex.from(graph);

        for (EvidenceGraphFact fact : graph.facts()) {
            if ("Diagnostic".equals(fact.type())) {
                referenceIndex.requireResolvable(fact.id(), fact.evidenceRefs());
            } else {
                referenceIndex.requireEvidence(fact.id(), fact.evidenceRefs());
            }
            for (PhysicalEndpointRef endpoint : fact.endpoints()) {
                String endpointKey = endpoint.displayName();
                endpointEvidence.computeIfAbsent(endpointKey, ignored -> new ArrayList<>()).addAll(fact.evidenceRefs());
                endpointEvidence.computeIfAbsent(endpoint.table(), ignored -> new ArrayList<>()).addAll(fact.evidenceRefs());
            }
        }

        for (PhysicalEndpointRef endpoint : graph.endpoints()) {
            if (endpoint.isColumnLevel()) {
                List<String> columnRefs = refs(endpointEvidence, endpoint.displayName());
                List<String> tableRefs = refs(endpointEvidence, endpoint.table());
                referenceIndex.requireEvidence(columnNodeId(endpoint), columnRefs);
                referenceIndex.requireEvidence(tableNodeId(endpoint.table()), tableRefs);
                identity.addNode(new SemanticNode(columnNodeId(endpoint), "PhysicalColumn", endpoint.displayName(),
                        BigDecimal.ONE, "EVIDENCE_SUPPORTED", columnRefs,
                        Map.of("table", endpoint.table(), "column", endpoint.column())));
                identity.addNode(new SemanticNode(tableNodeId(endpoint.table()), "PhysicalTable", endpoint.table(),
                        BigDecimal.ONE, "EVIDENCE_SUPPORTED", tableRefs,
                        Map.of("table", endpoint.table())));
                addEdge(identity, referenceIndex, new SemanticEdge(
                        "edge:table-column:" + endpoint.displayName(), "TABLE_COLUMN",
                        tableNodeId(endpoint.table()), columnNodeId(endpoint), BigDecimal.ONE,
                        columnRefs, Map.of()));
            } else {
                List<String> tableRefs = refs(endpointEvidence, endpoint.table());
                referenceIndex.requireEvidence(tableNodeId(endpoint.table()), tableRefs);
                identity.addNode(new SemanticNode(tableNodeId(endpoint.table()), "PhysicalTable", endpoint.table(),
                        BigDecimal.ONE, "EVIDENCE_SUPPORTED", tableRefs,
                        Map.of("table", endpoint.table())));
            }
        }

        for (EvidenceGraphFact fact : graph.facts()) {
            String nodeType = switch (fact.type()) {
                case "RelationshipFact", "DerivedRelationshipFact" -> "RelationshipFact";
                case "LineageFact", "DerivedLineageFact" -> "LineageFact";
                case "NamingEvidenceFact" -> "NamingEvidenceFact";
                case "SemanticEventCandidate" -> "Event";
                case "Diagnostic" -> "Diagnostic";
                default -> fact.type();
            };
            identity.addNode(new SemanticNode(fact.id(), nodeType, fact.label(), fact.confidence(),
                    reviewStatus(fact), fact.evidenceRefs(), fact.attributes()));
            connectFact(identity, referenceIndex, fact);
            if ("RelationshipFact".equals(fact.type()) || "DerivedRelationshipFact".equals(fact.type())) {
                addJoinPath(identity, referenceIndex, fact);
            }
        }

        Map<String, Integer> summary = new LinkedHashMap<>();
        summary.put("nodeCount", identity.nodeCount());
        summary.put("edgeCount", identity.edgeCount());
        summary.put("evidenceRefCount", graph.evidenceRefs().size());
        summary.put("diagnosticCount", graph.diagnostics().size());
        summary.put("inputRelationshipCount", graph.scanBundle().relationships().size());
        summary.put("inputDataLineageCount", graph.scanBundle().dataLineages().size());
        summary.put("inputNamingEvidenceCount", graph.scanBundle().namingEvidence().size());
        summary.put("inputDerivedRelationshipCount", graph.scanBundle().derivedRelationships().size());
        summary.put("inputDerivedDataLineageCount", graph.scanBundle().derivedDataLineages().size());
        summary.put("eventCandidateCount", (int) graph.facts().stream()
                .filter(fact -> "SemanticEventCandidate".equals(fact.type()))
                .count());

        return new SemanticKnowledgeGraph(buildRun(graph.scanBundle()), summary, identity.nodes(),
                identity.edges(), graph.evidenceRefs(), graph.diagnostics());
    }

    private void connectFact(
            SemanticKgIdentityRegistry identity,
            ReferenceIndex referenceIndex,
            EvidenceGraphFact fact
    ) {
        List<PhysicalEndpointRef> endpoints = fact.endpoints();
        for (int i = 0; i < endpoints.size(); i++) {
            PhysicalEndpointRef endpoint = endpoints.get(i);
            String endpointNode = endpoint.isColumnLevel() ? columnNodeId(endpoint) : tableNodeId(endpoint.table());
            String type = switch (fact.type()) {
                case "RelationshipFact", "DerivedRelationshipFact" -> i == 0 ? "RELATIONSHIP_SOURCE" : "RELATIONSHIP_TARGET";
                case "LineageFact", "DerivedLineageFact" -> i == endpoints.size() - 1 ? "LINEAGE_TARGET" : "LINEAGE_SOURCE";
                case "NamingEvidenceFact" -> i == 0 ? "NAMING_SOURCE" : "NAMING_TARGET";
                case "SemanticEventCandidate" -> i < eventInputEndpointCount(fact) ? "EVENT_INPUT" : "EVENT_OUTPUT";
                default -> "FACT_ENDPOINT";
            };
            addEdge(identity, referenceIndex, new SemanticEdge(
                    "edge:" + type + ":" + fact.id() + ":" + endpoint.displayName() + ":" + i,
                    type, fact.id(), endpointNode, fact.confidence(), fact.evidenceRefs(), Map.of("ordinal", i)));
        }
        for (String evidenceRef : fact.evidenceRefs()) {
            addEdge(identity, referenceIndex, new SemanticEdge(
                    "edge:supported-by:" + fact.id() + ":" + evidenceRef,
                    "SUPPORTED_BY_EVIDENCE", fact.id(), evidenceRef, fact.confidence(), List.of(evidenceRef), Map.of()));
        }
    }

    private int eventInputEndpointCount(EvidenceGraphFact fact) {
        Object value = fact.attributes().get("inputEndpointCount");
        return value instanceof Number number ? number.intValue() : 0;
    }

    private void addJoinPath(
            SemanticKgIdentityRegistry identity,
            ReferenceIndex referenceIndex,
            EvidenceGraphFact fact
    ) {
        List<PhysicalEndpointRef> endpoints = fact.endpoints();
        if (endpoints.size() < 2) {
            return;
        }
        String pathId = "joinpath:" + fact.id().replaceFirst("^(derived-relationship:|relationship:)", "");
        identity.addNode(new SemanticNode(pathId, "JoinPath", fact.label(), fact.confidence(), "EVIDENCE_SUPPORTED",
                fact.evidenceRefs(), Map.of("sourceFact", fact.id(), "hopCount", Math.max(1, endpoints.size() - 1))));
        addEdge(identity, referenceIndex, new SemanticEdge("edge:joinpath-source:" + pathId, "JOIN_PATH_SOURCE",
                pathId, endpointNodeId(endpoints.get(0)), fact.confidence(), fact.evidenceRefs(), Map.of()));
        addEdge(identity, referenceIndex, new SemanticEdge("edge:joinpath-target:" + pathId, "JOIN_PATH_TARGET",
                pathId, endpointNodeId(endpoints.get(endpoints.size() - 1)), fact.confidence(), fact.evidenceRefs(), Map.of()));
        for (int i = 0; i < endpoints.size() - 1; i++) {
            addEdge(identity, referenceIndex, new SemanticEdge("edge:joinpath-step:" + pathId + ":" + i,
                    "JOIN_PATH_STEP", endpointNodeId(endpoints.get(i)), endpointNodeId(endpoints.get(i + 1)),
                    fact.confidence(), fact.evidenceRefs(), Map.of("joinPath", pathId, "ordinal", i)));
        }
    }

    private String endpointNodeId(PhysicalEndpointRef endpoint) {
        return endpoint.isColumnLevel() ? columnNodeId(endpoint) : tableNodeId(endpoint.table());
    }

    private void addEdge(
            SemanticKgIdentityRegistry identity,
            ReferenceIndex referenceIndex,
            SemanticEdge edge
    ) {
        referenceIndex.requireEvidence(edge.id(), edge.evidenceRefs());
        identity.addEdge(edge);
    }

    private List<String> refs(Map<String, List<String>> refsByEndpoint, String key) {
        return refsByEndpoint.getOrDefault(key, List.of()).stream().distinct().toList();
    }

    private String tableNodeId(String table) {
        return "table:" + table;
    }

    private String columnNodeId(PhysicalEndpointRef endpoint) {
        return "column:" + endpoint.displayName();
    }

    private String reviewStatus(EvidenceGraphFact fact) {
        return "Diagnostic".equals(fact.type()) ? "NEEDS_MORE_EVIDENCE" : "EVIDENCE_SUPPORTED";
    }

    private Map<String, Object> buildRun(ScanBundle bundle) {
        return Map.of(
                "builtAt", Instant.now(clock).toString(),
                "database", Map.of("type", bundle.databaseType(), "catalog", bundle.catalog(), "schema", bundle.schema()),
                "generatedAt", bundle.generatedAt(),
                "sources", bundle.sources(),
                "inputFiles", bundle.inputFiles().stream().map(SemanticInputPathCanonicalizer::canonicalize).toList()
        );
    }
}
