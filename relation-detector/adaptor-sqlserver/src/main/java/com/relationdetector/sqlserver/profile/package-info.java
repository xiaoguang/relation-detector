/**
 * CN: SQL Server 精确 live data profiling SQL 渲染。
 *
 * <p>EN: SQL Server exact live data-profiling SQL rendering.
 * <p>Responsibility: 渲染 SQL Server profile queries 并委托安全 JDBC template / Profiles SQL Server candidates.
 * <p>Inputs: candidate endpoints、connection and request / Candidate endpoints, connection, and request.
 * <p>Outputs: independent metrics、evidence or sanitized warning / Metrics, evidence, or warnings.
 * <p>Upstream/Downstream: core profile orchestration 上游，relationship enhancement 下游 / Feeds enhancement.
 * <p>Forbidden: 不泄露 SQL/URL 或把全部错误归为 permission / Must not leak details or overclassify errors.
 */
package com.relationdetector.sqlserver.profile;
