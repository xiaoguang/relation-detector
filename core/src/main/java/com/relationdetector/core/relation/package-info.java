/**
 * relationship 语义层。
 *
 * <p>CN: 本包把 SQL 结构事件和 DDL 结构事件转换为 RelationshipCandidate，并负责
 * relationship 合并。FK-like 方向、列级/表级共现、自连接弱关系、DDL FK/index
 * evidence 等语义都集中在这里，避免散落到方言 parser。
 *
 * <p>EN: Relationship semantic layer. It converts SQL and DDL structured events
 * into RelationshipCandidate instances and merges them. FK-like direction,
 * column/table co-occurrence, self-join weak relations, and DDL FK/index
 * evidence are centralized here rather than duplicated in dialect parsers.
 */
package com.relationdetector.core.relation;
