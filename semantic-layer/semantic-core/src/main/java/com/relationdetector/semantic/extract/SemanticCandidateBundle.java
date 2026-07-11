package com.relationdetector.semantic.extract;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.relationdetector.semantic.event.SemanticEventCandidate;

@JsonIgnoreProperties(ignoreUnknown = true)
final class SemanticCandidateBundle {
    public List<SemanticEventCandidate> eventCandidates;
    public List<TripletCandidate> tripletCandidates;
    public List<ReviewItemCandidate> reviewItemCandidates;

    void ensureSections() {
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
