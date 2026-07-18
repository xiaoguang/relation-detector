package com.relationdetector.semantic.extract.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * CN: 语义抽取各 section 共享的 mutable transport base，承载 stable id、label、review 和 evidence refs；normalizer 负责校验并冻结语义，本模型不解析物理事实。
 * EN: Mutable transport base shared by semantic extraction sections, carrying stable identity, labels, review state, and evidence references. Normalization validates semantics; this model does not parse physical facts.
 */
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
