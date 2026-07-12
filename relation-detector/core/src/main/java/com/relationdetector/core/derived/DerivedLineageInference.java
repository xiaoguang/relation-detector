package com.relationdetector.core.derived;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.relationdetector.contracts.Enums.DerivedPathKind;
import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.model.DerivedPathCandidate;
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
            candidate.attributes().put("observationCount", variants.size());

            List<String> refs = variants.stream().flatMap(path -> path.edges().stream())
                    .map(DerivedEdge::ref).distinct().sorted().toList();
            Evidence first = graphs.pathEvidence(
                    representative, "derived:data_lineage", false, endpoints, endpoints);
            Map<String, Object> summary = new LinkedHashMap<>(first.attributes());
            summary.put("count", variants.size());
            summary.put("pathEvidenceRefs", refs);
            candidate.evidence().add(new Evidence(
                    first.type(), candidate.confidence(), first.sourceType(),
                    first.source(), first.detail(), summary));
            Map<Evidence, Integer> rawObservations = new LinkedHashMap<>();
            for (DerivedPathObservation variant : variants) {
                List<Endpoint> variantEndpoints = graphs.endpoints(variant);
                Evidence rawObservation = graphs.pathEvidence(
                        variant, "derived:data_lineage", false,
                        variantEndpoints, variantEndpoints);
                rawObservations.merge(rawObservation, 1, Integer::sum);
            }
            for (Map.Entry<Evidence, Integer> entry : rawObservations.entrySet()) {
                Evidence observation = entry.getKey();
                if (entry.getValue() == 1) {
                    candidate.rawEvidence().add(observation);
                    continue;
                }
                Map<String, Object> attributes = new LinkedHashMap<>(observation.attributes());
                attributes.put("occurrenceCount", entry.getValue());
                candidate.rawEvidence().add(new Evidence(
                        observation.type(), observation.score(), observation.sourceType(),
                        observation.source(), observation.detail(), attributes));
            }
            result.add(candidate);
            if (graphs.limitReached(result.size())) {
                break;
            }
        }
        return List.copyOf(result);
    }

    private DerivedEdge edge(Endpoint source, DataLineageCandidate lineage) {
        String ref = "lineage:" + source.normalizedKey() + "->" + lineage.target().normalizedKey()
                + ":" + lineage.flowKind().name() + ":" + lineage.transformType().name();
        return new DerivedEdge(
                source, lineage.target(), DerivedEdgeKind.LINEAGE,
                lineage.confidence(), ref, List.of());
    }

    private boolean isPureNoOpSelfLineage(Endpoint source, DataLineageCandidate lineage) {
        return source.normalizedKey().equals(lineage.target().normalizedKey())
                && lineage.transformType() == LineageTransformType.DIRECT
                && lineage.evidence().stream().allMatch(evidence ->
                evidence.transformType() == LineageTransformType.DIRECT);
    }
}
