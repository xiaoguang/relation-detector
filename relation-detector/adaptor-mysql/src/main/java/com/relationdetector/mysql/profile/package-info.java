/**
 * CN: MySQL 精确 live data profiling SQL 渲染。
 *
 * <p>EN: MySQL exact live data-profiling SQL rendering.
 * <p>Responsibility: 渲染并执行 MySQL containment profile queries / Renders and runs MySQL profile queries.
 * <p>Inputs: candidate endpoints、connection and profile request / Candidate endpoints, connection, and request.
 * <p>Outputs: independent row/distinct metrics、evidence or safe warning / Metrics, evidence, or safe warnings.
 * <p>Upstream/Downstream: core profile template 调用，relationship enhancement 消费 / Feeds relationship enhancement.
 * <p>Forbidden: 不输出 SQL 或决定 relationship direction / Must not leak SQL or decide relationship direction.
 */
package com.relationdetector.mysql.profile;
