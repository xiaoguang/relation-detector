/**
 * CN: SQL Server 2022 full-grammar binding 与 context adapter。
 *
 * <p>EN: SQL Server 2022 full-grammar binding and context adapter.
 * <p>Responsibility: 绑定 SQL Server 2022 generated grammar / Binds the SQL Server 2022 generated grammar.
 * <p>Inputs: framed SQL Server 2022 T-SQL / Framed SQL Server 2022 statements.
 * <p>Outputs: v2022 parse trees and typed events / Versioned parse trees and events.
 * <p>Upstream/Downstream: v2022 grammar 上游，fullgrammar.common 下游 / Connects grammar to shared semantics.
 * <p>Forbidden: 不调用其他版本或 token parser / Must not delegate to another version or parser mode.
 */
package com.relationdetector.sqlserver.fullgrammar.v2022;
