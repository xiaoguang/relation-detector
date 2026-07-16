/**
 * SQL、DDL 和对象定义解析契约层。
 *
 * <p>CN: 本包定义 parser 输入、结构化事件和 parse result。token-event 与
 * full-grammar 都必须输出这里的统一事件形状，后续 relationship / lineage
 * 语义层才可以共享同一套抽取逻辑。
 *
 * <p>EN: Parse-contract layer for SQL, DDL, and database object definitions.
 * Both token-event and full-grammar parsers emit the unified event shape defined
 * here so relationship and lineage semantic extractors can stay shared.
 * <p>Responsibility: 定义 framing 与 typed parse 交换模型 / Defines framing and typed-parse exchange models.
 * <p>Inputs: script text、statement provenance 与 typed grammar events / Script text, provenance, and typed events.
 * <p>Outputs: statement records、parse results 与 live definitions / Statement records, parse results, and definitions.
 * <p>Upstream/Downstream: framer/parser 生产，core extraction 消费 / Produced by framers/parsers and consumed by core.
 * <p>Forbidden: 不实现任何方言 grammar 或事实推断 / Must not implement dialect grammars or fact inference.
 */
package com.relationdetector.contracts.parse;
