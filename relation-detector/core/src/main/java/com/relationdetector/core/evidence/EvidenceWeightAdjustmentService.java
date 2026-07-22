package com.relationdetector.core.evidence;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.Collectors.EvidenceWeightAdjuster;
import com.relationdetector.core.naming.NamingEvidencePool;
import com.relationdetector.core.scan.AdaptorContractException;
import com.relationdetector.core.scan.AdaptorResultDetachmentSupport;

/**
 * CN: 在 merger 前对 relationship 和 naming observations 各执行一次 adaptor evidence weight hook，不改变事实身份。
 * EN: Applies an adaptor evidence-weight hook once to relationship and naming observations before merge without changing fact identity.
 */
public final class EvidenceWeightAdjustmentService {
    private static final BigDecimal MIN_SCORE = BigDecimal.ONE.negate();
    private static final BigDecimal MAX_SCORE = BigDecimal.ONE;
    private final AdaptorResultDetachmentSupport detachment = new AdaptorResultDetachmentSupport();

    public void adjust(
            List<RelationshipCandidate> relationships,
            NamingEvidencePool namingEvidence,
            EvidenceWeightAdjuster adjuster,
            AdaptorContext context
    ) {
        if (adjuster == null) {
            throw violation("evidence weight adjuster is required");
        }
        AtomicBoolean warningAttempted = new AtomicBoolean();
        AdaptorContext detachedContext = detachedContext(context, warningAttempted);
        List<Replacement> replacements = new ArrayList<>();
        if (relationships != null) {
            for (RelationshipCandidate candidate : relationships) {
                replacements.add(replacement(candidate, adjuster, detachedContext, warningAttempted));
            }
        }
        if (namingEvidence != null) {
            namingEvidence.adjustRawEvidence(
                    evidence -> adjusted(evidence, adjuster, detachedContext, warningAttempted));
        }
        replacements.forEach(Replacement::apply);
    }

    private Replacement replacement(
            RelationshipCandidate candidate,
            EvidenceWeightAdjuster adjuster,
            AdaptorContext context,
            AtomicBoolean warningAttempted
    ) {
        if (candidate == null) {
            throw violation("relationship candidate must not be null");
        }
        List<Evidence> observations = candidate.rawEvidence().isEmpty()
                ? candidate.evidence() : candidate.rawEvidence();
        List<Evidence> adjusted = new ArrayList<>(observations.size());
        for (Evidence evidence : observations) {
            adjusted.add(adjusted(evidence, adjuster, context, warningAttempted));
        }
        return new Replacement(observations, adjusted);
    }

    private Evidence adjusted(
            Evidence evidence,
            EvidenceWeightAdjuster adjuster,
            AdaptorContext context,
            AtomicBoolean warningAttempted
    ) {
        if (evidence == null) {
            throw violation("raw evidence must not be null");
        }
        Evidence baseline = detachedEvidence(evidence, "evidence weight input attributes");
        Evidence adjusted;
        try {
            adjusted = adjuster.adjust(baseline, context);
        } catch (AdaptorContractException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw violation("evidence weight adjuster failed");
        }
        if (warningAttempted.get()) {
            throw violation("evidence weight adjuster must not emit warnings");
        }
        if (adjusted == null) {
            throw violation("evidence weight adjuster must not return null");
        }
        Evidence detachedAdjusted = detachedEvidence(adjusted, "adjusted evidence attributes");
        if (detachedAdjusted.score().compareTo(MIN_SCORE) < 0
                || detachedAdjusted.score().compareTo(MAX_SCORE) > 0) {
            throw violation("adjusted evidence score must be within [-1, 1]");
        }
        if (detachedAdjusted.type() != baseline.type()
                || detachedAdjusted.sourceType() != baseline.sourceType()
                || !Objects.equals(detachedAdjusted.source(), baseline.source())
                || !Objects.equals(detachedAdjusted.detail(), baseline.detail())
                || !Objects.equals(detachedAdjusted.attributes(), baseline.attributes())) {
            throw violation("evidence weight adjuster may only change score");
        }
        return new Evidence(
                baseline.type(), detachedAdjusted.score(), baseline.sourceType(),
                baseline.source(), baseline.detail(), baseline.attributes());
    }

    private Evidence detachedEvidence(Evidence evidence, String boundary) {
        return new Evidence(
                evidence.type(), evidence.score(), evidence.sourceType(), evidence.source(), evidence.detail(),
                detachment.attributes(evidence.attributes(), boundary));
    }

    private AdaptorContext detachedContext(AdaptorContext context, AtomicBoolean warningAttempted) {
        return context == null
                ? new AdaptorContext(null, java.util.Map.of(), warning -> warningAttempted.set(true))
                : new AdaptorContext(
                        context.scope(), detachment.attributes(context.options(), "evidence weight options"),
                        warning -> warningAttempted.set(true));
    }

    private AdaptorContractException violation(String message) {
        return new AdaptorContractException("adaptor evidence-weight contract violation: " + message);
    }

    private record Replacement(List<Evidence> target, List<Evidence> values) {
        private void apply() {
            target.clear();
            target.addAll(values);
        }
    }
}
