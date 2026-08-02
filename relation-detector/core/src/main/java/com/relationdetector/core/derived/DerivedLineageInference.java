package com.relationdetector.core.derived;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.math.BigInteger;

import com.relationdetector.contracts.Enums.DerivedEvidenceHopKind;
import com.relationdetector.contracts.Enums.DerivedPathKind;
import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.model.DerivedPathCandidate;
import com.relationdetector.contracts.model.DerivedEvidenceSet;
import com.relationdetector.contracts.model.DataLineageEvidence;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.core.scan.ScanConfig;

final class DerivedLineageInference {
    private final ScanConfig config;
    private final DerivedPathGraphBuilder graphs;

    DerivedLineageInference(ScanConfig config, DerivedPathGraphBuilder graphs) {
        this.config = config;
        this.graphs = graphs;
    }

    List<DerivedPathCandidate> infer(List<DataLineageCandidate> dataLineages) {
        List<DerivedEdge> edges = new ArrayList<>();
        Set<String> directPairs = new HashSet<>();
        for (DataLineageCandidate lineage : dataLineages) {
            if (lineage.flowKind() != LineageFlowKind.VALUE) {
                continue;
            }
            for (Endpoint source : lineage.sources()) {
                if (!source.isColumnLevel() || !lineage.target().isColumnLevel()
                        || isPureNoOpSelfLineage(source, lineage)) {
                    continue;
                }
                directPairs.addAll(graphs.pairKeys(source, lineage.target()));
                edges.add(edge(source, lineage));
            }
        }
        List<DerivedPathObservation> observations =
                graphs.enumerate(graphs.build(edges), directPairs, false);
        return merge(observations);
    }

    private List<DerivedPathCandidate> merge(List<DerivedPathObservation> observations) {
        Map<String, List<DerivedPathObservation>> grouped = new LinkedHashMap<>();
        for (DerivedPathObservation observation : observations) {
            grouped.computeIfAbsent(
                    graphs.canonicalPathKey(DerivedPathKind.DATA_LINEAGE.name(), observation),
                    ignored -> new ArrayList<>()).add(observation);
        }
        List<DerivedPathCandidate> result = new ArrayList<>();
        for (List<DerivedPathObservation> variants : grouped.values()) {
            DerivedPathObservation representative = variants.stream()
                    .max(Comparator.comparing(graphs::confidence))
                    .orElseThrow();
            List<Endpoint> endpoints = graphs.endpoints(representative);
            DerivedPathCandidate candidate = new DerivedPathCandidate(
                    DerivedPathKind.DATA_LINEAGE,
                    representative.source(), representative.target(), endpoints);
            candidate.confidence(graphs.confidence(representative));
            candidate.attributes().put("pathLength", representative.edges().size());
            candidate.attributes().put("containsNamingEdge", false);
            candidate.attributes().put("containsTableIdentityBridge", false);
            candidate.attributes().put("path", graphs.endpointNames(endpoints));
            List<DerivedEvidenceSet> evidenceSets = variants.stream()
                    .map(variant -> graphs.evidenceSet(variant, endpoints, false))
                    .distinct()
                    .sorted(Comparator.comparing(graphs::evidenceSetKey))
                    .toList();
            candidate.evidenceSets().addAll(evidenceSets);
            BigInteger supportCombinations = evidenceSets.stream()
                    .map(DerivedEvidenceSet::combinationCount)
                    .reduce(BigInteger.ZERO, BigInteger::add);
            candidate.attributes().put("evidenceSetCount", evidenceSets.size());
            candidate.attributes().put("supportCombinationCount", supportCombinations);

            List<String> refs = evidenceSets.stream().flatMap(set -> set.hops().stream())
                    .flatMap(hop -> hop.evidenceRefs().stream()).distinct().sorted().toList();
            Evidence first = graphs.pathEvidence(
                    representative, "derived:data_lineage", false, endpoints, endpoints);
            Map<String, Object> summary = new LinkedHashMap<>(first.attributes());
            summary.put("evidenceSetCount", evidenceSets.size());
            summary.put("supportCombinationCount", supportCombinations);
            summary.put("pathEvidenceRefs", refs);
            candidate.evidence().add(new Evidence(
                    first.type(), candidate.confidence(), first.sourceType(),
                    first.source(), first.detail(), summary));
            result.add(candidate);
        }
        return List.copyOf(result);
    }

    private DerivedEdge edge(Endpoint source, DataLineageCandidate lineage) {
        List<DataLineageEvidence> evidence = lineage.rawEvidence().isEmpty()
                ? lineage.evidence() : lineage.rawEvidence();
        List<String> refs = evidence.stream().map(item -> lineageReference(source, lineage, item))
                .distinct().sorted().toList();
        return new DerivedEdge(
                source, lineage.target(), DerivedEvidenceHopKind.LINEAGE,
                lineage.confidence(), refs, List.of());
    }

    private String lineageReference(
            Endpoint source,
            DataLineageCandidate lineage,
            DataLineageEvidence evidence
    ) {
        return "lineage:" + source.normalizedKey() + "->" + lineage.target().normalizedKey()
                + ":" + lineage.flowKind().name() + ":" + evidence.transformType().name()
                + ":" + evidence.sourceType() + ":" + evidence.source()
                + ":" + evidence.attributes().getOrDefault("sourceStatementId", "")
                + ":" + evidence.attributes().getOrDefault("sourceLine", "")
                + ":" + evidence.attributes().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> entry.getKey() + "=" + String.valueOf(entry.getValue()))
                        .reduce((left, right) -> left + "," + right).orElse("")
                + ":" + evidence.detail();
    }

    private boolean isPureNoOpSelfLineage(Endpoint source, DataLineageCandidate lineage) {
        return graphs.sameEndpoint(source, lineage.target())
                && lineage.transformType() == LineageTransformType.DIRECT
                && lineage.evidence().stream().allMatch(evidence ->
                evidence.transformType() == LineageTransformType.DIRECT);
    }
}
