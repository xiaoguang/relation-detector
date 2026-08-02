package com.relationdetector.semantic.extraction.normalization;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * CN: 从 semantic evidence bundle 的事实、证据和候选 section 建立全局稳定引用索引，供 normalizer 校验
 * evidenceRefs 及 candidateRef 的类型。输入是 reader 已验证的 bundle，输出是不可变引用和候选类别查询；
 * 本类不修复缺失 ID、不按内容猜引用，也不拥有物理 endpoint 校验。
 *
 * EN: Builds a global stable-reference index over fact, evidence, and candidate sections in a semantic evidence
 * bundle so normalizers can validate evidence references and candidate-reference kinds. Its input is a reader-validated
 * bundle and its output is immutable reference membership; it does not repair missing IDs, infer references from
 * content, or own physical endpoint validation.
 */
public final class SemanticReferenceIndex {
    private static final Map<String, String> SECTIONS = Map.ofEntries(
            Map.entry("evidence", "evidence"),
            Map.entry("metadataTables", "fact"),
            Map.entry("metadataColumns", "fact"),
            Map.entry("metadataConstraints", "fact"),
            Map.entry("metadataIndexes", "fact"),
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
    private final Map<String, Set<String>> referencesBySection;

    private SemanticReferenceIndex(
            Set<String> references,
            Map<String, String> candidateKinds,
            Map<String, Set<String>> referencesBySection
    ) {
        this.references = Set.copyOf(references);
        this.candidateKinds = Map.copyOf(candidateKinds);
        Map<String, Set<String>> detached = new LinkedHashMap<>();
        referencesBySection.forEach((section, values) -> detached.put(section, Set.copyOf(values)));
        this.referencesBySection = Map.copyOf(detached);
    }

    public static SemanticReferenceIndex from(JsonNode bundle) {
        if (bundle == null || !bundle.isObject()) {
            throw new SemanticExtractionValidationException(
                    "semantic evidence bundle is required");
        }
        Set<String> references = new LinkedHashSet<>();
        Map<String, String> candidateKinds = new LinkedHashMap<>();
        Map<String, Set<String>> referencesBySection = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : SECTIONS.entrySet()) {
            JsonNode section = bundle.path(entry.getKey());
            if (!section.isArray()) {
                throw new SemanticExtractionValidationException(
                        "semantic evidence bundle section must be an array: " + entry.getKey());
            }
            for (JsonNode item : section) {
                String id = item.path("id").asText("");
                if (id.isBlank()) {
                    throw new SemanticExtractionValidationException(
                            "semantic evidence bundle id is required in " + entry.getKey());
                }
                if (!references.add(id)) {
                    throw new SemanticExtractionValidationException(
                            "duplicate semantic evidence bundle id: " + id);
                }
                referencesBySection.computeIfAbsent(entry.getKey(), ignored -> new LinkedHashSet<>()).add(id);
                if (entry.getValue().endsWith("Candidate")) {
                    candidateKinds.put(id, entry.getValue());
                }
            }
        }
        return new SemanticReferenceIndex(references, candidateKinds, referencesBySection);
    }

    public boolean contains(String reference) {
        return reference != null && references.contains(reference);
    }

    public boolean isCandidate(String reference, String expectedKind) {
        return expectedKind.equals(candidateKinds.get(reference));
    }

    public boolean contains(String section, String reference) {
        return reference != null
                && referencesBySection.getOrDefault(section, Set.of()).contains(reference);
    }
}
