package com.relationdetector.semantic.extract.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Typed semantic extraction document used by normalization and KG preparation. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class SemanticExtractionDocument {
    public List<SemanticEntity> entities;
    public List<SemanticEvent> events;
    public List<SemanticRelation> relations;
    public List<SemanticLineage> lineage;
    public List<SemanticMetric> metrics;
    public List<SemanticDimension> dimensions;
    public List<SemanticTriplet> triplets;
    public List<SemanticReviewItem> reviewItems;
    public SemanticGraph semanticGraph;
    public SemanticValidation validation;

    public void ensureSections() {
        entities = mutable(entities);
        events = mutable(events);
        relations = mutable(relations);
        lineage = mutable(lineage);
        metrics = mutable(metrics);
        dimensions = mutable(dimensions);
        triplets = mutable(triplets);
        reviewItems = mutable(reviewItems);
    }

    public List<SemanticEntity> entities() {
        return entities;
    }

    public List<SemanticEvent> events() {
        return events;
    }

    private static <T> List<T> mutable(List<T> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }
}
