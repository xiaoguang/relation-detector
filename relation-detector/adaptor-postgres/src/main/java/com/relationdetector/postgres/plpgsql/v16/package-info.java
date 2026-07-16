/**
 * CN: PostgreSQL 16 full-grammar 模式的 PL/pgSQL 过程外壳解析。
 *
 * <p>EN: PL/pgSQL procedural-shell parsing for PostgreSQL 16 full-grammar mode.
 * <p>Responsibility: typed 解析 PostgreSQL 16 PL/pgSQL shell / Parses the PostgreSQL 16 PL/pgSQL shell.
 * <p>Inputs: v16 routine body and source provenance / Versioned routine body and provenance.
 * <p>Outputs: static SQL fragments and procedural diagnostics / Static fragments and shell diagnostics.
 * <p>Upstream/Downstream: v16 dispatcher 上游，v16 full SQL parser 下游 / Feeds only the v16 SQL parser.
 * <p>Forbidden: 不调用 token/v17/v18 parser 或解析 SQL internals / Must not cross modes/versions or parse SQL internals.
 */
package com.relationdetector.postgres.plpgsql.v16;
