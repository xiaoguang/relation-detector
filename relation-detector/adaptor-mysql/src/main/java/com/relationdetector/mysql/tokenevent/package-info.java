/**
 * MySQL token-event parser 子包。
 *
 * <p>CN: 本包提供 MySQL 生产 fallback parser 入口。SQL/DDL 路径运行 MySQL
 * typed structural grammar 和 parse-tree visitor，并通过 core token-event helper
 * 发射结构事件。它不包含 full-grammar 版本化实现。
 *
 * <p>EN: MySQL token-event parser package. It provides production fallback
 * parser entry points. SQL/DDL paths run the MySQL typed structural grammar
 * and parse-tree visitor and emit events through shared core token-event
 * helpers. Versioned full-grammar implementation is not kept here.
 * <p>Responsibility: 用 compact MySQL grammar 产生 typed SQL/DDL events / Emits typed events from compact grammar.
 * <p>Inputs: framed statements and per-parse adaptor context / Framed statements and parse context.
 * <p>Outputs: parser-mode independent rowset/predicate/write/DDL events / Structured SQL and DDL events.
 * <p>Upstream/Downstream: token grammar 上游，core fact extractors 下游 / Between token grammar and extractors.
 * <p>Forbidden: 不调用 full-grammar parser 或共享 mutable parse state / Must not call full grammar or share state.
 */
package com.relationdetector.mysql.tokenevent;
