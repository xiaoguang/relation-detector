package com.relationdetector.semantic.extract.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Mutable transport DTO shared by semantic extraction sections. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class SemanticItem {
    public String id;
    public String name;
    public String type;
    public String machineType;
    public String description;
    public String reviewStatus;
    public String severity;
    public List<String> evidenceRefs;

    public String id() {
        return id;
    }

    public String reviewStatus() {
        return reviewStatus;
    }

    public List<String> evidenceRefs() {
        return evidenceRefs == null ? List.of() : evidenceRefs;
    }
}
