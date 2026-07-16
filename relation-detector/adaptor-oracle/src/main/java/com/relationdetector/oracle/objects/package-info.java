/**
 * CN: Oracle 数据库对象枚举与完整声明采集。
 *
 * <p>EN: Oracle database-object enumeration and complete declaration collection.
 * <p>Responsibility: 枚举 Oracle objects 并通过 DBMS_METADATA 获取完整 declaration / Collects complete declarations.
 * <p>Inputs: resolved owner、JDBC connection and object catalog rows / Owner, connection, and object rows.
 * <p>Outputs: DatabaseObjectDefinition 或 DEFINITION_UNAVAILABLE warning / Definitions or safe warnings.
 * <p>Upstream/Downstream: core live scan 调用，script/object parser 消费 / Called by scan and consumed by parser pipeline.
 * <p>Forbidden: 不静默跳过 blank definition 或返回 fragment / Must not silently skip blanks or return fragments.
 */
package com.relationdetector.oracle.objects;
