package com.relationdetector.semantic.graph;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.relationdetector.semantic.model.PhysicalEndpointRef;
import com.relationdetector.semantic.reader.ScanBundle;

/**
 * CN: 保存 KG materialization 前的 physical endpoints、facts、evidence refs、diagnostics 和 source bundle；所有集合不可变，graph 不执行 LLM enrichment。
 * EN: Immutable pre-KG evidence graph containing physical endpoints, facts, evidence references, diagnostics, and the source bundle. It performs no LLM enrichment.
 */
public record EvidenceGraph(
        ScanBundle scanBundle,
        List<PhysicalEndpointRef> endpoints,
        List<EvidenceGraphFact> facts,
        List<EvidenceReference> evidenceRefs,
        List<JsonNode> diagnostics,
        Map<String, Integer> summary
) {
    public EvidenceGraph {
        if (scanBundle == null) {
            throw new IllegalArgumentException("scan bundle is required");
        }
        endpoints = List.copyOf(endpoints == null ? List.of() : endpoints);
        facts = List.copyOf(facts == null ? List.of() : facts);
        evidenceRefs = List.copyOf(evidenceRefs == null ? List.of() : evidenceRefs);
        diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
        summary = Map.copyOf(summary == null ? Map.of() : summary);
    }
}
