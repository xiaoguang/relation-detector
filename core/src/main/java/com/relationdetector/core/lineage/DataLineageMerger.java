package com.relationdetector.core.lineage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.relationdetector.contracts.model.DataLineageCandidate;

/** Deduplicates field-level lineage without touching relationship confidence. */
public final class DataLineageMerger {
    public List<DataLineageCandidate> merge(List<DataLineageCandidate> candidates) {
        Map<String, DataLineageCandidate> merged = new LinkedHashMap<>();
        for (DataLineageCandidate candidate : candidates) {
            String key = key(candidate);
            DataLineageCandidate existing = merged.get(key);
            if (existing == null) {
                merged.put(key, candidate);
                continue;
            }
            existing.evidence().addAll(candidate.evidence());
            existing.warnings().addAll(candidate.warnings());
            existing.attributes().putAll(candidate.attributes());
        }
        return new ArrayList<>(merged.values());
    }

    private String key(DataLineageCandidate candidate) {
        return candidate.flowKind() + "|"
                + candidate.transformType() + "|"
                + candidate.sources().stream()
                        .map(source -> source.normalizedKey())
                        .collect(Collectors.joining(",")) + "|"
                + candidate.target().normalizedKey();
    }
}
