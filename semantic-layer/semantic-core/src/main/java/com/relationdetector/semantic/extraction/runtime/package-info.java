/**
 * CN: 责任是编排磁盘processing session、顺序模型调用和Codex response completion；输入为validated config、plan
 * 及response，输出为完整run或pending审计。上游是三个CLI facade，下游是prompt/normalization/artifact；禁止复制底层校验规则或并行分片。
 * EN: Responsibility: orchestrate disk-backed processing sessions, sequential model calls, and Codex response
 * completion. Inputs are validated configuration, plans, and responses; output is a complete run or pending audit.
 * Upstream is the three CLI facades and downstream is prompt/normalization/artifact. Duplicated validators and parallel shard execution are forbidden.
 */
package com.relationdetector.semantic.extraction.runtime;
