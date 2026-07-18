package com.relationdetector.core.evidence;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.Collectors.EvidenceWeightAdjuster;
import com.relationdetector.core.naming.NamingEvidencePool;

/**
 * CN: 在 merger 前对 relationship 和 naming observations 各执行一次 adaptor evidence weight hook，不改变事实身份。
 * EN: Applies an adaptor evidence-weight hook once to relationship and naming observations before merge without changing fact identity.
 */
public final class EvidenceWeightAdjustmentService {
    private static final BigDecimal MIN_SCORE = BigDecimal.ONE.negate();
    private static final BigDecimal MAX_SCORE = BigDecimal.ONE;

    public void adjust(
            List<RelationshipCandidate> relationships,
            NamingEvidencePool namingEvidence,
            EvidenceWeightAdjuster adjuster,
            AdaptorContext context
    ) {
        if (adjuster == null) {
            throw new IllegalArgumentException("evidence weight adjuster is required");
        }
        if (relationships != null) {
            relationships.forEach(candidate -> adjust(candidate, adjuster, context));
        }
        if (namingEvidence != null) {
            namingEvidence.adjustRawEvidence(evidence -> adjusted(evidence, adjuster, context));
        }
    }

    private void adjust(RelationshipCandidate candidate, EvidenceWeightAdjuster adjuster, AdaptorContext context) {
        List<Evidence> observations = candidate.rawEvidence().isEmpty()
                ? candidate.evidence() : candidate.rawEvidence();
        List<Evidence> adjusted = new ArrayList<>(observations.size());
        for (Evidence evidence : observations) {
            adjusted.add(adjusted(evidence, adjuster, context));
        }
        observations.clear();
        observations.addAll(adjusted);
    }

    private Evidence adjusted(Evidence evidence, EvidenceWeightAdjuster adjuster, AdaptorContext context) {
        if (evidence == null) {
            throw new IllegalArgumentException("raw evidence must not be null");
        }
        Evidence adjusted = adjuster.adjust(evidence, context);
        if (adjusted == null) {
            throw new IllegalArgumentException("evidence weight adjuster must not return null");
        }
        if (adjusted.score().compareTo(MIN_SCORE) < 0 || adjusted.score().compareTo(MAX_SCORE) > 0) {
            throw new IllegalArgumentException("adjusted evidence score must be within [-1, 1]");
        }
        return adjusted;
    }
}
