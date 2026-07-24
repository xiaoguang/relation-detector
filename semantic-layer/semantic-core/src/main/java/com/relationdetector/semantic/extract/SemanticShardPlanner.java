package com.relationdetector.semantic.extract;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.StableSemanticId;

/**
 * CN: 按物理 table connected component 和 evidence closure 规划稳定 shard；超预算 table owner 可按稳定 root
 * 拆成子片，但单个 root 及其 typed closure 不可切分。输入完整 bundle 与预算，输出唯一 owner plan，禁止数组
 * 截断或重写 fact/candidate ID。
 * EN: Plans stable shards from physical-table connected components and evidence closure. An oversized table owner may
 * split by stable roots, while each root and its typed closure remains indivisible. It consumes a complete bundle plus
 * budgets and never truncates arrays or rewrites fact/candidate IDs.
 */
public final class SemanticShardPlanner {
    private final SemanticPromptBudgetEstimator estimator = new SemanticPromptBudgetEstimator();
    private final SemanticExtractionPromptBuilder promptBuilder = new SemanticExtractionPromptBuilder();

    public SemanticShardPlan plan(ObjectNode fullBundle, SemanticShardingOptions options) {
        SemanticShardingOptions resolved = options == null ? SemanticShardingOptions.defaults() : options;
        SemanticShardBundleIndex index = new SemanticShardBundleIndex(fullBundle);
        Set<String> fullFacts = ids(index, SemanticShardBundleIndex.FACT_SECTIONS);
        Set<String> fullCandidates = ids(index, SemanticShardBundleIndex.CANDIDATE_SECTIONS);
        if (resolved.mode() != SemanticShardMode.FORCE) {
            ObjectNode completeBundle = withShardContext(
                    fullBundle, "shard-0001", "full", fullFacts, fullCandidates, Set.of());
            int fullEstimate = estimate(completeBundle);
            if (resolved.mode() == SemanticShardMode.OFF) {
                if (fullEstimate > resolved.maxInputTokens()) {
                    throw new SemanticShardingException(
                            "semantic prompt exceeds maximum input budget while sharding is off");
                }
                return completePlan(index, completeBundle, fullEstimate);
            }
            if (fullEstimate <= resolved.targetInputTokens()) {
                return completePlan(index, completeBundle, fullEstimate);
            }
        }

        List<Set<String>> tableGroups = connectedComponents(index);
        List<ObjectNode> bundles = new ArrayList<>();
        List<String> ownerKeys = new ArrayList<>();
        boolean assignedUntabled = false;
        for (Set<String> component : tableGroups) {
            ObjectNode componentBundle = shardBundle(index, component, !assignedUntabled);
            int estimate = estimate(componentBundle);
            if (estimate <= resolved.targetInputTokens()) {
                bundles.add(componentBundle);
                ownerKeys.add(String.join("|", component));
                assignedUntabled = true;
                continue;
            }
            boolean firstAtomic = true;
            for (String table : component.stream().sorted().toList()) {
                boolean includeUntabled = !assignedUntabled && firstAtomic;
                ObjectNode atomic = shardBundle(index, Set.of(table), includeUntabled);
                List<ObjectNode> atomicBundles;
                if (estimateWithOwnershipContext(atomic, table) > resolved.maxInputTokens()) {
                    atomicBundles = splitTableOwner(
                            index,
                            table,
                            includeUntabled,
                            resolved.targetInputTokens(),
                            resolved.maxInputTokens());
                } else {
                    atomicBundles = List.of(atomic);
                }
                for (int part = 0; part < atomicBundles.size(); part++) {
                    bundles.add(atomicBundles.get(part));
                    ownerKeys.add(atomicBundles.size() == 1
                            ? table
                            : table + "#part-%04d".formatted(part + 1));
                }
                firstAtomic = false;
            }
            assignedUntabled = true;
        }
        if (bundles.isEmpty()) {
            bundles.add(shardBundle(index, Set.of(), true));
            ownerKeys.add("global");
        }
        if (resolved.mode() == SemanticShardMode.AUTO) {
            PackedBundles packed = packAutoBundles(
                    index, bundles, ownerKeys, resolved.targetInputTokens());
            bundles = packed.bundles();
            ownerKeys = packed.ownerKeys();
        }
        if (bundles.size() > resolved.maxShardCount()) {
            throw new SemanticShardingException("semantic shard count exceeds configured maximum");
        }
        SemanticShardPlan plan = assemblePlan(index, bundles, ownerKeys, resolved.maxInputTokens());
        new SemanticShardCoverageValidator().validate(fullBundle, plan);
        return plan;
    }

    private SemanticShardPlan completePlan(SemanticShardBundleIndex index, ObjectNode fullBundle, int estimate) {
        Set<String> facts = ids(index, SemanticShardBundleIndex.FACT_SECTIONS);
        Set<String> candidates = ids(index, SemanticShardBundleIndex.CANDIDATE_SECTIONS);
        SemanticShard shard = new SemanticShard("shard-0001", "full", fullBundle, facts, candidates, Set.of(), estimate);
        Map<String, String> factOwners = owners(facts, shard.id());
        Map<String, String> candidateOwners = owners(candidates, shard.id());
        SemanticShardPlan plan = new SemanticShardPlan(
                hash(index.bundle()), List.of(shard), factOwners, candidateOwners);
        new SemanticShardCoverageValidator().validate(fullBundle, plan);
        return plan;
    }

    private SemanticShardPlan assemblePlan(
            SemanticShardBundleIndex index,
            List<ObjectNode> bundles,
            List<String> ownerKeys,
            int maxInputTokens
    ) {
        Map<String, String> factOwners = new LinkedHashMap<>();
        Map<String, String> candidateOwners = new LinkedHashMap<>();
        List<SemanticShard> draft = new ArrayList<>();
        for (int position = 0; position < bundles.size(); position++) {
            String id = "shard-%04d".formatted(position + 1);
            ObjectNode bundle = bundles.get(position);
            Set<String> facts = ids(bundle, SemanticShardBundleIndex.FACT_SECTIONS);
            Set<String> candidates = ids(bundle, SemanticShardBundleIndex.CANDIDATE_SECTIONS);
            Set<String> ownedFacts = new LinkedHashSet<>();
            Set<String> ownedCandidates = new LinkedHashSet<>();
            Set<String> overlap = new LinkedHashSet<>();
            facts.forEach(ref -> {
                if (factOwners.putIfAbsent(ref, id) == null) ownedFacts.add(ref);
                else overlap.add(ref);
            });
            candidates.forEach(ref -> {
                if (candidateOwners.putIfAbsent(ref, id) == null) ownedCandidates.add(ref);
                else overlap.add(ref);
            });
            ObjectNode ownedBundle = withShardContext(
                    bundle, id, ownerKeys.get(position), ownedFacts, ownedCandidates, overlap);
            int finalEstimate = estimate(ownedBundle);
            if (finalEstimate > maxInputTokens) {
                throw new SemanticShardingException(
                        "semantic shard exceeds maximum input token budget after ownership context");
            }
            draft.add(new SemanticShard(id, ownerKeys.get(position), ownedBundle,
                    ownedFacts, ownedCandidates, overlap, finalEstimate));
        }
        return new SemanticShardPlan(hash(index.bundle()), draft, factOwners, candidateOwners);
    }

    private ObjectNode withShardContext(
            ObjectNode source,
            String shardId,
            String ownerKey,
            Set<String> ownedFacts,
            Set<String> ownedCandidates,
            Set<String> overlap
    ) {
        ObjectNode result = source.deepCopy();
        ObjectNode context = result.putObject("shardContext");
        context.put("shardId", shardId);
        context.put("ownerKey", ownerKey);
        context.put("outputOwnedReferencesOnly", true);
        addRefs(context.putArray("ownedFactRefs"), ownedFacts);
        addRefs(context.putArray("ownedCandidateRefs"), ownedCandidates);
        addRefs(context.putArray("overlapRefs"), overlap);
        return result;
    }

    private PackedBundles packAutoBundles(
            SemanticShardBundleIndex index,
            List<ObjectNode> units,
            List<String> unitOwnerKeys,
            int targetInputTokens
    ) {
        List<ObjectNode> packedBundles = new ArrayList<>();
        List<String> packedOwnerKeys = new ArrayList<>();
        Set<String> currentIds = new LinkedHashSet<>();
        List<String> currentKeys = new ArrayList<>();
        for (int position = 0; position < units.size(); position++) {
            Set<String> unitIds = ids(units.get(position), SemanticShardBundleIndex.ITEM_SECTIONS);
            Set<String> candidateIds = new LinkedHashSet<>(currentIds);
            candidateIds.addAll(unitIds);
            ObjectNode candidate = bundleForIds(index, candidateIds);
            if (!currentIds.isEmpty() && estimate(candidate) > targetInputTokens) {
                packedBundles.add(bundleForIds(index, currentIds));
                packedOwnerKeys.add(packedOwnerKey(currentKeys));
                currentIds.clear();
                currentKeys.clear();
            }
            currentIds.addAll(unitIds);
            currentKeys.add(unitOwnerKeys.get(position));
        }
        if (!currentIds.isEmpty() || packedBundles.isEmpty()) {
            packedBundles.add(bundleForIds(index, currentIds));
            packedOwnerKeys.add(packedOwnerKey(currentKeys));
        }
        return new PackedBundles(packedBundles, packedOwnerKeys);
    }

    private String packedOwnerKey(List<String> ownerKeys) {
        if (ownerKeys.isEmpty()) return "global";
        if (ownerKeys.size() == 1) return ownerKeys.get(0);
        String hash = StableSemanticId.of("semantic-shard-pack", String.join("\u0000", ownerKeys));
        return "components:" + ownerKeys.size() + ":" + hash;
    }

    private void addRefs(ArrayNode target, Set<String> refs) {
        refs.stream().sorted().forEach(target::add);
    }

    private List<Set<String>> connectedComponents(SemanticShardBundleIndex index) {
        Map<String, Set<String>> graph = new LinkedHashMap<>();
        index.tables().forEach(table -> graph.put(table, new LinkedHashSet<>()));
        for (SemanticShardBundleIndex.Item item : index.items()) {
            List<String> touched = item.tables().stream().sorted().toList();
            for (String table : touched) graph.computeIfAbsent(table, ignored -> new LinkedHashSet<>());
            for (int i = 1; i < touched.size(); i++) {
                graph.get(touched.get(0)).add(touched.get(i));
                graph.get(touched.get(i)).add(touched.get(0));
            }
        }
        List<Set<String>> result = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        for (String start : graph.keySet().stream().sorted().toList()) {
            if (!visited.add(start)) continue;
            Set<String> component = new LinkedHashSet<>();
            Deque<String> pending = new ArrayDeque<>();
            pending.add(start);
            while (!pending.isEmpty()) {
                String current = pending.removeFirst();
                component.add(current);
                for (String neighbor : graph.getOrDefault(current, Set.of()).stream().sorted().toList()) {
                    if (visited.add(neighbor)) pending.addLast(neighbor);
                }
            }
            result.add(component);
        }
        return result;
    }

    private ObjectNode shardBundle(SemanticShardBundleIndex index, Set<String> anchorTables, boolean includeUntabled) {
        Set<String> selectedIds = new LinkedHashSet<>();
        for (SemanticShardBundleIndex.Item item : index.items()) {
            boolean touches = item.tables().stream().anyMatch(anchorTables::contains);
            if (touches || includeUntabled && item.tables().isEmpty()) {
                selectedIds.add(item.id());
            }
        }
        return bundleForRoots(index, selectedIds);
    }

    /**
     * CN: 单表owner仍超预算时，按稳定root ID切分；每个分片重新闭合typed依赖，单个root不可切分。
     * EN: Splits an oversized table owner by stable root IDs, re-closing typed dependencies per shard while
     * treating one root and its dependency closure as indivisible.
     */
    private List<ObjectNode> splitTableOwner(
            SemanticShardBundleIndex index,
            String table,
            boolean includeUntabled,
            int targetInputTokens,
            int maxInputTokens
    ) {
        List<String> roots = index.items().stream()
                .filter(item -> item.tables().contains(table)
                        || includeUntabled && item.tables().isEmpty())
                .map(SemanticShardBundleIndex.Item::id)
                .sorted()
                .toList();
        List<ObjectNode> result = new ArrayList<>();
        ObjectNode empty = bundleForRoots(index, Set.of());
        int fixedEstimate = estimateWithOwnershipContext(empty, table);
        if (fixedEstimate > maxInputTokens) {
            throw new SemanticShardingException(
                    "semantic shard fixed context exceeds maximum input token budget for table " + table);
        }
        int preferredPayloadBudget = Math.max(
                1, Math.min(targetInputTokens, maxInputTokens) - fixedEstimate);
        List<String> currentRoots = new ArrayList<>();
        int currentWeight = 0;
        for (String root : roots) {
            ObjectNode rootBundle = bundleForRoots(index, Set.of(root));
            int rootEstimate = estimateWithOwnershipContext(rootBundle, table);
            if (rootEstimate > maxInputTokens) {
                throw new SemanticShardingException(
                        "atomic evidence closure exceeds maximum input token budget for table " + table);
            }
            int rootWeight = Math.max(1, rootEstimate - fixedEstimate);
            if (!currentRoots.isEmpty()
                    && currentWeight + rootWeight > preferredPayloadBudget) {
                appendValidatedRootBatch(index, table, currentRoots, maxInputTokens, result);
                currentRoots.clear();
                currentWeight = 0;
            }
            currentRoots.add(root);
            currentWeight += rootWeight;
        }
        if (!currentRoots.isEmpty()) {
            appendValidatedRootBatch(index, table, currentRoots, maxInputTokens, result);
        }
        return result;
    }

    private void appendValidatedRootBatch(
            SemanticShardBundleIndex index,
            String table,
            List<String> roots,
            int maxInputTokens,
            List<ObjectNode> result
    ) {
        ObjectNode bundle = bundleForRoots(index, new LinkedHashSet<>(roots));
        if (estimateWithOwnershipContext(bundle, table) <= maxInputTokens) {
            result.add(bundle);
            return;
        }
        if (roots.size() == 1) {
            throw new SemanticShardingException(
                    "atomic evidence closure exceeds maximum input token budget for table " + table);
        }
        int midpoint = roots.size() / 2;
        appendValidatedRootBatch(index, table, roots.subList(0, midpoint), maxInputTokens, result);
        appendValidatedRootBatch(index, table, roots.subList(midpoint, roots.size()), maxInputTokens, result);
    }

    private ObjectNode bundleForRoots(SemanticShardBundleIndex index, Set<String> rootIds) {
        Set<String> selectedIds = new LinkedHashSet<>(rootIds);
        boolean changed;
        do {
            changed = false;
            for (String id : List.copyOf(selectedIds)) {
                SemanticShardBundleIndex.Item item = index.item(id);
                if (item == null) continue;
                for (String dependency : index.dependencyRefs(item.document())) {
                    if (index.item(dependency) != null && selectedIds.add(dependency)) changed = true;
                }
                for (String reference : index.evidenceRefs(item.document())) {
                    if (index.item(reference) != null && selectedIds.add(reference)) changed = true;
                }
            }
        } while (changed);

        return bundleForIds(index, selectedIds);
    }

    private int estimateWithOwnershipContext(ObjectNode bundle, String ownerKey) {
        return estimate(withShardContext(
                bundle,
                "shard-probe",
                ownerKey,
                ids(bundle, SemanticShardBundleIndex.FACT_SECTIONS),
                ids(bundle, SemanticShardBundleIndex.CANDIDATE_SECTIONS),
                Set.of()));
    }

    private ObjectNode bundleForIds(SemanticShardBundleIndex index, Set<String> selectedIds) {
        Set<String> evidenceIds = new LinkedHashSet<>();
        Set<String> selectedTables = new LinkedHashSet<>();
        for (String id : selectedIds) {
            SemanticShardBundleIndex.Item item = index.item(id);
            if (item == null) continue;
            selectedTables.addAll(item.tables());
            for (String ref : index.evidenceRefs(item.document())) {
                if (index.evidence(ref) != null) evidenceIds.add(ref);
            }
        }
        ObjectNode source = index.bundle();
        ObjectNode result = source.objectNode();
        source.fields().forEachRemaining(field -> {
            if (!"tables".equals(field.getKey())
                    && !"evidence".equals(field.getKey())
                    && !"shardContext".equals(field.getKey())
                    && !SemanticShardBundleIndex.ITEM_SECTIONS.contains(field.getKey())) {
                result.set(field.getKey(), field.getValue().deepCopy());
            }
        });
        replaceTextArray(result, "tables", selectedTables.stream().sorted().toList());
        for (String section : SemanticShardBundleIndex.ITEM_SECTIONS) {
            ArrayNode sectionResult = result.putArray(section);
            for (JsonNode item : source.path(section)) {
                if (selectedIds.contains(item.path("id").asText())) sectionResult.add(item.deepCopy());
            }
        }
        ArrayNode evidence = result.putArray("evidence");
        for (JsonNode item : source.path("evidence")) {
            if (evidenceIds.contains(item.path("id").asText())) evidence.add(item.deepCopy());
        }
        return result;
    }

    private void replaceTextArray(ObjectNode root, String field, List<String> values) {
        ArrayNode array = root.putArray(field);
        values.forEach(array::add);
    }

    private Set<String> ids(SemanticShardBundleIndex index, List<String> sections) {
        Set<String> result = new LinkedHashSet<>();
        for (SemanticShardBundleIndex.Item item : index.items()) {
            if (sections.contains(item.section())) result.add(item.id());
        }
        return result;
    }

    private Set<String> ids(ObjectNode bundle, List<String> sections) {
        Set<String> result = new LinkedHashSet<>();
        for (String section : sections) {
            bundle.path(section).forEach(item -> {
                String id = item.path("id").asText("");
                if (!id.isBlank()) result.add(id);
            });
        }
        return result;
    }

    private Map<String, String> owners(Set<String> ids, String shardId) {
        Map<String, String> result = new LinkedHashMap<>();
        ids.forEach(id -> result.put(id, shardId));
        return result;
    }

    private record PackedBundles(List<ObjectNode> bundles, List<String> ownerKeys) {
        private PackedBundles {
            bundles = List.copyOf(bundles);
            ownerKeys = List.copyOf(ownerKeys);
        }
    }

    private int estimate(ObjectNode bundle) {
        return estimator.estimate(promptBuilder.build(bundle));
    }

    private String hash(ObjectNode bundle) {
        return StableSemanticId.of("semantic-bundle", StableSemanticId.canonicalJson(bundle));
    }
}
