package com.relationdetector.core.naming;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.RelationSubType;
import com.relationdetector.contracts.Enums.RelationType;
import com.relationdetector.contracts.model.DerivedPathCandidate;
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
    public void enhance(List<RelationshipCandidate> candidates, NamingEvidencePool namingEvidence) {
        for (RelationshipCandidate candidate : candidates) {
            if (!isEligible(candidate) || hasNamingMatch(candidate)) {
                continue;
            }
            namingEvidence.findFor(candidate)
                    .ifPresent(item -> candidate.evidence().add(referenceEvidence(item)));
        }
    }

    /** Rebinds pre-merge naming aliases to the retained top-level fact id. */
    public void normalizeReferences(
            List<RelationshipCandidate> relationships,
            List<DerivedPathCandidate> derivedRelationships,
            NamingEvidencePool namingEvidence
    ) {
        relationships.forEach(candidate -> {
            normalizeReferences(candidate.evidence(), namingEvidence);
            normalizeReferences(candidate.rawEvidence(), namingEvidence);
        });
        derivedRelationships.forEach(candidate -> {
            normalizeReferences(candidate.evidence(), namingEvidence);
            normalizeReferences(candidate.rawEvidence(), namingEvidence);
        });
    }

    private void normalizeReferences(List<Evidence> evidence, NamingEvidencePool namingEvidence) {
        for (int index = 0; index < evidence.size(); index++) {
            Evidence item = evidence.get(index);
            if (item.type() != EvidenceType.NAMING_MATCH) {
                continue;
            }
            Object referenceValue = item.attributes().get("evidenceRef");
            if (referenceValue == null) {
                continue;
            }
            String reference = String.valueOf(referenceValue);
            String retained = namingEvidence.resolveReferenceId(reference).orElse(reference);
            if (retained.equals(reference)) {
                continue;
            }
            Map<String, Object> attributes = new LinkedHashMap<>(item.attributes());
            attributes.put("evidenceRef", retained);
            String source = reference.equals(item.source()) ? retained : item.source();
            String detail = ("Naming evidence " + reference).equals(item.detail())
                    ? "Naming evidence " + retained : item.detail();
            evidence.set(index, new Evidence(
                    item.type(), item.score(), item.sourceType(), source, detail, attributes));
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

    private Evidence referenceEvidence(NamingEvidenceCandidate naming) {
        Evidence evidence = naming.evidence();
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("evidenceRef", naming.id());
        attributes.put("suggestedSourceEndpointKey", naming.source().normalizedKey());
        attributes.put("suggestedTargetEndpointKey", naming.target().normalizedKey());
        copyAttribute(evidence, attributes, "namingRule");
        copyAttribute(evidence, attributes, "suggestedSourceEndpoint");
        copyAttribute(evidence, attributes, "suggestedTargetEndpoint");
        copyAttribute(evidence, attributes, "matchedColumn");
        copyAttribute(evidence, attributes, "matchedTable");
        copyAttribute(evidence, attributes, "configuredRuleId");
        copyAttribute(evidence, attributes, "configuredRuleDescription");
        copyAttribute(evidence, attributes, "ruleSource");
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

}
