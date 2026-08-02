package com.relationdetector.semantic.extraction.normalization;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.relationdetector.semantic.model.PhysicalEndpointRef;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relationdetector.semantic.event.SemanticEventCandidate;
import com.relationdetector.semantic.extraction.normalization.SemanticCandidateBundle.ReviewItemCandidate;
import com.relationdetector.semantic.extraction.normalization.SemanticCandidateBundle.TripletCandidate;
import com.relationdetector.semantic.extraction.model.SemanticEntity;
import com.relationdetector.semantic.extraction.model.SemanticEvent;
import com.relationdetector.semantic.extraction.model.SemanticExtractionDocument;
import com.relationdetector.semantic.extraction.model.SemanticLineage;
import com.relationdetector.semantic.extraction.model.SemanticReviewItem;
import com.relationdetector.semantic.extraction.model.SemanticTriplet;

/**
 * CN: 用当前 evidence bundle 中由该 shard 拥有的确定性候选补齐模型遗漏 section；输入是已解析文档与 bundle，输出为原文档的受控补齐，禁止从 overlap 候选创建重复语义对象。
 * EN: Backfills model-omitted sections from deterministic candidates owned by the current shard. It mutates only the parsed document and never materializes read-only overlap candidates.
 */
public final class SemanticCandidateBackfill {
    private static final ObjectMapper JSON = new ObjectMapper();

    public void apply(SemanticExtractionDocument document, JsonNode evidenceBundle) {
        if (evidenceBundle == null || !evidenceBundle.isObject()) {
            return;
        }
        SemanticCandidateBundle candidates = read(evidenceBundle);
        retainOwnedCandidates(candidates, evidenceBundle.path("shardContext"));
        Map<String, String> entityRefsByPhysical = backfillTripletEntities(
                document.entities, candidates.tripletCandidates, evidenceBundle.path("tables"));
        backfillLineageEntities(
                document.entities, document.lineage, evidenceBundle.path("tables"), entityRefsByPhysical);
        Map<String, String> namesByPhysical = entityNamesByPhysical(document.entities);
        backfillEvents(document.events, candidates.eventCandidates, namesByPhysical, entityRefsByPhysical);
        backfillTriplets(
                document.triplets, candidates.tripletCandidates, namesByPhysical, entityRefsByPhysical);
        backfillReviewItems(document.reviewItems, candidates.reviewItemCandidates);
    }

    private SemanticCandidateBundle read(JsonNode evidenceBundle) {
        try {
            SemanticCandidateBundle candidates = JSON.treeToValue(evidenceBundle, SemanticCandidateBundle.class);
            candidates.ensureSections();
            return candidates;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("failed to read semantic candidate bundle", e);
        }
    }

    private void retainOwnedCandidates(SemanticCandidateBundle candidates, JsonNode shardContext) {
        if (!shardContext.isObject()) {
            return;
        }
        Set<String> owned = new LinkedHashSet<>();
        shardContext.path("ownedCandidateRefs").forEach(ref -> {
            if (ref.isTextual() && !ref.asText().isBlank()) {
                owned.add(ref.asText());
            }
        });
        candidates.eventCandidates.removeIf(candidate -> !owned.contains(candidate.id()));
        candidates.tripletCandidates.removeIf(candidate -> !owned.contains(candidate.id()));
        candidates.reviewItemCandidates.removeIf(candidate -> !owned.contains(candidate.id()));
    }

    private Map<String, String> entityNamesByPhysical(List<SemanticEntity> entities) {
        Map<String, String> result = new LinkedHashMap<>();
        for (SemanticEntity entity : entities) {
            if (present(entity.physicalName) && present(entity.name)) {
                result.put(entity.physicalName, entity.name);
            }
        }
        return result;
    }

    private void backfillLineageEntities(
            List<SemanticEntity> entities,
            List<SemanticLineage> lineages,
            JsonNode tableValues,
            Map<String, String> entityRefsByPhysical
    ) {
        Set<String> physicalTables = physicalTables(tableValues);
        for (SemanticLineage lineage : lineages) {
            List<String> endpoints = new ArrayList<>(lineage.fromPhysical == null
                    ? List.of()
                    : lineage.fromPhysical);
            if (present(lineage.toPhysical)) {
                endpoints.add(lineage.toPhysical);
            }
            for (String endpoint : endpoints) {
                String table = physicalTable(endpoint, physicalTables);
                if (!present(table) || entityRefsByPhysical.containsKey(table)) {
                    continue;
                }
                List<String> evidenceRefs = lineage.evidenceRefs().stream()
                        .filter(this::present)
                        .distinct()
                        .sorted()
                        .toList();
                SemanticEntity entity = new SemanticEntity();
                entity.id = SemanticCanonicalIdentity.entity(
                        table, table, "PHYSICAL_ENTITY", "物理实体候选", evidenceRefs)
                        .canonicalId();
                entity.name = table;
                entity.type = "物理实体候选";
                entity.machineType = "PHYSICAL_ENTITY";
                entity.physicalName = table;
                entity.ownedGroundingRefs = evidenceRefs;
                entity.evidenceRefs = evidenceRefs;
                entities.add(entity);
                entityRefsByPhysical.put(table, entity.id);
            }
        }
    }

    private Set<String> physicalTables(JsonNode tableValues) {
        Set<String> physicalTables = new LinkedHashSet<>();
        if (tableValues.isArray()) {
            tableValues.forEach(value -> {
                if (value.isTextual() && !value.asText().isBlank()) {
                    physicalTables.add(value.asText());
                }
            });
        }
        return physicalTables;
    }

    private Map<String, String> backfillTripletEntities(
            List<SemanticEntity> entities,
            List<TripletCandidate> candidates,
            JsonNode tableValues
    ) {
        Set<String> physicalTables = new LinkedHashSet<>();
        if (tableValues.isArray()) {
            tableValues.forEach(value -> {
                if (value.isTextual() && !value.asText().isBlank()) {
                    physicalTables.add(value.asText());
                }
            });
        }
        Map<String, SemanticEntity> entitiesByPhysical = new LinkedHashMap<>();
        for (SemanticEntity entity : entities) {
            if (present(entity.physicalName)) {
                entitiesByPhysical.putIfAbsent(entity.physicalName, entity);
            }
        }
        for (TripletCandidate candidate : candidates) {
            for (String endpoint : new String[] {candidate.subject(), candidate.object()}) {
                String table = physicalTable(endpoint, physicalTables);
                if (!present(table) || entitiesByPhysical.containsKey(table)) {
                    continue;
                }
                SemanticEntity entity = new SemanticEntity();
                entity.id = SemanticCanonicalIdentity.entity(
                        table, table, "PHYSICAL_ENTITY", "物理实体候选", List.of(candidate.id()))
                        .canonicalId();
                entity.name = table;
                entity.type = "物理实体候选";
                entity.machineType = "PHYSICAL_ENTITY";
                entity.physicalName = table;
                entity.ownedGroundingRefs = List.of(candidate.id());
                entity.evidenceRefs = List.of(candidate.id());
                entities.add(entity);
                entitiesByPhysical.put(table, entity);
            }
        }
        Map<String, String> result = new LinkedHashMap<>();
        entitiesByPhysical.forEach((physical, entity) -> result.put(
                physical,
                present(entity.id)
                        ? entity.id
                        : SemanticCanonicalIdentity.entity(
                                entity.physicalName,
                                entity.name,
                                entity.machineType,
                                entity.type,
                                entity.ownedGroundingRefs()).canonicalId()));
        return result;
    }

    private void backfillEvents(
            List<SemanticEvent> events,
            List<SemanticEventCandidate> candidates,
            Map<String, String> namesByPhysical,
            Map<String, String> entityRefsByPhysical
    ) {
        Map<String, SemanticEventCandidate> candidatesById = new LinkedHashMap<>();
        for (SemanticEventCandidate candidate : candidates) {
            if (present(candidate.id())) {
                candidatesById.put(candidate.id(), candidate);
            }
        }
        Set<String> existing = new LinkedHashSet<>();
        for (SemanticEvent event : events) {
            if (present(event.eventCandidateRef)) {
                existing.add(event.eventCandidateRef);
                SemanticEventCandidate candidate = candidatesById.get(event.eventCandidateRef);
                if (candidate != null && hasNoEventEndpoints(event)) {
                    applyEventEndpoints(event, candidate, namesByPhysical, entityRefsByPhysical);
                }
            }
        }
        for (SemanticEventCandidate candidate : candidates) {
            if (!present(candidate.id()) || !existing.add(candidate.id())) {
                continue;
            }
            SemanticEvent event = new SemanticEvent();
            event.name = firstPresent(candidate.readableNameHint(), candidate.sourceObjectName(), candidate.eventKind());
            event.type = "业务/数据处理事件";
            event.machineType = firstPresent(candidate.eventKind(), "Event");
            event.eventCandidateRef = candidate.id();
            event.description = firstPresent(candidate.businessActionHint(), "");
            applyEventEndpoints(event, candidate, namesByPhysical, entityRefsByPhysical);
            event.evidenceRefs = List.of(candidate.id());
            events.add(event);
        }
    }

    private boolean hasNoEventEndpoints(SemanticEvent event) {
        return empty(event.inputs)
                && empty(event.outputs)
                && empty(event.inputEntityRefs)
                && empty(event.outputEntityRefs);
    }

    private boolean empty(List<?> values) {
        return values == null || values.isEmpty();
    }

    private void applyEventEndpoints(
            SemanticEvent event,
            SemanticEventCandidate candidate,
            Map<String, String> namesByPhysical,
            Map<String, String> entityRefsByPhysical
    ) {
        EventEntities inputs = eventEntities(
                candidate.inputEndpoints(), namesByPhysical, entityRefsByPhysical);
        EventEntities outputs = eventEntities(
                candidate.outputEndpoints(), namesByPhysical, entityRefsByPhysical);
        event.inputs = inputs.names();
        event.outputs = outputs.names();
        event.inputEntityRefs = inputs.refs();
        event.outputEntityRefs = outputs.refs();
    }

    private EventEntities eventEntities(
            List<String> endpoints,
            Map<String, String> namesByPhysical,
            Map<String, String> entityRefsByPhysical
    ) {
        List<String> names = new ArrayList<>();
        List<String> refs = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String endpoint : endpoints == null ? List.<String>of() : endpoints) {
            String table = physicalTable(endpoint, entityRefsByPhysical.keySet());
            String ref = entityRefsByPhysical.get(table);
            if (!present(ref) || !seen.add(ref)) {
                continue;
            }
            names.add(namesByPhysical.getOrDefault(table, table));
            refs.add(ref);
        }
        return new EventEntities(List.copyOf(names), List.copyOf(refs));
    }

    private void backfillTriplets(
            List<SemanticTriplet> triplets,
            List<TripletCandidate> candidates,
            Map<String, String> namesByPhysical,
            Map<String, String> entityRefsByPhysical
    ) {
        Map<String, TripletCandidate> candidatesById = new LinkedHashMap<>();
        for (TripletCandidate candidate : candidates) {
            if (present(candidate.id())) candidatesById.put(candidate.id(), candidate);
        }
        Set<String> existing = new LinkedHashSet<>();
        for (SemanticTriplet triplet : triplets) {
            if (present(triplet.candidateRef)) {
                existing.add(triplet.candidateRef);
                linkTriplet(triplet, candidatesById.get(triplet.candidateRef), entityRefsByPhysical);
            }
        }
        for (TripletCandidate candidate : candidates) {
            if (!present(candidate.id()) || !existing.add(candidate.id())) {
                continue;
            }
            String subject = entityName(candidate.subject(), namesByPhysical);
            String object = entityName(candidate.object(), namesByPhysical);
            SemanticTriplet triplet = new SemanticTriplet();
            triplet.candidateRef = candidate.id();
            triplet.type = "语义三元组";
            triplet.machineType = firstPresent(candidate.type(), "");
            triplet.subject = subject;
            triplet.predicate = firstPresent(candidate.predicate(), "关联");
            triplet.object = object;
            triplet.readable = subject + " " + triplet.predicate + " " + object;
            linkTriplet(triplet, candidate, entityRefsByPhysical);
            triplet.ownedGroundingRefs = List.of(candidate.id());
            triplet.evidenceRefs = List.of(candidate.id());
            triplets.add(triplet);
        }
    }

    private void linkTriplet(
            SemanticTriplet triplet,
            TripletCandidate candidate,
            Map<String, String> entityRefsByPhysical
    ) {
        if (candidate == null) return;
        String subjectTable = physicalTable(candidate.subject(), entityRefsByPhysical.keySet());
        String objectTable = physicalTable(candidate.object(), entityRefsByPhysical.keySet());
        if (!present(triplet.subjectRef)) triplet.subjectRef = entityRefsByPhysical.get(subjectTable);
        if (!present(triplet.objectRef)) triplet.objectRef = entityRefsByPhysical.get(objectTable);
    }

    private String physicalTable(String endpointOrTable, Set<String> physicalTables) {
        if (!present(endpointOrTable)) return "";
        if (physicalTables.contains(endpointOrTable)) return endpointOrTable;
        try {
            String table = PhysicalEndpointRef.column(endpointOrTable).table();
            return physicalTables.contains(table) ? table : "";
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    private void backfillReviewItems(List<SemanticReviewItem> reviewItems, List<ReviewItemCandidate> candidates) {
        Set<String> existingTargets = new LinkedHashSet<>();
        for (SemanticReviewItem item : reviewItems) {
            String targetRef = firstPresent(item.targetRef, item.target);
            if (present(targetRef)) {
                existingTargets.add(targetRef);
            }
        }
        for (ReviewItemCandidate candidate : candidates) {
            if (!present(candidate.targetRef()) || !existingTargets.add(candidate.targetRef())) {
                continue;
            }
            SemanticReviewItem review = new SemanticReviewItem();
            review.id = firstPresent(candidate.id(), SemanticCanonicalIdentity.review(
                    candidate.targetRef(), candidate.targetSection(), candidate.type()));
            review.targetRef = candidate.targetRef();
            review.targetSection = firstPresent(candidate.targetSection(), "");
            review.type = firstPresent(candidate.type(), "REVIEW_NEEDED");
            review.severity = firstPresent(candidate.severity(), "MEDIUM");
            review.reason = firstPresent(candidate.reason(), "Candidate requires review.");
            review.evidenceRefs = candidate.evidenceRefs() == null || candidate.evidenceRefs().isEmpty()
                    ? List.of(firstPresent(candidate.id(), ""))
                    : List.copyOf(candidate.evidenceRefs());
            reviewItems.add(review);
        }
    }

    private String entityName(String endpointOrTable, Map<String, String> namesByPhysical) {
        String table = firstPresent(endpointOrTable, "");
        if (!namesByPhysical.containsKey(table) && table.contains(".")) {
            table = PhysicalEndpointRef.column(table).table();
        }
        return namesByPhysical.getOrDefault(table, table);
    }

    private String firstPresent(String... values) {
        for (String value : values) {
            if (present(value)) {
                return value;
            }
        }
        return "";
    }

    private boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private record EventEntities(List<String> names, List<String> refs) {
    }
}
