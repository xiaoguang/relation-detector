/**
 * CN: MySQL full-grammar 各版本共享的 typed 语义支持。
 *
 * <p>EN: Typed semantic support shared by versioned MySQL full-grammar parsers.
 * <p>Responsibility: 共享 MySQL 5.7/8.0 typed event 与 expression semantics / Shares MySQL full-grammar semantics.
 * <p>Inputs: version adapter 提供的 generated contexts / Generated contexts exposed by version adapters.
 * <p>Outputs: StructuredSqlEvent、DDL events 与 expression traces / Typed events and expression traces.
 * <p>Upstream/Downstream: version bindings 上游，core extractors 下游 / Between version bindings and core extractors.
 * <p>Forbidden: 不 import 另一版本 parser 或回退 token-event / Must not import another version or delegate modes.
 */
package com.relationdetector.mysql.fullgrammar.common;
