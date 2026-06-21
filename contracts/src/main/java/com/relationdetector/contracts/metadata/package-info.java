/**
 * 数据库 catalog 事实模型层。
 *
 * <p>CN: 本包承载从数据库 metadata collector 读出的表、列、索引、约束和快照事实。
 * 这些事实用于证据增强和置信度计算，但不直接生成 SQL/DDL parser 关系。
 *
 * <p>EN: Database catalog fact model layer for tables, columns, indexes,
 * constraints, and snapshots. These facts enrich evidence and scoring; they do
 * not directly parse SQL/DDL or create parser-only relationships.
 */
package com.relationdetector.contracts.metadata;
