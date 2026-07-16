/**
 * CN: SQL Server full-grammar 各版本共享的 typed 语义支持。
 *
 * <p>EN: Typed semantic support shared by versioned SQL Server full-grammar parsers.
 * <p>Responsibility: 共享 SQL Server 2016-2025 typed event semantics / Shares SQL Server full-grammar semantics.
 * <p>Inputs: version bindings 提供的 generated parse trees / Generated trees from version bindings.
 * <p>Outputs: rowset、predicate、write、DDL and expression events / Structured events and traces.
 * <p>Upstream/Downstream: version modules 上游，core extractors 下游 / Between version modules and core extraction.
 * <p>Forbidden: 不按 rule name/反射推断 context，不调用 token parser / Must not use reflection or delegate modes.
 */
package com.relationdetector.sqlserver.fullgrammar.common;
