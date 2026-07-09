package com.relationdetector.core.derived;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.relationdetector.contracts.Enums.DerivedPathKind;
import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.Enums.RelationType;
import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.model.DerivedPathCandidate;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.NamingEvidenceCandidate;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.scoring.DefaultEvidenceScores;
import com.relationdetector.core.scan.ScanConfig;

/**
 * Builds auditable transitive-path facts from already merged evidence.
 *
 * <p>CN: 本服务只消费已结构化、已定向的事实或命名证据，不解析 SQL 文本，也不改变直接
 * relationship / lineage。derived 输出始终带完整 path，供审计。</p>
 */
public final class DerivedPathInferenceService {
    private static final String TRANSITIVE_NAMING_RULE = "TRANSITIVE_NAMING_PATH";

    public List<NamingEvidenceCandidate> deriveNamingEvidence(
            List<NamingEvidenceCandidate> namingEvidence,
            ScanConfig config
    ) {
        if (!enabled(config) || !config.derivedNamingEvidenceEnabled) {
            return List.of();
        }
        List<Edge> edges = namingEvidence.stream()
                .filter(NamingEvidenceCandidate::directionHint)
                .filter(candidate -> !isDerived(candidate.evidence()))
                .map(this::namingEdge)
                .sorted(edgeComparator())
                .toList();
        return enumerate(edges, config, Set.of(), true).stream()
                .map(path -> toNamingEvidence(path, config))
                .toList();
    }

    public DerivedPathInferenceResult infer(
            List<RelationshipCandidate> relationships,
            List<DataLineageCandidate> dataLineages,
            List<NamingEvidenceCandidate> namingEvidence,
            ScanConfig config
    ) {
        if (!enabled(config)) {
            return DerivedPathInferenceResult.empty();
        }
        RelationshipInference relationshipInference = config.derivedRelationshipsEnabled
                ? inferRelationships(relationships, namingEvidence, config)
                : new RelationshipInference(List.of(), List.of());
        List<DerivedPathCandidate> derivedLineages = config.derivedDataLineageEnabled
                ? inferLineages(dataLineages, config)
                : List.of();
        return new DerivedPathInferenceResult(
                relationshipInference.derivedRelationships(),
                derivedLineages,
                relationshipInference.derivedNamingEvidence());
    }

    private RelationshipInference inferRelationships(
            List<RelationshipCandidate> relationships,
            List<NamingEvidenceCandidate> namingEvidence,
            ScanConfig config
    ) {
        List<Edge> relationshipEdges = new ArrayList<>();
        List<Edge> reverseStartEdges = new ArrayList<>();
        Set<String> directPairs = new HashSet<>();
        Map<String, List<Edge>> referencedBy = new LinkedHashMap<>();
        Map<String, List<Endpoint>> keyEndpointsByTable = new LinkedHashMap<>();
        for (RelationshipCandidate relationship : relationships) {
            if (relationship.relationType() != RelationType.FK_LIKE
                    || !relationship.source().isColumnLevel()
                    || !relationship.target().isColumnLevel()
                    || hasTransitiveEvidence(relationship.evidence())) {
                continue;
            }
            directPairs.addAll(pairKeys(relationship.source(), relationship.target()));
            Edge edge = relationshipEdge(relationship);
            relationshipEdges.add(edge);
            Edge reverseEdge = edge.reverse();
            reverseStartEdges.add(reverseEdge);
            addAdjacency(referencedBy, reverseEdge);
            addKeyEndpoint(keyEndpointsByTable, relationship.target());
        }
        if (config.derivedIncludeNamingEdgesInRelationshipPaths) {
            namingEvidence.stream()
                    .filter(NamingEvidenceCandidate::directionHint)
                    .filter(candidate -> candidate.source().isColumnLevel() && candidate.target().isColumnLevel())
                    .map(this::namingEdge)
                    .map(Edge::reverse)
                    .forEach(edge -> {
                        reverseStartEdges.add(edge);
                        addAdjacency(referencedBy, edge);
                    });
        }
        List<PathObservation> paths = enumerateReferencedBy(
                reverseStartEdges.stream().sorted(edgeComparator()).toList(),
                referencedBy,
                keyEndpointsByTable,
                config,
                directPairs);
        List<DerivedPathCandidate> derivedRelationships = new ArrayList<>();
        List<NamingEvidenceCandidate> derivedNaming = new ArrayList<>();
        for (PathObservation path : paths) {
            NamingEvidenceCandidate naming = toRelationshipPathNamingEvidence(path, config);
            derivedRelationships.add(toDerivedRelationshipPath(path, config, naming));
            if (naming != null) {
                derivedNaming.add(naming);
            }
        }
        return new RelationshipInference(derivedRelationships, derivedNaming);
    }

    private void addAdjacency(Map<String, List<Edge>> adjacency, Edge edge) {
        for (String key : graphKeys(edge.source())) {
            adjacency.computeIfAbsent(key, ignored -> new ArrayList<>()).add(edge);
        }
    }

    private void addKeyEndpoint(Map<String, List<Endpoint>> keyEndpointsByTable, Endpoint endpoint) {
        for (String tableKey : tableGraphKeys(endpoint)) {
            List<Endpoint> endpoints = keyEndpointsByTable.computeIfAbsent(tableKey, ignored -> new ArrayList<>());
            if (endpoints.stream().noneMatch(existing -> existing.normalizedKey().equals(endpoint.normalizedKey()))) {
                endpoints.add(endpoint);
            }
        }
    }

    private List<PathObservation> enumerateReferencedBy(
            List<Edge> startEdges,
            Map<String, List<Edge>> referencedBy,
            Map<String, List<Endpoint>> keyEndpointsByTable,
            ScanConfig config,
            Set<String> directPairs
    ) {
        List<PathObservation> observations = new ArrayList<>();
        Map<String, Integer> pathsPerPair = new LinkedHashMap<>();
        Set<String> seenPaths = new LinkedHashSet<>();
        for (Edge start : startEdges) {
            if (start.kind() == EdgeKind.TABLE_IDENTITY_BRIDGE) {
                continue;
            }
            LinkedHashSet<String> visited = new LinkedHashSet<>();
            visited.addAll(graphKeys(start.source()));
            visited.addAll(graphKeys(start.target()));
            dfsReferencedBy(start.source(), start.target(), List.of(start), visited,
                    referencedBy, keyEndpointsByTable, directPairs, pathsPerPair,
                    seenPaths, observations, config);
            if (limitReached(config, observations.size())) {
                break;
            }
        }
        return observations;
    }

    private void dfsReferencedBy(
            Endpoint origin,
            Endpoint current,
            List<Edge> path,
            LinkedHashSet<String> visited,
            Map<String, List<Edge>> referencedBy,
            Map<String, List<Endpoint>> keyEndpointsByTable,
            Set<String> directPairs,
            Map<String, Integer> pathsPerPair,
            Set<String> seenPaths,
            List<PathObservation> observations,
            ScanConfig config
    ) {
        if (path.size() >= 2 && lastEdge(path).kind() == EdgeKind.RELATIONSHIP) {
            boolean selfLoop = overlaps(graphKeys(origin), graphKeys(current));
            boolean direct = pairKeys(current, origin).stream().anyMatch(directPairs::contains);
            boolean namingOnly = path.stream().allMatch(edge -> edge.kind() == EdgeKind.NAMING);
            if (!selfLoop && !direct && !namingOnly) {
                String pair = pairKey(current, origin);
                String pathKey = relationshipOutputPath(path).stream()
                        .map(Endpoint::normalizedKey)
                        .reduce((left, right) -> left + "->" + right)
                        .orElse(pair);
                int count = pathsPerPair.getOrDefault(pair, 0);
                if (seenPaths.add(pathKey)
                        && (config.derivedMaxPathsPerPair == 0 || count < config.derivedMaxPathsPerPair)) {
                    observations.add(new PathObservation(origin, current, List.copyOf(path)));
                    pathsPerPair.put(pair, count + 1);
                }
            }
        }
        if (path.size() >= config.derivedMaxPathLength || limitReached(config, observations.size())) {
            return;
        }
        List<Edge> outgoing = referencedByOutgoing(current, referencedBy, keyEndpointsByTable);
        for (Edge edge : outgoing) {
            LinkedHashSet<String> nextKeys = graphKeys(edge.target());
            if (overlaps(visited, nextKeys)) {
                continue;
            }
            visited.addAll(nextKeys);
            List<Edge> nextPath = new ArrayList<>(path);
            nextPath.add(edge);
            dfsReferencedBy(origin, edge.target(), nextPath, visited,
                    referencedBy, keyEndpointsByTable, directPairs, pathsPerPair,
                    seenPaths, observations, config);
            visited.removeAll(nextKeys);
            if (limitReached(config, observations.size())) {
                break;
            }
        }
    }

    private List<Edge> referencedByOutgoing(
            Endpoint current,
            Map<String, List<Edge>> referencedBy,
            Map<String, List<Endpoint>> keyEndpointsByTable
    ) {
        List<Edge> outgoing = new ArrayList<>();
        graphKeys(current).stream()
                .flatMap(key -> referencedBy.getOrDefault(key, List.of()).stream())
                .forEach(outgoing::add);
        outgoing.addAll(lazyTableIdentityBridgeEdges(current, keyEndpointsByTable));
        return outgoing.stream().sorted(edgeComparator()).distinct().toList();
    }

    private List<Edge> lazyTableIdentityBridgeEdges(
            Endpoint current,
            Map<String, List<Endpoint>> keyEndpointsByTable
    ) {
        List<Edge> bridges = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String tableKey : tableGraphKeys(current)) {
            for (Endpoint keyEndpoint : keyEndpointsByTable.getOrDefault(tableKey, List.of())) {
                if (overlaps(graphKeys(current), graphKeys(keyEndpoint))) {
                    continue;
                }
                String ref = "table-identity:" + current.normalizedKey()
                        + "->" + keyEndpoint.normalizedKey();
                if (seen.add(ref)) {
                    bridges.add(new Edge(current, keyEndpoint,
                            EdgeKind.TABLE_IDENTITY_BRIDGE,
                            BigDecimal.valueOf(DefaultEvidenceScores.SQL_LOG_JOIN),
                            ref,
                            List.of()));
                }
            }
        }
        return bridges;
    }

    private List<DerivedPathCandidate> inferLineages(List<DataLineageCandidate> dataLineages, ScanConfig config) {
        List<Edge> edges = new ArrayList<>();
        Set<String> directPairs = new HashSet<>();
        for (DataLineageCandidate lineage : dataLineages) {
            if (lineage.flowKind() != LineageFlowKind.VALUE) {
                continue;
            }
            for (Endpoint source : lineage.sources()) {
                if (!source.isColumnLevel() || !lineage.target().isColumnLevel()) {
                    continue;
                }
                if (isPureNoOpSelfLineage(source, lineage)) {
                    continue;
                }
                directPairs.addAll(pairKeys(source, lineage.target()));
                edges.add(lineageEdge(source, lineage));
            }
        }
        return enumerate(edges.stream().sorted(edgeComparator()).toList(), config, directPairs, false).stream()
                .map(path -> toDerivedPath(DerivedPathKind.DATA_LINEAGE, path, config))
                .toList();
    }

    private List<PathObservation> enumerate(
            List<Edge> edges,
            ScanConfig config,
            Set<String> directPairs,
            boolean allowNamingOnlyGraph
    ) {
        Map<String, List<Edge>> adjacency = new LinkedHashMap<>();
        for (Edge edge : edges) {
            for (String key : graphKeys(edge.source())) {
                adjacency.computeIfAbsent(key, ignored -> new ArrayList<>()).add(edge);
            }
        }
        List<PathObservation> observations = new ArrayList<>();
        Map<String, Integer> pathsPerPair = new LinkedHashMap<>();
        for (Edge start : edges) {
            if (start.kind() == EdgeKind.TABLE_IDENTITY_BRIDGE) {
                continue;
            }
            LinkedHashSet<String> visited = new LinkedHashSet<>();
            visited.addAll(graphKeys(start.source()));
            dfs(start.source(), start.target(), List.of(start), visited, adjacency,
                    directPairs, pathsPerPair, observations, config, allowNamingOnlyGraph);
            if (limitReached(config, observations.size())) {
                break;
            }
        }
        return observations;
    }

    private void dfs(
            Endpoint origin,
            Endpoint current,
            List<Edge> path,
            LinkedHashSet<String> visited,
            Map<String, List<Edge>> adjacency,
            Set<String> directPairs,
            Map<String, Integer> pathsPerPair,
            List<PathObservation> observations,
            ScanConfig config,
            boolean allowNamingOnlyGraph
    ) {
        if (path.size() >= 2) {
            boolean selfLoop = overlaps(graphKeys(origin), graphKeys(current));
            boolean direct = pairKeys(origin, current).stream().anyMatch(directPairs::contains);
            boolean namingOnly = path.stream().allMatch(edge -> edge.kind() == EdgeKind.NAMING);
            if (!selfLoop && !direct && (allowNamingOnlyGraph || !namingOnly)) {
                String pair = pairKey(origin, current);
                int count = pathsPerPair.getOrDefault(pair, 0);
                if (config.derivedMaxPathsPerPair == 0 || count < config.derivedMaxPathsPerPair) {
                    observations.add(new PathObservation(origin, current, List.copyOf(path)));
                    pathsPerPair.put(pair, count + 1);
                }
            }
        }
        if (path.size() >= config.derivedMaxPathLength || limitReached(config, observations.size())) {
            return;
        }
        LinkedHashSet<String> branchVisited = new LinkedHashSet<>(visited);
        branchVisited.addAll(graphKeys(current));
        List<Edge> outgoing = graphKeys(current).stream()
                .flatMap(key -> adjacency.getOrDefault(key, List.of()).stream())
                .distinct()
                .toList();
        for (Edge edge : outgoing) {
            LinkedHashSet<String> nextKeys = graphKeys(edge.target());
            if (overlaps(branchVisited, nextKeys)) {
                continue;
            }
            LinkedHashSet<String> nextVisited = new LinkedHashSet<>(branchVisited);
            nextVisited.addAll(nextKeys);
            List<Edge> nextPath = new ArrayList<>(path);
            nextPath.add(edge);
            dfs(origin, edge.target(), nextPath, nextVisited, adjacency,
                    directPairs, pathsPerPair, observations, config, allowNamingOnlyGraph);
            if (limitReached(config, observations.size())) {
                break;
            }
        }
    }

    private boolean limitReached(ScanConfig config, int count) {
        return config.derivedMaxFacts > 0 && count >= config.derivedMaxFacts;
    }

    private Edge lastEdge(PathObservation path) {
        return path.edges().get(path.edges().size() - 1);
    }

    private Edge lastEdge(List<Edge> path) {
        return path.get(path.size() - 1);
    }

    private NamingEvidenceCandidate toNamingEvidence(PathObservation path, ScanConfig config) {
        Evidence evidence = pathEvidence(path, config, "derived:naming", true,
                endpoints(path), endpoints(path));
        return new NamingEvidenceCandidate(
                path.source(),
                path.target(),
                evidence,
                TRANSITIVE_NAMING_RULE,
                true,
                List.of(evidence));
    }

    private NamingEvidenceCandidate toRelationshipPathNamingEvidence(PathObservation path, ScanConfig config) {
        if (!config.derivedNamingEvidenceEnabled || relationshipNamingRefs(path).isEmpty()) {
            return null;
        }
        List<Endpoint> outputPath = relationshipOutputPath(path.edges());
        List<Endpoint> traversalPath = endpoints(path);
        Endpoint source = outputPath.get(0);
        Endpoint target = outputPath.get(outputPath.size() - 1);
        Evidence evidence = pathEvidence(path, config, "derived:naming", true, outputPath, traversalPath);
        return new NamingEvidenceCandidate(source, target, evidence, TRANSITIVE_NAMING_RULE, true, List.of(evidence));
    }

    private DerivedPathCandidate toDerivedRelationshipPath(
            PathObservation observation,
            ScanConfig config,
            NamingEvidenceCandidate namingEvidence
    ) {
        List<Endpoint> outputPath = relationshipOutputPath(observation.edges());
        List<Endpoint> traversalPath = endpoints(observation);
        DerivedPathCandidate candidate = new DerivedPathCandidate(
                DerivedPathKind.RELATIONSHIP,
                outputPath.get(0),
                outputPath.get(outputPath.size() - 1),
                outputPath);
        BigDecimal confidence = confidence(observation, config);
        candidate.confidence(confidence);
        candidate.attributes().put("pathLength", observation.edges().size());
        candidate.attributes().put("traversalMode", "REVERSE_REFERENCED_BY");
        candidate.attributes().put("outputDirection", "FK_LIKE_FORWARD");
        candidate.attributes().put("containsNamingEdge",
                observation.edges().stream().anyMatch(edge -> edge.kind() == EdgeKind.NAMING));
        candidate.attributes().put("containsTableIdentityBridge",
                observation.edges().stream().anyMatch(edge -> edge.kind() == EdgeKind.TABLE_IDENTITY_BRIDGE));
        candidate.attributes().put("path", endpointNames(outputPath));
        candidate.attributes().put("traversalPath", endpointNames(traversalPath));
        Evidence evidence = pathEvidence(observation, config, "derived:relationship", false, outputPath, traversalPath);
        candidate.evidence().add(evidence);
        candidate.rawEvidence().add(evidence);
        if (namingEvidence != null) {
            Evidence naming = new Evidence(
                    EvidenceType.NAMING_MATCH,
                    namingEvidence.evidence().score(),
                    EvidenceSourceType.INFERENCE,
                    "derived:naming",
                    namingEvidence.source().displayName() + " -> " + namingEvidence.target().displayName(),
                    Map.of(
                            "evidenceRef", namingEvidence.id(),
                            "namingRule", TRANSITIVE_NAMING_RULE,
                            "directionHint", true,
                            "suggestedSourceEndpoint", namingEvidence.source().normalizedKey(),
                            "suggestedTargetEndpoint", namingEvidence.target().normalizedKey()));
            candidate.evidence().add(naming);
        }
        return candidate;
    }

    private DerivedPathCandidate toDerivedPath(
            DerivedPathKind kind,
            PathObservation observation,
            ScanConfig config
    ) {
        DerivedPathCandidate candidate = new DerivedPathCandidate(
                kind,
                observation.source(),
                observation.target(),
                endpoints(observation));
        BigDecimal confidence = confidence(observation, config);
        candidate.confidence(confidence);
        candidate.attributes().put("pathLength", observation.edges().size());
        candidate.attributes().put("containsNamingEdge",
                observation.edges().stream().anyMatch(edge -> edge.kind() == EdgeKind.NAMING));
        candidate.attributes().put("containsTableIdentityBridge",
                observation.edges().stream().anyMatch(edge -> edge.kind() == EdgeKind.TABLE_IDENTITY_BRIDGE));
        candidate.attributes().put("path", endpointNames(endpoints(observation)));
        Evidence evidence = pathEvidence(observation, config, "derived:" + kind.name().toLowerCase(), false,
                endpoints(observation), endpoints(observation));
        candidate.evidence().add(evidence);
        candidate.rawEvidence().add(evidence);
        return candidate;
    }

    private Evidence pathEvidence(
            PathObservation observation,
            ScanConfig config,
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
        attributes.put("pathEvidenceRefs", observation.edges().stream().map(Edge::ref).toList());
        attributes.put("confidenceDecay", config.derivedConfidenceDecay);
        if (naming) {
            attributes.put("namingRule", TRANSITIVE_NAMING_RULE);
            attributes.put("suggestedSourceEndpoint", outputPath.get(0).normalizedKey());
            attributes.put("suggestedTargetEndpoint", outputPath.get(outputPath.size() - 1).normalizedKey());
            attributes.put("directionHint", true);
        }
        return new Evidence(
                naming ? EvidenceType.NAMING_MATCH : EvidenceType.TRANSITIVE_PATH,
                confidence(observation, config),
                EvidenceSourceType.INFERENCE,
                source,
                String.join(" -> ", endpointNames(outputPath)),
                attributes);
    }

    private BigDecimal confidence(PathObservation observation, ScanConfig config) {
        BigDecimal min = observation.edges().stream()
                .map(Edge::confidence)
                .min(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);
        double decayed = min.doubleValue() * Math.pow(config.derivedConfidenceDecay, observation.edges().size() - 1);
        if (decayed < config.derivedMinConfidence) {
            decayed = config.derivedMinConfidence;
        }
        return BigDecimal.valueOf(decayed).setScale(4, RoundingMode.HALF_UP);
    }

    private List<Endpoint> endpoints(PathObservation observation) {
        List<Endpoint> endpoints = new ArrayList<>();
        endpoints.add(observation.source());
        observation.edges().forEach(edge -> endpoints.add(edge.target()));
        return endpoints;
    }

    private List<Endpoint> relationshipOutputPath(List<Edge> traversalEdges) {
        List<Endpoint> outputPath = new ArrayList<>();
        outputPath.add(traversalEdges.get(traversalEdges.size() - 1).target());
        for (int i = traversalEdges.size() - 1; i >= 0; i--) {
            outputPath.add(traversalEdges.get(i).source());
        }
        return outputPath;
    }

    private List<String> endpointNames(List<Endpoint> endpoints) {
        return endpoints.stream().map(Endpoint::displayName).toList();
    }

    private Edge relationshipEdge(RelationshipCandidate relationship) {
        String ref = "relationship:" + relationship.source().normalizedKey() + "->" + relationship.target().normalizedKey();
        return new Edge(relationship.source(), relationship.target(), EdgeKind.RELATIONSHIP,
                relationship.confidence(), ref, relationshipNamingRefs(relationship));
    }

    private Edge lineageEdge(Endpoint source, DataLineageCandidate lineage) {
        String ref = "lineage:" + source.normalizedKey() + "->" + lineage.target().normalizedKey();
        return new Edge(source, lineage.target(), EdgeKind.LINEAGE, lineage.confidence(), ref, List.of());
    }

    private boolean isPureNoOpSelfLineage(Endpoint source, DataLineageCandidate lineage) {
        return source.normalizedKey().equals(lineage.target().normalizedKey())
                && lineage.transformType() == LineageTransformType.DIRECT
                && lineage.evidence().stream()
                .allMatch(evidence -> evidence.transformType()
                        == LineageTransformType.DIRECT);
    }

    private Edge namingEdge(NamingEvidenceCandidate naming) {
        return new Edge(naming.source(), naming.target(), EdgeKind.NAMING,
                naming.evidence().score(), naming.id(), List.of(naming.id()));
    }

    private List<String> relationshipNamingRefs(RelationshipCandidate relationship) {
        return relationship.evidence().stream()
                .filter(evidence -> evidence.type() == EvidenceType.NAMING_MATCH)
                .map(evidence -> evidence.attributes().get("evidenceRef"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .sorted()
                .toList();
    }

    private List<String> relationshipNamingRefs(PathObservation path) {
        return path.edges().stream()
                .flatMap(edge -> edge.namingRefs().stream())
                .distinct()
                .sorted()
                .toList();
    }

    private Comparator<Edge> edgeComparator() {
        return Comparator
                .comparing((Edge edge) -> edge.source().normalizedKey())
                .thenComparing(edge -> edge.target().normalizedKey())
                .thenComparing(edge -> edge.kind().name())
                .thenComparing(Edge::ref);
    }

    private boolean enabled(ScanConfig config) {
        return config != null && config.derivedPathsEnabled;
    }

    private boolean isDerived(Evidence evidence) {
        return Boolean.TRUE.equals(evidence.attributes().get("derived"));
    }

    private boolean hasTransitiveEvidence(List<Evidence> evidence) {
        return evidence.stream().anyMatch(item -> item.type() == EvidenceType.TRANSITIVE_PATH);
    }

    private String pairKey(Endpoint source, Endpoint target) {
        return source.normalizedKey() + "->" + target.normalizedKey();
    }

    private Set<String> pairKeys(Endpoint source, Endpoint target) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (String sourceKey : graphKeys(source)) {
            for (String targetKey : graphKeys(target)) {
                keys.add(sourceKey + "->" + targetKey);
            }
        }
        return keys;
    }

    private LinkedHashSet<String> graphKeys(Endpoint endpoint) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        keys.add(endpoint.normalizedKey());
        return keys;
    }

    private LinkedHashSet<String> tableGraphKeys(Endpoint endpoint) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        String schema = endpoint.table().schema();
        String table = endpoint.table().tableName();
        if (table == null || table.isBlank()) {
            return keys;
        }
        if (schema != null && !schema.isBlank()) {
            keys.add(schema + "." + table);
            return keys;
        }
        keys.add(table);
        return keys;
    }

    private boolean overlaps(Set<String> left, Set<String> right) {
        for (String value : left) {
            if (right.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private record Edge(
            Endpoint source,
            Endpoint target,
            EdgeKind kind,
            BigDecimal confidence,
            String ref,
            List<String> namingRefs
    ) {
        Edge reverse() {
            return new Edge(target, source, kind, confidence, ref, namingRefs);
        }
    }

    private record PathObservation(
            Endpoint source,
            Endpoint target,
            List<Edge> edges
    ) {
    }

    private record RelationshipInference(
            List<DerivedPathCandidate> derivedRelationships,
            List<NamingEvidenceCandidate> derivedNamingEvidence
    ) {
    }

    private enum EdgeKind {
        RELATIONSHIP,
        LINEAGE,
        NAMING,
        TABLE_IDENTITY_BRIDGE
    }
}
