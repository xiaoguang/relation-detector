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
    private final SemanticKgRecordFactory records = new SemanticKgRecordFactory();

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
                addRecords(identity, referenceIndex, records.endpoint(endpoint, columnRefs, tableRefs));
            } else {
                List<String> tableRefs = refs(endpointEvidence, endpoint.table());
                referenceIndex.requireEvidence(tableNodeId(endpoint.table()), tableRefs);
                addRecords(identity, referenceIndex, records.endpoint(endpoint, tableRefs, tableRefs));
            }
        }

        for (EvidenceGraphFact fact : graph.facts()) {
            addRecords(identity, referenceIndex, records.fact(fact));
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

    private void addRecords(
            SemanticKgIdentityRegistry identity,
            ReferenceIndex referenceIndex,
            SemanticKgRecordFactory.Records materialized
    ) {
        materialized.nodes().forEach(identity::addNode);
        materialized.edges().forEach(edge -> addEdge(identity, referenceIndex, edge));
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
