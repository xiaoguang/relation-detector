package com.relationdetector.core.profile;

import java.util.stream.Stream;

import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.spi.ProfileRequest;
import com.relationdetector.core.relation.RelationshipConditionAttributes;

/**
 * CN: 将负向值不匹配证据限制为可审计的非条件声明式外键验证。
 *
 * <p>EN: Restricts negative value-mismatch evidence to auditable,
 * unconditional declared foreign-key verification. It does not affect positive
 * containment or overlap evidence.
 */
final class NegativeProfileEvidencePolicy {
    boolean allows(ProfileRequest request, DataProfileMetrics metrics) {
        if (request == null || request.candidate() == null || metrics == null
                || !"LIVE_DATABASE".equals(metrics.profileMode())) {
            return false;
        }
        return allows(request);
    }

    boolean allows(ProfileRequest request) {
        if (request == null || request.candidate() == null) {
            return false;
        }
        RelationshipCandidate candidate = request.candidate();
        if (!candidate.source().isColumnLevel() || !candidate.target().isColumnLevel()) {
            return false;
        }
        if (conditional(candidate)) {
            return false;
        }
        return Stream.concat(candidate.evidence().stream(), candidate.rawEvidence().stream())
                .anyMatch(evidence -> evidence.type() == EvidenceType.DDL_FOREIGN_KEY
                        || evidence.type() == EvidenceType.METADATA_FOREIGN_KEY);
    }

    private boolean conditional(RelationshipCandidate candidate) {
        return Stream.concat(
                Stream.of(candidate.attributes()),
                Stream.concat(candidate.evidence().stream(), candidate.rawEvidence().stream())
                        .map(evidence -> evidence.attributes()))
                .anyMatch(attributes -> Boolean.TRUE.equals(attributes.get("conditional"))
                        || Boolean.TRUE.equals(attributes.get("polymorphic"))
                        || !RelationshipConditionAttributes.conditions(attributes).isEmpty());
    }
}
