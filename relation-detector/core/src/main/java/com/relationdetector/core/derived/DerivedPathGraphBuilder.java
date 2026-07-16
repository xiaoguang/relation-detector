package com.relationdetector.core.derived;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.relationdetector.contracts.Enums.DerivedPathKind;
import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.scoring.DefaultEvidenceScores;
import com.relationdetector.core.identity.CanonicalEndpointKeyProvider;
import com.relationdetector.core.scan.ScanConfig;

/**
 *
 * Builds immutable adjacency once and enumerates bounded, cycle-free paths.
 */
final class DerivedPathGraphBuilder {
    private final ScanConfig config;
    private final CanonicalEndpointKeyProvider endpointKeys;

    DerivedPathGraphBuilder(ScanConfig config, CanonicalEndpointKeyProvider endpointKeys) {
        this.config = config;
        this.endpointKeys = endpointKeys;
    }

    DerivedPathGraph build(List<DerivedEdge> sourceEdges) {
        List<DerivedEdge> edges = sourceEdges.stream().sorted(edgeComparator()).toList();
        Map<String, List<DerivedEdge>> mutable = new LinkedHashMap<>();
        for (DerivedEdge edge : edges) {
            for (String key : graphKeys(edge.source())) {
                mutable.computeIfAbsent(key, ignored -> new ArrayList<>()).add(edge);
            }
        }
        Map<String, List<DerivedEdge>> adjacency = new LinkedHashMap<>();
        mutable.forEach((key, values) -> adjacency.put(
                key, values.stream().distinct().sorted(edgeComparator()).toList()));
        return new DerivedPathGraph(edges, adjacency);
    }

    List<DerivedPathObservation> enumerate(
            DerivedPathGraph graph,
            Set<String> directPairs,
            boolean allowNamingOnlyGraph
    ) {
        List<DerivedPathObservation> observations = new ArrayList<>();
        Map<String, Integer> pathsPerPair = new LinkedHashMap<>();
        for (DerivedEdge start : graph.edges()) {
            if (start.kind() == DerivedEdgeKind.TABLE_IDENTITY_BRIDGE) {
                continue;
            }
            LinkedHashSet<String> visited = new LinkedHashSet<>(graphKeys(start.source()));
            dfs(start.source(), start.target(), List.of(start), visited, graph,
                    directPairs, pathsPerPair, observations, allowNamingOnlyGraph);
            if (limitReached(observations.size())) {
                break;
            }
        }
        return List.copyOf(observations);
    }

    List<DerivedPathObservation> enumerateReferencedBy(
            DerivedPathGraph graph,
            Map<String, List<Endpoint>> keyEndpointsByTable,
            Set<String> directPairs
    ) {
        List<DerivedPathObservation> observations = new ArrayList<>();
        Map<String, Integer> pathsPerPair = new LinkedHashMap<>();
        Map<String, List<DerivedEdge>> bridgeCache = new LinkedHashMap<>();
        Set<String> acceptedCanonicalPaths = new LinkedHashSet<>();
        for (DerivedEdge start : graph.edges()) {
            if (start.kind() == DerivedEdgeKind.TABLE_IDENTITY_BRIDGE) {
                continue;
            }
            LinkedHashSet<String> visited = new LinkedHashSet<>();
            visited.addAll(graphKeys(start.source()));
            visited.addAll(graphKeys(start.target()));
            dfsReferencedBy(start.source(), start.target(), List.of(start), visited,
                    graph, keyEndpointsByTable, bridgeCache, directPairs,
                    pathsPerPair, acceptedCanonicalPaths, observations);
        }
        return List.copyOf(observations);
    }

    private void dfs(
            Endpoint origin,
            Endpoint current,
            List<DerivedEdge> path,
            LinkedHashSet<String> visited,
            DerivedPathGraph graph,
            Set<String> directPairs,
            Map<String, Integer> pathsPerPair,
            List<DerivedPathObservation> observations,
            boolean allowNamingOnlyGraph
    ) {
        if (path.size() >= 2) {
            boolean selfLoop = overlaps(graphKeys(origin), graphKeys(current));
            boolean direct = pairKeys(origin, current).stream().anyMatch(directPairs::contains);
            boolean namingOnly = path.stream().allMatch(edge -> edge.kind() == DerivedEdgeKind.NAMING);
            if (!selfLoop && !direct && (allowNamingOnlyGraph || !namingOnly)) {
                addPath(origin, current, path, pathsPerPair, observations);
            }
        }
        if (path.size() >= config.derivedMaxPathLength || limitReached(observations.size())) {
            return;
        }
        LinkedHashSet<String> branchVisited = new LinkedHashSet<>(visited);
        branchVisited.addAll(graphKeys(current));
        for (DerivedEdge edge : outgoing(graph, current)) {
            LinkedHashSet<String> nextKeys = graphKeys(edge.target());
            if (overlaps(branchVisited, nextKeys)) {
                continue;
            }
            LinkedHashSet<String> nextVisited = new LinkedHashSet<>(branchVisited);
            nextVisited.addAll(nextKeys);
            List<DerivedEdge> nextPath = append(path, edge);
            dfs(origin, edge.target(), nextPath, nextVisited, graph,
                    directPairs, pathsPerPair, observations, allowNamingOnlyGraph);
            if (limitReached(observations.size())) {
                break;
            }
        }
    }

    private void dfsReferencedBy(
            Endpoint origin,
            Endpoint current,
            List<DerivedEdge> path,
            LinkedHashSet<String> visited,
            DerivedPathGraph graph,
            Map<String, List<Endpoint>> keyEndpointsByTable,
            Map<String, List<DerivedEdge>> bridgeCache,
            Set<String> directPairs,
            Map<String, Integer> pathsPerPair,
            Set<String> acceptedCanonicalPaths,
            List<DerivedPathObservation> observations
    ) {
        if (path.size() >= 2 && lastEdge(path).kind() == DerivedEdgeKind.RELATIONSHIP) {
            boolean selfLoop = overlaps(graphKeys(origin), graphKeys(current));
            boolean direct = pairKeys(current, origin).stream().anyMatch(directPairs::contains);
            boolean namingOnly = path.stream().allMatch(edge -> edge.kind() == DerivedEdgeKind.NAMING);
            if (!selfLoop && !direct && !namingOnly) {
                addReferencedByPath(origin, current, path, pathsPerPair,
                        acceptedCanonicalPaths, observations);
            }
        }
        if (path.size() >= config.derivedMaxPathLength) {
            return;
        }
        for (DerivedEdge edge : referencedByOutgoing(
                graph, current, keyEndpointsByTable, bridgeCache)) {
            LinkedHashSet<String> nextKeys = graphKeys(edge.target());
            if (overlaps(visited, nextKeys)) {
                continue;
            }
            visited.addAll(nextKeys);
            dfsReferencedBy(origin, edge.target(), append(path, edge), visited,
                    graph, keyEndpointsByTable, bridgeCache, directPairs,
                    pathsPerPair, acceptedCanonicalPaths, observations);
            visited.removeAll(nextKeys);
        }
    }

    private void addPath(
            Endpoint source,
            Endpoint target,
            List<DerivedEdge> path,
            Map<String, Integer> pathsPerPair,
            List<DerivedPathObservation> observations
    ) {
        String pair = pairKey(source, target);
        int count = pathsPerPair.getOrDefault(pair, 0);
        if (config.derivedMaxPathsPerPair == 0 || count < config.derivedMaxPathsPerPair) {
            observations.add(new DerivedPathObservation(source, target, path));
            pathsPerPair.put(pair, count + 1);
        }
    }

    private void addReferencedByPath(
            Endpoint source,
            Endpoint target,
            List<DerivedEdge> path,
            Map<String, Integer> pathsPerPair,
            Set<String> acceptedCanonicalPaths,
            List<DerivedPathObservation> observations
    ) {
        DerivedPathObservation observation = new DerivedPathObservation(source, target, path);
        String pathKey = canonicalPathKey(DerivedPathKind.RELATIONSHIP.name(), observation);
        if (acceptedCanonicalPaths.contains(pathKey)) {
            observations.add(observation);
            return;
        }
        if (limitReached(acceptedCanonicalPaths.size())) {
            return;
        }
        String pair = pairKey(source, target);
        int count = pathsPerPair.getOrDefault(pair, 0);
        if (config.derivedMaxPathsPerPair == 0 || count < config.derivedMaxPathsPerPair) {
            acceptedCanonicalPaths.add(pathKey);
            observations.add(observation);
            pathsPerPair.put(pair, count + 1);
        }
    }

    private List<DerivedEdge> outgoing(DerivedPathGraph graph, Endpoint current) {
        return graphKeys(current).stream()
                .flatMap(key -> graph.adjacency().getOrDefault(key, List.of()).stream())
                .distinct()
                .toList();
    }

    private List<DerivedEdge> referencedByOutgoing(
            DerivedPathGraph graph,
            Endpoint current,
            Map<String, List<Endpoint>> keyEndpointsByTable,
            Map<String, List<DerivedEdge>> bridgeCache
    ) {
        List<DerivedEdge> outgoing = new ArrayList<>(outgoing(graph, current));
        outgoing.addAll(bridgeCache.computeIfAbsent(
                endpointKeys.factKey(current),
                ignored -> lazyIdentityBridges(current, keyEndpointsByTable)));
        return outgoing.stream().distinct().sorted(edgeComparator()).toList();
    }

    private List<DerivedEdge> lazyIdentityBridges(
            Endpoint current, Map<String, List<Endpoint>> keyEndpointsByTable
    ) {
        List<DerivedEdge> bridges = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String tableKey : tableGraphKeys(current)) {
            for (Endpoint keyEndpoint : keyEndpointsByTable.getOrDefault(tableKey, List.of())) {
                if (overlaps(graphKeys(current), graphKeys(keyEndpoint))) {
                    continue;
                }
                String bridgeKey = endpointKeys.factKey(current) + "->" + endpointKeys.factKey(keyEndpoint);
                String ref = "table-identity:" + current.normalizedKey()
                        + "->" + keyEndpoint.normalizedKey();
                if (seen.add(bridgeKey)) {
                    bridges.add(new DerivedEdge(
                            current,
                            keyEndpoint,
                            DerivedEdgeKind.TABLE_IDENTITY_BRIDGE,
                            BigDecimal.valueOf(DefaultEvidenceScores.SQL_LOG_JOIN),
                            ref,
                            List.of()));
                }
            }
        }
        return bridges.stream().sorted(edgeComparator()).toList();
    }

    Evidence pathEvidence(
            DerivedPathObservation observation,
            String source,
            boolean naming,
            List<Endpoint> outputPath,
            List<Endpoint> traversalPath
    ) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("derived", true);
        attributes.put("path", endpointNames(outputPath));
        attributes.put("traversalPath", endpointNames(traversalPath));
        attributes.put("pathLength", observation.edges().size());
        attributes.put("pathEvidenceRefs", observation.edges().stream().map(DerivedEdge::ref).toList());
        attributes.put("confidenceDecay", config.derivedConfidenceDecay);
        if (naming) {
            attributes.put("namingRule", DerivedNamingInference.TRANSITIVE_NAMING_RULE);
            attributes.put("suggestedSourceEndpoint", outputPath.get(0).normalizedKey());
            attributes.put("suggestedTargetEndpoint", outputPath.get(outputPath.size() - 1).normalizedKey());
            attributes.put("suggestedSourceEndpointKey", outputPath.get(0).normalizedKey());
            attributes.put("suggestedTargetEndpointKey", outputPath.get(outputPath.size() - 1).normalizedKey());
            attributes.put("directionHint", true);
        }
        return new Evidence(
                naming ? EvidenceType.NAMING_MATCH : EvidenceType.TRANSITIVE_PATH,
                confidence(observation),
                EvidenceSourceType.INFERENCE,
                source,
                String.join(" -> ", endpointNames(outputPath)),
                attributes);
    }

    BigDecimal confidence(DerivedPathObservation observation) {
        BigDecimal minimum = observation.edges().stream()
                .map(DerivedEdge::confidence)
                .min(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);
        double value = minimum.doubleValue()
                * Math.pow(config.derivedConfidenceDecay, observation.edges().size() - 1);
        return BigDecimal.valueOf(Math.max(value, config.derivedMinConfidence))
                .setScale(4, RoundingMode.HALF_UP);
    }

    List<Endpoint> endpoints(DerivedPathObservation observation) {
        List<Endpoint> endpoints = new ArrayList<>();
        endpoints.add(observation.source());
        observation.edges().forEach(edge -> endpoints.add(edge.target()));
        return List.copyOf(endpoints);
    }

    List<Endpoint> relationshipOutputPath(List<DerivedEdge> traversalEdges) {
        List<Endpoint> output = new ArrayList<>();
        output.add(traversalEdges.get(traversalEdges.size() - 1).target());
        for (int index = traversalEdges.size() - 1; index >= 0; index--) {
            output.add(traversalEdges.get(index).source());
        }
        return List.copyOf(output);
    }

    List<String> endpointNames(List<Endpoint> endpoints) {
        return endpoints.stream().map(Endpoint::displayName).toList();
    }

    String canonicalPathKey(String kind, DerivedPathObservation observation) {
        return kind + ":" + endpointKeys.factKey(observation.source()) + "->"
                + endpointKeys.factKey(observation.target()) + ":"
                + endpoints(observation).stream().map(endpointKeys::factKey)
                .reduce((left, right) -> left + "->" + right).orElse("");
    }

    Comparator<DerivedEdge> edgeComparator() {
        return Comparator
                .comparing((DerivedEdge edge) -> endpointKeys.factKey(edge.source()))
                .thenComparing(edge -> endpointKeys.factKey(edge.target()))
                .thenComparing(edge -> edge.source().normalizedKey())
                .thenComparing(edge -> edge.target().normalizedKey())
                .thenComparing(edge -> edge.kind().name())
                .thenComparing(DerivedEdge::ref);
    }

    String pairKey(Endpoint source, Endpoint target) {
        return endpointKeys.factKey(source) + "->" + endpointKeys.factKey(target);
    }

    Set<String> pairKeys(Endpoint source, Endpoint target) {
        Set<String> keys = new LinkedHashSet<>();
        for (String sourceKey : graphKeys(source)) {
            for (String targetKey : graphKeys(target)) {
                keys.add(sourceKey + "->" + targetKey);
            }
        }
        return keys;
    }

    LinkedHashSet<String> graphKeys(Endpoint endpoint) {
        return new LinkedHashSet<>(List.of(endpointKeys.factKey(endpoint)));
    }

    LinkedHashSet<String> tableGraphKeys(Endpoint endpoint) {
        return new LinkedHashSet<>(List.of(endpointKeys.factKey(Endpoint.table(endpoint.table()))));
    }

    boolean sameEndpoint(Endpoint left, Endpoint right) {
        return endpointKeys.same(left, right);
    }

    boolean limitReached(int count) {
        return config.derivedMaxFacts > 0 && count >= config.derivedMaxFacts;
    }

    private boolean overlaps(Set<String> left, Set<String> right) {
        return left.stream().anyMatch(right::contains);
    }

    private DerivedEdge lastEdge(List<DerivedEdge> path) {
        return path.get(path.size() - 1);
    }

    private List<DerivedEdge> append(List<DerivedEdge> path, DerivedEdge edge) {
        List<DerivedEdge> result = new ArrayList<>(path);
        result.add(edge);
        return List.copyOf(result);
    }
}
