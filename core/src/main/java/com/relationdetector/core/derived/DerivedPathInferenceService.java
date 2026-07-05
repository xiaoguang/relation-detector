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
        List<DerivedPathCandidate> derivedRelationships = config.derivedRelationshipsEnabled
                ? inferRelationships(relationships, namingEvidence, config)
                : List.of();
        List<DerivedPathCandidate> derivedLineages = config.derivedDataLineageEnabled
                ? inferLineages(dataLineages, config)
                : List.of();
        return new DerivedPathInferenceResult(derivedRelationships, derivedLineages);
    }

    private List<DerivedPathCandidate> inferRelationships(
            List<RelationshipCandidate> relationships,
            List<NamingEvidenceCandidate> namingEvidence,
            ScanConfig config
    ) {
        List<Edge> edges = new ArrayList<>();
        List<Edge> relationshipEdges = new ArrayList<>();
        Set<String> directPairs = new HashSet<>();
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
            edges.add(edge);
        }
        edges.addAll(tableIdentityBridgeEdges(relationshipEdges));
        if (config.derivedIncludeNamingEdgesInRelationshipPaths) {
            namingEvidence.stream()
                    .filter(NamingEvidenceCandidate::directionHint)
                    .filter(candidate -> candidate.source().isColumnLevel() && candidate.target().isColumnLevel())
                    .map(this::namingEdge)
                    .forEach(edges::add);
        }
        return enumerate(edges.stream().sorted(edgeComparator()).toList(), config, directPairs, true).stream()
                .filter(path -> path.edges().stream().anyMatch(edge -> edge.kind() == EdgeKind.RELATIONSHIP))
                .filter(path -> lastEdge(path).kind() == EdgeKind.RELATIONSHIP)
                .map(path -> toDerivedPath(DerivedPathKind.RELATIONSHIP, path, config))
                .toList();
    }

    private List<Edge> tableIdentityBridgeEdges(List<Edge> relationshipEdges) {
        Map<String, List<Endpoint>> outgoingForeignKeysByTable = new LinkedHashMap<>();
        for (Edge edge : relationshipEdges) {
            for (String tableKey : tableGraphKeys(edge.source())) {
                outgoingForeignKeysByTable
                        .computeIfAbsent(tableKey, ignored -> new ArrayList<>())
                        .add(edge.source());
            }
        }
        List<Edge> bridges = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Edge edge : relationshipEdges) {
            for (String tableKey : tableGraphKeys(edge.target())) {
                for (Endpoint outgoingForeignKey : outgoingForeignKeysByTable.getOrDefault(tableKey, List.of())) {
                    if (overlaps(graphKeys(edge.target()), graphKeys(outgoingForeignKey))) {
                        continue;
                    }
                    String ref = "table-identity:" + edge.target().normalizedKey()
                            + "->" + outgoingForeignKey.normalizedKey();
                    if (seen.add(ref)) {
                        bridges.add(new Edge(edge.target(), outgoingForeignKey,
                                EdgeKind.TABLE_IDENTITY_BRIDGE,
                                edge.confidence(), ref));
                    }
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
        List<Edge> outgoing = graphKeys(current).stream()
                .flatMap(key -> adjacency.getOrDefault(key, List.of()).stream())
                .distinct()
                .toList();
        for (Edge edge : outgoing) {
            LinkedHashSet<String> nextKeys = graphKeys(edge.target());
            if (overlaps(visited, nextKeys)) {
                continue;
            }
            visited.addAll(nextKeys);
            List<Edge> nextPath = new ArrayList<>(path);
            nextPath.add(edge);
            dfs(origin, edge.target(), nextPath, visited, adjacency,
                    directPairs, pathsPerPair, observations, config, allowNamingOnlyGraph);
            visited.removeAll(nextKeys);
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

    private NamingEvidenceCandidate toNamingEvidence(PathObservation path, ScanConfig config) {
        Evidence evidence = pathEvidence(path, config, "derived:naming", true);
        return new NamingEvidenceCandidate(
                path.source(),
                path.target(),
                evidence,
                TRANSITIVE_NAMING_RULE,
                true,
                List.of(evidence));
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
        Evidence evidence = pathEvidence(observation, config, "derived:" + kind.name().toLowerCase(), false);
        candidate.evidence().add(evidence);
        candidate.rawEvidence().add(evidence);
        return candidate;
    }

    private Evidence pathEvidence(PathObservation observation, ScanConfig config, String source, boolean naming) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("derived", true);
        attributes.put("path", endpointNames(endpoints(observation)));
        attributes.put("pathLength", observation.edges().size());
        attributes.put("pathEvidenceRefs", observation.edges().stream().map(Edge::ref).toList());
        attributes.put("confidenceDecay", config.derivedConfidenceDecay);
        if (naming) {
            attributes.put("namingRule", TRANSITIVE_NAMING_RULE);
            attributes.put("suggestedSourceEndpoint", observation.source().normalizedKey());
            attributes.put("suggestedTargetEndpoint", observation.target().normalizedKey());
            attributes.put("directionHint", true);
        }
        return new Evidence(
                naming ? EvidenceType.NAMING_MATCH : EvidenceType.TRANSITIVE_PATH,
                confidence(observation, config),
                EvidenceSourceType.INFERENCE,
                source,
                String.join(" -> ", endpointNames(endpoints(observation))),
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

    private List<String> endpointNames(List<Endpoint> endpoints) {
        return endpoints.stream().map(Endpoint::displayName).toList();
    }

    private Edge relationshipEdge(RelationshipCandidate relationship) {
        String ref = "relationship:" + relationship.source().normalizedKey() + "->" + relationship.target().normalizedKey();
        return new Edge(relationship.source(), relationship.target(), EdgeKind.RELATIONSHIP,
                relationship.confidence(), ref);
    }

    private Edge lineageEdge(Endpoint source, DataLineageCandidate lineage) {
        String ref = "lineage:" + source.normalizedKey() + "->" + lineage.target().normalizedKey();
        return new Edge(source, lineage.target(), EdgeKind.LINEAGE, lineage.confidence(), ref);
    }

    private Edge namingEdge(NamingEvidenceCandidate naming) {
        return new Edge(naming.source(), naming.target(), EdgeKind.NAMING,
                naming.evidence().score(), naming.id());
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
            String ref
    ) {
    }

    private record PathObservation(
            Endpoint source,
            Endpoint target,
            List<Edge> edges
    ) {
    }

    private enum EdgeKind {
        RELATIONSHIP,
        LINEAGE,
        NAMING,
        TABLE_IDENTITY_BRIDGE
    }
}
