package com.relationdetector.core.relation;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.RelationSubType;
import com.relationdetector.contracts.Enums.RelationType;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.scoring.DefaultEvidenceScores;

/**
 * Adds naming-direction evidence to existing SQL predicate relationships.
 *
 * <p>CN: 命名匹配只能增强已经由 typed SQL parser 发现的列级谓词候选，不能凭表名/列名凭空
 * 创建 relationship。它只读取已解析 endpoint 的 table/column 名称，不参与 SQL 结构判断。
 *
 * <p>EN: Naming evidence only enriches existing column-level SQL predicate
 * candidates. It never creates relationships from names alone and does not parse
 * SQL structure; it only inspects already-resolved table/column endpoints.
 */
public final class NamingMatchEvidenceEnhancer {
    public void enhance(List<RelationshipCandidate> candidates) {
        for (RelationshipCandidate candidate : candidates) {
            if (!isEligible(candidate) || hasNamingMatch(candidate)) {
                continue;
            }
            NamingMatch match = match(candidate.source(), candidate.target(), hasSelfJoinRole(candidate));
            if (match == null) {
                continue;
            }
            candidate.evidence().add(new Evidence(
                    EvidenceType.NAMING_MATCH,
                    BigDecimal.valueOf(DefaultEvidenceScores.NAMING_MATCH),
                    EvidenceSourceType.NAMING_HEURISTIC,
                    "naming heuristic",
                    match.sourceEndpoint() + " matches " + match.targetEndpoint(),
                    match.attributes()));
        }
    }

    private boolean isEligible(RelationshipCandidate candidate) {
        return candidate.source().isColumnLevel()
                && candidate.target().isColumnLevel()
                && candidate.relationType() == RelationType.CO_OCCURRENCE
                && candidate.relationSubType() == RelationSubType.COLUMN_CO_OCCURRENCE
                && hasSqlPredicateEvidence(candidate);
    }

    private boolean hasSqlPredicateEvidence(RelationshipCandidate candidate) {
        return candidate.evidence().stream().anyMatch(evidence -> switch (evidence.type()) {
            case SQL_LOG_JOIN, SQL_LOG_SUBQUERY_IN, SQL_LOG_EXISTS, SQL_LOG_COLUMN_CO_OCCURRENCE,
                    VIEW_JOIN, PROCEDURE_JOIN, TRIGGER_REFERENCE -> true;
            default -> false;
        });
    }

    private boolean hasNamingMatch(RelationshipCandidate candidate) {
        return candidate.evidence().stream().anyMatch(evidence -> evidence.type() == EvidenceType.NAMING_MATCH);
    }

    private boolean hasSelfJoinRole(RelationshipCandidate candidate) {
        return candidate.evidence().stream()
                .anyMatch(evidence -> Boolean.TRUE.equals(evidence.attributes().get("selfJoinRole")));
    }

    private NamingMatch match(Endpoint left, Endpoint right, boolean selfJoinRole) {
        if (selfJoinRole && sameTable(left, right)) {
            NamingMatch self = selfRoleId(left, right);
            if (self != null) {
                return self;
            }
        }
        NamingMatch leftTableId = tableId(left, right);
        NamingMatch rightTableId = tableId(right, left);
        if (leftTableId != null ^ rightTableId != null) {
            return leftTableId != null ? leftTableId : rightTableId;
        }
        NamingMatch leftSuffix = idSuffixToId(left, right);
        NamingMatch rightSuffix = idSuffixToId(right, left);
        if (leftSuffix != null ^ rightSuffix != null) {
            return leftSuffix != null ? leftSuffix : rightSuffix;
        }
        return null;
    }

    private NamingMatch tableId(Endpoint source, Endpoint target) {
        if (!isId(target)) {
            return null;
        }
        String sourceStem = idPrefix(source.column().columnName());
        String targetStem = singularStem(target.table().tableName());
        if (sourceStem.isBlank() || targetStem.isBlank() || sourceStem.equals(normalize(target.table().tableName()))) {
            return null;
        }
        if (!sourceStem.equals(targetStem)) {
            return null;
        }
        return match("TABLE_ID", source, target, source.column().columnName(), target.table().tableName());
    }

    private NamingMatch idSuffixToId(Endpoint source, Endpoint target) {
        if (!isId(target) || !endsWithIdSuffix(source.column().columnName())) {
            return null;
        }
        return match("ID_SUFFIX_TO_ID", source, target, source.column().columnName(), target.table().tableName());
    }

    private NamingMatch selfRoleId(Endpoint left, Endpoint right) {
        NamingMatch leftToRight = selfRoleDirection(left, right);
        NamingMatch rightToLeft = selfRoleDirection(right, left);
        if (leftToRight != null ^ rightToLeft != null) {
            return leftToRight != null ? leftToRight : rightToLeft;
        }
        return null;
    }

    private NamingMatch selfRoleDirection(Endpoint source, Endpoint target) {
        if (!isId(target) || !endsWithIdSuffix(source.column().columnName())) {
            return null;
        }
        return match("SELF_ROLE_ID", source, target, source.column().columnName(), target.table().tableName());
    }

    private NamingMatch match(String rule, Endpoint source, Endpoint target, String matchedColumn, String matchedTable) {
        return new NamingMatch(rule, source.displayName(), target.displayName(), matchedColumn, matchedTable);
    }

    private boolean sameTable(Endpoint left, Endpoint right) {
        return normalize(left.table().normalizedName()).equals(normalize(right.table().normalizedName()));
    }

    private boolean isId(Endpoint endpoint) {
        return normalize(endpoint.column().columnName()).equals("id");
    }

    private boolean endsWithIdSuffix(String column) {
        return normalize(column).endsWith("_id") && !normalize(column).equals("id");
    }

    private String idPrefix(String column) {
        String normalized = normalize(column);
        if (!normalized.endsWith("_id") || normalized.length() <= 3) {
            return "";
        }
        return normalized.substring(0, normalized.length() - 3);
    }

    private String singularStem(String table) {
        String value = normalize(table);
        if (value.endsWith("ies") && value.length() > 3) {
            return value.substring(0, value.length() - 3) + "y";
        }
        if (value.endsWith("ses") || value.endsWith("xes") || value.endsWith("zes")
                || value.endsWith("ches") || value.endsWith("shes")) {
            return value.substring(0, value.length() - 2);
        }
        if (value.endsWith("s") && value.length() > 3) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record NamingMatch(
            String rule,
            String sourceEndpoint,
            String targetEndpoint,
            String matchedColumn,
            String matchedTable
    ) {
        Map<String, Object> attributes() {
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("namingRule", rule);
            attributes.put("suggestedSourceEndpoint", sourceEndpoint);
            attributes.put("suggestedTargetEndpoint", targetEndpoint);
            attributes.put("matchedColumn", matchedColumn);
            attributes.put("matchedTable", matchedTable);
            attributes.put("directionHint", true);
            return attributes;
        }
    }
}
