package com.relationdetector.semantic.extract;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Normalizes LLM semantic extraction output into a ref-closed semantic document. */
public final class SemanticExtractionDocumentNormalizer {
    private static final ObjectMapper JSON = new ObjectMapper();

    public ObjectNode normalize(JsonNode rawDocument) {
        if (rawDocument == null || !rawDocument.isObject()) {
            throw new IllegalArgumentException("semantic extraction document must be a JSON object");
        }
        ObjectNode root = rawDocument.deepCopy();
        Map<String, String> entityByName = new LinkedHashMap<>();
        Map<String, String> entityByPhysical = new LinkedHashMap<>();
        Map<String, ObjectNode> graphNodes = new LinkedHashMap<>();
        Map<String, ObjectNode> graphEdges = new LinkedHashMap<>();
        Set<String> linkedEntities = new LinkedHashSet<>();
        ValidationState validationState = new ValidationState();

        normalizeEntities(root.withArray("entities"), entityByName, entityByPhysical, graphNodes, validationState);
        normalizeEvents(root.withArray("events"), entityByName, graphNodes, graphEdges, linkedEntities, validationState);
        normalizeRelations(root.withArray("relations"), entityByName, graphNodes, graphEdges, linkedEntities,
                validationState);
        normalizeLineage(root.withArray("lineage"), entityByName, entityByPhysical, graphNodes, graphEdges,
                linkedEntities, validationState);
        normalizeMetrics(root.withArray("metrics"), entityByPhysical, graphNodes, graphEdges, linkedEntities,
                validationState);
        normalizeDimensions(root.withArray("dimensions"), entityByName, entityByPhysical, graphNodes, graphEdges,
                linkedEntities, validationState);
        normalizeTriplets(root.withArray("triplets"), entityByName, graphNodes, graphEdges, linkedEntities,
                validationState);
        validationState.generatedReviewItemCount += generateReviewItemsForReviewNeeded(root);
        normalizeReviewItems(root.withArray("reviewItems"), graphNodes, graphEdges, validationState);
        root.set("semanticGraph", graph(graphNodes, graphEdges));
        root.set("validation", validation(root.withArray("entities"), linkedEntities, validationState));
        return root;
    }

    public ObjectNode normalize(JsonNode rawDocument, JsonNode evidenceBundle) {
        if (rawDocument == null || !rawDocument.isObject()) {
            throw new IllegalArgumentException("semantic extraction document must be a JSON object");
        }
        if (evidenceBundle == null || !evidenceBundle.isObject()) {
            return normalize(rawDocument);
        }
        return normalize(withCandidateBackfill((ObjectNode) rawDocument.deepCopy(), evidenceBundle));
    }

    private ObjectNode withCandidateBackfill(ObjectNode root, JsonNode evidenceBundle) {
        Map<String, String> entityNamesByPhysical = entityNamesByPhysical(root.withArray("entities"));
        backfillEventCandidates(root.withArray("events"), evidenceBundle.path("eventCandidates"), entityNamesByPhysical);
        backfillTripletCandidates(root.withArray("triplets"), evidenceBundle.path("tripletCandidates"), entityNamesByPhysical);
        backfillReviewItemCandidates(root.withArray("reviewItems"), evidenceBundle.path("reviewItemCandidates"));
        return root;
    }

    private Map<String, String> entityNamesByPhysical(ArrayNode entities) {
        Map<String, String> result = new LinkedHashMap<>();
        for (JsonNode node : entities) {
            String physical = node.path("physicalName").asText("");
            String name = node.path("name").asText("");
            if (!physical.isBlank() && !name.isBlank()) {
                result.put(physical, name);
            }
        }
        return result;
    }

    private void backfillEventCandidates(
            ArrayNode events,
            JsonNode candidates,
            Map<String, String> entityNamesByPhysical
    ) {
        Set<String> existing = new LinkedHashSet<>();
        for (JsonNode event : events) {
            String ref = event.path("eventCandidateRef").asText("");
            if (!ref.isBlank()) {
                existing.add(ref);
            }
        }
        if (!candidates.isArray()) {
            return;
        }
        for (JsonNode candidate : candidates) {
            String id = candidate.path("id").asText("");
            if (id.isBlank() || existing.contains(id)) {
                continue;
            }
            ObjectNode event = events.addObject();
            event.put("name", nonBlank(candidate.path("readableNameHint").asText(""),
                    nonBlank(candidate.path("sourceObjectName").asText(""), candidate.path("eventKind").asText("Event"))));
            event.put("type", "业务/数据处理事件");
            event.put("machineType", candidate.path("eventKind").asText("Event"));
            event.put("eventCandidateRef", id);
            event.put("description", candidate.path("businessActionHint").asText(""));
            event.set("inputs", entityNames(candidate.path("inputEndpoints"), entityNamesByPhysical));
            event.set("outputs", entityNames(candidate.path("outputEndpoints"), entityNamesByPhysical));
            event.set("evidenceRefs", stringArray(List.of(id)));
            existing.add(id);
        }
    }

    private void backfillTripletCandidates(
            ArrayNode triplets,
            JsonNode candidates,
            Map<String, String> entityNamesByPhysical
    ) {
        Set<String> existing = new LinkedHashSet<>();
        for (JsonNode triplet : triplets) {
            String ref = triplet.path("candidateRef").asText("");
            if (!ref.isBlank()) {
                existing.add(ref);
            }
        }
        if (!candidates.isArray()) {
            return;
        }
        for (JsonNode candidate : candidates) {
            String id = candidate.path("id").asText("");
            if (id.isBlank() || existing.contains(id)) {
                continue;
            }
            String subject = entityName(candidate.path("subject").asText(""), entityNamesByPhysical);
            String object = entityName(candidate.path("object").asText(""), entityNamesByPhysical);
            ObjectNode triplet = triplets.addObject();
            triplet.put("candidateRef", id);
            triplet.put("type", "语义三元组");
            triplet.put("machineType", candidate.path("type").asText(""));
            triplet.put("subject", subject);
            triplet.put("predicate", candidate.path("predicate").asText("关联"));
            triplet.put("object", object);
            triplet.put("readable", subject + " " + triplet.path("predicate").asText("关联") + " " + object);
            triplet.set("evidenceRefs", stringArray(List.of(id)));
            existing.add(id);
        }
    }

    private void backfillReviewItemCandidates(ArrayNode reviewItems, JsonNode candidates) {
        Set<String> existingTargets = new LinkedHashSet<>();
        for (JsonNode reviewItem : reviewItems) {
            String targetRef = reviewItem.path("targetRef").asText(reviewItem.path("target").asText(""));
            if (!targetRef.isBlank()) {
                existingTargets.add(targetRef);
            }
        }
        if (!candidates.isArray()) {
            return;
        }
        for (JsonNode candidate : candidates) {
            String targetRef = candidate.path("targetRef").asText("");
            if (targetRef.isBlank() || existingTargets.contains(targetRef)) {
                continue;
            }
            ObjectNode review = reviewItems.addObject();
            review.put("id", candidate.path("id").asText("review:" + slug(targetRef)));
            review.put("targetRef", targetRef);
            review.put("targetSection", candidate.path("targetSection").asText(""));
            review.put("type", candidate.path("type").asText("REVIEW_NEEDED"));
            review.put("severity", candidate.path("severity").asText("MEDIUM"));
            review.put("reason", candidate.path("reason").asText("Candidate requires review."));
            review.set("evidenceRefs", evidenceRefsOrCandidate(candidate));
            existingTargets.add(targetRef);
        }
    }

    private ArrayNode entityNames(JsonNode endpoints, Map<String, String> entityNamesByPhysical) {
        ArrayNode result = JSON.createArrayNode();
        if (!endpoints.isArray()) {
            return result;
        }
        for (JsonNode endpoint : endpoints) {
            String name = entityName(endpoint.asText(""), entityNamesByPhysical);
            if (!name.isBlank() && !contains(result, name)) {
                result.add(name);
            }
        }
        return result;
    }

    private String entityName(String endpointOrTable, Map<String, String> entityNamesByPhysical) {
        String table = endpointOrTable == null ? "" : endpointOrTable;
        if (table.contains(".")) {
            table = tableOf(table);
        }
        return entityNamesByPhysical.getOrDefault(table, table);
    }

    private ArrayNode evidenceRefsOrCandidate(JsonNode candidate) {
        ArrayNode refs = JSON.createArrayNode();
        if (candidate.path("evidenceRefs").isArray() && !candidate.path("evidenceRefs").isEmpty()) {
            candidate.path("evidenceRefs").forEach(ref -> refs.add(ref.asText("")));
        } else {
            refs.add(candidate.path("id").asText(""));
        }
        return refs;
    }

    private ArrayNode stringArray(List<String> values) {
        ArrayNode result = JSON.createArrayNode();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                result.add(value);
            }
        }
        return result;
    }

    private void normalizeEntities(
            ArrayNode entities,
            Map<String, String> entityByName,
            Map<String, String> entityByPhysical,
            Map<String, ObjectNode> graphNodes,
            ValidationState validationState
    ) {
        for (JsonNode node : entities) {
            if (!node.isObject()) {
                continue;
            }
            ObjectNode entity = (ObjectNode) node;
            String id = nonBlank(entity.path("id").asText(""), "entity:" + slug(entityKey(entity)));
            entity.put("id", id);
            requireEvidence(validationState, "entity", id, entity);
            entityByName.put(entity.path("name").asText(""), id);
            entityByPhysical.put(entity.path("physicalName").asText(""), id);
            graphNodes.put(id, graphNode(id, "Entity", entity.path("name").asText(""), entity.path("type").asText(""),
                    entity.path("evidenceRefs")));
        }
    }

    private void normalizeEvents(
            ArrayNode events,
            Map<String, String> entityByName,
            Map<String, ObjectNode> graphNodes,
            Map<String, ObjectNode> graphEdges,
            Set<String> linkedEntities,
            ValidationState validationState
    ) {
        for (JsonNode node : events) {
            if (!node.isObject()) {
                continue;
            }
            ObjectNode event = (ObjectNode) node;
            String id = nonBlank(event.path("id").asText(""), "event:" + slug(eventKey(event)));
            event.put("id", id);
            requireEvidence(validationState, "event", id, event);
            requireEventCandidateRef(validationState, id, event);
            graphNodes.put(id, graphNode(id, "Event", event.path("name").asText(""), event.path("type").asText(""),
                    event.path("evidenceRefs")));
            JsonNode originalInputRefs = event.path("inputEntityRefs").deepCopy();
            JsonNode originalOutputRefs = event.path("outputEntityRefs").deepCopy();
            ArrayNode inputRefs = event.putArray("inputEntityRefs");
            for (JsonNode inputRef : originalInputRefs) {
                String ref = inputRef.asText("");
                addKnownEntityRef(inputRefs, ref, entityByName, linkedEntities);
                addEdge(graphEdges, "event-input", id, knownEntityRef(ref, entityByName), "EVENT_INPUT",
                        event.path("evidenceRefs"));
                requireResolved(validationState, id, "inputEntityRefs", ref, knownEntityRef(ref, entityByName),
                        "entity");
            }
            for (JsonNode input : event.path("inputs")) {
                String ref = entityByName.get(input.asText(""));
                addEntityRefIfAbsent(inputRefs, ref, linkedEntities);
                addEdge(graphEdges, "event-input", id, ref, "EVENT_INPUT", event.path("evidenceRefs"));
                requireResolved(validationState, id, "inputs", input.asText(""), ref, "entity");
            }
            ArrayNode outputRefs = event.putArray("outputEntityRefs");
            for (JsonNode outputRef : originalOutputRefs) {
                String ref = outputRef.asText("");
                addKnownEntityRef(outputRefs, ref, entityByName, linkedEntities);
                addEdge(graphEdges, "event-output", id, knownEntityRef(ref, entityByName), "EVENT_OUTPUT",
                        event.path("evidenceRefs"));
                requireResolved(validationState, id, "outputEntityRefs", ref, knownEntityRef(ref, entityByName),
                        "entity");
            }
            for (JsonNode output : event.path("outputs")) {
                String ref = entityByName.get(output.asText(""));
                addEntityRefIfAbsent(outputRefs, ref, linkedEntities);
                addEdge(graphEdges, "event-output", id, ref, "EVENT_OUTPUT", event.path("evidenceRefs"));
                requireResolved(validationState, id, "outputs", output.asText(""), ref, "entity");
            }
        }
    }

    private void normalizeRelations(
            ArrayNode relations,
            Map<String, String> entityByName,
            Map<String, ObjectNode> graphNodes,
            Map<String, ObjectNode> graphEdges,
            Set<String> linkedEntities,
            ValidationState validationState
    ) {
        int index = 0;
        for (JsonNode node : relations) {
            if (!node.isObject()) {
                continue;
            }
            ObjectNode relation = (ObjectNode) node;
            String fromRef = entityByName.get(relation.path("from").asText(""));
            String toRef = entityByName.get(relation.path("to").asText(""));
            String id = nonBlank(relation.path("id").asText(""),
                    "relation:" + slug(relation.path("from").asText("")) + ":" + slug(relation.path("to").asText(""))
                            + ":" + index);
            relation.put("id", id);
            requireEvidence(validationState, "relation", id, relation);
            putIfPresent(relation, "fromEntityRef", fromRef);
            putIfPresent(relation, "toEntityRef", toRef);
            requireResolved(validationState, id, "from", relation.path("from").asText(""), fromRef, "entity");
            requireResolved(validationState, id, "to", relation.path("to").asText(""), toRef, "entity");
            graphNodes.put(id, graphNode(id, "Relation", relation.path("type").asText(""),
                    relation.path("machineType").asText(""), relation.path("evidenceRefs")));
            addEdge(graphEdges, "relation-from", id, fromRef, "RELATION_FROM", relation.path("evidenceRefs"));
            addEdge(graphEdges, "relation-to", id, toRef, "RELATION_TO", relation.path("evidenceRefs"));
            addEdge(graphEdges, "relation", fromRef, toRef, relation.path("type").asText("RELATES_TO"),
                    relation.path("evidenceRefs"));
            addLinked(linkedEntities, fromRef, toRef);
            index++;
        }
    }

    private void normalizeLineage(
            ArrayNode lineages,
            Map<String, String> entityByName,
            Map<String, String> entityByPhysical,
            Map<String, ObjectNode> graphNodes,
            Map<String, ObjectNode> graphEdges,
            Set<String> linkedEntities,
            ValidationState validationState
    ) {
        int index = 0;
        for (JsonNode node : lineages) {
            if (!node.isObject()) {
                continue;
            }
            ObjectNode lineage = (ObjectNode) node;
            String id = nonBlank(lineage.path("id").asText(""),
                    "lineage:" + slug(lineage.path("toPhysical").asText(lineage.path("to").asText(""))) + ":" + index);
            lineage.put("id", id);
            requireEvidence(validationState, "lineage", id, lineage);
            graphNodes.put(id, graphNode(id, "Lineage", lineage.path("to").asText(""), lineage.path("transform").asText(""),
                    lineage.path("evidenceRefs")));
            ArrayNode sourceRefs = lineage.putArray("sourceEntityRefs");
            for (JsonNode source : lineage.path("fromPhysical")) {
                String sourceRef = entityByPhysical.get(tableOf(source.asText("")));
                addEntityRef(sourceRefs, sourceRef, linkedEntities);
                addEdge(graphEdges, "lineage-source", id, sourceRef, "LINEAGE_SOURCE", lineage.path("evidenceRefs"));
                requireResolved(validationState, id, "fromPhysical", source.asText(""), sourceRef, "entity");
            }
            String targetRef = entityByPhysical.get(tableOf(lineage.path("toPhysical").asText("")));
            putIfPresent(lineage, "targetEntityRef", targetRef);
            addEntityRef(null, targetRef, linkedEntities);
            addEdge(graphEdges, "lineage-target", id, targetRef, "LINEAGE_TARGET", lineage.path("evidenceRefs"));
            requireResolved(validationState, id, "toPhysical", lineage.path("toPhysical").asText(""), targetRef,
                    "entity");
            index++;
        }
    }

    private void normalizeMetrics(
            ArrayNode metrics,
            Map<String, String> entityByPhysical,
            Map<String, ObjectNode> graphNodes,
            Map<String, ObjectNode> graphEdges,
            Set<String> linkedEntities,
            ValidationState validationState
    ) {
        for (JsonNode node : metrics) {
            if (!node.isObject()) {
                continue;
            }
            ObjectNode metric = (ObjectNode) node;
            String id = nonBlank(metric.path("id").asText(""), "metric:" + slug(metric.path("physicalField").asText(metric.path("name").asText(""))));
            metric.put("id", id);
            requireEvidence(validationState, "metric", id, metric);
            String ownerRef = entityByPhysical.get(tableOf(metric.path("physicalField").asText("")));
            putIfPresent(metric, "ownerEntityRef", ownerRef);
            addEntityRef(null, ownerRef, linkedEntities);
            requireResolved(validationState, id, "physicalField", metric.path("physicalField").asText(""), ownerRef,
                    "entity");
            ArrayNode sourceRefs = metric.putArray("sourceEntityRefs");
            for (JsonNode source : metric.path("sourceFields")) {
                String sourceRef = entityByPhysical.get(tableOf(source.asText("")));
                addEntityRef(sourceRefs, sourceRef, linkedEntities);
                requireResolved(validationState, id, "sourceFields", source.asText(""), sourceRef, "entity");
            }
            graphNodes.put(id, graphNode(id, "Metric", metric.path("name").asText(""), metric.path("type").asText(""),
                    metric.path("evidenceRefs")));
            addEdge(graphEdges, "metric-owner", id, ownerRef, "METRIC_OWNER", metric.path("evidenceRefs"));
        }
    }

    private void normalizeDimensions(
            ArrayNode dimensions,
            Map<String, String> entityByName,
            Map<String, String> entityByPhysical,
            Map<String, ObjectNode> graphNodes,
            Map<String, ObjectNode> graphEdges,
            Set<String> linkedEntities,
            ValidationState validationState
    ) {
        for (JsonNode node : dimensions) {
            if (!node.isObject()) {
                continue;
            }
            ObjectNode dimension = (ObjectNode) node;
            String id = nonBlank(dimension.path("id").asText(""),
                    "dimension:" + slug(dimension.path("physicalField").asText(dimension.path("name").asText(""))));
            dimension.put("id", id);
            requireEvidence(validationState, "dimension", id, dimension);
            String ownerRef = entityByPhysical.get(tableOf(dimension.path("physicalField").asText("")));
            String dimensionRef = entityByPhysical.get(dimension.path("dimensionTable").asText(""));
            if (dimensionRef == null) {
                dimensionRef = entityByName.get(dimension.path("name").asText(""));
            }
            putIfPresent(dimension, "ownerEntityRef", ownerRef);
            putIfPresent(dimension, "dimensionEntityRef", dimensionRef);
            addLinked(linkedEntities, ownerRef, dimensionRef);
            requireResolved(validationState, id, "physicalField", dimension.path("physicalField").asText(""), ownerRef,
                    "entity");
            requireResolved(validationState, id, "dimensionTable", dimension.path("dimensionTable").asText(""),
                    dimensionRef, "entity");
            graphNodes.put(id, graphNode(id, "Dimension", dimension.path("name").asText(""), dimension.path("type").asText(""),
                    dimension.path("evidenceRefs")));
            addEdge(graphEdges, "dimension-owner", id, ownerRef, "DIMENSION_OWNER", dimension.path("evidenceRefs"));
            addEdge(graphEdges, "dimension-target", id, dimensionRef, "DIMENSION_TARGET", dimension.path("evidenceRefs"));
        }
    }

    private void normalizeTriplets(
            ArrayNode triplets,
            Map<String, String> entityByName,
            Map<String, ObjectNode> graphNodes,
            Map<String, ObjectNode> graphEdges,
            Set<String> linkedEntities,
            ValidationState validationState
    ) {
        int index = 0;
        for (JsonNode node : triplets) {
            if (!node.isObject()) {
                continue;
            }
            ObjectNode triplet = (ObjectNode) node;
            String id = nonBlank(triplet.path("id").asText(""),
                    "triplet:" + slug(triplet.path("subject").asText("")) + ":" + slug(triplet.path("predicate").asText(""))
                            + ":" + slug(triplet.path("object").asText("")) + ":" + index);
            triplet.put("id", id);
            requireEvidence(validationState, "triplet", id, triplet);
            requireTripletCandidateRef(validationState, id, triplet);
            String subjectRef = entityByName.get(triplet.path("subject").asText(""));
            String objectRef = entityByName.get(triplet.path("object").asText(""));
            putIfPresent(triplet, "subjectRef", subjectRef);
            putIfPresent(triplet, "objectRef", objectRef);
            requireResolved(validationState, id, "subject", triplet.path("subject").asText(""), subjectRef, "entity");
            requireResolved(validationState, id, "object", triplet.path("object").asText(""), objectRef, "entity");
            addLinked(linkedEntities, subjectRef, objectRef);
            graphNodes.put(id, graphNode(id, "Triplet", triplet.path("readable").asText(""), triplet.path("predicate").asText(""),
                    triplet.path("evidenceRefs")));
            addEdge(graphEdges, "triplet-subject", id, subjectRef, "TRIPLET_SUBJECT", triplet.path("evidenceRefs"));
            addEdge(graphEdges, "triplet-object", id, objectRef, "TRIPLET_OBJECT", triplet.path("evidenceRefs"));
            index++;
        }
    }

    private int generateReviewItemsForReviewNeeded(ObjectNode root) {
        ArrayNode reviewItems = root.withArray("reviewItems");
        Set<String> existingTargets = new LinkedHashSet<>();
        for (JsonNode reviewItem : reviewItems) {
            String targetRef = reviewItem.path("targetRef").asText(reviewItem.path("target").asText(""));
            if (!targetRef.isBlank()) {
                existingTargets.add(targetRef);
            }
        }
        int generated = 0;
        generated += generateReviewItemsForSection(reviewItems, existingTargets, root.withArray("entities"), "entities");
        generated += generateReviewItemsForSection(reviewItems, existingTargets, root.withArray("events"), "events");
        generated += generateReviewItemsForSection(reviewItems, existingTargets, root.withArray("relations"), "relations");
        generated += generateReviewItemsForSection(reviewItems, existingTargets, root.withArray("lineage"), "lineage");
        generated += generateReviewItemsForSection(reviewItems, existingTargets, root.withArray("metrics"), "metrics");
        generated += generateReviewItemsForSection(reviewItems, existingTargets, root.withArray("dimensions"), "dimensions");
        generated += generateReviewItemsForSection(reviewItems, existingTargets, root.withArray("triplets"), "triplets");
        return generated;
    }

    private int generateReviewItemsForSection(
            ArrayNode reviewItems,
            Set<String> existingTargets,
            ArrayNode sectionItems,
            String targetSection
    ) {
        int generated = 0;
        for (JsonNode node : sectionItems) {
            if (!node.isObject() || !"REVIEW_NEEDED".equalsIgnoreCase(node.path("reviewStatus").asText(""))) {
                continue;
            }
            ObjectNode item = (ObjectNode) node;
            String targetRef = item.path("id").asText("");
            if (targetRef.isBlank() || existingTargets.contains(targetRef)) {
                continue;
            }
            ObjectNode review = reviewItems.addObject();
            review.put("id", "review:auto:" + slug(targetRef));
            review.put("targetRef", targetRef);
            review.put("targetSection", targetSection);
            review.put("type", "REVIEW_NEEDED");
            review.put("severity", item.path("severity").asText("MEDIUM"));
            review.put("reason", "Semantic item is marked REVIEW_NEEDED and requires business or data owner review.");
            ArrayNode evidenceRefs = review.putArray("evidenceRefs");
            if (item.path("evidenceRefs").isArray() && !item.path("evidenceRefs").isEmpty()) {
                item.path("evidenceRefs").forEach(ref -> evidenceRefs.add(ref.asText("")));
            } else {
                evidenceRefs.add(targetRef);
            }
            existingTargets.add(targetRef);
            generated++;
        }
        return generated;
    }

    private void normalizeReviewItems(ArrayNode reviewItems, Map<String, ObjectNode> graphNodes,
            Map<String, ObjectNode> graphEdges,
            ValidationState validationState) {
        int index = 0;
        for (JsonNode node : reviewItems) {
            if (!node.isObject()) {
                continue;
            }
            ObjectNode item = (ObjectNode) node;
            String targetRef = item.path("targetRef").asText(item.path("target").asText(""));
            String id = nonBlank(item.path("id").asText(""), "review:" + slug(targetRef) + ":" + index);
            item.put("id", id);
            putIfPresent(item, "targetRef", targetRef);
            if (!item.hasNonNull("targetSection")) {
                item.put("targetSection", item.path("section").asText(""));
            }
            requireEvidence(validationState, "reviewItem", id, item);
            graphNodes.put(id, graphNode(id, "ReviewItem", targetRef, "REVIEW_NEEDED",
                    item.path("evidenceRefs")));
            addEdge(graphEdges, "review-target", id, targetRef, "REVIEW_TARGET", item.path("evidenceRefs"));
            index++;
        }
    }

    private ObjectNode graph(Map<String, ObjectNode> nodes, Map<String, ObjectNode> edges) {
        ObjectNode graph = JSON.createObjectNode();
        ArrayNode nodeArray = graph.putArray("nodes");
        nodes.values().forEach(nodeArray::add);
        ArrayNode edgeArray = graph.putArray("edges");
        edges.values().forEach(edgeArray::add);
        graph.putObject("summary").put("nodeCount", nodes.size()).put("edgeCount", edges.size());
        return graph;
    }

    private ObjectNode validation(ArrayNode entities, Set<String> linkedEntities, ValidationState validationState) {
        ObjectNode validation = JSON.createObjectNode();
        ArrayNode isolated = validation.putArray("isolatedEntities");
        for (JsonNode entity : entities) {
            String id = entity.path("id").asText("");
            if (!id.isBlank() && !linkedEntities.contains(id)) {
                ObjectNode item = isolated.addObject();
                item.put("id", id);
                item.put("name", entity.path("name").asText(""));
                item.put("physicalName", entity.path("physicalName").asText(""));
                item.put("reason", "Entity has evidence but is not referenced by event, relation, lineage, metric, dimension, or triplet sections.");
            }
        }
        ArrayNode unresolved = validation.putArray("unresolvedReferences");
        validationState.unresolvedReferences.values().forEach(unresolved::add);
        ArrayNode missingEvidence = validation.putArray("missingEvidenceRefs");
        validationState.missingEvidenceRefs.values().forEach(missingEvidence::add);
        validation.put("generatedReviewItemCount", validationState.generatedReviewItemCount);
        validation.put("isRefClosed", isolated.isEmpty() && unresolved.isEmpty() && missingEvidence.isEmpty());
        return validation;
    }

    private void requireEvidence(ValidationState validationState, String section, String id, ObjectNode item) {
        JsonNode evidenceRefs = item.path("evidenceRefs");
        if (evidenceRefs.isArray() && !evidenceRefs.isEmpty()) {
            return;
        }
        String key = section + ":" + id;
        validationState.missingEvidenceRefs.computeIfAbsent(key, ignored -> {
            ObjectNode missing = JSON.createObjectNode();
            missing.put("section", section);
            missing.put("id", id);
            missing.put("reason", "Semantic item has no evidenceRefs.");
            return missing;
        });
    }

    private void requireEventCandidateRef(ValidationState validationState, String id, ObjectNode event) {
        if (!event.path("eventCandidateRef").asText("").isBlank()) {
            return;
        }
        String key = "eventCandidateRef:" + id;
        validationState.unresolvedReferences.computeIfAbsent(key, ignored -> {
            ObjectNode missing = JSON.createObjectNode();
            missing.put("id", id);
            missing.put("field", "eventCandidateRef");
            missing.put("value", "");
            missing.put("expectedRefKind", "eventCandidate");
            missing.put("reason", "Event must reference a deterministic eventCandidate.");
            return missing;
        });
    }

    private void requireTripletCandidateRef(ValidationState validationState, String id, ObjectNode triplet) {
        if (!triplet.path("candidateRef").asText("").isBlank()) {
            return;
        }
        String key = "tripletCandidateRef:" + id;
        validationState.unresolvedReferences.computeIfAbsent(key, ignored -> {
            ObjectNode missing = JSON.createObjectNode();
            missing.put("id", id);
            missing.put("field", "candidateRef");
            missing.put("value", "");
            missing.put("expectedRefKind", "tripletCandidate");
            missing.put("reason", "Triplet must reference a deterministic tripletCandidate.");
            return missing;
        });
    }

    private void requireResolved(
            ValidationState validationState,
            String ownerId,
            String field,
            String value,
            String resolvedRef,
            String expectedRefKind
    ) {
        if (value == null || value.isBlank() || (resolvedRef != null && !resolvedRef.isBlank())) {
            return;
        }
        String key = ownerId + ":" + field + ":" + value;
        validationState.unresolvedReferences.computeIfAbsent(key, ignored -> {
            ObjectNode unresolved = JSON.createObjectNode();
            unresolved.put("id", ownerId);
            unresolved.put("field", field);
            unresolved.put("value", value);
            unresolved.put("expectedRefKind", expectedRefKind);
            unresolved.put("reason", "Referenced semantic item could not be resolved to a stable id.");
            return unresolved;
        });
    }

    private ObjectNode graphNode(String id, String kind, String label, String type, JsonNode evidenceRefs) {
        ObjectNode node = JSON.createObjectNode();
        node.put("id", id);
        node.put("kind", kind);
        node.put("label", label);
        node.put("type", type);
        node.set("evidenceRefs", evidenceArray(evidenceRefs));
        return node;
    }

    private void addEdge(
            Map<String, ObjectNode> graphEdges,
            String prefix,
            String source,
            String target,
            String type,
            JsonNode evidenceRefs
    ) {
        if (source == null || source.isBlank() || target == null || target.isBlank()) {
            return;
        }
        String id = prefix + ":" + source + "->" + target + ":" + slug(type);
        ObjectNode edge = JSON.createObjectNode();
        edge.put("id", id);
        edge.put("source", source);
        edge.put("target", target);
        edge.put("type", type);
        edge.set("evidenceRefs", evidenceArray(evidenceRefs));
        graphEdges.putIfAbsent(id, edge);
    }

    private ArrayNode evidenceArray(JsonNode evidenceRefs) {
        ArrayNode result = JSON.createArrayNode();
        if (evidenceRefs != null && evidenceRefs.isArray()) {
            evidenceRefs.forEach(item -> result.add(item.asText("")));
        }
        return result;
    }

    private void addEntityRef(ArrayNode refs, String ref, Set<String> linkedEntities) {
        if (ref == null || ref.isBlank()) {
            return;
        }
        if (refs != null) {
            refs.add(ref);
        }
        linkedEntities.add(ref);
    }

    private void addKnownEntityRef(
            ArrayNode refs,
            String ref,
            Map<String, String> entityByName,
            Set<String> linkedEntities
    ) {
        addEntityRefIfAbsent(refs, knownEntityRef(ref, entityByName), linkedEntities);
    }

    private String knownEntityRef(String ref, Map<String, String> entityByName) {
        if (ref == null || ref.isBlank()) {
            return null;
        }
        return entityByName.containsValue(ref) ? ref : null;
    }

    private void addEntityRefIfAbsent(ArrayNode refs, String ref, Set<String> linkedEntities) {
        if (ref == null || ref.isBlank()) {
            return;
        }
        if (refs != null && !contains(refs, ref)) {
            refs.add(ref);
        }
        linkedEntities.add(ref);
    }

    private boolean contains(ArrayNode refs, String ref) {
        for (JsonNode node : refs) {
            if (ref.equals(node.asText())) {
                return true;
            }
        }
        return false;
    }

    private void addLinked(Set<String> linkedEntities, String... refs) {
        for (String ref : refs) {
            if (ref != null && !ref.isBlank()) {
                linkedEntities.add(ref);
            }
        }
    }

    private void putIfPresent(ObjectNode node, String field, String value) {
        if (value != null && !value.isBlank()) {
            node.put(field, value);
        }
    }

    private String entityKey(ObjectNode entity) {
        String physical = entity.path("physicalName").asText("");
        return physical.isBlank() ? entity.path("name").asText("entity") : physical;
    }

    private String eventKey(ObjectNode event) {
        String physical = event.path("physicalName").asText("");
        return physical.isBlank() ? event.path("name").asText("event") : physical.replace("ROUTINE:", "");
    }

    private String tableOf(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return "";
        }
        int index = endpoint.lastIndexOf('.');
        return index < 0 ? endpoint : endpoint.substring(0, index);
    }

    private String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String slug(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replace("routine:", "");
        normalized = normalized.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}._-]+", "_");
        normalized = normalized.replaceAll("_+", "_");
        if (normalized.startsWith("_")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("_")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isBlank() ? "unknown" : normalized;
    }

    private static final class ValidationState {
        private final Map<String, ObjectNode> unresolvedReferences = new LinkedHashMap<>();
        private final Map<String, ObjectNode> missingEvidenceRefs = new LinkedHashMap<>();
        private int generatedReviewItemCount;
    }
}
