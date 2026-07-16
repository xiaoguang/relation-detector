/**
 * CN: MySQL 实时表 DDL 采集与 DDL 输入适配。
 *
 * <p>EN: MySQL live table-DDL collection and DDL input adaptation.
 * <p>Responsibility: 通过 SHOW CREATE 采集完整 MySQL table DDL / Collects complete MySQL table DDL.
 * <p>Inputs: canonical catalog scope and JDBC connection / Canonical catalog scope and connection.
 * <p>Outputs: DatabaseDdlDefinition 或安全 live warning / DDL definitions or sanitized warnings.
 * <p>Upstream/Downstream: core live scan 调用，structured DDL parser 消费 / Called by scan and consumed by DDL parser.
 * <p>Forbidden: 不返回空 definition 或泄露 driver message / Must not return blank definitions or leak driver text.
 */
package com.relationdetector.mysql.ddl;
