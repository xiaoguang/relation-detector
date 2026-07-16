/**
 * CN: Oracle 19c full-grammar profile binding。
 *
 * <p>EN: Oracle 19c full-grammar profile binding.
 * <p>Responsibility: 绑定 Oracle 19c generated grammar 与 typed adapter / Binds the Oracle 19c generated grammar.
 * <p>Inputs: framed Oracle 19c SQL/DDL statements / Framed Oracle 19c statements.
 * <p>Outputs: v19c parse trees and structured events / Versioned parse trees and events.
 * <p>Upstream/Downstream: oracle-v19c grammar 上游，fullgrammar.common 下游 / Connects grammar to shared semantics.
 * <p>Forbidden: 不调用 12c/21c/26ai 或 token parser / Must not delegate to other versions or token mode.
 */
package com.relationdetector.oracle.fullgrammar.v19c;
