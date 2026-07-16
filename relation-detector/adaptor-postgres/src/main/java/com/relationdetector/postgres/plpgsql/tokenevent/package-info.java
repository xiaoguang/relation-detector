/**
 * CN: token-event 模式的 compact PL/pgSQL 过程外壳解析。
 *
 * <p>EN: Compact PL/pgSQL procedural-shell parsing for token-event mode.
 * <p>Responsibility: 用 compact PL/pgSQL shell grammar typed 定位过程结构与 static SQL / Parses the compact shell.
 * <p>Inputs: token-event routine body and provenance / Routine body and provenance.
 * <p>Outputs: static fragments、locals、dynamic/unsupported locations / Shell structure and diagnostics locations.
 * <p>Upstream/Downstream: routine dispatcher 上游，token-event SQL parser 下游 / Feeds the token-event SQL parser.
 * <p>Forbidden: 不加载 versioned parser 或复制 SQL 子语法 / Must not load version parsers or duplicate SQL grammar.
 */
package com.relationdetector.postgres.plpgsql.tokenevent;
