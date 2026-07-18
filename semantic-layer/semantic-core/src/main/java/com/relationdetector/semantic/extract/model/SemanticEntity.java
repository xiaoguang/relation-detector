package com.relationdetector.semantic.extract.model;

/**
 * CN: 表示映射到一个 evidence-backed physical table 的语义实体候选；normalizer 校验 physicalName 并解析引用，本 DTO 不做名称推测。
 * EN: Represents a semantic entity candidate mapped to one evidence-backed physical table. The normalizer validates physicalName and references; this DTO performs no naming inference.
 */
public final class SemanticEntity extends SemanticItem {
    public String physicalName;

    public String physicalName() {
        return physicalName;
    }
}
