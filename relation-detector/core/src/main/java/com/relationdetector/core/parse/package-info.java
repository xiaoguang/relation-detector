/**
 * ANTLR parse support 层。
 *
 * <p>CN: 本包只封装底层 lexer/parser 调用、token 提取、syntax diagnostics 和 dialect
 * 标识。它不生成业务结构事件；token-event 和 full-grammar 的事件来源层会消费这里
 * 或 adaptor grammar 提供的 parse/token 信息。
 *
 * <p>EN: ANTLR parse-support layer. It wraps low-level lexer/parser invocation,
 * token extraction, syntax diagnostics, and dialect identity. It does not emit
 * business events; token-event and full-grammar event-source layers consume the
 * parse/token information.
 * <p>Responsibility: 提供 ANTLR invocation、SLL/LL fallback 和 dialect parse support / Provides ANTLR invocation support.
 * <p>Inputs: token streams、parser factories 与 typed root functions / Token streams and parser/root factories.
 * <p>Outputs: parse root、syntax errors 与确定的 fallback outcome / Parse roots, errors, and fallback outcomes.
 * <p>Upstream/Downstream: adaptor parsers 调用，typed visitors 消费 / Called by adaptors and consumed by visitors.
 * <p>Forbidden: 不缓存业务结果或改变 parser 的语义事件 / Must not cache business results or alter semantic events.
 */
package com.relationdetector.core.parse;
