package com.relationdetector.semantic.extraction.normalization;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.relationdetector.semantic.event.SemanticEventCandidate;

/**
 * CN: 承载evidence bundle中event、triplet与review候选的有界transport window；输入由slice reader反序列化，
 * 输出供normalizer完成typed reference校验。上游是evidence slice，下游是normalization；本类不拥有完整bundle、
 * 不生成候选，也不放宽未知字段之外的证据闭包。
 *
 * <p>EN: Bounded transport-window model for event, triplet, and review candidates from an evidence bundle. A slice
 * reader supplies it and normalization consumes it for typed-reference checks. It does not own a complete bundle,
 * generate candidates, or weaken evidence closure beyond ignoring transport extensions.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class SemanticCandidateBundle {
    public List<SemanticEventCandidate> eventCandidates;
    public List<TripletCandidate> tripletCandidates;
    public List<ReviewItemCandidate> reviewItemCandidates;

    public void ensureSections() {
        eventCandidates = mutable(eventCandidates);
        tripletCandidates = mutable(tripletCandidates);
        reviewItemCandidates = mutable(reviewItemCandidates);
    }

    private static <T> List<T> mutable(List<T> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TripletCandidate(
            String id,
            String type,
            String subject,
            String predicate,
            String object,
            String readable,
            List<String> evidenceRefs
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReviewItemCandidate(
            String id,
            String targetRef,
            String targetSection,
            String type,
            String severity,
            String reason,
            List<String> evidenceRefs
    ) {
    }
}
