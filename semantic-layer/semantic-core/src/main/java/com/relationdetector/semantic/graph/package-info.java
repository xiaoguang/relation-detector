/**
 * CN: graph 包负责把 typed scan facts 和事件候选组装为确定性 evidence graph，并维护可解析 evidenceRefs。输入来自 reader/event，输出交给 enrichment、KG 和 extraction；禁止丢弃 provenance、覆盖重复 ID 或改变物理事实语义。
 * EN: The graph package assembles typed scan facts and event candidates into a deterministic evidence graph with resolvable evidence references. It serves enrichment, KG, and extraction downstream consumers and must not discard provenance, overwrite duplicate IDs, or alter physical facts.
 */
package com.relationdetector.semantic.graph;
