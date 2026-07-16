/**
 * PostgreSQL 17 full-grammar profile.
 *
 * <p>CN: 该包承载 postgresql-17 profile 的 adaptor 注册入口、独立 grammar
 * package、parser support、typed visitor 和 DDL collector。版本选择发生在 core
 * profile registry，具体 parser module 由 adaptor 通过 ServiceLoader 注入。
 *
 * <p>EN: PostgreSQL 17 full-grammar profile package. It owns its grammar
 * package, parser support, typed visitor, and DDL collector. Core selects the
 * profile; the adaptor contributes the concrete module through ServiceLoader.
 * <p>Responsibility: 绑定 PostgreSQL 17 generated SQL grammar 与 PL/pgSQL v17 shell / Binds the v17 parser stack.
 * <p>Inputs: framed PostgreSQL 17 SQL/DDL/routine declarations / Framed v17 statements.
 * <p>Outputs: v17 typed contexts、events and routine descriptors / Versioned contexts and events.
 * <p>Upstream/Downstream: v17 grammar artifacts 上游，common semantics 下游 / Connects grammar to common semantics.
 * <p>Forbidden: 不调用 v16/v18 或 token parser / Must not delegate to another version or parser mode.
 */
package com.relationdetector.postgres.fullgrammar.v17;
