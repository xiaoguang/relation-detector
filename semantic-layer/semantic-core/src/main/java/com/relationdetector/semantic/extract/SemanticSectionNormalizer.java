package com.relationdetector.semantic.extract;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.relationdetector.semantic.StableSemanticId;
import com.relationdetector.semantic.extract.SemanticReferenceValidator.Session;
import com.relationdetector.semantic.extract.model.SemanticDimension;
import com.relationdetector.semantic.extract.model.SemanticEntity;
import com.relationdetector.semantic.extract.model.SemanticEvent;
import com.relationdetector.semantic.extract.model.SemanticExtractionDocument;
import com.relationdetector.semantic.extract.model.SemanticLineage;
import com.relationdetector.semantic.extract.model.SemanticMetric;
import com.relationdetector.semantic.extract.model.SemanticRelation;
import com.relationdetector.semantic.extract.model.SemanticReviewItem;
import com.relationdetector.semantic.extract.model.SemanticTriplet;

/** Normalizes typed semantic sections and emits graph facts through dedicated collaborators. */
final class SemanticSectionNormalizer {
    NormalizationResult normalizeFacts(
            SemanticExtractionDocument document,
            SemanticGraphAssembler graph,
            Session validator
    ) {
        Map<String, String> entityByName = new LinkedHashMap<>();
        Map<String, String> entityByPhysical = new LinkedHashMap<>();
        Set<String> linkedEntities = new LinkedHashSet<>();

        normalizeEntities(document.entities, entityByName, entityByPhysical, graph, validator);
        normalizeEvents(document.events, entityByName, linkedEntities, graph, validator);
        normalizeRelations(document.relations, entityByName, linkedEntities, graph, validator);
        normalizeLineage(document.lineage, entityByPhysical, linkedEntities, graph, validator);
        normalizeMetrics(document.metrics, entityByPhysical, linkedEntities, graph, validator);
        normalizeDimensions(document.dimensions, entityByName, entityByPhysical, linkedEntities, graph, validator);
        normalizeTriplets(document.triplets, entityByName, linkedEntities, graph, validator);
        return new NormalizationResult(linkedEntities);
    }

    void normalizeReviewItems(
            List<SemanticReviewItem> reviewItems,
            SemanticGraphAssembler graph,
            Session validator
    ) {
        for (SemanticReviewItem item : reviewItems) {
            String targetRef = SemanticNormalizationSupport.nonBlank(item.targetRef, item.target);
            item.id = SemanticNormalizationSupport.nonBlank(
                    item.id,
                    StableSemanticId.of("review", targetRef, item.targetSection, item.type, item.reason));
            validator.registerOwner("reviewItem", item.id);
            if (!blank(targetRef)) {
                item.targetRef = targetRef;
            }
            if (item.targetSection == null) {
                item.targetSection = text(item.section);
            }
            validator.requireEvidence("reviewItem", item.id, item);
            graph.addNode(item.id, "ReviewItem", targetRef, "REVIEW_NEEDED", item.evidenceRefs());
            graph.addEdge("review-target", item.id, targetRef, "REVIEW_TARGET", item.evidenceRefs());
        }
    }

    private void normalizeEntities(
            List<SemanticEntity> entities,
            Map<String, String> entityByName,
            Map<String, String> entityByPhysical,
            SemanticGraphAssembler graph,
            Session validator
    ) {
        for (SemanticEntity entity : entities) {
            String key = blank(entity.physicalName) ? SemanticNormalizationSupport.nonBlank(entity.name, "entity")
                    : entity.physicalName;
            entity.id = SemanticNormalizationSupport.nonBlank(entity.id, "entity:" + SemanticNormalizationSupport.slug(key));
            validator.registerOwner("entity", entity.id);
            validator.requirePhysicalTable("entity", entity.id, "physicalName", entity.physicalName);
            validator.requireEvidence("entity", entity.id, entity);
            entityByName.put(text(entity.name), entity.id);
            entityByPhysical.put(text(entity.physicalName), entity.id);
            graph.addNode(entity.id, "Entity", entity.name, entity.type, entity.evidenceRefs());
        }
    }

    private void normalizeEvents(
            List<SemanticEvent> events,
            Map<String, String> entityByName,
            Set<String> linkedEntities,
            SemanticGraphAssembler graph,
            Session validator
    ) {
        Set<String> knownEntityIds = new LinkedHashSet<>(entityByName.values());
        for (SemanticEvent event : events) {
            String eventKey = blank(event.physicalName)
                    ? SemanticNormalizationSupport.nonBlank(event.name, "event")
                    : event.physicalName.replace("ROUTINE:", "");
            event.id = SemanticNormalizationSupport.nonBlank(event.id,
                    "event:" + SemanticNormalizationSupport.slug(eventKey));
            validator.registerOwner("event", event.id);
            validator.requireEvidence("event", event.id, event);
            validator.requireEventCandidateRef(event.id, event.eventCandidateRef);
            graph.addNode(event.id, "Event", event.name, event.type, event.evidenceRefs());

            List<String> originalInputs = SemanticNormalizationSupport.mutableStrings(event.inputEntityRefs);
            event.inputEntityRefs = new ArrayList<>();
            for (String ref : originalInputs) {
                String knownRef = knownEntityIds.contains(ref) ? ref : null;
                SemanticNormalizationSupport.addIfAbsent(event.inputEntityRefs, knownRef, linkedEntities);
                graph.addEdge("event-input", event.id, knownRef, "EVENT_INPUT", event.evidenceRefs());
                validator.requireResolved(event.id, "inputEntityRefs", ref, knownRef, "entity");
            }
            for (String input : values(event.inputs)) {
                String ref = entityByName.get(input);
                SemanticNormalizationSupport.addIfAbsent(event.inputEntityRefs, ref, linkedEntities);
                graph.addEdge("event-input", event.id, ref, "EVENT_INPUT", event.evidenceRefs());
                validator.requireResolved(event.id, "inputs", input, ref, "entity");
            }

            List<String> originalOutputs = SemanticNormalizationSupport.mutableStrings(event.outputEntityRefs);
            event.outputEntityRefs = new ArrayList<>();
            for (String ref : originalOutputs) {
                String knownRef = knownEntityIds.contains(ref) ? ref : null;
                SemanticNormalizationSupport.addIfAbsent(event.outputEntityRefs, knownRef, linkedEntities);
                graph.addEdge("event-output", event.id, knownRef, "EVENT_OUTPUT", event.evidenceRefs());
                validator.requireResolved(event.id, "outputEntityRefs", ref, knownRef, "entity");
            }
            for (String output : values(event.outputs)) {
                String ref = entityByName.get(output);
                SemanticNormalizationSupport.addIfAbsent(event.outputEntityRefs, ref, linkedEntities);
                graph.addEdge("event-output", event.id, ref, "EVENT_OUTPUT", event.evidenceRefs());
                validator.requireResolved(event.id, "outputs", output, ref, "entity");
            }
        }
    }

    private void normalizeRelations(
            List<SemanticRelation> relations,
            Map<String, String> entityByName,
            Set<String> linkedEntities,
            SemanticGraphAssembler graph,
            Session validator
    ) {
        for (SemanticRelation relation : relations) {
            String fromRef = entityByName.get(text(relation.from));
            String toRef = entityByName.get(text(relation.to));
            relation.id = SemanticNormalizationSupport.nonBlank(relation.id,
                    StableSemanticId.of("relation", relation.from, relation.to, relation.type, relation.machineType));
            validator.registerOwner("relation", relation.id);
            validator.requireEvidence("relation", relation.id, relation);
            relation.fromEntityRef = present(fromRef) ? fromRef : relation.fromEntityRef;
            relation.toEntityRef = present(toRef) ? toRef : relation.toEntityRef;
            validator.requireResolved(relation.id, "from", relation.from, fromRef, "entity");
            validator.requireResolved(relation.id, "to", relation.to, toRef, "entity");
            graph.addNode(relation.id, "Relation", relation.type, relation.machineType, relation.evidenceRefs());
            graph.addEdge("relation-from", relation.id, fromRef, "RELATION_FROM", relation.evidenceRefs());
            graph.addEdge("relation-to", relation.id, toRef, "RELATION_TO", relation.evidenceRefs());
            graph.addEdge("relation", fromRef, toRef,
                    SemanticNormalizationSupport.nonBlank(relation.type, "RELATES_TO"), relation.evidenceRefs());
            addLinked(linkedEntities, fromRef, toRef);
        }
    }

    private void normalizeLineage(
            List<SemanticLineage> lineages,
            Map<String, String> entityByPhysical,
            Set<String> linkedEntities,
            SemanticGraphAssembler graph,
            Session validator
    ) {
        for (SemanticLineage lineage : lineages) {
            String targetKey = SemanticNormalizationSupport.nonBlank(lineage.toPhysical, lineage.to);
            List<String> sourceKeys = new ArrayList<>(values(lineage.fromPhysical));
            if (sourceKeys.isEmpty()) {
                sourceKeys.addAll(values(lineage.from));
            }
            sourceKeys.sort(String::compareTo);
            lineage.id = SemanticNormalizationSupport.nonBlank(lineage.id,
                    StableSemanticId.of("semantic-lineage", String.join("\u001f", sourceKeys), targetKey,
                            lineage.transform));
            validator.registerOwner("lineage", lineage.id);
            validator.requireEvidence("lineage", lineage.id, lineage);
            graph.addNode(lineage.id, "Lineage", lineage.to, lineage.transform, lineage.evidenceRefs());
            lineage.sourceEntityRefs = new ArrayList<>();
            for (String source : values(lineage.fromPhysical)) {
                validator.requirePhysicalColumn("lineage", lineage.id, "fromPhysical", source);
                String sourceRef = entityByPhysical.get(SemanticNormalizationSupport.tableOf(source));
                SemanticNormalizationSupport.addIfAbsent(lineage.sourceEntityRefs, sourceRef, linkedEntities);
                graph.addEdge("lineage-source", lineage.id, sourceRef, "LINEAGE_SOURCE", lineage.evidenceRefs());
                validator.requireResolved(lineage.id, "fromPhysical", source, sourceRef, "entity");
            }
            validator.requirePhysicalColumn("lineage", lineage.id, "toPhysical", lineage.toPhysical);
            String targetRef = entityByPhysical.get(SemanticNormalizationSupport.tableOf(lineage.toPhysical));
            if (present(targetRef)) {
                lineage.targetEntityRef = targetRef;
                linkedEntities.add(targetRef);
            }
            graph.addEdge("lineage-target", lineage.id, targetRef, "LINEAGE_TARGET", lineage.evidenceRefs());
            validator.requireResolved(lineage.id, "toPhysical", lineage.toPhysical, targetRef, "entity");
        }
    }

    private void normalizeMetrics(
            List<SemanticMetric> metrics,
            Map<String, String> entityByPhysical,
            Set<String> linkedEntities,
            SemanticGraphAssembler graph,
            Session validator
    ) {
        for (SemanticMetric metric : metrics) {
            String key = SemanticNormalizationSupport.nonBlank(metric.physicalField, metric.name);
            metric.id = SemanticNormalizationSupport.nonBlank(metric.id,
                    "metric:" + SemanticNormalizationSupport.slug(key));
            validator.registerOwner("metric", metric.id);
            validator.requirePhysicalColumn("metric", metric.id, "physicalField", metric.physicalField);
            validator.requireEvidence("metric", metric.id, metric);
            String ownerRef = entityByPhysical.get(SemanticNormalizationSupport.tableOf(metric.physicalField));
            if (present(ownerRef)) {
                metric.ownerEntityRef = ownerRef;
                linkedEntities.add(ownerRef);
            }
            validator.requireResolved(metric.id, "physicalField", metric.physicalField, ownerRef, "entity");
            metric.sourceEntityRefs = new ArrayList<>();
            for (String source : values(metric.sourceFields)) {
                validator.requirePhysicalColumn("metric", metric.id, "sourceFields", source);
                String sourceRef = entityByPhysical.get(SemanticNormalizationSupport.tableOf(source));
                SemanticNormalizationSupport.addIfAbsent(metric.sourceEntityRefs, sourceRef, linkedEntities);
                validator.requireResolved(metric.id, "sourceFields", source, sourceRef, "entity");
            }
            graph.addNode(metric.id, "Metric", metric.name, metric.type, metric.evidenceRefs());
            graph.addEdge("metric-owner", metric.id, ownerRef, "METRIC_OWNER", metric.evidenceRefs());
        }
    }

    private void normalizeDimensions(
            List<SemanticDimension> dimensions,
            Map<String, String> entityByName,
            Map<String, String> entityByPhysical,
            Set<String> linkedEntities,
            SemanticGraphAssembler graph,
            Session validator
    ) {
        for (SemanticDimension dimension : dimensions) {
            String key = SemanticNormalizationSupport.nonBlank(dimension.physicalField, dimension.name);
            dimension.id = SemanticNormalizationSupport.nonBlank(dimension.id,
                    "dimension:" + SemanticNormalizationSupport.slug(key));
            validator.registerOwner("dimension", dimension.id);
            validator.requirePhysicalColumn("dimension", dimension.id, "physicalField", dimension.physicalField);
            validator.requirePhysicalTable("dimension", dimension.id, "dimensionTable", dimension.dimensionTable);
            validator.requireEvidence("dimension", dimension.id, dimension);
            String ownerRef = entityByPhysical.get(SemanticNormalizationSupport.tableOf(dimension.physicalField));
            String dimensionRef = entityByPhysical.get(text(dimension.dimensionTable));
            if (dimensionRef == null) {
                dimensionRef = entityByName.get(text(dimension.name));
            }
            if (present(ownerRef)) {
                dimension.ownerEntityRef = ownerRef;
            }
            if (present(dimensionRef)) {
                dimension.dimensionEntityRef = dimensionRef;
            }
            addLinked(linkedEntities, ownerRef, dimensionRef);
            validator.requireResolved(dimension.id, "physicalField", dimension.physicalField, ownerRef, "entity");
            validator.requireResolved(dimension.id, "dimensionTable", dimension.dimensionTable, dimensionRef, "entity");
            graph.addNode(dimension.id, "Dimension", dimension.name, dimension.type, dimension.evidenceRefs());
            graph.addEdge("dimension-owner", dimension.id, ownerRef, "DIMENSION_OWNER", dimension.evidenceRefs());
            graph.addEdge("dimension-target", dimension.id, dimensionRef, "DIMENSION_TARGET", dimension.evidenceRefs());
        }
    }

    private void normalizeTriplets(
            List<SemanticTriplet> triplets,
            Map<String, String> entityByName,
            Set<String> linkedEntities,
            SemanticGraphAssembler graph,
            Session validator
    ) {
        for (SemanticTriplet triplet : triplets) {
            triplet.id = SemanticNormalizationSupport.nonBlank(triplet.id,
                    StableSemanticId.of("triplet", triplet.candidateRef, triplet.subject, triplet.predicate,
                            triplet.object, triplet.machineType));
            validator.registerOwner("triplet", triplet.id);
            validator.requireEvidence("triplet", triplet.id, triplet);
            validator.requireTripletCandidateRef(triplet.id, triplet.candidateRef);
            String subjectRef = entityByName.get(text(triplet.subject));
            String objectRef = entityByName.get(text(triplet.object));
            if (present(subjectRef)) {
                triplet.subjectRef = subjectRef;
            }
            if (present(objectRef)) {
                triplet.objectRef = objectRef;
            }
            validator.requireResolved(triplet.id, "subject", triplet.subject, subjectRef, "entity");
            validator.requireResolved(triplet.id, "object", triplet.object, objectRef, "entity");
            addLinked(linkedEntities, subjectRef, objectRef);
            graph.addNode(triplet.id, "Triplet", triplet.readable, triplet.predicate, triplet.evidenceRefs());
            graph.addEdge("triplet-subject", triplet.id, subjectRef, "TRIPLET_SUBJECT", triplet.evidenceRefs());
            graph.addEdge("triplet-object", triplet.id, objectRef, "TRIPLET_OBJECT", triplet.evidenceRefs());
        }
    }

    private List<String> values(List<String> values) {
        return values == null ? List.of() : values;
    }

    private void addLinked(Set<String> linkedEntities, String... refs) {
        for (String ref : refs) {
            if (present(ref)) {
                linkedEntities.add(ref);
            }
        }
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    private boolean present(String value) {
        return !blank(value);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    record NormalizationResult(Set<String> linkedEntities) {
        NormalizationResult {
            linkedEntities = Set.copyOf(linkedEntities);
        }
    }
}
