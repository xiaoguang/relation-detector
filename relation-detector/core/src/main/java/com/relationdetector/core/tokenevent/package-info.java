/**
 * token-event 事件来源层。
 *
 * <p>CN: 本包基于 typed structural ANTLR grammar 和 parse-tree visitor 生成统一
 * StructuredSqlEvent。它是无 profile、版本不支持或显式 {@code parser.mode=token-event}
 * 时的正式 fallback。它只负责结构事件来源，不负责 FK-like 方向、共现或 lineage
 * 置信度语义。
 *
 * <p>EN: Token-event event-source layer. It builds unified StructuredSqlEvent
 * records from typed structural ANTLR grammar contexts and is the official
 * fallback when no full-grammar profile is selected. It owns structure extraction, not
 * FK-like direction, co-occurrence, lineage transform, or confidence semantics.
 * <p>Responsibility: 提供 compact token-event parser 的公共 typed event helpers / Provides common token-event helpers.
 * <p>Inputs: common/adaptor generated token-event contexts / Generated compact grammar contexts.
 * <p>Outputs: parser-mode 独立的 StructuredSqlEvent records / Parser-mode-neutral structured events.
 * <p>Upstream/Downstream: compact grammar 上游，core extractors 下游 / Between compact grammars and extractors.
 * <p>Forbidden: 不调用 full-grammar parser 或以 raw SQL 补结构 / Must not call full grammar or scan raw SQL.
 */
package com.relationdetector.core.tokenevent;
