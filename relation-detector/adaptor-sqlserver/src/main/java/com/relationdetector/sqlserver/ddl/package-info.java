/**
 * CN: SQL Server sys catalog 表 DDL 与组合约束重建。
 *
 * <p>EN: SQL Server sys-catalog table-DDL and composite-constraint reconstruction.
 * <p>Responsibility: 从 sys catalogs 与 module definitions 重建 SQL Server DDL / Collects SQL Server database DDL.
 * <p>Inputs: validated catalog/schema scope and JDBC connection / Validated scope and connection.
 * <p>Outputs: 供 structured DDL parser 消费的 structural skeleton 或 sanitized warning；不承诺可回放的完整原始 DDL /
 * Structural skeletons for the structured DDL parser, or sanitized warnings; not replayable complete declarations.
 * <p>Upstream/Downstream: core live scan 调用，structured DDL parser 消费 / Called by scan and consumed by DDL parser.
 * <p>Forbidden: 不错配 composite FK ordinals 或返回 blank SQL / Must not mispair composite FKs or return blank SQL.
 */
package com.relationdetector.sqlserver.ddl;
