/**
 * CLI 入口层。
 *
 * <p>CN: 本包读取 YAML 与 CLI 覆盖参数，发现数据库 adaptor，调用 ScanEngine，并把
 * ScanResult 渲染为 JSON 或 table。CLI 只负责用户入口和输出落盘，不承载 parser
 * 或 relationship 语义。
 *
 * <p>EN: Command-line entry layer. It reads YAML plus CLI overrides, discovers
 * database adaptors, invokes ScanEngine, and renders ScanResult as JSON or table
 * output. CLI owns user entry and output writing, not parser or relationship
 * semantics.
 * <p>Responsibility: 解析 CLI/YAML、发现 adaptor、调用 ScanEngine 并落盘结果 / Owns command-line orchestration.
 * <p>Inputs: user arguments、YAML configuration and plugin directory / User arguments, configuration, and plugins.
 * <p>Outputs: JSON/table files、exit codes 与 batch reports / Output files, exit codes, and batch reports.
 * <p>Upstream/Downstream: 用户/automation 上游，core scan 与 output 下游 / Connects users to core scan and output.
 * <p>Forbidden: 不实现 parser、relationship 或 lineage 语义 / Must not implement parsing or fact semantics.
 */
package com.relationdetector.cli;
