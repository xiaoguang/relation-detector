/**
 * CN: SQL Server 日志输入提取与来源映射。
 *
 * <p>EN: SQL Server log-input extraction and provenance mapping.
 * <p>Responsibility: 将 SQL Server logs 转为 framed T-SQL statements / Extracts framed SQL Server log statements.
 * <p>Inputs: log files、format hints and GO-aware script framer / Logs, hints, and framer.
 * <p>Outputs: provenance-bearing SqlStatementRecord values / Statements consumed by core parsing.
 * <p>Upstream/Downstream: source files 上游，core log/parser 下游 / Feeds the core log/parser pipeline.
 * <p>Forbidden: 不按 raw text 猜 system rowsets 或创建 facts / Must not guess rowsets or emit facts.
 */
package com.relationdetector.sqlserver.log;
