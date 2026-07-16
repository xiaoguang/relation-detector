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
 * <p>Responsibility: 从 typed predicate/DDL events 抽取、定向并合并 relationship / Extracts and merges relationships.
 * <p>Inputs: scoped rowsets、predicate events 与 DDL/metadata/profile evidence / Typed events and directional evidence.
 * <p>Outputs: direct relationship facts、conditions 与 raw observations / Direct facts, conditions, and observations.
 * <p>Upstream/Downstream: parser/collectors 上游，naming/derived/output 下游 / Feeds naming, derived, and output stages.
 * <p>Forbidden: 函数比较不得伪装 direct equality，命名不得在此执行 / Must not fake direct equality or run naming.
 */
package com.relationdetector.core.relation;
