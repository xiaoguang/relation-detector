/**
 * CN: 事实 observation 聚合、共识属性与 evidence identity。
 *
 * <p>EN: Fact-observation aggregation, consensus attributes, and evidence identity.
 * <p>Responsibility: 规范 evidence observation 身份和聚合属性 / Normalizes evidence observation identity and attributes.
 * <p>Inputs: relationship、lineage 与 naming 的 raw observations / Raw observations from fact extractors.
 * <p>Outputs: merger 可消费的稳定 evidence groups / Stable evidence groups consumed by mergers.
 * <p>Upstream/Downstream: extractors 上游，fact mergers 下游 / Sits between extractors and fact mergers.
 * <p>Forbidden: 不删除不同 SQL 位置或改写事实 endpoint / Must not erase distinct locations or rewrite endpoints.
 */
package com.relationdetector.core.evidence;
