/**
 * CN: PostgreSQL routine、view 与 trigger 对象声明采集。
 *
 * <p>EN: PostgreSQL routine, view, and trigger declaration collection.
 * <p>Responsibility: 枚举 PostgreSQL routines/views/triggers 并获取完整 definitions / Collects complete object definitions.
 * <p>Inputs: validated namespace and JDBC catalog rows / Validated namespace and catalog rows.
 * <p>Outputs: DatabaseObjectDefinition 或 DEFINITION_UNAVAILABLE warning / Definitions or sanitized warnings.
 * <p>Upstream/Downstream: core live scan 调用，script/routine parser 消费 / Called by scan and consumed by parser pipeline.
 * <p>Forbidden: 不返回 null SQL 或将 function body fragment 伪装 declaration / Must not return null or fragments.
 */
package com.relationdetector.postgres.objects;
