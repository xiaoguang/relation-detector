package com.relationdetector.semantic.extract.model;

/**
 * CN: 表示两个语义实体之间的关系声明，保存名称端和解析后的 entity refs；normalizer 验证闭包，本 DTO 不替代物理 relationship。
 * EN: Represents a relation between two semantic entities with names and resolved entity references. Normalization verifies closure; this DTO never replaces the physical relationship fact.
 */
public final class SemanticRelation extends SemanticItem {
    public String from;
    public String to;
    public String fromEntityRef;
    public String toEntityRef;
}
