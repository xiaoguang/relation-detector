package com.relationdetector.core.relation;

import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.NamingEvidenceCandidate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Merges name-only evidence into one output row per source-target-rule while
 * preserving every concrete observation for audit.
 *
 * <p>CN: naming evidence 是证据池，不是最终 relationship。这里按
 * source-target-rule 去重输出，但是 rawEvidence 仍保存每一次出现的位置和细节。
 */
public final class NamingEvidenceMerger {
    public List<NamingEvidenceCandidate> merge(List<NamingEvidenceCandidate> candidates) {
        Map<String, Accumulator> grouped = new LinkedHashMap<>();
        for (NamingEvidenceCandidate candidate : candidates) {
            grouped.computeIfAbsent(key(candidate), ignored -> new Accumulator(candidate)).add(candidate);
        }
        List<NamingEvidenceCandidate> output = grouped.values().stream()
                .map(Accumulator::toCandidate)
                .sorted(Comparator
                        .comparing((NamingEvidenceCandidate candidate) -> candidate.source().displayName())
                        .thenComparing(candidate -> candidate.target().displayName())
                        .thenComparing(NamingEvidenceCandidate::rule))
                .toList();
        return output;
    }

    private String key(NamingEvidenceCandidate candidate) {
        return candidate.source().normalizedKey() + "->"
                + candidate.target().normalizedKey() + ":"
                + candidate.rule();
    }

    private static final class Accumulator {
        private static final int MAX_SAMPLE_DETAILS = 5;

        private final NamingEvidenceCandidate first;
        private final List<Evidence> rawEvidence = new ArrayList<>();
        private final List<String> sampleDetails = new ArrayList<>();
        private int count;
        private String lastDetail;
        private boolean directionHint;

        Accumulator(NamingEvidenceCandidate first) {
            this.first = first;
        }

        void add(NamingEvidenceCandidate candidate) {
            directionHint = directionHint || candidate.directionHint();
            List<Evidence> observations = candidate.rawEvidence().isEmpty()
                    ? List.of(candidate.evidence())
                    : candidate.rawEvidence();
            for (Evidence evidence : observations) {
                rawEvidence.add(evidence);
                count++;
                lastDetail = evidence.detail();
                if (sampleDetails.size() < MAX_SAMPLE_DETAILS) {
                    sampleDetails.add(evidence.detail());
                }
            }
        }

        NamingEvidenceCandidate toCandidate() {
            Evidence summary = summaryEvidence();
            return new NamingEvidenceCandidate(
                    first.source(),
                    first.target(),
                    summary,
                    first.rule(),
                    directionHint,
                    rawEvidence);
        }

        private Evidence summaryEvidence() {
            Evidence firstEvidence = first.evidence();
            Map<String, Object> attributes = new LinkedHashMap<>(firstEvidence.attributes());
            attributes.put("count", count);
            if (count > 1) {
                attributes.put("firstDetail", rawEvidence.get(0).detail());
                attributes.put("lastDetail", lastDetail);
                attributes.put("sampleDetails", List.copyOf(sampleDetails));
                attributes.put("sampleTruncated", count > sampleDetails.size());
            }
            return new Evidence(
                    firstEvidence.type(),
                    firstEvidence.score(),
                    firstEvidence.sourceType(),
                    firstEvidence.source(),
                    firstEvidence.detail(),
                    attributes);
        }
    }
}
