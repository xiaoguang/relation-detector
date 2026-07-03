package com.relationdetector.core.lineage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.relationdetector.contracts.model.DataLineageEvidence;
import com.relationdetector.contracts.model.DataLineageCandidate;

/** Deduplicates field-level lineage without touching relationship confidence. */
public final class DataLineageMerger {
    public List<DataLineageCandidate> merge(List<DataLineageCandidate> candidates) {
        Map<String, DataLineageCandidate> merged = new LinkedHashMap<>();
        for (DataLineageCandidate candidate : candidates) {
            String key = key(candidate);
            DataLineageCandidate existing = merged.get(key);
            if (existing == null) {
                existing = copyWithoutEvidence(candidate);
                merged.put(key, existing);
            }
            existing.evidence().addAll(observations(candidate));
            existing.warnings().addAll(candidate.warnings());
            existing.attributes().putAll(candidate.attributes());
        }
        for (DataLineageCandidate candidate : merged.values()) {
            summarizeRepeatedEvidence(candidate);
        }
        return new ArrayList<>(merged.values());
    }

    private DataLineageCandidate copyWithoutEvidence(DataLineageCandidate candidate) {
        DataLineageCandidate copy = new DataLineageCandidate(
                List.copyOf(candidate.sources()),
                candidate.target(),
                candidate.flowKind(),
                candidate.transformType());
        copy.confidence(candidate.confidence());
        return copy;
    }

    private List<DataLineageEvidence> observations(DataLineageCandidate candidate) {
        return candidate.rawEvidence().isEmpty() ? candidate.evidence() : candidate.rawEvidence();
    }

    private void summarizeRepeatedEvidence(DataLineageCandidate candidate) {
        Map<String, EvidenceAccumulator> grouped = new LinkedHashMap<>();
        for (DataLineageEvidence evidence : candidate.evidence()) {
            grouped.computeIfAbsent(evidenceKey(evidence), ignored -> new EvidenceAccumulator(evidence))
                    .add(evidence);
        }
        candidate.rawEvidence().clear();
        candidate.rawEvidence().addAll(candidate.evidence());
        candidate.evidence().clear();
        for (EvidenceAccumulator accumulator : grouped.values()) {
            candidate.evidence().add(accumulator.toEvidence());
        }
    }

    private String evidenceKey(DataLineageEvidence evidence) {
        return evidence.transformType() + "|"
                + evidence.sourceType() + "|"
                + evidence.source() + "|"
                + evidence.score();
    }

    private String key(DataLineageCandidate candidate) {
        return candidate.flowKind() + "|"
                + candidate.transformType() + "|"
                + candidate.sources().stream()
                        .map(source -> source.normalizedKey())
                        .collect(Collectors.joining(",")) + "|"
                + candidate.target().normalizedKey();
    }

    private static final class EvidenceAccumulator {
        private static final int MAX_SAMPLE_DETAILS = 5;
        private final DataLineageEvidence first;
        private final List<String> sampleDetails = new ArrayList<>();
        private int count;
        private String lastDetail;

        EvidenceAccumulator(DataLineageEvidence first) {
            this.first = first;
        }

        void add(DataLineageEvidence evidence) {
            count++;
            lastDetail = evidence.detail();
            if (sampleDetails.size() < MAX_SAMPLE_DETAILS) {
                sampleDetails.add(evidence.detail());
            }
        }

        DataLineageEvidence toEvidence() {
            Map<String, Object> attributes = new LinkedHashMap<>(first.attributes());
            attributes.put("count", count);
            if (count > 1) {
                attributes.put("firstDetail", first.detail());
                attributes.put("lastDetail", lastDetail);
                attributes.put("sampleDetails", List.copyOf(sampleDetails));
                attributes.put("sampleTruncated", count > sampleDetails.size());
            }
            return new DataLineageEvidence(
                    first.transformType(),
                    first.score(),
                    first.sourceType(),
                    first.source(),
                    first.detail(),
                    attributes);
        }
    }
}
