/**
 * CN: Oracle 12c Release 2 / 12.2 full-grammar profile binding。
 *
 * <p>EN: Oracle 12c Release 2 / 12.2 full-grammar profile binding.
 * <p>Responsibility: 绑定 Oracle 12c generated grammar 与 typed adapter / Binds the Oracle 12c generated grammar.
 * <p>Inputs: framed Oracle 12c SQL/DDL statements / Framed Oracle 12c statements.
 * <p>Outputs: v12c parse trees and structured events / Versioned parse trees and events.
 * <p>Upstream/Downstream: oracle-v12c grammar 上游，fullgrammar.common 下游 / Connects grammar to shared semantics.
 * <p>Forbidden: 不接受新版本专属 context 或调用其他版本 parser / Must not consume or call other versions.
 */
package com.relationdetector.oracle.fullgrammar.v12c;
