/**
 * CN: 责任是从完整typed输入构建Evidence Graph、event/triplet/review候选和transport window；输入为只读
 * inventory/fact游标，输出为闭合evidence store。上游是ingest，下游是KG和sharding；禁止调用LLM或改写物理事实。
 * EN: Responsibility: build Evidence Graph plus event, triplet, review candidates and transport windows from complete
 * typed input. Inputs are read-only inventory/fact cursors; output is a closed evidence store. Upstream is ingest and
 * downstream is KG/sharding. LLM calls and physical-fact rewriting are forbidden.
 */
package com.relationdetector.semantic.evidence;
