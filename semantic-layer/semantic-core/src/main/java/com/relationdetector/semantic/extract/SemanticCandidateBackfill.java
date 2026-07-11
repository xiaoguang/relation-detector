package com.relationdetector.semantic.extract;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relationdetector.semantic.event.SemanticEventCandidate;
import com.relationdetector.semantic.extract.SemanticCandidateBundle.ReviewItemCandidate;
import com.relationdetector.semantic.extract.SemanticCandidateBundle.TripletCandidate;
import com.relationdetector.semantic.extract.model.SemanticEntity;
import com.relationdetector.semantic.extract.model.SemanticEvent;
import com.relationdetector.semantic.extract.model.SemanticExtractionDocument;
import com.relationdetector.semantic.extract.model.SemanticReviewItem;
import com.relationdetector.semantic.extract.model.SemanticTriplet;

/** Completes omitted LLM sections from deterministic candidate anchors. */
final class SemanticCandidateBackfill {
    private static final ObjectMapper JSON = new ObjectMapper();

    void apply(SemanticExtractionDocument document, JsonNode evidenceBundle) {
        if (evidenceBundle == null || !evidenceBundle.isObject()) {
            return;
        }
        SemanticCandidateBundle candidates = read(evidenceBundle);
        Map<String, String> namesByPhysical = entityNamesByPhysical(document.entities);
        backfillEvents(document.events, candidates.eventCandidates, namesByPhysical);
        backfillTriplets(document.triplets, candidates.tripletCandidates, namesByPhysical);
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

    private Map<String, String> entityNamesByPhysical(List<SemanticEntity> entities) {
        Map<String, String> result = new LinkedHashMap<>();
        for (SemanticEntity entity : entities) {
            if (present(entity.physicalName) && present(entity.name)) {
                result.put(entity.physicalName, entity.name);
            }
        }
        return result;
    }

    private void backfillEvents(
            List<SemanticEvent> events,
            List<SemanticEventCandidate> candidates,
            Map<String, String> namesByPhysical
    ) {
        Set<String> existing = new LinkedHashSet<>();
        for (SemanticEvent event : events) {
            if (present(event.eventCandidateRef)) {
                existing.add(event.eventCandidateRef);
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
            event.inputs = entityNames(candidate.inputEndpoints(), namesByPhysical);
            event.outputs = entityNames(candidate.outputEndpoints(), namesByPhysical);
            event.evidenceRefs = List.of(candidate.id());
            events.add(event);
        }
    }

    private void backfillTriplets(
            List<SemanticTriplet> triplets,
            List<TripletCandidate> candidates,
            Map<String, String> namesByPhysical
    ) {
        Set<String> existing = new LinkedHashSet<>();
        for (SemanticTriplet triplet : triplets) {
            if (present(triplet.candidateRef)) {
                existing.add(triplet.candidateRef);
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
            triplet.evidenceRefs = List.of(candidate.id());
            triplets.add(triplet);
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
            review.id = firstPresent(candidate.id(), "review:" + SemanticNormalizationSupport.slug(candidate.targetRef()));
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

    private List<String> entityNames(List<String> endpoints, Map<String, String> namesByPhysical) {
        Set<String> result = new LinkedHashSet<>();
        for (String endpoint : endpoints == null ? List.<String>of() : endpoints) {
            String name = entityName(endpoint, namesByPhysical);
            if (present(name)) {
                result.add(name);
            }
        }
        return new ArrayList<>(result);
    }

    private String entityName(String endpointOrTable, Map<String, String> namesByPhysical) {
        String table = firstPresent(endpointOrTable, "");
        if (table.contains(".")) {
            table = SemanticNormalizationSupport.tableOf(table);
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
}
