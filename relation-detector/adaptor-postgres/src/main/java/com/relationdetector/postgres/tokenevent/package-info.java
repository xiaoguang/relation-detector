/**
 * PostgreSQL token-event parser 子包。
 *
 * <p>CN: 本包提供 PostgreSQL 生产 fallback parser 入口。SQL/DDL 路径运行
 * PostgreSQL typed structural grammar 和 parse-tree visitor，并通过 core token-event
 * helper 发射结构事件。它不包含 full-grammar 版本化实现。
 *
 * <p>EN: PostgreSQL token-event parser package. It provides production fallback
 * parser entry points. SQL/DDL paths run the PostgreSQL typed structural
 * grammar and parse-tree visitor and emit events through shared core token-event
 * helpers. Versioned full-grammar implementation is not kept here.
 * <p>Responsibility: 用 compact PostgreSQL grammar 产生 typed SQL/DDL/routine declaration events / Emits typed events.
 * <p>Inputs: framed PostgreSQL statements and per-parse context / Framed statements and parse context.
 * <p>Outputs: structured events and routine descriptors / Structured SQL/DDL events and routine descriptors.
 * <p>Upstream/Downstream: token grammar 上游，core extractors/routine dispatcher 下游 / Feeds extractors and dispatcher.
 * <p>Forbidden: 不加载 versioned full parser 或共享 mutable state / Must not load version parsers or share state.
 */
package com.relationdetector.postgres.tokenevent;
