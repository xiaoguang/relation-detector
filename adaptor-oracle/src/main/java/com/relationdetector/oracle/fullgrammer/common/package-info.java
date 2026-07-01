/**
 * Shared Oracle full-grammer module support.
 *
 * <p>CN: 这里集中 Oracle 12c/19c/21c/26ai profile module 的公共 parser 包装。
 * 版本 package 只声明 profile 与能力。当前实现使用各版本自己的 generated
 * lexer/parser 和 typed visitor 生成结构事件，不持有 token-event parser delegate。
 * 更完整的 Oracle 官方语法覆盖应继续扩展本 full-grammer 链路，而不是复用
 * token-event 事件来源。
 *
 * <p>EN: Shared Oracle full-grammer module support. Version packages declare
 * only profiles and capabilities. The current implementation uses each version's
 * own generated lexer/parser and typed visitor to produce structured events; it
 * does not hold a token-event parser delegate. Broader Oracle official grammar
 * coverage should extend this full-grammer path instead of reusing token-event
 * event production.
 */
package com.relationdetector.oracle.fullgrammer.common;
