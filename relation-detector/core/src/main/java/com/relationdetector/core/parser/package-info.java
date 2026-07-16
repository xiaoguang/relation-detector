/**
 * SQL/DDL parser 选择与运行层。
 *
 * <p>CN: 本包按 {@code parser.mode}、grammar profile、数据库版本和 adaptor 能力
 * 选择 full-grammar 或 token-event，并把结构化结果交给 relationship / DDL
 * 语义抽取。fallback 只发生在这里，不在 full-grammar event 生成层混入 token-event
 * 事件。
 *
 * <p>EN: SQL/DDL parser selection and runner layer. It selects full-grammar or
 * token-event by parser.mode, grammar profile, database version, and adaptor
 * capability, then feeds structured results into semantic extraction. Fallback
 * happens here, not inside full-grammar event generation.
 * <p>Responsibility: 编排 structured SQL/DDL parser 与事实 extractor / Orchestrates parsers and fact extractors.
 * <p>Inputs: framed statement、adaptor parser capabilities 与 context / Statements, parser capabilities, and context.
 * <p>Outputs: direct relationship/lineage/DDL outcomes and diagnostics / Direct facts and diagnostics.
 * <p>Upstream/Downstream: statement execution 上游，evidence enhancement 下游 / Between execution and enhancement.
 * <p>Forbidden: 不运行 naming rules 两次或绕过 typed parse contracts / Must not duplicate naming or bypass typed parse.
 */
package com.relationdetector.core.parser;
