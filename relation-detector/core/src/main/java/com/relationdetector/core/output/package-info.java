/**
 * 输出渲染层。
 *
 * <p>CN: 本包把 ScanResult 渲染为 JSON 或终端表格。输出层只负责格式化、证据和
 * warning 展示开关，不重新计算 relationship、lineage 或 confidence。
 *
 * <p>EN: Output rendering layer. It renders ScanResult as JSON or terminal
 * tables and only handles formatting plus evidence/warning visibility flags; it
 * does not recompute relationships, lineage, or confidence.
 * <p>Responsibility: 将稳定 ScanResult 渲染为 JSON 与表格 / Renders stable scan results as JSON and tables.
 * <p>Inputs: 已完成 merge/derived 的 ScanResult 与输出选项 / Completed ScanResult and output options.
 * <p>Outputs: portable JSON streams/files and human-readable tables / Portable JSON and readable tables.
 * <p>Upstream/Downstream: scan engine 上游，CLI/semantic-layer 下游 / Between scan engine and external consumers.
 * <p>Forbidden: 不重新解析、评分、合并或推导事实 / Must not parse, score, merge, or derive facts.
 */
package com.relationdetector.core.output;
