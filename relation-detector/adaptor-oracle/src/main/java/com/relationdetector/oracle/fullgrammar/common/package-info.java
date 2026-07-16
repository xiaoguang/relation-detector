/**
 * Shared Oracle full-grammar module support.
 *
 * <p>CN: 这里集中 Oracle 12c/19c/21c/26ai profile module 的公共 parser 包装。
 * 版本 package 只声明 profile 与能力。当前实现使用各版本自己的 generated
 * lexer/parser 和 typed visitor 生成结构事件，不持有 token-event parser delegate。
 * 更完整的 Oracle 官方语法覆盖应继续扩展本 full-grammar 链路，而不是复用
 * token-event 事件来源。
 *
 * <p>EN: Shared Oracle full-grammar module support. Version packages declare
 * only profiles and capabilities. The current implementation uses each version's
 * own generated lexer/parser and typed visitor to produce structured events; it
 * does not hold a token-event parser delegate. Broader Oracle official grammar
 * coverage should extend this full-grammar path instead of reusing token-event
 * event production.
 * <p>Responsibility: 共享 Oracle 12c/19c/21c/26ai typed event semantics / Shares Oracle full-grammar event semantics.
 * <p>Inputs: version adapter 提供的 official grammar contexts / Version-adapted generated contexts.
 * <p>Outputs: rowset、predicate、write、DDL 与 expression events / Structured SQL/DDL events and traces.
 * <p>Upstream/Downstream: version modules 上游，core extractors 下游 / Between version modules and core extractors.
 * <p>Forbidden: 不反射 context、不扫描 raw SQL、不调用 token parser / Must not reflect, scan text, or delegate modes.
 */
package com.relationdetector.oracle.fullgrammar.common;
