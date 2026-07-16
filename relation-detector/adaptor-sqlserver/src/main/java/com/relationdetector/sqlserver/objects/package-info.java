/**
 * CN: SQL Server module 对象枚举与完整声明采集。
 *
 * <p>EN: SQL Server module-object enumeration and complete declaration collection.
 * <p>Responsibility: 从 sys.sql_modules 采集 SQL Server routines/views/triggers / Collects complete module definitions.
 * <p>Inputs: validated namespace and JDBC object rows / Validated namespace and object rows.
 * <p>Outputs: DatabaseObjectDefinition 或 DEFINITION_UNAVAILABLE warning / Definitions or sanitized warnings.
 * <p>Upstream/Downstream: core live scan 调用，script/object parser 消费 / Called by scan and consumed by parser pipeline.
 * <p>Forbidden: 不传递 null definition 或 driver message / Must not pass blank definitions or driver messages.
 */
package com.relationdetector.sqlserver.objects;
