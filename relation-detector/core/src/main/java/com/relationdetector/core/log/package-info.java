/**
 * SQL 日志输入层。
 *
 * <p>CN: 本包负责把纯 SQL 文件或数据库原生日志拆成 SqlStatementRecord，并过滤
 * system schema、截断片段等日志噪声。它只决定“哪些 SQL 进入 parser”，不解释
 * relationship 或 lineage。
 *
 * <p>EN: SQL log input layer. It splits plain SQL files or native database logs
 * into SqlStatementRecord objects and filters system-schema/truncated noise. It
 * decides which SQL reaches parsers, not relationship or lineage semantics.
 * <p>Responsibility: 规范 SQL log 来源并按 typed rowsets 过滤系统噪声 / Normalizes logs and classifies typed noise.
 * <p>Inputs: native/plain log statements 与 structured parse outcomes / Log statements and typed parse outcomes.
 * <p>Outputs: 可审计的业务 statements 与 source names / Auditable business statements and normalized sources.
 * <p>Upstream/Downstream: adaptor log extractor 上游，statement execution 下游 / Feeds statement execution.
 * <p>Forbidden: 不以 raw SQL regex 猜测表或静默丢弃解析失败语句 / Must not guess structure or silently drop failures.
 */
package com.relationdetector.core.log;
