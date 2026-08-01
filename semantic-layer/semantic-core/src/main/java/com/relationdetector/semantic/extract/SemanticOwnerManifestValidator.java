package com.relationdetector.semantic.extract;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.reader.SemanticEvidenceStore;

/**
 * CN: 验证全局owner manifest的hash、唯一identity、section类别及单个shard的owned/overlap完整分类；
 * 输入是run plan、shard descriptor和落盘bundle，输出仅为校验成功或固定安全异常。本类不生成owner。
 * EN: Validates the global owner manifest hash, unique identities, section categories, and complete owned/overlap
 * classification for one shard. It consumes a run plan, shard descriptor, and persisted bundle, returning only
 * success or a fixed safe exception; it never assigns ownership.
 */
final class SemanticOwnerManifestValidator {
    private static final List<String> FACT_SECTIONS = List.of(
            "metadataTables", "metadataColumns", "metadataConstraints", "metadataIndexes",
            "relationships", "lineage", "derivedRelationships", "derivedLineage",
            "namingEvidence", "diagnostics");
    private static final List<String> CANDIDATE_SECTIONS = List.of(
            "eventCandidates", "reviewItemCandidates", "tripletCandidates");
    private final SemanticPathRunPlan runPlan;
    private final SemanticEvidenceStore evidenceStore;

    SemanticOwnerManifestValidator(
            SemanticPathRunPlan runPlan,
            SemanticEvidenceStore evidenceStore
    ) {
        if (runPlan == null || evidenceStore == null) {
            throw new IllegalArgumentException(
                    "semantic owner run plan and evidence store are required");
        }
        this.runPlan = runPlan;
        this.evidenceStore = evidenceStore;
        requireManifestHash();
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
    void validate(SemanticPathShard descriptor, ObjectNode bundle) {
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

        Map<String, Boolean> bundleIds = bundleIds(bundle);
        Set<String> externalAudit = SemanticExternalAuditReferences.read(
                SemanticExternalAuditReferences.sidecar(descriptor.bundlePath()));
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
        try (BufferedReader reader = Files.newBufferedReader(
                runPlan.ownerManifestPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split("\\t", 3);
                if (fields.length != 3 || fields[2].isBlank()) {
                    throw new SemanticExtractionValidationException(
                            "semantic owner manifest contains a malformed record");
                }
                String id = decode(fields[0]);
                if (externalAudit.contains(id)) {
                    matchedExternal.add(id);
                }
                if (!bundleIds.containsKey(id)) {
                    continue;
                }
                if (!matched.add(id)) {
                    throw new SemanticExtractionValidationException(
                            "semantic owner manifest contains a duplicate identity");
                }
                boolean manifestFact = isFactSection(fields[1]);
                if (manifestFact != bundleIds.get(id)) {
                    throw new SemanticExtractionValidationException(
                            "semantic owner manifest section disagrees with the bundle");
                }
                boolean currentOwner = descriptor.id().equals(fields[2]);
                Set<String> expected = manifestFact ? ownedFacts : ownedCandidates;
                if (currentOwner && !expected.contains(id)
                        || !currentOwner && !overlap.contains(id)) {
                    throw new SemanticExtractionValidationException(
                            "semantic shard context disagrees with the global owner manifest");
                }
            }
        } catch (IOException failure) {
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
            if (evidenceStore.find(SemanticEvidenceStore.Section.EVIDENCE, reference).isEmpty()) {
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

    private Map<String, Boolean> bundleIds(ObjectNode bundle) {
        Map<String, Boolean> result = new LinkedHashMap<>();
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
            Map<String, Boolean> target
    ) {
        JsonNode values = bundle.path(section);
        if (!values.isArray()) {
            throw new SemanticExtractionValidationException(
                    "semantic shard section must be an array: " + section);
        }
        for (JsonNode item : values) {
            String id = item.path("id").asText("");
            if (id.isBlank() || target.putIfAbsent(id, fact) != null) {
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

    private void requireKind(Map<String, Boolean> bundleIds, String id, boolean expectedFact) {
        if (!bundleIds.containsKey(id) || bundleIds.get(id) != expectedFact) {
            throw new SemanticExtractionValidationException(
                    "semantic shard owner identity is assigned to the wrong section kind");
        }
    }

    private boolean isFactSection(String section) {
        return switch (section) {
            case "METADATA_TABLES", "METADATA_COLUMNS", "METADATA_CONSTRAINTS", "METADATA_INDEXES",
                    "RELATIONSHIPS", "LINEAGE", "DERIVED_RELATIONSHIPS", "DERIVED_LINEAGE",
                    "NAMING_EVIDENCE", "DIAGNOSTICS" -> true;
            case "EVENT_CANDIDATES", "REVIEW_ITEM_CANDIDATES", "TRIPLET_CANDIDATES" -> false;
            default -> throw new SemanticExtractionValidationException(
                    "semantic owner manifest contains an invalid section");
        };
    }

    private void requireManifestHash() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(runPlan.ownerManifestPath())) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            String actual = HexFormat.of().formatHex(digest.digest());
            if (!actual.equals(runPlan.ownerManifestHash())) {
                throw new SemanticExtractionValidationException(
                        "semantic owner manifest hash does not match the run plan");
            }
        } catch (IOException | NoSuchAlgorithmException failure) {
            throw new SemanticExtractionValidationException(
                    "semantic owner manifest cannot be verified");
        }
    }

    private String decode(String value) {
        try {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException failure) {
            throw new SemanticExtractionValidationException(
                    "semantic owner manifest contains an invalid identity");
        }
    }
}
