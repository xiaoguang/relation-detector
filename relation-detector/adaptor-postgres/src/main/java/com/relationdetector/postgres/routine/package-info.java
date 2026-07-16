/**
 * CN: PostgreSQL 方言级 routine body 解析与 language 分发。
 *
 * <p>EN: This package is intentionally outside {@code fullgrammar}: token-event and
 * versioned full-grammar parsers both use it after extracting a PL/pgSQL routine
 * body from the outer statement.
 * <p>Responsibility: 建模 routine body、按 LANGUAGE 分发并回调当前 parser mode / Dispatches PostgreSQL routines.
 * <p>Inputs: outer grammar routine descriptor、body and provenance / Typed descriptor, body, and provenance.
 * <p>Outputs: structured events、warnings and recovery counts / Events, diagnostics, and recovery counts.
 * <p>Upstream/Downstream: outer SQL parser 上游，PL/pgSQL shell/current SQL parser 下游 / Connects routine parsing stages.
 * <p>Forbidden: 不猜缺失 LANGUAGE，不扫描 dynamic SQL，不跨 parser mode / Must not guess, scan dynamics, or cross modes.
 */
package com.relationdetector.postgres.routine;
