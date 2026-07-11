package com.relationdetector.core.lineage;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.model.DataLineageEvidence;
import com.relationdetector.core.evidence.EvidenceObservationAggregator;
import com.relationdetector.core.evidence.EvidenceObservationAggregator.SummaryGroup;

/** Deduplicates field-level lineage without touching relationship confidence. */
public final class DataLineageMerger {
    private static final Set<String> SUMMARY_ATTRIBUTE_KEYS = Set.of(
            "occurrenceCount", "count", "firstDetail", "lastDetail", "sampleDetails", "sampleTruncated");

    private final EvidenceObservationAggregator<DataLineageEvidence> observations =
            new EvidenceObservationAggregator<>();
    private final LineageObservationPolicy observationPolicy = new LineageObservationPolicy();

    public List<DataLineageCandidate> merge(List<DataLineageCandidate> candidates) {
        Map<String, CandidateAccumulator> merged = new LinkedHashMap<>();
        for (DataLineageCandidate candidate : candidates) {
            merged.computeIfAbsent(key(candidate), ignored -> new CandidateAccumulator(candidate)).add(candidate);
        }
        return merged.values().stream().map(CandidateAccumulator::toCandidate).toList();
    }

    private String key(DataLineageCandidate candidate) {
        return candidate.flowKind() + "|" + candidate.transformType() + "|"
                + candidate.sources().stream().map(source -> source.normalizedKey())
                .collect(Collectors.joining(",")) + "|" + candidate.target().normalizedKey();
    }

    private DataLineageCandidate copyWithoutEvidence(DataLineageCandidate candidate) {
        DataLineageCandidate copy = new DataLineageCandidate(
                List.copyOf(candidate.sources()), candidate.target(),
                candidate.flowKind(), candidate.transformType());
        copy.confidence(candidate.confidence());
        return copy;
    }

    private List<DataLineageEvidence> rawObservations(DataLineageCandidate candidate) {
        return candidate.rawEvidence().isEmpty() ? candidate.evidence() : candidate.rawEvidence();
    }

    private DataLineageEvidence summary(SummaryGroup<DataLineageEvidence> group) {
        DataLineageEvidence first = group.first();
        Map<String, Object> attributes = new LinkedHashMap<>(group.consensusAttributes());
        attributes.put("count", group.count());
        if (group.count() > 1) {
            attributes.put("firstDetail", group.firstDetail());
            attributes.put("lastDetail", group.lastDetail());
            attributes.put("sampleDetails", group.sampleDetails());
            attributes.put("sampleTruncated", group.sampleTruncated());
        }
        return new DataLineageEvidence(
                first.transformType(), first.score(), first.sourceType(),
                first.source(), first.detail(), attributes);
    }

    private final class CandidateAccumulator {
        private final DataLineageCandidate candidate;
        private final List<DataLineageEvidence> raw = new ArrayList<>();
        private final Map<String, Object> consensusAttributes = new LinkedHashMap<>();
        private boolean first = true;

        CandidateAccumulator(DataLineageCandidate firstCandidate) {
            candidate = copyWithoutEvidence(firstCandidate);
        }

        void add(DataLineageCandidate incoming) {
            raw.addAll(rawObservations(incoming));
            candidate.warnings().addAll(incoming.warnings());
            if (first) {
                consensusAttributes.putAll(incoming.attributes());
                first = false;
            } else {
                EvidenceObservationAggregator.retainConsensusAttributes(
                        consensusAttributes, incoming.attributes());
            }
        }

        DataLineageCandidate toCandidate() {
            candidate.attributes().putAll(consensusAttributes);
            var aggregation = observations.aggregate(raw, observationPolicy, true);
            candidate.rawEvidence().addAll(aggregation.rawObservations());
            aggregation.groups().stream().map(DataLineageMerger.this::summary)
                    .forEach(candidate.evidence()::add);
            return candidate;
        }
    }

    private static final class LineageObservationPolicy
            implements EvidenceObservationAggregator.ObservationPolicy<DataLineageEvidence> {
        @Override
        public Object exactKey(DataLineageEvidence evidence) {
            return new ExactKey(
                    evidence.transformType(), evidence.score(), evidence.sourceType(),
                    evidence.source(), evidence.detail(), observationAttributes(evidence));
        }

        @Override
        public Object summaryKey(DataLineageEvidence evidence) {
            return new SummaryKey(
                    evidence.transformType(), evidence.sourceType(), evidence.source(), evidence.score());
        }

        @Override
        public int occurrenceCount(DataLineageEvidence evidence) {
            Object explicit = evidence.attributes().get("occurrenceCount");
            if (explicit instanceof Number number) {
                return Math.max(1, number.intValue());
            }
            Object grouped = evidence.attributes().get("count");
            return grouped instanceof Number number ? Math.max(1, number.intValue()) : 1;
        }

        @Override
        public Map<String, Object> observationAttributes(DataLineageEvidence evidence) {
            Map<String, Object> result = new LinkedHashMap<>();
            evidence.attributes().forEach((key, value) -> {
                if (!SUMMARY_ATTRIBUTE_KEYS.contains(key)) {
                    result.put(key, value);
                }
            });
            return result;
        }

        @Override
        public String detail(DataLineageEvidence evidence) {
            return evidence.detail();
        }

        @Override
        public DataLineageEvidence withOccurrenceCount(DataLineageEvidence evidence, int count) {
            Map<String, Object> attributes = observationAttributes(evidence);
            if (count > 1) {
                attributes.put("occurrenceCount", count);
            }
            return new DataLineageEvidence(
                    evidence.transformType(), evidence.score(), evidence.sourceType(),
                    evidence.source(), evidence.detail(), attributes);
        }

        private record ExactKey(
                LineageTransformType transform,
                BigDecimal score,
                EvidenceSourceType sourceType,
                String source,
                String detail,
                Map<String, Object> attributes
        ) {
        }

        private record SummaryKey(
                LineageTransformType transform,
                EvidenceSourceType sourceType,
                String source,
                BigDecimal score
        ) {
        }
    }
}
