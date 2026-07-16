/**
 * CN: Oracle 精确 live data profiling SQL 渲染。
 *
 * <p>EN: Oracle exact live data-profiling SQL rendering.
 * <p>Responsibility: 渲染 Oracle-compatible profile queries 并委托安全 JDBC template / Profiles Oracle candidates.
 * <p>Inputs: candidate endpoints、connection and request / Candidate endpoints, connection, and request.
 * <p>Outputs: row/distinct/containment metrics、evidence or warning / Metrics, evidence, or sanitized warnings.
 * <p>Upstream/Downstream: core profiling 上游，relationship enhancement 下游 / Feeds relationship enhancement.
 * <p>Forbidden: 不泄露 query text 或把 vendor errors 都归为 permission / Must not leak SQL or overclassify errors.
 */
package com.relationdetector.oracle.profile;
