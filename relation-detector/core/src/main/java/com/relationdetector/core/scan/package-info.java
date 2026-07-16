/**
 * 扫描编排层。
 *
 * <p>CN: 本包把配置、数据库 adaptor、metadata、数据库 DDL、对象定义、日志文件、
 * SQL/DDL parser、relationship 合并和 Data Lineage 合并串成一次完整扫描。这里
 * 负责流程和容错，不负责具体方言解析或 relationship 语义判断。
 *
 * <p>EN: Scan orchestration layer. It connects configuration, adaptors,
 * metadata, database DDL, object definitions, logs, SQL/DDL parsers,
 * relationship merging, and data-lineage merging into one scan. It owns flow and
 * failure isolation, not dialect parsing or semantic relationship rules.
 * <p>Responsibility: 校验配置并串联 live/file collectors、parsers、mergers 与 output model / Orchestrates a full scan.
 * <p>Inputs: ResolvedScanConfig、DatabaseAdaptor、files and optional JDBC connection / Resolved configuration and sources.
 * <p>Outputs: 包含 direct/derived facts、warnings 与 provenance 的 ScanResult / Complete ScanResult.
 * <p>Upstream/Downstream: CLI/embedding API 上游，output writer 下游 / Between callers and output writers.
 * <p>Forbidden: capability 未通过前不得打开 JDBC，也不实现方言语义 / Must not open JDBC before preflight or parse dialects.
 */
package com.relationdetector.core.scan;
