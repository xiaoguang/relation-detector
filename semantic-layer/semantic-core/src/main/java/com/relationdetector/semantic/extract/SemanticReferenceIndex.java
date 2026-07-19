package com.relationdetector.semantic.extract;

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
