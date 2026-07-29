package com.relationdetector.semantic.extract;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.reader.DiskTableComponentIndex;
import com.relationdetector.semantic.reader.ExternalJsonRecordStore;
import com.relationdetector.semantic.reader.ExternalLineSorter;
import com.relationdetector.semantic.reader.ScanResultContractException;
import com.relationdetector.semantic.reader.SemanticEvidenceStore;

/**
 * CN: 在完整磁盘evidence store上计算typed table components、stable-root closure和唯一owner，并只将一个
 * token受限root/shard装入内存；输入不受raw chunk边界影响，输出path-backed shard与全局owner manifest。
 * EN: Computes typed table components, stable-root closure, and unique ownership over the complete disk evidence
 * store while loading only one token-bounded root or shard. Raw chunk boundaries cannot affect the path-backed shards
 * or the global owner manifest it emits.
 */
final class SemanticGlobalOwnerPlanner {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> FACT_SECTIONS = List.of(
            "metadataTables", "metadataColumns", "metadataConstraints", "metadataIndexes",
            "relationships", "lineage", "derivedRelationships", "derivedLineage",
            "namingEvidence", "diagnostics");
    private static final List<String> CANDIDATE_SECTIONS = List.of(
            "eventCandidates", "reviewItemCandidates", "tripletCandidates");
    private static final List<String> ITEM_SECTIONS = joinedSections();

    private final SemanticPromptBudgetEstimator estimator = new SemanticPromptBudgetEstimator();
    private final SemanticExtractionPromptBuilder promptBuilder = new SemanticExtractionPromptBuilder();

    SemanticPathRunPlan plan(
            SemanticEvidenceStore evidence,
            Path workspace,
            SemanticShardingOptions options,
            Path fullBundle,
            String fullHash
    ) {
        SemanticShardingOptions resolved = options == null ? SemanticShardingOptions.defaults() : options;
        try (SemanticDiskBundleIndex index =
                     new SemanticDiskBundleIndex(evidence, workspace.resolve("bundle-index"))) {
            Files.createDirectories(workspace.resolve("shards"));
            Path roots = workspace.resolve("roots.raw");
            Path tables = workspace.resolve("tables.raw");
            Path edges = workspace.resolve("edges.raw");
            inventoryRoots(index, resolved.maxInputTokens(), roots, tables, edges);
            Path assignments = assignComponents(workspace, roots, tables, edges);
            return publishShards(
                    index, workspace, assignments, resolved, fullBundle, fullHash);
        } catch (IOException failure) {
            throw new ScanResultContractException("failed to plan global semantic owners", failure);
        }
    }

    private void inventoryRoots(
            SemanticDiskBundleIndex index,
            int maxInputTokens,
            Path roots,
            Path tables,
            Path edges
    ) throws IOException {
        try (BufferedWriter rootWriter = writer(roots);
            BufferedWriter tableWriter = writer(tables);
             BufferedWriter edgeWriter = writer(edges)) {
            index.forEachRoot(root -> {
                SemanticDiskBundleIndex.RootClosure closure = index.closure(root.id(), maxInputTokens);
                List<String> touched = closure.tables().stream().sorted().toList();
                String owner = touched.isEmpty() ? "global" : touched.get(0);
                try {
                    rootWriter.write(encode(owner));
                    rootWriter.write('\t');
                    rootWriter.write(encode(root.id()));
                    rootWriter.newLine();
                    for (String table : touched) {
                        tableWriter.write(encode(table));
                        tableWriter.newLine();
                    }
                    for (int position = 1; position < touched.size(); position++) {
                        edgeWriter.write(encode(touched.get(0)));
                        edgeWriter.write('\t');
                        edgeWriter.write(encode(touched.get(position)));
                        edgeWriter.newLine();
                    }
                } catch (IOException failure) {
                    throw new ScanResultContractException("failed to spool semantic root ownership", failure);
                }
            });
        }
    }

    private Path assignComponents(
            Path workspace,
            Path roots,
            Path tables,
            Path edges
    ) throws IOException {
        Path raw = workspace.resolve("assignments.raw");
        try (DiskTableComponentIndex components =
                     new DiskTableComponentIndex(tables, edges, workspace.resolve("table-components"));
             BufferedReader reader = Files.newBufferedReader(roots, StandardCharsets.UTF_8);
             BufferedWriter writer = writer(raw)) {
            String line;
            while ((line = reader.readLine()) != null) {
                int split = line.indexOf('\t');
                String owner = line.substring(0, split);
                String component = "global".equals(decode(owner))
                        ? "global"
                        : components.componentKey(owner);
                writer.write(component);
                writer.write('\t');
                writer.write(owner);
                writer.write('\t');
                writer.write(line.substring(split + 1));
                writer.newLine();
            }
        }
        Path sorted = workspace.resolve("assignments.sorted");
        new ExternalLineSorter().sort(raw, sorted, workspace.resolve("assignment-sort-work"));
        return sorted;
    }

    private SemanticPathRunPlan publishShards(
            SemanticDiskBundleIndex index,
            Path workspace,
            Path assignments,
            SemanticShardingOptions options,
            Path fullBundle,
            String fullHash
    ) throws IOException {
        List<Draft> drafts = new ArrayList<>();
        ExternalJsonRecordStore owners = new ExternalJsonRecordStore(workspace.resolve("owners"));
        try {
            packRoots(index, workspace, assignments, options, owners, drafts);
            if (drafts.isEmpty()) {
                flushDraft(
                        workspace,
                        new MutableShard(index.emptyBundle(), "global"),
                        drafts,
                        owners);
            }
            owners.finish();
            if (drafts.size() > options.maxShardCount()) {
                throw new SemanticShardingException(
                        "semantic shard count exceeds configured maximum");
            }
            List<SemanticPathShard> shards = finalizeShards(index, workspace, drafts, owners, options);
            Path manifest = writeOwnerManifest(index, workspace, owners);
            return new SemanticPathRunPlan(
                    fullBundle,
                    fullHash,
                    shards,
                    options.reconcile(),
                    options.maxInputTokens(),
                    manifest,
                    sha256(manifest));
        } finally {
            owners.close();
        }
    }

    private void packRoots(
            SemanticDiskBundleIndex index,
            Path workspace,
            Path assignments,
            SemanticShardingOptions options,
            ExternalJsonRecordStore owners,
            List<Draft> drafts
    ) throws IOException {
        MutableShard current = null;
        try (BufferedReader reader = Files.newBufferedReader(assignments, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split("\\t", 3);
                String component = fields[0];
                String owner = decode(fields[1]);
                String rootId = decode(fields[2]);
                SemanticDiskBundleIndex.RootClosure root = index.closure(rootId, options.maxInputTokens());
                if (current == null) {
                    current = new MutableShard(index.emptyBundle(), component);
                }
                boolean forceBoundary = options.mode() == SemanticShardMode.FORCE
                        && !current.rootIds.isEmpty()
                        && !current.component.equals(component);
                MutableShard candidate = current.copy();
                candidate.add(root, owner);
                int estimate = estimate(candidate.probeBundle());
                int target = options.mode() == SemanticShardMode.OFF
                        ? options.maxInputTokens()
                        : options.targetInputTokens();
                if ((forceBoundary || !current.rootIds.isEmpty() && estimate > target)) {
                    flushDraft(workspace, current, drafts, owners);
                    current = new MutableShard(index.emptyBundle(), component);
                    current.add(root, owner);
                    estimate = estimate(current.probeBundle());
                } else {
                    current = candidate;
                }
                if (estimate > options.maxInputTokens()) {
                    throw new SemanticShardingException(
                            "atomic semantic root closure exceeds maximum input token budget");
                }
            }
        }
        if (current != null && !current.rootIds.isEmpty()) {
            flushDraft(workspace, current, drafts, owners);
        }
        if (options.mode() == SemanticShardMode.OFF && drafts.size() != 1) {
            throw new SemanticShardingException(
                    "semantic evidence bundle exceeds the configured input budget while sharding is off");
        }
    }

    private void flushDraft(
            Path workspace,
            MutableShard shard,
            List<Draft> drafts,
            ExternalJsonRecordStore owners
    ) throws IOException {
        String id = "shard-%04d".formatted(drafts.size() + 1);
        Path directory = workspace.resolve("shards").resolve(id);
        Files.createDirectories(directory);
        Path path = directory.resolve("draft-evidence-bundle.json");
        JSON.writeValue(path.toFile(), shard.bundle);
        for (String rootId : shard.rootIds) {
            SemanticDiskBundleIndex.Item root = shard.rootSections.get(rootId);
            ObjectNode owner = JSON.createObjectNode();
            owner.put("shardId", id);
            owner.put("section", root.section().name());
            owners.append(rootId, owner);
        }
        drafts.add(new Draft(id, shard.ownerKey(), path));
    }

    private List<SemanticPathShard> finalizeShards(
            SemanticDiskBundleIndex index,
            Path workspace,
            List<Draft> drafts,
            ExternalJsonRecordStore owners,
            SemanticShardingOptions options
    ) throws IOException {
        List<SemanticPathShard> result = new ArrayList<>();
        for (Draft draft : drafts) {
            ObjectNode bundle = (ObjectNode) JSON.readTree(draft.path().toFile());
            Set<String> ownedFacts = new LinkedHashSet<>();
            Set<String> ownedCandidates = new LinkedHashSet<>();
            Set<String> overlap = new LinkedHashSet<>();
            for (String section : ITEM_SECTIONS) {
                for (JsonNode item : bundle.path(section)) {
                    String id = item.path("id").asText("");
                    String owner = owners.get(id).orElseThrow(
                            () -> new SemanticShardingException(
                                    "semantic owner manifest is missing an item")).value()
                            .path("shardId").asText("");
                    if (draft.id().equals(owner)) {
                        if (FACT_SECTIONS.contains(section)) {
                            ownedFacts.add(id);
                        } else {
                            ownedCandidates.add(id);
                        }
                    } else {
                        overlap.add(id);
                    }
                }
            }
            ObjectNode context = bundle.putObject("shardContext");
            context.put("shardId", draft.id());
            context.put("ownerKey", draft.ownerKey());
            context.put("outputOwnedReferencesOnly", true);
            appendSorted(context.putArray("ownedFactRefs"), ownedFacts);
            appendSorted(context.putArray("ownedCandidateRefs"), ownedCandidates);
            appendSorted(context.putArray("overlapRefs"), overlap);
            int estimate = estimate(bundle);
            if (estimate > options.maxInputTokens()) {
                throw new SemanticShardingException(
                        "semantic shard exceeds maximum input token budget after ownership context");
            }
            Path finalPath = draft.path().getParent().resolve("evidence-bundle.json");
            JSON.writeValue(finalPath.toFile(), bundle);
            Files.deleteIfExists(draft.path());
            result.add(new SemanticPathShard(
                    draft.id(),
                    draft.ownerKey(),
                    finalPath,
                    estimate,
                    ownedFacts.size(),
                    ownedCandidates.size()));
        }
        return List.copyOf(result);
    }

    private Path writeOwnerManifest(
            SemanticDiskBundleIndex index,
            Path workspace,
            ExternalJsonRecordStore owners
    ) throws IOException {
        Path manifest = workspace.resolve("owner-manifest.tsv");
        try (BufferedWriter writer = writer(manifest)) {
            index.forEachRoot(root -> {
                JsonNode owner = owners.get(root.id()).orElseThrow(
                        () -> new SemanticShardingException(
                                "semantic owner manifest is missing an item")).value();
                try {
                    writer.write(encode(root.id()));
                    writer.write('\t');
                    writer.write(root.section().name());
                    writer.write('\t');
                    writer.write(owner.path("shardId").asText(""));
                    writer.newLine();
                } catch (IOException failure) {
                    throw new ScanResultContractException("failed to write semantic owner manifest", failure);
                }
            });
        }
        return manifest;
    }

    private int estimate(ObjectNode bundle) {
        return estimator.estimate(promptBuilder.build(bundle));
    }

    private void appendSorted(ArrayNode target, Set<String> values) {
        values.stream().sorted().forEach(target::add);
    }

    private BufferedWriter writer(Path path) throws IOException {
        return Files.newBufferedWriter(
                path, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }

    private String sha256(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException failure) {
            throw new ScanResultContractException("failed to hash semantic owner manifest", failure);
        }
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static List<String> joinedSections() {
        List<String> result = new ArrayList<>(FACT_SECTIONS);
        result.addAll(CANDIDATE_SECTIONS);
        return List.copyOf(result);
    }

    private record Draft(String id, String ownerKey, Path path) {
    }

    private static final class MutableShard {
        private final ObjectNode bundle;
        private final String component;
        private final Set<String> rootIds = new LinkedHashSet<>();
        private final Set<String> owners = new LinkedHashSet<>();
        private final Map<String, SemanticDiskBundleIndex.Item> rootSections = new LinkedHashMap<>();
        private final Map<String, Set<String>> idsBySection = new LinkedHashMap<>();
        private final Set<String> evidenceIds = new LinkedHashSet<>();
        private final Set<String> tables = new LinkedHashSet<>();

        private MutableShard(ObjectNode bundle, String component) {
            this.bundle = bundle;
            this.component = component;
            ITEM_SECTIONS.forEach(section -> idsBySection.put(section, new LinkedHashSet<>()));
        }

        private MutableShard copy() {
            MutableShard copy = new MutableShard(bundle.deepCopy(), component);
            copy.rootIds.addAll(rootIds);
            copy.owners.addAll(owners);
            copy.rootSections.putAll(rootSections);
            idsBySection.forEach((section, ids) -> copy.idsBySection.get(section).addAll(ids));
            copy.evidenceIds.addAll(evidenceIds);
            copy.tables.addAll(tables);
            return copy;
        }

        private void add(SemanticDiskBundleIndex.RootClosure root, String owner) {
            rootIds.add(root.rootId());
            owners.add(owner);
            rootSections.put(root.rootId(), new SemanticDiskBundleIndex.Item(
                    root.rootId(), root.rootSection(), root.bundle()));
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

        private ObjectNode probeBundle() {
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
            return probe;
        }

        private String ownerKey() {
            if (owners.isEmpty()) {
                return "global";
            }
            if (owners.size() == 1) {
                return owners.iterator().next();
            }
            return "component:" + component;
        }
    }
}
