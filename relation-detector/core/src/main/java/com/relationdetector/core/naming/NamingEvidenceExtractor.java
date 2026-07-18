package com.relationdetector.core.naming;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.metadata.MetadataColumnFact;
import com.relationdetector.contracts.metadata.MetadataSnapshot;
import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.NamingEvidenceCandidate;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.contracts.scoring.DefaultEvidenceScores;
import com.relationdetector.core.log.SourceNameNormalizer;
import com.relationdetector.core.identity.CanonicalEndpointKeyProvider;

/**
 * CN: 本类按有向 endpoint pair 执行命名规则，只生成 NAMING_MATCH evidence pool，不创建 RelationshipCandidate。
 * 当同端点 relationship candidate 已由 SQL/DDL/metadata/profile 产生时，pool 中的
 * evidence 才会被 {@link NamingMatchEvidenceEnhancer} 合并进去。
 * EN: Runs naming rules on directional endpoint pairs and emits only independent NAMING_MATCH evidence for existing candidates.
 */
public final class NamingEvidenceExtractor {
    private final NamingRuleEngine namingRuleEngine;
    private final CanonicalEndpointKeyProvider endpointKeys;

    public NamingEvidenceExtractor() {
        this(CanonicalEndpointKeyProvider.defaults());
    }

    public NamingEvidenceExtractor(CanonicalEndpointKeyProvider endpointKeys) {
        this.endpointKeys = java.util.Objects.requireNonNull(endpointKeys, "endpointKeys");
        this.namingRuleEngine = new NamingRuleEngine(endpointKeys);
    }

    public List<NamingEvidenceCandidate> extractFromMetadata(MetadataSnapshot metadata) {
        return extractFromMetadata(metadata, null);
    }

    public List<NamingEvidenceCandidate> extractFromMetadata(MetadataSnapshot metadata, com.relationdetector.core.scan.ScanConfig config) {
        if (metadata == null) {
            return List.of();
        }
        List<EndpointObservation> endpoints = new ArrayList<>();
        for (ColumnRef column : metadata.columns()) {
            endpoints.add(metadataObservation(column));
        }
        for (MetadataColumnFact fact : metadata.columnFacts()) {
            TableId table = new TableId(fact.catalog(), fact.schema(), fact.tableName(),
                    fact.schema() == null || fact.schema().isBlank()
                            ? fact.tableName() : fact.schema() + "." + fact.tableName());
            endpoints.add(metadataObservation(new ColumnRef(table, fact.columnName(), fact.columnName(),
                    fact.dataType(), fact.nullable())));
        }
        return extractFromEndpoints(endpoints, "metadata", "metadata catalog column names",
                NamingRuleScope.METADATA, ruleSet(config));
    }

    public List<NamingEvidenceCandidate> extractFromDdlEvents(List<StructuredSqlEvent> events) {
        return extractFromDdlEvents(events, null);
    }

    public List<NamingEvidenceCandidate> extractFromDdlEvents(
            List<StructuredSqlEvent> events,
            com.relationdetector.core.scan.ScanConfig config
    ) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        List<EndpointObservation> endpoints = new ArrayList<>();
        String source = "DDL column inventory";
        for (StructuredSqlEvent event : events) {
            if (event.type() != StructuredParseEventType.DDL_COLUMN) {
                continue;
            }
            String table = event.table();
            String column = event.column();
            if (table.isBlank() || column.isBlank()) {
                continue;
            }
            endpoints.add(ddlObservation(event, ColumnRef.of(tableId(table), column)));
            if (event.sourceName() != null && !event.sourceName().isBlank()) {
                source = event.sourceName();
            }
        }
        return extractFromEndpoints(endpoints, source, "DDL column inventory",
                NamingRuleScope.DDL_COLUMN_INVENTORY, ruleSet(config));
    }

    public List<NamingEvidenceCandidate> extractFromRelationshipCandidates(List<RelationshipCandidate> candidates) {
        return extractFromRelationshipCandidates(candidates, null);
    }

    public List<NamingEvidenceCandidate> extractFromRelationshipCandidates(
            List<RelationshipCandidate> candidates,
            com.relationdetector.core.scan.ScanConfig config
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<NamingEvidenceCandidate> result = new ArrayList<>();
        NamingRuleSet ruleSet = ruleSet(config);
        Map<DirectionalEndpointPairKey, RelationshipCandidateGroup> groups = new LinkedHashMap<>();
        for (RelationshipCandidate candidate : candidates) {
            if (!isEligibleRelationshipCandidate(candidate)) {
                continue;
            }
            groups.computeIfAbsent(DirectionalEndpointPairKey.of(candidate, endpointKeys),
                            ignored -> new RelationshipCandidateGroup(candidate))
                    .add(candidate);
        }
        for (RelationshipCandidateGroup group : groups.values()) {
            RelationshipCandidate candidate = group.first();
            for (NamingRuleEngine.Match match : namingRuleEngine.match(
                    candidate.source(),
                    candidate.target(),
                    NamingRuleScope.RELATIONSHIP_CANDIDATE,
                    group.selfReference(),
                    ruleSet)) {
                result.add(candidate(match,
                        "naming heuristic",
                        match.source().displayName() + " matches " + match.target().displayName(),
                        group.structuralEvidence()));
            }
        }
        return List.copyOf(result);
    }

    private List<NamingEvidenceCandidate> extractFromEndpoints(
            List<EndpointObservation> endpoints,
            String source,
            String detailPrefix,
            NamingRuleScope scope,
            NamingRuleSet ruleSet
    ) {
        List<NamingEvidenceCandidate> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        List<List<EndpointObservation>> endpointGroups = groupEndpointObservations(endpoints);
        for (int leftIndex = 0; leftIndex < endpointGroups.size(); leftIndex++) {
            for (int rightIndex = leftIndex + 1; rightIndex < endpointGroups.size(); rightIndex++) {
                List<EndpointObservation> leftGroup = endpointGroups.get(leftIndex);
                List<EndpointObservation> rightGroup = endpointGroups.get(rightIndex);
                EndpointObservation left = leftGroup.get(0);
                EndpointObservation right = rightGroup.get(0);
                if (sameTable(left.endpoint().table(), right.endpoint().table())) {
                    continue;
                }
                for (NamingRuleEngine.Match match : namingRuleEngine.match(
                        left.endpoint(), right.endpoint(), scope, false, ruleSet)) {
                    add(result, seen, candidate(match, source,
                            detailPrefix + ": " + match.source().displayName()
                                    + " matches " + match.target().displayName(),
                            observationsFor(match, leftGroup, rightGroup)));
                }
            }
        }
        return List.copyOf(result);
    }

    private List<List<EndpointObservation>> groupEndpointObservations(List<EndpointObservation> endpoints) {
        Map<String, List<EndpointObservation>> grouped = new LinkedHashMap<>();
        for (EndpointObservation observation : endpoints) {
            if (observation == null || !observation.endpoint().isColumnLevel()) {
                continue;
            }
            grouped.computeIfAbsent(endpointKeys.factKey(observation.endpoint()), ignored -> new ArrayList<>())
                    .add(observation);
        }
        return List.copyOf(grouped.values());
    }

    private TableId tableId(String raw) {
        List<String> parts = identifierParts(raw);
        if (parts.isEmpty()) {
            return TableId.of(null, clean(raw));
        }
        String table = parts.get(parts.size() - 1);
        String schema = parts.size() > 1 ? parts.get(parts.size() - 2) : null;
        String catalog = parts.size() > 2
                ? String.join(".", parts.subList(0, parts.size() - 2))
                : null;
        String normalized = schema == null || schema.isBlank() ? table : schema + "." + table;
        return new TableId(catalog, schema, table, normalized);
    }

    private boolean sameTable(TableId left, TableId right) {
        return endpointKeys.sameTable(left, right);
    }

    /**
     * CN: 按 SQL 标识符引号和方括号边界拆分限定名，保留带点的 quoted 组件，供 naming
     * identity 构造使用。空输入返回空列表；未闭合引号不会触发结构猜测，而按剩余文本
     * 作为单个组件处理。
     *
     * EN: Splits a qualified identifier at unquoted dots while preserving quoted
     * or bracketed components that contain dots. Empty input returns no parts;
     * unmatched quoting is retained as one component rather than being interpreted
     * as SQL structure.
     */
    private List<String> identifierParts(String identifier) {
        List<String> parts = new ArrayList<>();
        if (identifier == null || identifier.isBlank()) {
            return parts;
        }
        StringBuilder current = new StringBuilder();
        char quote = 0;
        int bracketDepth = 0;
        for (int i = 0; i < identifier.length(); i++) {
            char c = identifier.charAt(i);
            if (quote == 0 && bracketDepth == 0 && (c == '"' || c == '`')) {
                quote = c;
                current.append(c);
                continue;
            }
            if (quote != 0 && c == quote) {
                quote = 0;
                current.append(c);
                continue;
            }
            if (quote == 0 && c == '[') {
                bracketDepth++;
                current.append(c);
                continue;
            }
            if (quote == 0 && c == ']' && bracketDepth > 0) {
                bracketDepth--;
                current.append(c);
                continue;
            }
            if (c == '.' && quote == 0 && bracketDepth == 0) {
                String part = clean(current.toString());
                if (!part.isBlank()) {
                    parts.add(part);
                }
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        String part = clean(current.toString());
        if (!part.isBlank()) {
            parts.add(part);
        }
        return parts;
    }

    private NamingEvidenceCandidate candidate(
            NamingRuleEngine.Match match,
            String source,
            String detail,
            List<Evidence> rawEvidence
    ) {
        return new NamingEvidenceCandidate(match.source(), match.target(), evidenceFor(match, source, detail),
                match.rule(), match.directionHint(), rawEvidence);
    }

    private EndpointObservation metadataObservation(ColumnRef column) {
        Endpoint endpoint = Endpoint.column(column);
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("catalog", nullToEmpty(column.table().catalog()));
        attributes.put("catalogSchema", nullToEmpty(column.table().schema()));
        attributes.put("catalogTable", column.table().tableName());
        attributes.put("catalogColumn", column.columnName());
        return new EndpointObservation(endpoint, new Evidence(
                EvidenceType.NAMING_MATCH,
                BigDecimal.valueOf(DefaultEvidenceScores.NAMING_MATCH),
                EvidenceSourceType.METADATA,
                "metadata catalog",
                "metadata catalog column " + endpoint.displayName(),
                attributes));
    }

    private EndpointObservation ddlObservation(StructuredSqlEvent event, ColumnRef column) {
        Endpoint endpoint = Endpoint.column(column);
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("table", event.table());
        attributes.put("column", event.column());
        if (event.provenance().tokenEventNative()) {
            attributes.put("tokenEventNative", true);
        }
        if (event.provenance().fullGrammarNative()) {
            attributes.put("fullGrammarNative", true);
        }
        if (!event.statementScope().isBlank()) {
            attributes.put("statementScope", event.statementScope());
        }
        attributes.put("line", event.line());
        attributes.put("sourceLine", event.line());
        putIfPresent(attributes, "sourceFile", event.provenance().sourceFile());
        putIfPresent(attributes, "sourceStatementId", event.provenance().sourceStatementId());
        putIfPresent(attributes, "sourceBlockId", event.provenance().sourceBlockId());
        putIfPresent(attributes, "sourceObjectType", event.provenance().sourceObjectType());
        putIfPresent(attributes, "sourceObjectName", event.provenance().sourceObjectName());
        return new EndpointObservation(endpoint, new Evidence(
                EvidenceType.NAMING_MATCH,
                BigDecimal.valueOf(DefaultEvidenceScores.NAMING_MATCH),
                EvidenceSourceType.DDL_FILE,
                SourceNameNormalizer.normalize(event.sourceName()),
                "DDL column inventory: " + endpoint.displayName(),
                attributes));
    }

    private void putIfPresent(Map<String, Object> attributes, String key, String value) {
        if (value != null && !value.isBlank()) {
            attributes.put(key, value);
        }
    }

    private List<Evidence> observationsFor(
            NamingRuleEngine.Match match,
            List<EndpointObservation> left,
            List<EndpointObservation> right
    ) {
        List<Evidence> observations = new ArrayList<>(left.size() + right.size());
        if (endpointKeys.same(match.source(), left.get(0).endpoint())) {
            left.stream().map(EndpointObservation::evidence).forEach(observations::add);
            right.stream().map(EndpointObservation::evidence).forEach(observations::add);
        } else {
            right.stream().map(EndpointObservation::evidence).forEach(observations::add);
            left.stream().map(EndpointObservation::evidence).forEach(observations::add);
        }
        return List.copyOf(observations);
    }

    private List<Evidence> structuralEvidence(RelationshipCandidate candidate) {
        return candidate.evidence().stream()
                .filter(this::isStructuralEndpointEvidence)
                .toList();
    }

    static Evidence evidenceFor(NamingRuleEngine.Match match, String source, String detail) {
        return new Evidence(
                EvidenceType.NAMING_MATCH,
                BigDecimal.valueOf(DefaultEvidenceScores.NAMING_MATCH),
                EvidenceSourceType.NAMING_HEURISTIC,
                SourceNameNormalizer.normalize(source),
                detail,
                match.attributes());
    }

    private NamingRuleSet ruleSet(com.relationdetector.core.scan.ScanConfig config) {
        return config == null ? NamingRuleSet.systemDefault() : config.namingRuleSet();
    }

    private void add(
            List<NamingEvidenceCandidate> result,
            Set<String> seen,
            NamingEvidenceCandidate candidate
    ) {
        String key = endpointKeys.factKey(candidate.source()) + "->" + endpointKeys.factKey(candidate.target())
                + ":" + candidate.rule();
        if (seen.add(key)) {
            result.add(candidate);
        }
    }

    private boolean isEligibleRelationshipCandidate(RelationshipCandidate candidate) {
        return candidate != null
                && candidate.source().isColumnLevel()
                && candidate.target().isColumnLevel()
                && hasStructuralEndpointEvidence(candidate);
    }

    private boolean hasStructuralEndpointEvidence(RelationshipCandidate candidate) {
        return candidate.evidence().stream().anyMatch(this::isStructuralEndpointEvidence);
    }

    private boolean isStructuralEndpointEvidence(Evidence evidence) {
        return switch (evidence.type()) {
            case DDL_FOREIGN_KEY, METADATA_FOREIGN_KEY,
                    SQL_LOG_JOIN, SQL_LOG_SUBQUERY_IN, SQL_LOG_EXISTS, SQL_LOG_COLUMN_CO_OCCURRENCE,
                    VIEW_JOIN, PROCEDURE_JOIN, TRIGGER_REFERENCE -> true;
            default -> false;
        };
    }

    private boolean hasSelfJoinRole(RelationshipCandidate candidate) {
        return candidate.evidence().stream()
                .anyMatch(evidence -> Boolean.TRUE.equals(evidence.attributes().get("selfJoinRole")));
    }

    private boolean isDeclaredSelfReference(RelationshipCandidate candidate) {
        if (!endpointKeys.sameTable(candidate.source().table(), candidate.target().table())
                || endpointKeys.same(candidate.source(), candidate.target())) {
            return false;
        }
        return candidate.evidence().stream().anyMatch(evidence -> switch (evidence.type()) {
            case DDL_FOREIGN_KEY, METADATA_FOREIGN_KEY -> true;
            default -> false;
        });
    }

    private final class RelationshipCandidateGroup {
        private final RelationshipCandidate first;
        private final List<Evidence> structuralEvidence = new ArrayList<>();
        private boolean selfReference;

        private RelationshipCandidateGroup(RelationshipCandidate first) {
            this.first = first;
        }

        private void add(RelationshipCandidate candidate) {
            structuralEvidence.addAll(NamingEvidenceExtractor.this.structuralEvidence(candidate));
            selfReference = selfReference || hasSelfJoinRole(candidate) || isDeclaredSelfReference(candidate);
        }

        private RelationshipCandidate first() {
            return first;
        }

        private List<Evidence> structuralEvidence() {
            return List.copyOf(structuralEvidence);
        }

        private boolean selfReference() {
            return selfReference;
        }
    }

    private String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
        while ((text.startsWith("[") && text.endsWith("]"))
                || (text.startsWith("\"") && text.endsWith("\""))
                || (text.startsWith("`") && text.endsWith("`"))) {
            text = text.substring(1, text.length() - 1).trim();
        }
        return text;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record EndpointObservation(Endpoint endpoint, Evidence evidence) {
    }
}
