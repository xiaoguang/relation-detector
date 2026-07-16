/**
 * Oracle token-event parser entry points.
 *
 * <p>CN: Oracle token-event 是 fallback 路径，使用 typed structural grammar
 * 入口生成统一 StructuredSqlEvent。它不能使用 scanner、regex 或特殊表/列名过滤
 * 判断 SQL 结构。
 *
 * <p>EN: Oracle token-event fallback parser entry points. They emit shared
 * StructuredSqlEvent objects from structured parsing and must not rely on
 * scanner, regex, or name-specific SQL-structure inference.
 * <p>Responsibility: 用 compact Oracle grammar 产生 typed SQL/DDL events / Emits typed events from compact grammar.
 * <p>Inputs: framed Oracle statements and per-parse context / Framed statements and parse context.
 * <p>Outputs: rowset、predicate、write、trigger and DDL events / Structured SQL and DDL events.
 * <p>Upstream/Downstream: token grammar 上游，core extractors 下游 / Between compact grammar and core extractors.
 * <p>Forbidden: 不调用 full grammar 或共享 mutable routine state / Must not call full grammar or share parse state.
 */
package com.relationdetector.oracle.tokenevent;
