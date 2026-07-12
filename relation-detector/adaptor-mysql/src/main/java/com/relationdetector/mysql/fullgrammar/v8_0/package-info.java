/**
 * MySQL 8.0 full-grammar profile 实现包。
 *
 * <p>CN: 本包持有 vendored MySQL 8.0 full grammar、ANTLR helper、typed parse-tree
 * visitor、expression analyzer、SQL/DDL parser 和 profile module。版本由 package
 * 表达；类名不再携带 80。这里输出统一 StructuredSqlEvent / DDL events，语义判断
 * 仍回到 core。
 *
 * <p>EN: MySQL 8.0 full-grammar profile implementation package. It owns the
 * vendored grammar, ANTLR helpers, typed parse-tree visitor, expression
 * analyzer, SQL/DDL parsers, and profile module. Version is expressed by the
 * package; classes do not carry 80. It emits unified StructuredSqlEvent / DDL
 * events while semantic decisions stay in core.
 */
package com.relationdetector.mysql.fullgrammar.v8_0;
