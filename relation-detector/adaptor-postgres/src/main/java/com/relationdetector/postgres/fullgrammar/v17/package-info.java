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
 */
package com.relationdetector.postgres.fullgrammar.v17;
