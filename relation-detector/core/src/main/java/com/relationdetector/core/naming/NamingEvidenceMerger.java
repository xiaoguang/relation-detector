package com.relationdetector.core.naming;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.NamingEvidenceCandidate;
import com.relationdetector.core.evidence.EvidenceObservationAggregator;
import com.relationdetector.core.evidence.EvidenceObservationAggregator.SummaryGroup;
import com.relationdetector.core.identity.CanonicalEndpointKeyProvider;

/**
 *
 * Merges one naming fact per source-target-rule while preserving observations.
 */
public final class NamingEvidenceMerger {
    private final CanonicalEndpointKeyProvider endpointKeys;
    private final EvidenceObservationAggregator<Evidence> observations =
            new EvidenceObservationAggregator<>();
    private final NamingObservationPolicy policy = new NamingObservationPolicy();

    public NamingEvidenceMerger() {
        this(CanonicalEndpointKeyProvider.defaults());
    }

    public NamingEvidenceMerger(CanonicalEndpointKeyProvider endpointKeys) {
        this.endpointKeys = java.util.Objects.requireNonNull(endpointKeys, "endpointKeys");
    }

    public List<NamingEvidenceCandidate> merge(List<NamingEvidenceCandidate> candidates) {
        Map<String, Accumulator> grouped = new LinkedHashMap<>();
        for (NamingEvidenceCandidate candidate : candidates) {
            grouped.computeIfAbsent(key(candidate), ignored -> new Accumulator(candidate)).add(candidate);
        }
        return grouped.values().stream()
                .map(Accumulator::toCandidate)
                .sorted(Comparator
                        .comparing((NamingEvidenceCandidate candidate) -> candidate.source().displayName())
                        .thenComparing(candidate -> candidate.target().displayName())
                        .thenComparing(NamingEvidenceCandidate::rule))
                .toList();
    }

    private String key(NamingEvidenceCandidate candidate) {
        return endpointKeys.factKey(candidate.source()) + "->"
                + endpointKeys.factKey(candidate.target()) + ":" + candidate.rule();
    }

    private final class Accumulator {
        private final NamingEvidenceCandidate first;
        private final List<Evidence> raw = new ArrayList<>();
        private boolean directionHint;

        Accumulator(NamingEvidenceCandidate first) {
            this.first = first;
        }

        void add(NamingEvidenceCandidate candidate) {
            directionHint = directionHint || candidate.directionHint();
            List<Evidence> incoming = candidate.rawEvidence().isEmpty()
                    ? List.of(candidate.evidence()) : candidate.rawEvidence();
            raw.addAll(incoming);
        }

        NamingEvidenceCandidate toCandidate() {
            var aggregation = observations.aggregate(raw, policy, true);
            return new NamingEvidenceCandidate(
                    first.source(), first.target(), summaryEvidence(aggregation.groups().get(0)),
                    first.rule(), directionHint,
                    aggregation.rawObservations());
        }

        private Evidence summaryEvidence(SummaryGroup<Evidence> group) {
            Evidence firstEvidence = group.first();
            Map<String, Object> attributes = new LinkedHashMap<>(group.consensusAttributes());
            summarizeConditional(attributes);
            attributes.put("count", group.count());
            if (group.count() > 1) {
                attributes.put("firstDetail", group.firstDetail());
                attributes.put("lastDetail", group.lastDetail());
                attributes.put("sampleDetails", group.sampleDetails());
                attributes.put("sampleTruncated", group.sampleTruncated());
            }
            return new Evidence(
                    firstEvidence.type(), firstEvidence.score(), firstEvidence.sourceType(),
                    firstEvidence.source(), firstEvidence.detail(), attributes);
        }

        private void summarizeConditional(Map<String, Object> attributes) {
            boolean allConditional = !raw.isEmpty() && raw.stream()
                    .allMatch(evidence -> Boolean.TRUE.equals(evidence.attributes().get("conditional")));
            if (!allConditional) {
                attributes.remove("conditional");
                attributes.remove("conditions");
                return;
            }
            List<Map<String, Object>> conditions = raw.stream()
                    .flatMap(evidence -> conditionMaps(evidence).stream())
                    .distinct()
                    .sorted(Comparator.comparing((Map<String, Object> condition) ->
                                    String.valueOf(condition.get("discriminator")))
                            .thenComparing(condition -> String.valueOf(condition.get("operator")))
                            .thenComparing(condition -> String.valueOf(condition.get("value"))))
                    .toList();
            attributes.put("conditional", true);
            if (!conditions.isEmpty()) {
                attributes.put("conditions", conditions);
            }
        }

        private List<Map<String, Object>> conditionMaps(Evidence evidence) {
            Object value = evidence.attributes().get("conditions");
            if (!(value instanceof List<?> list)) {
                return List.of();
            }
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> condition = new LinkedHashMap<>();
                    map.forEach((key, entry) -> condition.put(String.valueOf(key), entry));
                    result.add(Map.copyOf(condition));
                }
            }
            return List.copyOf(result);
        }
    }

    private static final class NamingObservationPolicy
            implements EvidenceObservationAggregator.ObservationPolicy<Evidence> {
        @Override
        public Object exactKey(Evidence evidence) {
            return new ExactKey(
                    evidence.type(), evidence.sourceType(), evidence.source(), evidence.detail(),
                    observationAttributes(evidence));
        }

        @Override
        public Object summaryKey(Evidence evidence) {
            return evidence.type();
        }

        @Override
        public int occurrenceCount(Evidence evidence) {
            return EvidenceObservationAggregator.occurrenceCount(evidence.attributes());
        }

        @Override
        public Map<String, Object> observationAttributes(Evidence evidence) {
            Map<String, Object> attributes = new LinkedHashMap<>();
            evidence.attributes().forEach((key, value) -> {
                if (!"occurrenceCount".equals(key)) {
                    attributes.put(key, value);
                }
            });
            return attributes;
        }

        @Override
        public String detail(Evidence evidence) {
            return evidence.detail();
        }

        @Override
        public Evidence withOccurrenceCount(Evidence evidence, int count) {
            Map<String, Object> attributes = observationAttributes(evidence);
            if (count > 1) {
                attributes.put("occurrenceCount", count);
            }
            return new Evidence(
                    evidence.type(), evidence.score(), evidence.sourceType(),
                    evidence.source(), evidence.detail(), attributes);
        }

        private record ExactKey(
                com.relationdetector.contracts.Enums.EvidenceType type,
                com.relationdetector.contracts.Enums.EvidenceSourceType sourceType,
                String source,
                String detail,
                Map<String, Object> attributes
        ) {
        }
    }
}
