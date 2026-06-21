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
 */
package com.relationdetector.core.lineage;
