/**
 * 数据库 catalog 事实模型层。
 *
 * <p>CN: 本包承载从数据库 metadata collector 读出的表、列、索引、约束和快照事实。
 * 这些事实用于证据增强和置信度计算，但不直接生成 SQL/DDL parser 关系。
 *
 * <p>EN: Database catalog fact model layer for tables, columns, indexes,
 * constraints, and snapshots. These facts enrich evidence and scoring; they do
 * not directly parse SQL/DDL or create parser-only relationships.
 * <p>Responsibility: 表达 live catalog metadata 快照 / Models live catalog metadata snapshots.
 * <p>Inputs: adaptor catalog readers 的表列约束索引结果 / Table, column, constraint, and index rows from adaptors.
 * <p>Outputs: core metadata enhancement 可消费的 immutable facts / Immutable facts consumed by core enhancement.
 * <p>Upstream/Downstream: adaptor 生产，core.metadata 消费 / Produced by adaptors and consumed by core.metadata.
 * <p>Forbidden: 不查询 JDBC 或推断 relationship / Must not query JDBC or infer relationships.
 */
package com.relationdetector.contracts.metadata;
