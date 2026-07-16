/**
 * CN: Oracle 实时 catalog metadata 采集与事实装配。
 *
 * <p>EN: Oracle live catalog-metadata collection and fact assembly.
 * <p>Responsibility: 从 ALL_* views 采集 owner-scoped Oracle metadata / Collects owner-scoped Oracle metadata.
 * <p>Inputs: resolved owner and JDBC connection / Resolved owner and connection.
 * <p>Outputs: tables、columns、composite constraints/indexes and FK facts / Metadata snapshot and FK facts.
 * <p>Upstream/Downstream: core live scan 调用，metadata enhancement 消费 / Called by scan and consumed by enhancement.
 * <p>Forbidden: 不把 referenced key 自动当 unique 或泄露 JDBC message / Must not infer uniqueness or leak messages.
 */
package com.relationdetector.oracle.metadata;
