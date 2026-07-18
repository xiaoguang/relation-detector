package com.relationdetector.semantic.extract.model;

import java.util.List;

/**
 * CN: 表示由一个 owner entity 和已证明 source fields 支撑的语义指标定义；normalizer 解析引用并拒绝 bundle 外字段。
 * EN: Represents a semantic metric backed by an owner entity and proven source fields. Normalization resolves references and rejects fields absent from the evidence bundle.
 */
public final class SemanticMetric extends SemanticItem {
    public String physicalField;
    public List<String> sourceFields;
    public String ownerEntityRef;
    public List<String> sourceEntityRefs;
}
