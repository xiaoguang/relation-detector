package com.relationdetector.semantic.extraction.normalization;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.relationdetector.semantic.StableSemanticId;
import com.relationdetector.semantic.extraction.model.SemanticDimension;
import com.relationdetector.semantic.extraction.model.SemanticEntity;
import com.relationdetector.semantic.extraction.model.SemanticEvent;
import com.relationdetector.semantic.extraction.model.SemanticExtractionDocument;
import com.relationdetector.semantic.extraction.model.SemanticItem;
import com.relationdetector.semantic.extraction.model.SemanticLineage;
import com.relationdetector.semantic.extraction.model.SemanticMetric;
import com.relationdetector.semantic.extraction.model.SemanticRelation;
import com.relationdetector.semantic.extraction.model.SemanticReviewItem;
import com.relationdetector.semantic.extraction.model.SemanticTriplet;

/**
 * CN: 把模型提供的section-scoped临时alias替换为由typed canonical content生成的formal ID，
 * 并在graph、validation和跨分片合并之前重写所有typed semantic引用。输入是已decode和
 * candidate-backfill的文档，输出是detached canonical文档；同alias多义、canonical ID内容冲突
 * 或不确定去重均原子失败。本类不验证evidence、
 * 不解析SQL，也不把alias保留到正式输出。
 *
 * <p>EN: Replaces section-scoped model aliases with formal IDs derived from typed canonical content and rewrites all
 * typed semantic references before graph assembly, validation, and cross-shard merge. It consumes a decoded,
 * candidate-backfilled document and returns a detached canonical document. Ambiguous aliases, conflicting content
 * for one canonical ID, and uncertain deduplication fail atomically. It does not validate evidence, parse SQL, or
 * retain model aliases in formal output.
 */
public final class SemanticFormalIdentityCanonicalizer {
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * Returns a detached canonical document and leaves {@code source} unchanged on both success and failure.
     */
    public SemanticExtractionDocument canonicalize(SemanticExtractionDocument source) {
        if (source == null) {
            throw new IllegalArgumentException("semantic extraction document is required");
        }
        SemanticExtractionDocument detached = new SemanticExtractionDocumentCodec()
                .read(JSON.valueToTree(source));
        CanonicalizationState state = canonicalizeFacts(detached);
        canonicalizeReviewItems(detached.reviewItems, state);
        return detached;
    }

    CanonicalizationState canonicalizeFacts(SemanticExtractionDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("semantic extraction document is required");
        }
        document.ensureSections();
        CanonicalizationState state = new CanonicalizationState();

        canonicalizeSection(
                "entities",
                document.entities,
                entity -> SemanticCanonicalIdentity.entity(
                        entity.physicalName,
                        entity.name,
                        entity.machineType,
                        entity.type,
                        entity.ownedGroundingRefs()).canonicalId(),
                this::normalizeBase,
                state);

        Map<String, String> entityAliases = state.aliases("entities");
        EntityNameIndex entityNames = EntityNameIndex.from(document.entities);
        rewriteEntityReferences(document, entityAliases, entityNames);

        canonicalizeSection(
                "events",
                document.events,
                event -> SemanticCanonicalIdentity.event(event.eventCandidateRef),
                this::normalizeEvent,
                state);
        canonicalizeSection(
                "relations",
                document.relations,
                relation -> SemanticCanonicalIdentity.relation(
                        relation.fromEntityRef,
                        relation.toEntityRef,
                        relation.machineType,
                        relation.type),
                this::normalizeBase,
                state);
        canonicalizeSection(
                "lineage",
                document.lineage,
                lineage -> SemanticCanonicalIdentity.lineage(
                        lineage.fromPhysical,
                        lineage.toPhysical,
                        lineage.transform),
                this::normalizeLineage,
                state);
        canonicalizeSection(
                "metrics",
                document.metrics,
                metric -> SemanticCanonicalIdentity.metric(
                        metric.name,
                        metric.machineType,
                        metric.type,
                        metric.physicalField,
                        metric.sourceFields),
                this::normalizeMetric,
                state);
        canonicalizeSection(
                "dimensions",
                document.dimensions,
                dimension -> SemanticCanonicalIdentity.dimension(
                        dimension.name,
                        dimension.machineType,
                        dimension.type,
                        dimension.physicalField,
                        dimension.dimensionTable),
                this::normalizeBase,
                state);
        canonicalizeSection(
                "triplets",
                document.triplets,
                triplet -> SemanticCanonicalIdentity.triplet(triplet.candidateRef),
                this::normalizeBase,
                state);
        return state;
    }

    void canonicalizeReviewItems(
            List<SemanticReviewItem> reviewItems,
            CanonicalizationState state
    ) {
        if (reviewItems == null || state == null) {
            throw new IllegalArgumentException("semantic review items and canonical aliases are required");
        }
        for (SemanticReviewItem review : reviewItems) {
            review.targetRef = SemanticNormalizationSupport.nonBlank(review.targetRef, review.target);
            review.targetSection = SemanticNormalizationSupport.nonBlank(
                    review.targetSection, review.section);
            review.targetRef = state.aliases(review.targetSection)
                    .getOrDefault(text(review.targetRef), review.targetRef);
            review.target = null;
            review.section = null;
        }
        canonicalizeSection(
                "reviewItems",
                reviewItems,
                review -> SemanticCanonicalIdentity.review(
                        review.targetRef, review.targetSection, review.type),
                this::normalizeBase,
                state);
    }

    private void rewriteEntityReferences(
            SemanticExtractionDocument document,
            Map<String, String> aliases,
            EntityNameIndex names
    ) {
        for (SemanticEvent event : document.events) {
            event.inputEntityRefs = rewriteReferences(event.inputEntityRefs, aliases);
            event.outputEntityRefs = rewriteReferences(event.outputEntityRefs, aliases);
        }
        for (SemanticRelation relation : document.relations) {
            relation.fromEntityRef = rewriteReference(relation.fromEntityRef, aliases);
            relation.toEntityRef = rewriteReference(relation.toEntityRef, aliases);
            if (blank(relation.fromEntityRef)) {
                relation.fromEntityRef = names.unique(relation.from);
            }
            if (blank(relation.toEntityRef)) {
                relation.toEntityRef = names.unique(relation.to);
            }
        }
        for (SemanticLineage lineage : document.lineage) {
            lineage.sourceEntityRefs = rewriteReferences(lineage.sourceEntityRefs, aliases);
            lineage.targetEntityRef = rewriteReference(lineage.targetEntityRef, aliases);
        }
        for (SemanticMetric metric : document.metrics) {
            metric.ownerEntityRef = rewriteReference(metric.ownerEntityRef, aliases);
            metric.sourceEntityRefs = rewriteReferences(metric.sourceEntityRefs, aliases);
        }
        for (SemanticDimension dimension : document.dimensions) {
            dimension.ownerEntityRef = rewriteReference(dimension.ownerEntityRef, aliases);
            dimension.dimensionEntityRef = rewriteReference(dimension.dimensionEntityRef, aliases);
        }
        for (SemanticTriplet triplet : document.triplets) {
            triplet.subjectRef = rewriteReference(triplet.subjectRef, aliases);
            triplet.objectRef = rewriteReference(triplet.objectRef, aliases);
            if (blank(triplet.subjectRef)) {
                triplet.subjectRef = names.unique(triplet.subject);
            }
            if (blank(triplet.objectRef)) {
                triplet.objectRef = names.unique(triplet.object);
            }
        }
    }

    private <T extends SemanticItem> void canonicalizeSection(
            String section,
            List<T> items,
            Function<T, String> identity,
            Consumer<T> normalizer,
            CanonicalizationState state
    ) {
        Map<String, String> aliasContent = new LinkedHashMap<>();
        Map<String, String> contentByCanonicalId = new LinkedHashMap<>();
        Map<String, T> uniqueByCanonicalId = new LinkedHashMap<>();
        Map<String, String> aliases = state.aliases(section);

        for (T item : items) {
            if (item == null) {
                throw new SemanticExtractionValidationException(
                        "semantic formal section contains a null item: " + section);
            }
            String alias = text(item.id);
            normalizer.accept(item);
            String canonicalId = identity.apply(item);
            item.id = canonicalId;
            String canonicalContent = StableSemanticId.canonicalJson(JSON.valueToTree(item));

            if (!alias.isBlank()) {
                String previousContent = aliasContent.putIfAbsent(alias, canonicalContent);
                if (previousContent != null && !previousContent.equals(canonicalContent)) {
                    throw new SemanticExtractionValidationException(
                            "semantic model alias resolves to conflicting canonical content in " + section);
                }
                String previousId = aliases.putIfAbsent(alias, canonicalId);
                if (previousId != null && !previousId.equals(canonicalId)) {
                    throw new SemanticExtractionValidationException(
                            "semantic model alias resolves to multiple canonical ids in " + section);
                }
            }
            aliases.putIfAbsent(canonicalId, canonicalId);
            String previous = contentByCanonicalId.putIfAbsent(canonicalId, canonicalContent);
            if (previous != null && !previous.equals(canonicalContent)) {
                throw new SemanticExtractionValidationException(
                        "canonical semantic id has conflicting content in " + section);
            }
            uniqueByCanonicalId.putIfAbsent(canonicalId, item);
        }

        items.clear();
        uniqueByCanonicalId.values().stream()
                .sorted(Comparator.comparing(item -> item.id))
                .forEach(items::add);
    }

    private void normalizeEvent(SemanticEvent event) {
        normalizeBase(event);
        event.inputs = normalizeStrings(event.inputs);
        event.outputs = normalizeStrings(event.outputs);
        event.inputEntityRefs = normalizeStrings(event.inputEntityRefs);
        event.outputEntityRefs = normalizeStrings(event.outputEntityRefs);
    }

    private void normalizeLineage(SemanticLineage lineage) {
        normalizeBase(lineage);
        lineage.from = normalizeStrings(lineage.from);
        lineage.fromPhysical = normalizeStrings(lineage.fromPhysical);
        lineage.sourceEntityRefs = normalizeStrings(lineage.sourceEntityRefs);
    }

    private void normalizeMetric(SemanticMetric metric) {
        normalizeBase(metric);
        metric.sourceFields = normalizeStrings(metric.sourceFields);
        metric.sourceEntityRefs = normalizeStrings(metric.sourceEntityRefs);
    }

    private void normalizeBase(SemanticItem item) {
        item.ownedGroundingRefs = normalizeStrings(item.ownedGroundingRefs);
        item.evidenceRefs = normalizeStrings(item.evidenceRefs);
    }

    private List<String> rewriteReferences(List<String> values, Map<String, String> aliases) {
        if (values == null) {
            return null;
        }
        return normalizeStrings(values.stream()
                .map(value -> aliases.getOrDefault(text(value), value))
                .toList());
    }

    private String rewriteReference(String value, Map<String, String> aliases) {
        if (blank(value)) {
            return value;
        }
        return aliases.getOrDefault(text(value), value);
    }

    private List<String> normalizeStrings(List<String> values) {
        if (values == null) {
            return null;
        }
        return values.stream()
                .map(this::text)
                .filter(value -> !value.isBlank())
                .distinct()
                .sorted()
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean blank(String value) {
        return text(value).isBlank();
    }

    static final class CanonicalizationState {
        private final Map<String, Map<String, String>> aliasesBySection = new LinkedHashMap<>();

        Map<String, String> aliases(String section) {
            return aliasesBySection.computeIfAbsent(textSection(section), ignored -> new LinkedHashMap<>());
        }

        private static String textSection(String section) {
            return section == null ? "" : section.trim();
        }
    }

    private record EntityNameIndex(Map<String, String> unique, Set<String> ambiguous) {
        static EntityNameIndex from(List<SemanticEntity> entities) {
            Map<String, String> unique = new LinkedHashMap<>();
            Set<String> ambiguous = new LinkedHashSet<>();
            for (SemanticEntity entity : entities) {
                String name = textValue(entity.name);
                if (name.isBlank() || ambiguous.contains(name)) {
                    continue;
                }
                String previous = unique.putIfAbsent(name, entity.id);
                if (previous != null && !previous.equals(entity.id)) {
                    unique.remove(name);
                    ambiguous.add(name);
                }
            }
            return new EntityNameIndex(Map.copyOf(unique), Set.copyOf(ambiguous));
        }

        String unique(String name) {
            String key = textValue(name);
            return ambiguous.contains(key) ? null : unique.get(key);
        }

        private static String textValue(String value) {
            return value == null ? "" : value.trim();
        }
    }
}
