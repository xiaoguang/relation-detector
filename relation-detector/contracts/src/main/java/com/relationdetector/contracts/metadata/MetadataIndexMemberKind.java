package com.relationdetector.contracts.metadata;

/**
 * CN: 标识索引有序成员的结构类型，区分完整物理列、前缀列和表达式。
 *
 * <p>EN: Identifies the structural kind of an ordered index member: a full
 * physical column, a prefix column, or an expression.
 */
public enum MetadataIndexMemberKind {
    FULL_COLUMN,
    PREFIX_COLUMN,
    EXPRESSION
}
