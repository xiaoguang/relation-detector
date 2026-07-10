package com.relationdetector.core.relation;

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

/**
 * Extracts independent name-only evidence hints.
 *
 * <p>CN: 本类只生成 NAMING_MATCH evidence pool。它不会创建 RelationshipCandidate。
 * 当同端点 relationship candidate 已由 SQL/DDL/metadata/profile 产生时，pool 中的
 * evidence 才会被 {@link NamingMatchEvidenceEnhancer} 合并进去。
 */
public final class NamingEvidenceExtractor {
    private final NamingRuleEngine namingRuleEngine = new NamingRuleEngine();

    public List<NamingEvidenceCandidate> extractFromMetadata(MetadataSnapshot metadata) {
        return extractFromMetadata(metadata, null);
    }

    public List<NamingEvidenceCandidate> extractFromMetadata(MetadataSnapshot metadata, com.relationdetector.core.scan.ScanConfig config) {
        if (metadata == null) {
            return List.of();
        }
        List<Endpoint> endpoints = new ArrayList<>();
        for (ColumnRef column : metadata.columns()) {
            endpoints.add(Endpoint.column(column));
        }
        for (MetadataColumnFact fact : metadata.columnFacts()) {
            TableId table = TableId.of(fact.schema(), fact.tableName());
            endpoints.add(Endpoint.column(new ColumnRef(table, fact.columnName(), fact.columnName(),
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
        List<Endpoint> endpoints = new ArrayList<>();
        String source = "DDL column inventory";
        for (StructuredSqlEvent event : events) {
            if (event.type() != StructuredParseEventType.DDL_COLUMN) {
                continue;
            }
            String table = text(event.attributes(), "table");
            String column = text(event.attributes(), "column");
            if (table.isBlank() || column.isBlank()) {
                continue;
            }
            endpoints.add(Endpoint.column(ColumnRef.of(tableId(table), column)));
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
        Set<String> seen = new LinkedHashSet<>();
        NamingRuleSet ruleSet = ruleSet(config);
        for (RelationshipCandidate candidate : candidates) {
            if (!isEligibleRelationshipCandidate(candidate)) {
                continue;
            }
            for (NamingRuleEngine.Match match : namingRuleEngine.match(
                    candidate.source(),
                    candidate.target(),
                    NamingRuleScope.RELATIONSHIP_CANDIDATE,
                    hasSelfJoinRole(candidate) || isDeclaredSelfReference(candidate),
                    ruleSet)) {
                add(result, seen, candidate(match,
                        "naming heuristic",
                        match.source().displayName() + " matches " + match.target().displayName()));
            }
        }
        return List.copyOf(result);
    }

    private List<NamingEvidenceCandidate> extractFromEndpoints(
            List<Endpoint> endpoints,
            String source,
            String detailPrefix,
            NamingRuleScope scope,
            NamingRuleSet ruleSet
    ) {
        List<NamingEvidenceCandidate> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        List<Endpoint> uniqueEndpoints = dedupeEndpoints(endpoints);
        for (int leftIndex = 0; leftIndex < uniqueEndpoints.size(); leftIndex++) {
            for (int rightIndex = leftIndex + 1; rightIndex < uniqueEndpoints.size(); rightIndex++) {
                Endpoint left = uniqueEndpoints.get(leftIndex);
                Endpoint right = uniqueEndpoints.get(rightIndex);
                if (left.table().normalizedName().equals(right.table().normalizedName())) {
                    continue;
                }
                for (NamingRuleEngine.Match match : namingRuleEngine.match(left, right, scope, false, ruleSet)) {
                    add(result, seen, candidate(match, source,
                            detailPrefix + ": " + match.source().displayName()
                                    + " matches " + match.target().displayName()));
                }
            }
        }
        return List.copyOf(result);
    }

    private List<Endpoint> dedupeEndpoints(List<Endpoint> endpoints) {
        List<Endpoint> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Endpoint endpoint : endpoints) {
            if (endpoint == null || !endpoint.isColumnLevel()) {
                continue;
            }
            if (seen.add(endpoint.normalizedKey())) {
                result.add(endpoint);
            }
        }
        return result;
    }

    private TableId tableId(String raw) {
        List<String> parts = identifierParts(raw);
        if (parts.isEmpty()) {
            return TableId.of(null, clean(raw));
        }
        String table = parts.get(parts.size() - 1);
        String schema = parts.size() > 1 ? parts.get(parts.size() - 2) : null;
        return TableId.of(schema, table);
    }

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

    private NamingEvidenceCandidate candidate(NamingRuleEngine.Match match, String source, String detail) {
        return new NamingEvidenceCandidate(match.source(), match.target(), evidenceFor(match, source, detail),
                match.rule(), match.directionHint());
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
        String key = candidate.source().normalizedKey() + "->" + candidate.target().normalizedKey()
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
        return candidate.evidence().stream().anyMatch(evidence -> switch (evidence.type()) {
            case DDL_FOREIGN_KEY, METADATA_FOREIGN_KEY,
                    SQL_LOG_JOIN, SQL_LOG_SUBQUERY_IN, SQL_LOG_EXISTS, SQL_LOG_COLUMN_CO_OCCURRENCE,
                    VIEW_JOIN, PROCEDURE_JOIN, TRIGGER_REFERENCE -> true;
            default -> false;
        });
    }

    private boolean hasSelfJoinRole(RelationshipCandidate candidate) {
        return candidate.evidence().stream()
                .anyMatch(evidence -> Boolean.TRUE.equals(evidence.attributes().get("selfJoinRole")));
    }

    private boolean isDeclaredSelfReference(RelationshipCandidate candidate) {
        if (!candidate.source().table().normalizedName().equals(candidate.target().table().normalizedName())
                || candidate.source().column().normalizedName().equals(candidate.target().column().normalizedName())) {
            return false;
        }
        return candidate.evidence().stream().anyMatch(evidence -> switch (evidence.type()) {
            case DDL_FOREIGN_KEY, METADATA_FOREIGN_KEY -> true;
            default -> false;
        });
    }

    private String text(Map<String, Object> attributes, String key) {
        Object value = attributes.get(key);
        return value == null ? "" : String.valueOf(value);
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
}
