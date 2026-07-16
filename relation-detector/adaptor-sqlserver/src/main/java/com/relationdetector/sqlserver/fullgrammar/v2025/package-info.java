/**
 * CN: SQL Server 2025 full-grammar binding 与 context adapter。
 *
 * <p>EN: SQL Server 2025 full-grammar binding and context adapter.
 * <p>Responsibility: 绑定 SQL Server 2025 generated grammar / Binds the SQL Server 2025 generated grammar.
 * <p>Inputs: framed SQL Server 2025 T-SQL / Framed SQL Server 2025 statements.
 * <p>Outputs: v2025 parse trees and typed events / Versioned parse trees and events.
 * <p>Upstream/Downstream: v2025 grammar 上游，fullgrammar.common 下游 / Connects grammar to shared semantics.
 * <p>Forbidden: 不把 2025 feature 暴露为旧版本能力 / Must not expose 2025 features as older-version support.
 */
package com.relationdetector.sqlserver.fullgrammar.v2025;
