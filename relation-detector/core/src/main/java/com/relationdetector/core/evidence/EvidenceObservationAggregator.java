package com.relationdetector.core.evidence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * CN: 统一完全重复 observation 折叠与证据摘要分组，保留不同 SQL 位置的审计身份。
 * EN: Centralizes exact-observation folding and evidence summaries while preserving distinct SQL locations.
 */
public final class EvidenceObservationAggregator<O> {
    private static final int MAX_SAMPLE_DETAILS = 5;

    public Aggregation<O> aggregate(
            List<O> observations,
            ObservationPolicy<O> policy,
            boolean foldExact
    ) {
        List<O> raw = foldExact ? foldExact(observations, policy) : List.copyOf(observations);
        Map<Object, MutableGroup<O>> grouped = new LinkedHashMap<>();
        for (O observation : raw) {
            grouped.computeIfAbsent(
                    policy.summaryKey(observation),
                    ignored -> new MutableGroup<>(observation, policy))
                    .add(observation);
        }
        return new Aggregation<>(
                raw,
                grouped.values().stream().map(MutableGroup::freeze).toList());
    }

    public static void retainConsensusAttributes(
            Map<String, Object> consensus,
            Map<String, Object> candidate
    ) {
        consensus.entrySet().removeIf(entry ->
                !candidate.containsKey(entry.getKey())
                        || !Objects.deepEquals(entry.getValue(), candidate.get(entry.getKey())));
    }

    public static int occurrenceCount(Map<String, Object> attributes) {
        Object value = attributes == null ? null : attributes.get("occurrenceCount");
        if (value instanceof Number number && number.intValue() > 0) {
            return number.intValue();
        }
        return 1;
    }

    private List<O> foldExact(List<O> observations, ObservationPolicy<O> policy) {
        Map<Object, ExactGroup<O>> folded = new LinkedHashMap<>();
        for (O observation : observations) {
            folded.computeIfAbsent(
                    policy.exactKey(observation),
                    ignored -> new ExactGroup<>(observation))
                    .add(policy.occurrenceCount(observation));
        }
        return folded.values().stream()
                .map(group -> policy.withOccurrenceCount(group.first, group.count))
                .toList();
    }

    public interface ObservationPolicy<O> {
        Object exactKey(O observation);

        Object summaryKey(O observation);

        int occurrenceCount(O observation);

        Map<String, Object> observationAttributes(O observation);

        String detail(O observation);

        O withOccurrenceCount(O observation, int count);
    }

    public record Aggregation<O>(List<O> rawObservations, List<SummaryGroup<O>> groups) {
        public Aggregation {
            rawObservations = List.copyOf(rawObservations);
            groups = List.copyOf(groups);
        }
    }

    public record SummaryGroup<O>(
            O first,
            int count,
            int distinctObservationCount,
            String firstDetail,
            String lastDetail,
            List<String> sampleDetails,
            boolean sampleTruncated,
            Map<String, Object> consensusAttributes
    ) {
        public SummaryGroup {
            sampleDetails = List.copyOf(sampleDetails);
            consensusAttributes = Map.copyOf(consensusAttributes);
        }
    }

    private static final class ExactGroup<O> {
        private final O first;
        private int count;

        private ExactGroup(O first) {
            this.first = first;
        }

        private void add(int occurrences) {
            count += Math.max(1, occurrences);
        }
    }

    private static final class MutableGroup<O> {
        private final O first;
        private final ObservationPolicy<O> policy;
        private final List<String> sampleDetails = new ArrayList<>();
        private final Map<String, Object> consensusAttributes = new LinkedHashMap<>();
        private int count;
        private int distinctObservationCount;
        private String lastDetail;
        private boolean firstObservation = true;

        private MutableGroup(O first, ObservationPolicy<O> policy) {
            this.first = first;
            this.policy = policy;
        }

        private void add(O observation) {
            Map<String, Object> attributes = policy.observationAttributes(observation);
            int occurrences = policy.occurrenceCount(observation);
            if (firstObservation) {
                consensusAttributes.putAll(attributes);
                firstObservation = false;
            } else {
                retainConsensusAttributes(consensusAttributes, attributes);
            }
            count += occurrences;
            distinctObservationCount++;
            lastDetail = policy.detail(observation);
            if (sampleDetails.size() < MAX_SAMPLE_DETAILS) {
                sampleDetails.add(policy.detail(observation));
            }
        }

        private SummaryGroup<O> freeze() {
            return new SummaryGroup<>(
                    first,
                    count,
                    distinctObservationCount,
                    policy.detail(first),
                    lastDetail,
                    sampleDetails,
                    count > sampleDetails.size(),
                    consensusAttributes);
        }

    }
}
