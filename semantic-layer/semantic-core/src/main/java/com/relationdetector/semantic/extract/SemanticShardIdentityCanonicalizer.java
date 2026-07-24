package com.relationdetector.semantic.extract;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.StableSemanticId;

/**
 * CN: 依据完整物理名或“规范名称、类型、owned grounding”统一跨 shard entity identity，并只重写明确的
 * semantic entity reference字段；输入是已片内归一化文档与owner plan，输出detached文档和重复语义review。
 *
 * EN: Canonicalizes cross-shard entity identity from a complete physical name or normalized name, type, and owned
 * grounding. It rewrites only typed semantic entity-reference fields and returns detached documents plus duplicate
 * semantic review records.
 */
final class SemanticShardIdentityCanonicalizer {
    private static final Set<String> ENTITY_REFERENCE_FIELDS = Set.of(
            "inputEntityRefs", "outputEntityRefs", "fromEntityRef", "toEntityRef",
            "sourceEntityRefs", "targetEntityRef", "ownerEntityRef", "dimensionEntityRef",
            "subjectRef", "objectRef", "targetRef");

    CanonicalizedShardResults canonicalize(
            List<SemanticShardNormalizedResult> results,
            SemanticShardPlan plan
    ) {
        Map<String, Set<String>> ownedByShard = ownedReferences(plan);
        List<DocumentState> documents = new ArrayList<>();
        Map<String, List<EntityVariant>> byIdentity = new LinkedHashMap<>();
        for (SemanticShardNormalizedResult result : results) {
            ObjectNode document = result.trustedDocument().deepCopy();
            DocumentState state = new DocumentState(
                    result.shardId(), document, new LinkedHashMap<>(), ownedByShard.getOrDefault(result.shardId(), Set.of()));
            documents.add(state);
            for (JsonNode value : document.path("entities")) {
                if (!value.isObject()) {
                    throw new SemanticExtractionValidationException("semantic entity must be an object");
                }
                ObjectNode entity = (ObjectNode) value;
                EntityIdentity identity = identity(entity, state.ownedReferences());
                String oldId = text(entity, "id");
                if (oldId.isBlank()) {
                    throw new SemanticExtractionValidationException("normalized semantic entity is missing id");
                }
                String previous = state.entityAliases().putIfAbsent(oldId, identity.canonicalId());
                if (previous != null && !previous.equals(identity.canonicalId())) {
                    throw new SemanticExtractionValidationException(
                            "one shard entity id resolves to multiple canonical identities");
                }
                byIdentity.computeIfAbsent(identity.key(), ignored -> new ArrayList<>())
                        .add(new EntityVariant(state, entity.deepCopy(), identity));
            }
        }

        Map<String, ObjectNode> canonicalEntities = new LinkedHashMap<>();
        for (String key : byIdentity.keySet().stream().sorted().toList()) {
            List<EntityVariant> variants = byIdentity.get(key);
            canonicalEntities.put(key, mergeEntity(key, variants));
        }
        for (DocumentState state : documents) {
            rewriteEntityReferences(state.document(), state.entityAliases());
            ArrayNode entities = state.document().withArray("entities");
            for (int index = 0; index < entities.size(); index++) {
                ObjectNode entity = (ObjectNode) entities.get(index);
                String canonicalId = state.entityAliases().get(text(entity, "id"));
                EntityVariant variant = variantByCanonicalId(byIdentity, canonicalId);
                entities.set(index, canonicalEntities.get(variant.identity().key()).deepCopy());
            }
        }

        List<ObjectNode> reviews = duplicateNameReviews(canonicalEntities, byIdentity);
        return new CanonicalizedShardResults(
                documents.stream()
                        .map(state -> new SemanticShardNormalizedResult(state.shardId(), state.document()))
                        .toList(),
                reviews);
    }

    private Map<String, Set<String>> ownedReferences(SemanticShardPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("semantic shard plan is required for canonical identity");
        }
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (SemanticShard shard : plan.shards()) {
            Set<String> owned = new LinkedHashSet<>(shard.ownedFactRefs());
            owned.addAll(shard.ownedCandidateRefs());
            result.put(shard.id(), Set.copyOf(owned));
        }
        return result;
    }

    private EntityIdentity identity(ObjectNode entity, Set<String> ownedReferences) {
        String physicalName = text(entity, "physicalName");
        String name = normalizeText(text(entity, "name"));
        String type = normalizeText(firstNonBlank(text(entity, "machineType"), text(entity, "type")));
        if (!physicalName.isBlank()) {
            return new EntityIdentity(
                    "physical:" + physicalName,
                    StableSemanticId.of("entity-physical", physicalName),
                    name,
                    type,
                    List.of(),
                    true);
        }
        List<String> grounding = ownedGrounding(entity.path("ownedGroundingRefs"), ownedReferences);
        if (grounding.isEmpty()) {
            throw new SemanticExtractionValidationException(
                    "pure business semantic entity requires owned grounding");
        }
        if (name.isBlank() || type.isBlank()) {
            throw new SemanticExtractionValidationException(
                    "pure business semantic entity requires name and type");
        }
        String signature = String.join("\u001f", grounding);
        String canonicalId = StableSemanticId.of("entity-business", name, type, signature);
        return new EntityIdentity(
                "business:" + name + "\u0000" + type + "\u0000" + signature,
                canonicalId,
                name,
                type,
                grounding,
                false);
    }

    private List<String> ownedGrounding(JsonNode evidenceRefs, Set<String> ownedReferences) {
        Set<String> result = new LinkedHashSet<>();
        if (evidenceRefs.isArray()) {
            evidenceRefs.forEach(reference -> {
                if (reference.isTextual() && ownedReferences.contains(reference.asText())) {
                    result.add(reference.asText());
                }
            });
        }
        return result.stream().sorted().toList();
    }

    private ObjectNode mergeEntity(String key, List<EntityVariant> variants) {
        if (variants.isEmpty()) {
            throw new IllegalStateException("semantic canonical identity has no variants");
        }
        validateCompatible(key, variants);
        List<EntityVariant> ordered = variants.stream()
                .sorted(Comparator.comparing(variant -> StableSemanticId.canonicalJson(variant.document())))
                .toList();
        ObjectNode result = ordered.get(0).document().deepCopy();
        result.put("id", ordered.get(0).identity().canonicalId());
        mergeReferences(result, variants, "ownedGroundingRefs");
        mergeReferences(result, variants, "evidenceRefs");
        return result;
    }

    private void mergeReferences(
            ObjectNode result,
            List<EntityVariant> variants,
            String field
    ) {
        Set<String> references = new LinkedHashSet<>();
        variants.forEach(variant -> variant.document().path(field).forEach(reference -> {
            if (reference.isTextual() && !reference.asText().isBlank()) {
                references.add(reference.asText());
            }
        }));
        ArrayNode refs = result.putArray(field);
        references.stream().sorted().forEach(refs::add);
    }

    private void validateCompatible(String key, List<EntityVariant> variants) {
        Set<String> machineTypes = new LinkedHashSet<>();
        Set<String> physicalNames = new LinkedHashSet<>();
        for (EntityVariant variant : variants) {
            String machineType = normalizeText(firstNonBlank(
                    text(variant.document(), "machineType"), text(variant.document(), "type")));
            if (!machineType.isBlank()) machineTypes.add(machineType);
            String physicalName = text(variant.document(), "physicalName");
            if (!physicalName.isBlank()) physicalNames.add(physicalName);
        }
        if (machineTypes.size() > 1 || physicalNames.size() > 1) {
            throw new SemanticExtractionValidationException(
                    "canonical semantic entity has incompatible structural content: " + key);
        }
    }

    private EntityVariant variantByCanonicalId(
            Map<String, List<EntityVariant>> variants,
            String canonicalId
    ) {
        return variants.values().stream()
                .flatMap(List::stream)
                .filter(variant -> variant.identity().canonicalId().equals(canonicalId))
                .findFirst()
                .orElseThrow(() -> new SemanticExtractionValidationException(
                        "canonical semantic entity alias cannot be resolved"));
    }

    private void rewriteEntityReferences(JsonNode node, Map<String, String> aliases) {
        if (node == null || aliases.isEmpty()) return;
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            object.fields().forEachRemaining(field -> {
                if (ENTITY_REFERENCE_FIELDS.contains(field.getKey())) {
                    rewriteReferenceValue(object, field.getKey(), field.getValue(), aliases);
                } else {
                    rewriteEntityReferences(field.getValue(), aliases);
                }
            });
        } else if (node.isArray()) {
            node.forEach(child -> rewriteEntityReferences(child, aliases));
        }
    }

    private void rewriteReferenceValue(
            ObjectNode owner,
            String field,
            JsonNode value,
            Map<String, String> aliases
    ) {
        if (value.isTextual()) {
            String replacement = aliases.get(value.asText());
            if (replacement != null) owner.put(field, replacement);
        } else if (value.isArray()) {
            ArrayNode rewritten = owner.putArray(field);
            value.forEach(item -> {
                if (item.isTextual()) {
                    rewritten.add(aliases.getOrDefault(item.asText(), item.asText()));
                } else {
                    rewritten.add(item.deepCopy());
                }
            });
        }
    }

    private List<ObjectNode> duplicateNameReviews(
            Map<String, ObjectNode> canonicalEntities,
            Map<String, List<EntityVariant>> variants
    ) {
        Map<String, Set<String>> identitiesByName = new LinkedHashMap<>();
        for (Map.Entry<String, List<EntityVariant>> entry : variants.entrySet()) {
            EntityIdentity identity = entry.getValue().get(0).identity();
            if (!identity.physical()) {
                identitiesByName.computeIfAbsent(identity.name() + "\u0000" + identity.type(),
                        ignored -> new LinkedHashSet<>()).add(entry.getKey());
            }
        }
        List<ObjectNode> reviews = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : identitiesByName.entrySet()) {
            if (entry.getValue().size() < 2) continue;
            for (String identityKey : entry.getValue().stream().sorted().toList()) {
                ObjectNode entity = canonicalEntities.get(identityKey);
                ObjectNode review = entity.objectNode();
                String entityId = text(entity, "id");
                review.put("id", StableSemanticId.of("review-potential-semantic-duplicate", entityId));
                review.put("targetRef", entityId);
                review.put("targetSection", "entities");
                review.put("type", "POTENTIAL_SEMANTIC_DUPLICATE");
                review.put("severity", "MEDIUM");
                review.put("reason", "同名业务实体具有不同的owned grounding，需要业务所有者确认是否合并。");
                review.set("evidenceRefs", entity.path("evidenceRefs").deepCopy());
                reviews.add(review);
            }
        }
        return List.copyOf(reviews);
    }

    private String normalizeText(String value) {
        return Normalizer.normalize(value == null ? "" : value.trim(), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
    }

    private String text(ObjectNode node, String field) {
        return node.path(field).asText("").trim();
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    record CanonicalizedShardResults(
            List<SemanticShardNormalizedResult> results,
            List<ObjectNode> generatedReviews
    ) {
        CanonicalizedShardResults {
            results = List.copyOf(results);
            generatedReviews = generatedReviews.stream().map(ObjectNode::deepCopy).toList();
        }
    }

    private record DocumentState(
            String shardId,
            ObjectNode document,
            Map<String, String> entityAliases,
            Set<String> ownedReferences
    ) {
    }

    private record EntityIdentity(
            String key,
            String canonicalId,
            String name,
            String type,
            List<String> grounding,
            boolean physical
    ) {
    }

    private record EntityVariant(
            DocumentState state,
            ObjectNode document,
            EntityIdentity identity
    ) {
    }
}
