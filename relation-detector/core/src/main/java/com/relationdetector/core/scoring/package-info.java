/**
 * relationship 置信度计算层。
 *
 * <p>CN: 本包根据 evidence base score、metadata 增强和重复观察计算最终
 * relationship confidence。Data Lineage confidence 是独立概念，不进入这里的
 * relationship 评分公式。
 *
 * <p>EN: Relationship confidence scoring layer. It computes final relationship
 * confidence from evidence base scores, metadata enhancement, and repeated
 * observations. Data Lineage confidence is separate and does not feed this
 * formula.
 * <p>Responsibility: 按 evidence weights 计算可解释 confidence / Computes explainable confidence from evidence.
 * <p>Inputs: merged evidence types、subtype 与 configured scoring policy / Evidence, subtype, and scoring policy.
 * <p>Outputs: deterministic confidence values and breakdowns / Deterministic confidence values and breakdowns.
 * <p>Upstream/Downstream: fact merger 上游，ScanResult/output 下游 / Between fact merge and output.
 * <p>Forbidden: 不改变 endpoint、事实类型或 evidence provenance / Must not change endpoints, fact kinds, or provenance.
 */
package com.relationdetector.core.scoring;
