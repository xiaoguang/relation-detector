package com.relationdetector.core.relation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * candidates. It never creates relationships from names alone and never
 * recomputes naming rules locally; the top-level namingEvidence pool is the
 * single source of truth.
 */
public final class NamingMatchEvidenceEnhancer {
    public void enhance(List<RelationshipCandidate> candidates, List<NamingEvidenceCandidate> namingEvidence) {
        for (RelationshipCandidate candidate : candidates) {
            if (!isEligible(candidate) || hasNamingMatch(candidate)) {
                continue;
            }
            Optional<NamingEvidenceCandidate> pooled = matchingPoolEvidence(candidate, namingEvidence);
            pooled.ifPresent(item -> candidate.evidence().add(referenceEvidence(item)));
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

    private Optional<NamingEvidenceCandidate> matchingPoolEvidence(
            RelationshipCandidate candidate,
            List<NamingEvidenceCandidate> namingEvidence
    ) {
        return namingEvidence.stream()
                .filter(item -> sameEndpointPair(item, candidate))
                .findFirst();
    }

    private Evidence referenceEvidence(NamingEvidenceCandidate naming) {
        Evidence evidence = naming.evidence();
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("evidenceRef", naming.id());
        copyAttribute(evidence, attributes, "namingRule");
        copyAttribute(evidence, attributes, "suggestedSourceEndpoint");
        copyAttribute(evidence, attributes, "suggestedTargetEndpoint");
        copyAttribute(evidence, attributes, "matchedColumn");
        copyAttribute(evidence, attributes, "matchedTable");
        attributes.put("directionHint", naming.directionHint());
        return new Evidence(
                evidence.type(),
                evidence.score(),
                evidence.sourceType(),
                naming.id(),
                "Naming evidence " + naming.id(),
                attributes);
    }

    private void copyAttribute(Evidence evidence, Map<String, Object> attributes, String name) {
        if (evidence.attributes().containsKey(name)) {
            attributes.put(name, evidence.attributes().get(name));
        }
    }

    private boolean sameEndpointPair(NamingEvidenceCandidate item, RelationshipCandidate candidate) {
        return (sameEndpoint(item.source(), candidate.source()) && sameEndpoint(item.target(), candidate.target()))
                || (sameEndpoint(item.source(), candidate.target()) && sameEndpoint(item.target(), candidate.source()));
    }

    private boolean sameEndpoint(com.relationdetector.contracts.model.Endpoint left,
                                 com.relationdetector.contracts.model.Endpoint right) {
        return left.normalizedKey().equals(right.normalizedKey());
    }
}
