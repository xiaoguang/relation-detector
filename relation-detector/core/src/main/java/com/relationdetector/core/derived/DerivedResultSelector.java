package com.relationdetector.core.derived;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.model.DerivedPathCandidate;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.NamingEvidenceCandidate;
import com.relationdetector.core.identity.CanonicalEndpointKeyProvider;

/**
 * CN: 对已形成的 relationship、lineage 和 naming derived facts 应用一个稳定的全局配额，并修复被合并或
 * 裁剪 naming fact 的可选引用；不生成路径，也不修改 direct facts。
 *
 * EN: Applies one stable global quota to completed relationship, lineage, and naming derived facts, repairing
 * optional references to merged or trimmed naming facts. It neither discovers paths nor changes direct facts.
 */
final class DerivedResultSelector {
    private final CanonicalEndpointKeyProvider endpointKeys;

    DerivedResultSelector(CanonicalEndpointKeyProvider endpointKeys) {
        this.endpointKeys = endpointKeys;
    }

    DerivedPathInferenceResult select(
            List<DerivedPathCandidate> relationships,
            List<DerivedPathCandidate> lineages,
            List<NamingEvidenceCandidate> naming,
            List<NamingEvidenceCandidate> directNaming,
            List<NamingEvidenceCandidate> namingBeforeMerge,
            int maxFacts
    ) {
        Map<String, String> namingAliases = namingAliases(namingBeforeMerge, naming);
        if (maxFacts == 0) {
            repairNamingReferences(relationships, directNaming, naming, namingAliases);
            return new DerivedPathInferenceResult(relationships, lineages, naming);
        }

        int remaining = maxFacts;
        List<DerivedPathCandidate> retainedRelationships = take(
                relationships.stream().sorted(pathComparator()).toList(), remaining);
        remaining -= retainedRelationships.size();
        List<DerivedPathCandidate> retainedLineages = take(
                lineages.stream().sorted(pathComparator()).toList(), remaining);
        remaining -= retainedLineages.size();
        List<NamingEvidenceCandidate> retainedNaming = take(
                naming.stream().sorted(namingComparator()).toList(), remaining);
        repairNamingReferences(retainedRelationships, directNaming, retainedNaming, namingAliases);
        return new DerivedPathInferenceResult(
                retainedRelationships, retainedLineages, retainedNaming);
    }

    private void repairNamingReferences(
            List<DerivedPathCandidate> relationships,
            List<NamingEvidenceCandidate> directNaming,
            List<NamingEvidenceCandidate> retainedNaming,
            Map<String, String> namingAliases
    ) {
        Set<String> validNamingRefs = new LinkedHashSet<>();
        directNaming.stream().map(NamingEvidenceCandidate::id).forEach(validNamingRefs::add);
        retainedNaming.stream().map(NamingEvidenceCandidate::id).forEach(validNamingRefs::add);
        relationships.forEach(candidate -> retainNamingReferences(
                candidate, validNamingRefs, namingAliases));
    }

    private Map<String, String> namingAliases(
            List<NamingEvidenceCandidate> candidates,
            List<NamingEvidenceCandidate> merged
    ) {
        Map<String, String> retainedByKey = new LinkedHashMap<>();
        merged.forEach(candidate -> retainedByKey.put(namingKey(candidate), candidate.id()));
        Map<String, String> aliases = new LinkedHashMap<>();
        candidates.forEach(candidate -> {
            String retained = retainedByKey.get(namingKey(candidate));
            if (retained != null) {
                aliases.put(candidate.id(), retained);
            }
        });
        return Map.copyOf(aliases);
    }

    private <T> List<T> take(List<T> values, int count) {
        if (count <= 0 || values.isEmpty()) {
            return List.of();
        }
        return List.copyOf(values.subList(0, Math.min(count, values.size())));
    }

    private Comparator<DerivedPathCandidate> pathComparator() {
        return Comparator
                .comparing((DerivedPathCandidate candidate) -> endpointKeys.factKey(candidate.source()))
                .thenComparing(candidate -> endpointKeys.factKey(candidate.target()))
                .thenComparing(candidate -> candidate.path().stream()
                        .map(endpointKeys::factKey)
                        .collect(Collectors.joining("->")));
    }

    private Comparator<NamingEvidenceCandidate> namingComparator() {
        return Comparator
                .comparing((NamingEvidenceCandidate candidate) -> endpointKeys.factKey(candidate.source()))
                .thenComparing(candidate -> endpointKeys.factKey(candidate.target()))
                .thenComparing(NamingEvidenceCandidate::rule);
    }

    private String namingKey(NamingEvidenceCandidate candidate) {
        return endpointKeys.factKey(candidate.source()) + "->"
                + endpointKeys.factKey(candidate.target()) + ":" + candidate.rule();
    }

    private void retainNamingReferences(
            DerivedPathCandidate candidate,
            Set<String> validNamingRefs,
            Map<String, String> namingAliases
    ) {
        retainNamingReferences(candidate.evidence(), validNamingRefs, namingAliases);
        retainNamingReferences(candidate.rawEvidence(), validNamingRefs, namingAliases);
    }

    private void retainNamingReferences(
            List<Evidence> evidence,
            Set<String> validNamingRefs,
            Map<String, String> namingAliases
    ) {
        for (int index = evidence.size() - 1; index >= 0; index--) {
            Evidence item = evidence.get(index);
            if (item.type() != EvidenceType.NAMING_MATCH) {
                continue;
            }
            String reference = String.valueOf(item.attributes().get("evidenceRef"));
            String retained = namingAliases.getOrDefault(reference, reference);
            if (!validNamingRefs.contains(retained)) {
                evidence.remove(index);
                continue;
            }
            if (!retained.equals(reference)) {
                Map<String, Object> attributes = new LinkedHashMap<>(item.attributes());
                attributes.put("evidenceRef", retained);
                evidence.set(index, new Evidence(
                        item.type(), item.score(), item.sourceType(),
                        item.source(), item.detail(), attributes));
            }
        }
    }
}
