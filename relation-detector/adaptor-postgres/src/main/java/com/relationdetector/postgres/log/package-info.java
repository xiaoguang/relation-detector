/**
 * CN: PostgreSQL 日志输入提取与来源映射。
 *
 * <p>EN: PostgreSQL log-input extraction and provenance mapping.
 * <p>Responsibility: 将 PostgreSQL logs 转为 framed statements 与 provenance / Extracts PostgreSQL log statements.
 * <p>Inputs: log files、format hints and PostgreSQL script framer / Logs, hints, and framer.
 * <p>Outputs: SqlStatementRecord values for core parser pipeline / Provenance-bearing statements.
 * <p>Upstream/Downstream: source files 上游，core log/parser 下游 / Feeds the core log/parser pipeline.
 * <p>Forbidden: 不按文本猜 system noise 或生成 facts / Must not guess noise from text or emit facts.
 */
package com.relationdetector.postgres.log;
