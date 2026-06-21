/**
 * ANTLR parse support 层。
 *
 * <p>CN: 本包只封装底层 lexer/parser 调用、token 提取、syntax diagnostics 和 dialect
 * 标识。它不生成业务结构事件；token-event 和 full-grammer 的事件来源层会消费这里
 * 或 adaptor grammar 提供的 parse/token 信息。
 *
 * <p>EN: ANTLR parse-support layer. It wraps low-level lexer/parser invocation,
 * token extraction, syntax diagnostics, and dialect identity. It does not emit
 * business events; token-event and full-grammer event-source layers consume the
 * parse/token information.
 */
package com.relationdetector.core.parse;
