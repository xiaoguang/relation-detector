/**
 * CN: SQL Server token-event typed SQL/DDL 事件采集。
 *
 * <p>EN: SQL Server token-event typed SQL and DDL event collection.
 * <p>Responsibility: 用 compact SQL Server grammar 产生 typed SQL/DDL events / Emits typed events from compact grammar.
 * <p>Inputs: framed T-SQL statements and per-parse context / Framed statements and parse context.
 * <p>Outputs: rowset、predicate、APPLY、write、trigger and DDL events / Structured SQL and DDL events.
 * <p>Upstream/Downstream: token grammar 上游，core extractors 下游 / Between compact grammar and core extraction.
 * <p>Forbidden: 不调用 versioned full parser 或共享 mutable state / Must not call full grammar or share state.
 */
package com.relationdetector.sqlserver.tokenevent;
