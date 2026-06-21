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
 */
package com.relationdetector.cli;
