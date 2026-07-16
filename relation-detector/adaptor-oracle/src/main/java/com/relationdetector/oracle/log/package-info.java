/**
 * CN: Oracle 日志输入提取与来源映射。
 *
 * <p>EN: Oracle log-input extraction and provenance mapping.
 * <p>Responsibility: 将 Oracle logs 转为带 provenance 的 framed statements / Extracts framed Oracle log statements.
 * <p>Inputs: log files、format hints and Oracle script framer / Log files, hints, and framer.
 * <p>Outputs: SqlStatementRecord values for core parsing / Statements consumed by core parsing.
 * <p>Upstream/Downstream: file sources 上游，core log/parser pipeline 下游 / Feeds the core parser pipeline.
 * <p>Forbidden: 不以 regex 猜 SQL structure 或创建 facts / Must not guess SQL structure or create facts.
 */
package com.relationdetector.oracle.log;
