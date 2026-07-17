/**
 * CN: 有界 live JDBC data profiling 指标、SQL 模板、契约校验与 evidence 构建。
 *
 * <p>EN: Bounded live JDBC data-profiling metrics, SQL templates, contract validation, and evidence construction.
 * <p>Responsibility: 对候选 endpoint 执行受限 live profile 并构造 profile evidence / Profiles candidate endpoints.
 * <p>Inputs: directional candidates、JDBC connection 与 dialect query renderer / Candidates, connection, and renderer.
 * <p>Outputs: containment/overlap metrics、evidence 或安全 warning / Metrics, evidence, or sanitized warnings.
 * <p>Upstream/Downstream: relationship candidates 上游，evidence enhancement 下游 / Feeds enhanced relationships.
 * <p>Forbidden: 不实现 offline sample producer，不输出 SQL/driver message，组合唯一成员不单独定向 /
 * Must not implement offline sampling, leak SQL, or misuse composite uniqueness.
 */
package com.relationdetector.core.profile;
