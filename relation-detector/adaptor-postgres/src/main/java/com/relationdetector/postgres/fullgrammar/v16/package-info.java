/**
 * PostgreSQL 16 full-grammar profile 实现包。
 *
 * <p>CN: 本包持有 vendored PostgreSQL 16 full grammar、ANTLR helper、typed
 * parse-tree visitor、expression analyzer、SQL/DDL parser 和 profile module。版本由
 * package 表达；类名不再携带 16。这里输出统一 StructuredSqlEvent / DDL events，
 * 语义判断仍回到 core。
 *
 * <p>EN: PostgreSQL 16 full-grammar profile implementation package. It owns the
 * vendored grammar, ANTLR helpers, typed parse-tree visitor, expression
 * analyzer, SQL/DDL parsers, and profile module. Version is expressed by the
 * package; classes do not carry 16. It emits unified StructuredSqlEvent / DDL
 * events while semantic decisions stay in core.
 * <p>Responsibility: 绑定 PostgreSQL 16 generated SQL grammar 与 PL/pgSQL v16 shell / Binds the v16 parser stack.
 * <p>Inputs: framed PostgreSQL 16 SQL/DDL/routine declarations / Framed v16 statements.
 * <p>Outputs: v16 typed contexts、events and routine descriptors / Versioned contexts and events.
 * <p>Upstream/Downstream: v16 grammar artifacts 上游，common semantics 下游 / Connects grammar to common semantics.
 * <p>Forbidden: 不接受 MERGE RETURNING 或调用 v17/v18/token parser / Must preserve v16 boundaries and independence.
 */
package com.relationdetector.postgres.fullgrammar.v16;
