/**
 * PostgreSQL 18 full-grammar profile.
 *
 * <p>CN: 该包承载 postgresql-18 profile 的 adaptor 注册入口、独立 grammar
 * package、parser support、typed visitor 和 DDL collector，覆盖 18.x 专属
 * correctness fixture 的 profile selection。
 *
 * <p>EN: PostgreSQL 18 full-grammar profile package used by versioned
 * correctness fixtures and profile selection. It owns its grammar package,
 * parser support, typed visitor, and DDL collector.
 * <p>Responsibility: 绑定 PostgreSQL 18 generated SQL grammar 与 PL/pgSQL v18 shell / Binds the v18 parser stack.
 * <p>Inputs: framed PostgreSQL 18 SQL/DDL/routine declarations / Framed v18 statements.
 * <p>Outputs: v18 typed contexts、events and routine descriptors / Versioned contexts and events.
 * <p>Upstream/Downstream: v18 grammar artifacts 上游，common semantics 下游 / Connects grammar to common semantics.
 * <p>Forbidden: 不把 v18 capability 回填到旧版本或调用 token parser / Must preserve version boundaries.
 */
package com.relationdetector.postgres.fullgrammar.v18;
