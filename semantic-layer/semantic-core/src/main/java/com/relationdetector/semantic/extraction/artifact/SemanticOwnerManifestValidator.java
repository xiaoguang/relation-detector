package com.relationdetector.semantic.extraction.artifact;

import com.relationdetector.semantic.extraction.shard.SemanticShardDescriptor;

import com.relationdetector.semantic.extraction.shard.SemanticRunPlan;

import com.relationdetector.semantic.extraction.normalization.SemanticExtractionValidationException;

import com.relationdetector.semantic.extraction.normalization.SemanticEvidenceLookup;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.relationdetector.semantic.internal.io.SemanticFileDigest;
import com.relationdetector.semantic.internal.io.SemanticFileTreeOperations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * CN: 验证全局owner manifest的hash、唯一identity、section类别及单个shard的owned/overlap完整分类；
 * 输入是run plan、shard descriptor和落盘bundle，输出仅为校验成功或固定安全异常。本类不生成owner。
 * EN: Validates the global owner manifest hash, unique identities, section categories, and complete owned/overlap
 * classification for one shard. It consumes a run plan, shard descriptor, and persisted bundle, returning only
 * success or a fixed safe exception; it never assigns ownership.
 */
public final class SemanticOwnerManifestValidator implements AutoCloseable {
    private static final List<String> FACT_SECTIONS = List.of(
            "metadataTables", "metadataColumns", "metadataConstraints", "metadataIndexes",
            "relationships", "lineage", "derivedRelationships", "derivedLineage",
            "namingEvidence", "diagnostics");
    private static final List<String> CANDIDATE_SECTIONS = List.of(
            "eventCandidates", "reviewItemCandidates", "tripletCandidates");
    private final SemanticRunPlan runPlan;
    private final SemanticEvidenceLookup evidenceLookup;
    private final Path indexWorkspace;
    private SemanticOwnerManifestIndex ownerIndex;
    private boolean closed;

    public SemanticOwnerManifestValidator(
            SemanticRunPlan runPlan,
            SemanticEvidenceLookup evidenceLookup
    ) {
        if (runPlan == null || evidenceLookup == null) {
            throw new IllegalArgumentException(
                    "semantic owner run plan and evidence store are required");
        }
        this.runPlan = runPlan;
        this.evidenceLookup = evidenceLookup;
        requireManifestHash();
        this.indexWorkspace = manifestIndexWorkspace();
    }

    /**
     * CN: 将一个已落盘shard的owned fact、owned candidate与overlap集合，同全局owner manifest逐项核对，
     * 并验证section类别、计数、互斥性和完整覆盖。成功无副作用；任一缺失、重复、越界或hash不一致均
     * 原子抛出脱敏校验异常，不修改bundle或manifest。
     *
     * EN: Reconciles one persisted shard's owned facts, owned candidates, and overlaps against the global owner
     * manifest, checking section kind, counts, disjointness, and complete coverage. Success has no side effects;
     * missing, duplicate, out-of-owner, or hash-inconsistent data raises a sanitized validation exception atomically
     * without modifying the bundle or manifest.
     */
    public void validate(SemanticShardDescriptor descriptor, ObjectNode bundle) {
        if (descriptor == null || bundle == null) {
            throw new SemanticExtractionValidationException(
                    "semantic shard owner validation input is incomplete");
        }
        JsonNode context = bundle.path("shardContext");
        if (!context.isObject()) {
            throw new SemanticExtractionValidationException(
                    "semantic shardContext is missing or invalid");
        }
        Set<String> ownedFacts = textSet(context.path("ownedFactRefs"), "ownedFactRefs");
        Set<String> ownedCandidates = textSet(
                context.path("ownedCandidateRefs"), "ownedCandidateRefs");
        Set<String> overlap = textSet(context.path("overlapRefs"), "overlapRefs");
        requireDisjoint(ownedFacts, ownedCandidates, overlap);
        if (ownedFacts.size() != descriptor.ownedFactCount()
                || ownedCandidates.size() != descriptor.ownedCandidateCount()) {
            throw new SemanticExtractionValidationException(
                    "semantic shard descriptor does not match owner context");
        }

        Map<String, BundleItem> bundleIds = bundleIds(bundle);
        Set<String> externalAudit = SemanticExternalAuditReferences.read(
                descriptor.externalAuditSidecar().path());
        requireExternalAuditSummary(context, externalAudit);
        Set<String> classified = new LinkedHashSet<>(ownedFacts);
        classified.addAll(ownedCandidates);
        classified.addAll(overlap);
        if (!classified.equals(bundleIds.keySet())) {
            throw new SemanticExtractionValidationException(
                    "semantic shard ownership does not classify every bundle item exactly once");
        }
        if (!java.util.Collections.disjoint(classified, externalAudit)) {
            throw new SemanticExtractionValidationException(
                    "semantic shard external audit identities must not be local items");
        }
        ownedFacts.forEach(id -> requireKind(bundleIds, id, true));
        ownedCandidates.forEach(id -> requireKind(bundleIds, id, false));

        Set<String> matched = new LinkedHashSet<>();
        Set<String> matchedExternal = new LinkedHashSet<>();
        try {
            SemanticOwnerManifestIndex owners = owners();
            for (String id : externalAudit) {
                if (owners.find(id).isPresent()) {
                    matchedExternal.add(id);
                }
            }
            for (Map.Entry<String, BundleItem> item : bundleIds.entrySet()) {
                SemanticOwnerManifestIndex.Entry owner = owners.find(item.getKey()).orElseThrow(
                        () -> new SemanticExtractionValidationException(
                                "semantic owner manifest does not cover every shard item"));
                matched.add(item.getKey());
                if (owner.fact() != item.getValue().fact()
                        || !owner.section().equals(item.getValue().manifestSection())) {
                    throw new SemanticExtractionValidationException(
                            "semantic owner manifest section disagrees with the bundle");
                }
                boolean currentOwner = descriptor.id().equals(owner.ownerShardId());
                Set<String> expected = owner.fact() ? ownedFacts : ownedCandidates;
                if (currentOwner && !expected.contains(item.getKey())
                        || !currentOwner && !overlap.contains(item.getKey())) {
                    throw new SemanticExtractionValidationException(
                            "semantic shard context disagrees with the global owner manifest");
                }
            }
        } catch (RuntimeException failure) {
            if (failure instanceof SemanticExtractionValidationException validation
                    && !"semantic request bundle package is invalid".equals(
                            validation.getMessage())) {
                throw validation;
            }
            throw new SemanticExtractionValidationException(
                    "semantic owner manifest cannot be read");
        }
        if (!matched.equals(bundleIds.keySet())) {
            throw new SemanticExtractionValidationException(
                    "semantic owner manifest does not cover every shard item");
        }
        Set<String> externalEvidence = new LinkedHashSet<>(externalAudit);
        externalEvidence.removeAll(matchedExternal);
        for (String reference : externalEvidence) {
            if (evidenceLookup.findEvidence(reference).isEmpty()) {
                throw new SemanticExtractionValidationException(
                        "semantic external audit identity is unresolved");
            }
        }
    }

    private void requireExternalAuditSummary(JsonNode context, Set<String> externalAudit) {
        JsonNode count = context.path("externalAuditRefCount");
        JsonNode hash = context.path("externalAuditRefsSha256");
        if (!count.canConvertToInt() || !hash.isTextual()) {
            throw new SemanticExtractionValidationException(
                    "semantic shard external audit summary is missing or invalid");
        }
        SemanticExternalAuditReferences.Snapshot actual =
                SemanticExternalAuditReferences.snapshot(externalAudit);
        if (count.asInt() != actual.count() || !hash.asText().equals(actual.sha256())) {
            throw new SemanticExtractionValidationException(
                    "semantic shard external audit summary does not match typed references");
        }
    }

    private Map<String, BundleItem> bundleIds(ObjectNode bundle) {
        Map<String, BundleItem> result = new LinkedHashMap<>();
        for (String section : FACT_SECTIONS) {
            appendBundleIds(bundle, section, true, result);
        }
        for (String section : CANDIDATE_SECTIONS) {
            appendBundleIds(bundle, section, false, result);
        }
        return result;
    }

    private void appendBundleIds(
            ObjectNode bundle,
            String section,
            boolean fact,
            Map<String, BundleItem> target
    ) {
        JsonNode values = bundle.path(section);
        if (!values.isArray()) {
            throw new SemanticExtractionValidationException(
                    "semantic shard section must be an array: " + section);
        }
        for (JsonNode value : values) {
            String id = value.path("id").asText("");
            BundleItem bundleItem = new BundleItem(
                    SemanticOwnerManifestIndex.manifestSection(section), fact);
            if (id.isBlank() || target.putIfAbsent(id, bundleItem) != null) {
                throw new SemanticExtractionValidationException(
                        "semantic shard contains a missing or duplicate item identity");
            }
        }
    }

    private Set<String> textSet(JsonNode values, String field) {
        if (!values.isArray()) {
            throw new SemanticExtractionValidationException(
                    "semantic shard owner field must be an array: " + field);
        }
        Set<String> result = new LinkedHashSet<>();
        for (JsonNode value : values) {
            String id = value.asText("");
            if (id.isBlank() || !result.add(id)) {
                throw new SemanticExtractionValidationException(
                        "semantic shard owner field contains a missing or duplicate identity");
            }
        }
        return result;
    }

    private void requireDisjoint(Set<String> facts, Set<String> candidates, Set<String> overlap) {
        if (!java.util.Collections.disjoint(facts, candidates)
                || !java.util.Collections.disjoint(facts, overlap)
                || !java.util.Collections.disjoint(candidates, overlap)) {
            throw new SemanticExtractionValidationException(
                    "semantic shard owned and overlap identities must be disjoint");
        }
    }

    private void requireKind(
            Map<String, BundleItem> bundleIds,
            String id,
            boolean expectedFact
    ) {
        if (!bundleIds.containsKey(id) || bundleIds.get(id).fact() != expectedFact) {
            throw new SemanticExtractionValidationException(
                    "semantic shard owner identity is assigned to the wrong section kind");
        }
    }

    private Path manifestIndexWorkspace() {
        Path manifest = runPlan.ownerManifest().path().toAbsolutePath().normalize();
        Path parent = manifest.getParent();
        if (parent == null) {
            throw new SemanticExtractionValidationException(
                    "semantic owner manifest cannot be read");
        }
        return parent.resolve(".owner-manifest-index-" + UUID.randomUUID());
    }

    private void requireManifestHash() {
        try {
            SemanticFileDigest.Digest actual = SemanticFileDigest.computeNoFollow(
                    runPlan.ownerManifest().path());
            if (actual.bytes() != runPlan.ownerManifest().bytes()
                    || !actual.sha256().equals(runPlan.ownerManifest().sha256())) {
                throw new SemanticExtractionValidationException(
                        "semantic owner manifest hash does not match the run plan");
            }
        } catch (IOException failure) {
            throw new SemanticExtractionValidationException(
                    "semantic owner manifest cannot be verified");
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        RuntimeException failure = null;
        if (ownerIndex != null) {
            try {
                ownerIndex.close();
            } catch (RuntimeException error) {
                failure = error;
            }
        }
        SemanticFileTreeOperations.deleteRecursivelyBestEffort(indexWorkspace);
        if (failure != null) {
            throw failure;
        }
    }

    private SemanticOwnerManifestIndex owners() {
        if (closed) {
            throw new IllegalStateException("semantic owner manifest validator is closed");
        }
        if (ownerIndex != null) {
            return ownerIndex;
        }
        try {
            ownerIndex = SemanticOwnerManifestIndex.open(
                    runPlan.ownerManifest().path(),
                    indexWorkspace,
                    SemanticRequestPackageLimits.defaults());
            return ownerIndex;
        } catch (RuntimeException failure) {
            SemanticFileTreeOperations.deleteRecursivelyBestEffort(indexWorkspace);
            throw new SemanticExtractionValidationException(
                    "semantic owner manifest cannot be read");
        }
    }

    private record BundleItem(String manifestSection, boolean fact) {
    }
}
