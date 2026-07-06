/**
 * PostgreSQL 16 full-grammer profile 实现包。
 *
 * <p>CN: 本包持有 vendored PostgreSQL 16 full grammar、ANTLR helper、typed
 * parse-tree visitor、expression analyzer、SQL/DDL parser 和 profile module。版本由
 * package 表达；类名不再携带 16。这里输出统一 StructuredSqlEvent / DDL events，
 * 语义判断仍回到 core。
 *
 * <p>EN: PostgreSQL 16 full-grammer profile implementation package. It owns the
 * vendored grammar, ANTLR helpers, typed parse-tree visitor, expression
 * analyzer, SQL/DDL parsers, and profile module. Version is expressed by the
 * package; classes do not carry 16. It emits unified StructuredSqlEvent / DDL
 * events while semantic decisions stay in core.
 */
package com.relationdetector.postgres.fullgrammer.v16;
