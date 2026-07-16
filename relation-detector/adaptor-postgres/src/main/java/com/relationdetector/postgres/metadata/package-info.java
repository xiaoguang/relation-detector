/**
 * CN: PostgreSQL 实时 catalog metadata 采集与事实装配。
 *
 * <p>EN: PostgreSQL live catalog-metadata collection and fact assembly.
 * <p>Responsibility: 从 pg_catalog 采集当前 database 的 schema metadata / Collects PostgreSQL catalog metadata.
 * <p>Inputs: validated catalog/schema scope and JDBC connection / Validated scope and connection.
 * <p>Outputs: tables、columns、composite constraints/indexes and FK facts / Metadata snapshot and FK facts.
 * <p>Upstream/Downstream: core live scan 调用，metadata enhancement 消费 / Called by scan and consumed by enhancement.
 * <p>Forbidden: 不把当前 database facts 标成不同 catalog / Must not label current-database facts as another catalog.
 */
package com.relationdetector.postgres.metadata;
