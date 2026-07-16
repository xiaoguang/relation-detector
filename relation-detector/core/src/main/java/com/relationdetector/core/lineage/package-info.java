/**
 * Data Lineage 语义层。
 *
 * <p>CN: 本包从结构化写入事件中解析数据库内部字段血缘，处理 projection/derived
 * 回溯、transform 传播和候选合并。v1 不做 Parameter Binding，不把参数、literal、
 * JSON path 或局部变量作为 source。
 *
 * <p>EN: Data Lineage semantic layer. It resolves database-internal field
 * lineage from structured write events, including projection/derived traceback,
 * transform propagation, and candidate merging. v1 does not perform parameter
 * binding and does not treat parameters, literals, JSON paths, or local
 * variables as sources.
 * <p>Responsibility: 将 typed write events 转换并合并为 direct lineage facts / Extracts and merges direct lineage.
 * <p>Inputs: scoped rowsets、projections、VALUE/CONTROL expression traces / Scoped typed write and expression events.
 * <p>Outputs: 带 flow、transform 与 provenance 的 lineage candidates / Lineage candidates with role and provenance.
 * <p>Upstream/Downstream: adaptor events 上游，derived/output 下游 / Between adaptor events and derived/output stages.
 * <p>Forbidden: 不把非物理标识符变成 endpoint 或从 CONTROL 推导路径 / Must not invent endpoints or derive CONTROL.
 */
package com.relationdetector.core.lineage;
