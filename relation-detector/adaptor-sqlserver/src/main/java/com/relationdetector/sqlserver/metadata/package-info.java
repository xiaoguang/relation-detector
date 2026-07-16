/**
 * CN: SQL Server 实时 sys catalog metadata 采集与事实装配。
 *
 * <p>EN: SQL Server live sys-catalog metadata collection and fact assembly.
 * <p>Responsibility: 从 sys catalogs 采集当前 database 的 SQL Server metadata / Collects SQL Server metadata.
 * <p>Inputs: validated catalog/schema scope and JDBC connection / Validated scope and connection.
 * <p>Outputs: tables、columns、composite constraints/indexes and FK facts / Metadata snapshot and FK facts.
 * <p>Upstream/Downstream: core live scan 调用，metadata enhancement 消费 / Called by scan and consumed by enhancement.
 * <p>Forbidden: 不把当前 database facts 标成另一个 catalog / Must not mislabel current-database facts.
 */
package com.relationdetector.sqlserver.metadata;
