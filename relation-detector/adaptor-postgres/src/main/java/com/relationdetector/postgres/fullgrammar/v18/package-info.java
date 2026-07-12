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
 */
package com.relationdetector.postgres.fullgrammar.v18;
