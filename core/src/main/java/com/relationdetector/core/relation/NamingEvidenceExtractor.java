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

/**
 * Extracts independent name-only evidence hints.
 *
 * <p>CN: 本类只生成 NAMING_MATCH evidence pool。它不会创建 RelationshipCandidate。
 * 当同端点 relationship candidate 已由 SQL/DDL/metadata/profile 产生时，pool 中的
 * evidence 才会被 {@link NamingMatchEvidenceEnhancer} 合并进去。
 */
public final class NamingEvidenceExtractor {
    public List<NamingEvidenceCandidate> extractFromMetadata(MetadataSnapshot metadata) {
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
        return extractFromEndpoints(endpoints, "metadata", "metadata catalog column names");
    }

    public List<NamingEvidenceCandidate> extractFromDdlEvents(List<StructuredSqlEvent> events) {
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
            endpoints.add(Endpoint.column(ColumnRef.of(TableId.of(null, table), column)));
            if (event.sourceName() != null && !event.sourceName().isBlank()) {
                source = event.sourceName();
            }
        }
        return extractFromEndpoints(endpoints, source, "DDL column inventory");
    }

    public List<NamingEvidenceCandidate> extractFromRelationshipCandidates(List<RelationshipCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<NamingEvidenceCandidate> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (RelationshipCandidate candidate : candidates) {
            if (!isEligibleRelationshipCandidate(candidate)) {
                continue;
            }
            NamingMatchRules.match(candidate.source(), candidate.target(), hasSelfJoinRole(candidate))
                    .ifPresent(match -> add(result, seen, candidate(match,
                            "naming heuristic",
                            match.source().displayName() + " matches " + match.target().displayName())));
        }
        return List.copyOf(result);
    }

    private List<NamingEvidenceCandidate> extractFromEndpoints(
            List<Endpoint> endpoints,
            String source,
            String detailPrefix
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
                catalogNamingMatch(left, right)
                        .ifPresent(match -> add(result, seen, candidate(match, source,
                                detailPrefix + ": " + match.source().displayName()
                                        + " matches " + match.target().displayName())));
            }
        }
        return List.copyOf(result);
    }

    private java.util.Optional<NamingMatchRules.Match> catalogNamingMatch(Endpoint left, Endpoint right) {
        return NamingMatchRules.match(left, right, false)
                .filter(match -> "TABLE_ID".equals(match.rule()));
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

    private NamingEvidenceCandidate candidate(NamingMatchRules.Match match, String source, String detail) {
        return new NamingEvidenceCandidate(match.source(), match.target(), evidenceFor(match, source, detail),
                match.rule(), true);
    }

    static Evidence evidenceFor(NamingMatchRules.Match match, String source, String detail) {
        return new Evidence(
                EvidenceType.NAMING_MATCH,
                BigDecimal.valueOf(DefaultEvidenceScores.NAMING_MATCH),
                EvidenceSourceType.NAMING_HEURISTIC,
                source,
                detail,
                match.attributes());
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

    private String text(Map<String, Object> attributes, String key) {
        Object value = attributes.get(key);
        return value == null ? "" : String.valueOf(value);
    }
}
