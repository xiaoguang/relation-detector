/**
 * CN: Oracle 26ai full-grammar profile binding。
 *
 * <p>EN: Oracle 26ai full-grammar profile binding.
 * <p>Responsibility: 绑定 Oracle 26ai generated grammar 与 typed adapter / Binds the Oracle 26ai generated grammar.
 * <p>Inputs: framed Oracle 26ai SQL/DDL statements / Framed Oracle 26ai statements.
 * <p>Outputs: v26ai parse trees and structured events / Versioned parse trees and events.
 * <p>Upstream/Downstream: oracle-v26ai grammar 上游，fullgrammar.common 下游 / Connects grammar to shared semantics.
 * <p>Forbidden: 不把 26ai feature 宣称为旧版本能力 / Must not expose 26ai features as older-version support.
 */
package com.relationdetector.oracle.fullgrammar.v26ai;
