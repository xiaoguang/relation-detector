/**
 * CN: Oracle DBMS_METADATA 表 DDL 采集。
 *
 * <p>EN: Oracle table-DDL collection through DBMS_METADATA.
 * <p>Responsibility: 通过 DBMS_METADATA 采集 Oracle table DDL / Collects Oracle table DDL through DBMS_METADATA.
 * <p>Inputs: resolved owner and JDBC connection / Resolved owner and connection.
 * <p>Outputs: DatabaseDdlDefinition 或 sanitized live warnings / DDL definitions or sanitized warnings.
 * <p>Upstream/Downstream: core live scan 调用，Oracle DDL parser 消费 / Called by scan and consumed by DDL parser.
 * <p>Forbidden: 不返回 null/blank DDL 或泄露 database errors / Must not return blank DDL or leak database errors.
 */
package com.relationdetector.oracle.ddl;
