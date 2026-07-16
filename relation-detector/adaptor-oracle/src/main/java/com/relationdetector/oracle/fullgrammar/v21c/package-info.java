/**
 * CN: Oracle 21c full-grammar profile binding。
 *
 * <p>EN: Oracle 21c full-grammar profile binding.
 * <p>Responsibility: 绑定 Oracle 21c generated grammar 与 typed adapter / Binds the Oracle 21c generated grammar.
 * <p>Inputs: framed Oracle 21c SQL/DDL statements / Framed Oracle 21c statements.
 * <p>Outputs: v21c parse trees and structured events / Versioned parse trees and events.
 * <p>Upstream/Downstream: oracle-v21c grammar 上游，fullgrammar.common 下游 / Connects grammar to shared semantics.
 * <p>Forbidden: 不调用其他 versioned 或 token parser / Must not delegate to another version or parser mode.
 */
package com.relationdetector.oracle.fullgrammar.v21c;
