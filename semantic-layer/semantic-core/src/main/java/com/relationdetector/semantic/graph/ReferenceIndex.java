package com.relationdetector.semantic.graph;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * CN: 从 EvidenceGraph 建立唯一不可变 id lookup，验证 semantic owners 的 fact/evidence refs 是否闭合；未解析引用明确失败，不按名称补全。
 * EN: Builds one immutable id lookup from an EvidenceGraph to validate semantic fact and evidence-reference closure. Unresolved references fail without name-based completion.
 */
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
