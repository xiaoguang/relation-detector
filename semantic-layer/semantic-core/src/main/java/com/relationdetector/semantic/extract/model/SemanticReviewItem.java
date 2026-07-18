package com.relationdetector.semantic.extract.model;

/**
 * CN: 记录 normalization 产生的显式 review target、section 和 reason；它用于审计未闭合语义，不修改目标对象或物理事实。
 * EN: Records an explicit review target, section, and reason produced by normalization. It audits unresolved semantics without mutating the target or physical facts.
 */
public final class SemanticReviewItem extends SemanticItem {
    public String targetRef;
    public String targetSection;
    public String target;
    public String section;
    public String reason;
}
