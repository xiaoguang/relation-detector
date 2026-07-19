package com.relationdetector.semantic.graph;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * CN: 从已构建的 EvidenceGraph 收集 fact 与 evidence ID，形成 KG 构建阶段使用的唯一不可变 lookup。
 * semantic owner validator 是上游调用方，KG assembler 消费其闭合校验结果；未解析引用明确失败，本类不读取
 * 原始 ScanResult、不按名称补全，也不创建缺失节点。
 * EN: Collects fact and evidence IDs from an assembled EvidenceGraph into the single immutable lookup used during
 * KG construction. Semantic owner validation supplies requests and the KG assembler consumes the closure result;
 * unresolved references fail, while this index never reads raw ScanResult data, completes names, or creates nodes.
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

    public void requireEvidence(String ownerId, List<String> refs) {
        if (refs == null || refs.isEmpty()) {
            throw new IllegalArgumentException("semantic owner " + ownerId + " requires evidence");
        }
        requireResolvable(ownerId, refs);
    }
}
