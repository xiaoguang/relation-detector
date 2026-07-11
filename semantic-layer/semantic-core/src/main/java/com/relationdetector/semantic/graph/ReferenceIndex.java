package com.relationdetector.semantic.graph;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Single immutable lookup used to validate semantic fact and evidence references. */
public final class ReferenceIndex {
    private final Set<String> resolvableIds;

    private ReferenceIndex(Set<String> resolvableIds) {
        this.resolvableIds = Set.copyOf(resolvableIds);
    }

    public static ReferenceIndex from(EvidenceGraph graph) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        graph.facts().forEach(fact -> ids.add(fact.id()));
        graph.evidenceRefs().forEach(evidence -> ids.add(evidence.id()));
        return new ReferenceIndex(ids);
    }

    public boolean resolves(String id) {
        return id != null && !id.isBlank() && resolvableIds.contains(id);
    }

    public void requireResolvable(String ownerId, List<String> refs) {
        List<String> unresolved = refs.stream().filter(ref -> !resolves(ref)).distinct().toList();
        if (!unresolved.isEmpty()) {
            throw new IllegalArgumentException(
                    "semantic fact " + ownerId + " has unresolved evidence refs: " + unresolved);
        }
    }
}
