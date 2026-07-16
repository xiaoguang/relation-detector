/**
 * MySQL 5.7 full-grammar profile 实现包。
 *
 * <p>CN: 本包持有 vendored MySQL 5.7 full grammar、ANTLR helper、typed parse-tree
 * visitor、expression analyzer、SQL/DDL parser 和 profile module。版本由 package
 * 表达；类名不再携带 80。这里输出统一 StructuredSqlEvent / DDL events，语义判断
 * 仍回到 core。
 *
 * <p>EN: MySQL 5.7 full-grammar profile implementation package. It owns the
 * vendored grammar, ANTLR helpers, typed parse-tree visitor, expression
 * analyzer, SQL/DDL parsers, and profile module. Version is expressed by the
 * package; classes do not carry 80. It emits unified StructuredSqlEvent / DDL
 * events while semantic decisions stay in core.
 * <p>Responsibility: 绑定 MySQL 5.7 generated grammar 与 typed adapter / Binds the MySQL 5.7 generated grammar.
 * <p>Inputs: framed MySQL 5.7 SQL/DDL statements / Framed MySQL 5.7 statements.
 * <p>Outputs: v5.7 full-grammar parse trees and structured events / Versioned parse trees and events.
 * <p>Upstream/Downstream: grammar artifact 上游，fullgrammar.common 下游 / Between grammar artifact and common semantics.
 * <p>Forbidden: 不接受 8.0-only context 或调用 v8.0 parser / Must not accept 8.0 contexts or call its parser.
 */
package com.relationdetector.mysql.fullgrammar.v5_7;
