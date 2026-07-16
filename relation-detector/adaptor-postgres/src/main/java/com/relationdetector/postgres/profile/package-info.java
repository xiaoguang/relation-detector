/**
 * CN: PostgreSQL 精确 live data profiling SQL 渲染。
 *
 * <p>EN: PostgreSQL exact live data-profiling SQL rendering.
 * <p>Responsibility: 渲染 PostgreSQL profile query 并委托安全 JDBC template / Profiles PostgreSQL candidates.
 * <p>Inputs: candidate endpoints、connection and profile request / Candidate endpoints, connection, and request.
 * <p>Outputs: independent metrics、evidence or sanitized warning / Metrics, evidence, or warnings.
 * <p>Upstream/Downstream: core profile orchestration 上游，relationship enhancement 下游 / Feeds enhancement.
 * <p>Forbidden: 不泄露 SQL/JDBC details 或推断关系方向 / Must not leak SQL details or infer direction.
 */
package com.relationdetector.postgres.profile;
