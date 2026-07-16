/**
 * CN: MySQL 数据库对象枚举与完整 SHOW CREATE 声明采集。
 *
 * <p>EN: MySQL database-object enumeration and complete SHOW CREATE declaration collection.
 * <p>Responsibility: 通过 SHOW CREATE 采集完整 MySQL routine/view/trigger/event declarations / Collects declarations.
 * <p>Inputs: canonical catalog scope、JDBC connection and object inventory / Scope, connection, and inventory.
 * <p>Outputs: typed DatabaseObjectDefinition 或安全 warning / Complete definitions or sanitized warnings.
 * <p>Upstream/Downstream: core live scan 调用，script/parser pipeline 消费 / Called by scan and consumed by parser pipeline.
 * <p>Forbidden: 不把 body fragment 当完整 declaration 或返回空 SQL / Must not expose fragments or blank SQL.
 */
package com.relationdetector.mysql.objects;
