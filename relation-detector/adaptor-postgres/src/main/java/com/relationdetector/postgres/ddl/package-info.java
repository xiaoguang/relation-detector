/**
 * CN: PostgreSQL 实时 catalog 表 DDL 重建。
 *
 * <p>EN: PostgreSQL live-catalog table-DDL reconstruction.
 * <p>Responsibility: 通过 pg_get_* definition functions 采集 PostgreSQL database DDL / Collects live PostgreSQL DDL.
 * <p>Inputs: validated catalog/schema scope and JDBC connection / Validated scope and connection.
 * <p>Outputs: DatabaseDdlDefinition 或 sanitized live warning / DDL definitions or sanitized warnings.
 * <p>Upstream/Downstream: core live scan 调用，structured DDL parser 消费 / Called by scan and consumed by DDL parser.
 * <p>Forbidden: 不执行隐式跨 catalog 查询或返回 blank definition / Must not query another catalog or return blanks.
 */
package com.relationdetector.postgres.ddl;
