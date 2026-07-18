package com.relationdetector.semantic.extract;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

/** Indexes every stable fact, evidence, and candidate id exposed to semantic extraction. */
final class SemanticReferenceIndex {
    private static final Map<String, String> SECTIONS = Map.ofEntries(
            Map.entry("evidence", "evidence"),
            Map.entry("relationships", "fact"),
            Map.entry("lineage", "fact"),
            Map.entry("derivedRelationships", "fact"),
            Map.entry("derivedLineage", "fact"),
            Map.entry("namingEvidence", "fact"),
            Map.entry("diagnostics", "fact"),
            Map.entry("eventCandidates", "eventCandidate"),
            Map.entry("tripletCandidates", "tripletCandidate"),
            Map.entry("reviewItemCandidates", "reviewItemCandidate"));

    private final Set<String> references;
    private final Map<String, String> candidateKinds;

    private SemanticReferenceIndex(Set<String> references, Map<String, String> candidateKinds) {
        this.references = Set.copyOf(references);
        this.candidateKinds = Map.copyOf(candidateKinds);
    }

    static SemanticReferenceIndex from(JsonNode bundle) {
        if (bundle == null || !bundle.isObject()) {
            throw new IllegalArgumentException("semantic evidence bundle is required");
        }
        Set<String> references = new LinkedHashSet<>();
        Map<String, String> candidateKinds = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : SECTIONS.entrySet()) {
            JsonNode section = bundle.path(entry.getKey());
            if (!section.isArray()) {
                throw new IllegalArgumentException("semantic evidence bundle section must be an array: " + entry.getKey());
            }
            for (JsonNode item : section) {
                String id = item.path("id").asText("");
                if (id.isBlank()) {
                    throw new IllegalArgumentException("semantic evidence bundle id is required in " + entry.getKey());
                }
                if (!references.add(id)) {
                    throw new IllegalArgumentException("duplicate semantic evidence bundle id: " + id);
                }
                if (entry.getValue().endsWith("Candidate")) {
                    candidateKinds.put(id, entry.getValue());
                }
            }
        }
        return new SemanticReferenceIndex(references, candidateKinds);
    }

    boolean contains(String reference) {
        return reference != null && references.contains(reference);
    }

    boolean isCandidate(String reference, String expectedKind) {
        return expectedKind.equals(candidateKinds.get(reference));
    }
}
