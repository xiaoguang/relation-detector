package com.relationdetector.semantic.extract.model;

/**
 * CN: 表示 owner entity 上的语义维度及其 physical field/dimension entity 引用；normalizer 验证字段和实体，不推断维表关系。
 * EN: Represents a semantic dimension on an owner entity with physical-field and dimension-entity references. Normalization validates them without inferring dimension-table relationships.
 */
public final class SemanticDimension extends SemanticItem {
    public String physicalField;
    public String dimensionTable;
    public String ownerEntityRef;
    public String dimensionEntityRef;
}
