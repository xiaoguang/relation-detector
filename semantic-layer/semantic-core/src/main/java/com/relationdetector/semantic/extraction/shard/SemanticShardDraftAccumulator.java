package com.relationdetector.semantic.extraction.shard;

import com.relationdetector.semantic.extraction.artifact.SemanticExternalAuditReferences;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * CN: 累积一个尚未发布的typed semantic shard草稿；输入是已闭合的root contribution，输出是完成
 * section去重、owner追踪与审计引用摘要的有界bundle。它不选择component、分配最终shard ID或写artifact。
 * EN: Accumulates one unpublished typed semantic shard draft from closed root contributions and produces a bounded
 * bundle with section de-duplication, owner tracking, and audit-reference summaries. It does not select components,
 * assign final shard IDs, or publish artifacts.
 */
public final class SemanticShardDraftAccumulator {
    static final List<String> FACT_SECTIONS = List.of(
            "metadataTables", "metadataColumns", "metadataConstraints", "metadataIndexes",
            "relationships", "lineage", "derivedRelationships", "derivedLineage",
            "namingEvidence", "diagnostics");
    static final List<String> CANDIDATE_SECTIONS = List.of(
            "eventCandidates", "reviewItemCandidates", "tripletCandidates");
    static final List<String> ITEM_SECTIONS = joinedSections();

    private final ObjectNode bundle;
    private final String component;
    private final Set<String> rootIds = new LinkedHashSet<>();
    private final Set<String> owners = new LinkedHashSet<>();
    private final Map<String, SemanticDiskBundleIndex.Item> rootSections = new LinkedHashMap<>();
    private final Map<String, Set<String>> idsBySection = new LinkedHashMap<>();
    private final Set<String> evidenceIds = new LinkedHashSet<>();
    private final Set<String> externalAuditIds = new LinkedHashSet<>();
    private final Set<String> tables = new LinkedHashSet<>();

    public SemanticShardDraftAccumulator(ObjectNode bundle, String component) {
        this.bundle = bundle;
        this.component = component;
        ITEM_SECTIONS.forEach(section -> idsBySection.put(section, new LinkedHashSet<>()));
    }

    public SemanticShardDraftAccumulator copy() {
        SemanticShardDraftAccumulator copy =
                new SemanticShardDraftAccumulator(bundle.deepCopy(), component);
        copy.rootIds.addAll(rootIds);
        copy.owners.addAll(owners);
        copy.rootSections.putAll(rootSections);
        idsBySection.forEach((section, ids) -> copy.idsBySection.get(section).addAll(ids));
        copy.evidenceIds.addAll(evidenceIds);
        copy.externalAuditIds.addAll(externalAuditIds);
        copy.tables.addAll(tables);
        return copy;
    }

    public void add(SemanticDiskBundleIndex.RootClosure root, String owner) {
        rootIds.add(root.rootId());
        owners.add(owner);
        rootSections.put(root.rootId(), new SemanticDiskBundleIndex.Item(
                root.rootId(), root.rootSection(), root.bundle()));
        externalAuditIds.addAll(root.externalAuditRefs());
        root.tables().stream().sorted().forEach(table -> {
            if (tables.add(table)) {
                bundle.withArray("tables").add(table);
            }
        });
        for (String section : ITEM_SECTIONS) {
            for (JsonNode item : root.bundle().path(section)) {
                String id = item.path("id").asText("");
                if (idsBySection.get(section).add(id)) {
                    bundle.withArray(section).add(item.deepCopy());
                }
            }
        }
        for (JsonNode evidence : root.bundle().path("evidence")) {
            String id = evidence.path("id").asText("");
            if (evidenceIds.add(id)) {
                bundle.withArray("evidence").add(evidence.deepCopy());
            }
        }
    }

    public ObjectNode probeBundle() {
        ObjectNode probe = bundle.deepCopy();
        ObjectNode context = probe.putObject("shardContext");
        context.put("shardId", "shard-probe");
        context.put("ownerKey", ownerKey());
        context.put("outputOwnedReferencesOnly", true);
        Set<String> facts = new LinkedHashSet<>();
        Set<String> candidates = new LinkedHashSet<>();
        FACT_SECTIONS.forEach(section -> facts.addAll(idsBySection.get(section)));
        CANDIDATE_SECTIONS.forEach(section -> candidates.addAll(idsBySection.get(section)));
        facts.stream().sorted().forEach(context.putArray("ownedFactRefs")::add);
        candidates.stream().sorted().forEach(context.putArray("ownedCandidateRefs")::add);
        context.putArray("overlapRefs");
        SemanticExternalAuditReferences.appendSummary(context, externalAuditRefs());
        return probe;
    }

    public ObjectNode bundle() {
        return bundle;
    }

    public String component() {
        return component;
    }

    public boolean isEmpty() {
        return rootIds.isEmpty();
    }

    public Set<String> rootIds() {
        return Set.copyOf(rootIds);
    }

    SemanticDiskBundleIndex.Item rootSection(String rootId) {
        return rootSections.get(rootId);
    }

    public Set<String> externalAuditRefs() {
        Set<String> result = new LinkedHashSet<>(externalAuditIds);
        idsBySection.values().forEach(result::removeAll);
        return Set.copyOf(result);
    }

    public String ownerKey() {
        if (owners.isEmpty()) {
            return "global";
        }
        if (owners.size() == 1) {
            return owners.iterator().next();
        }
        return "component:" + component;
    }

    private static List<String> joinedSections() {
        List<String> result = new ArrayList<>(FACT_SECTIONS);
        result.addAll(CANDIDATE_SECTIONS);
        return List.copyOf(result);
    }
}
