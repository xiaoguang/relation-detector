/**
 * CN: MySQL 原生日志提取与语句来源映射。
 *
 * <p>EN: MySQL native-log extraction and statement provenance mapping.
 * <p>Responsibility: 将 MySQL native/plain logs 转为 framed statements / Extracts framed statements from MySQL logs.
 * <p>Inputs: log files、format hint 与 MySQL script framer / Log files, format hints, and framer.
 * <p>Outputs: 带 source provenance 的 SqlStatementRecord / Provenance-bearing statements.
 * <p>Upstream/Downstream: source files 上游，core log/parser pipeline 下游 / Feeds the core log/parser pipeline.
 * <p>Forbidden: 不按 SQL 文本猜测业务表或创建 facts / Must not guess business tables or create facts.
 */
package com.relationdetector.mysql.log;
