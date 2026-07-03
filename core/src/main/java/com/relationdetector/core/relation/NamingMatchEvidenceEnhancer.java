package com.relationdetector.core.relation;

import java.util.List;
import java.util.Optional;

import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.RelationSubType;
import com.relationdetector.contracts.Enums.RelationType;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.NamingEvidenceCandidate;
import com.relationdetector.contracts.model.RelationshipCandidate;

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
        enhance(candidates, List.of());
    }

    public void enhance(List<RelationshipCandidate> candidates, List<NamingEvidenceCandidate> namingEvidence) {
        for (RelationshipCandidate candidate : candidates) {
            if (!isEligible(candidate) || hasNamingMatch(candidate)) {
                continue;
            }
            Optional<NamingEvidenceCandidate> pooled = matchingPoolEvidence(candidate, namingEvidence);
            if (pooled.isPresent()) {
                candidate.evidence().add(pooled.get().evidence());
                continue;
            }
            NamingMatchRules.match(candidate.source(), candidate.target(), hasSelfJoinRole(candidate))
                    .ifPresent(match -> candidate.evidence().add(NamingEvidenceExtractor.evidenceFor(
                            match,
                            "naming heuristic",
                            match.source().displayName() + " matches " + match.target().displayName())));
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

    private Optional<NamingEvidenceCandidate> matchingPoolEvidence(
            RelationshipCandidate candidate,
            List<NamingEvidenceCandidate> namingEvidence
    ) {
        return namingEvidence.stream()
                .filter(item -> sameEndpoint(item.source(), candidate.source())
                        && sameEndpoint(item.target(), candidate.target()))
                .findFirst();
    }

    private boolean sameEndpoint(com.relationdetector.contracts.model.Endpoint left,
                                 com.relationdetector.contracts.model.Endpoint right) {
        return left.normalizedKey().equals(right.normalizedKey());
    }
}
