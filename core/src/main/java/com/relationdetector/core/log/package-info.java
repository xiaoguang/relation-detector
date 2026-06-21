/**
 * SQL 日志输入层。
 *
 * <p>CN: 本包负责把纯 SQL 文件或数据库原生日志拆成 SqlStatementRecord，并过滤
 * system schema、截断片段等日志噪声。它只决定“哪些 SQL 进入 parser”，不解释
 * relationship 或 lineage。
 *
 * <p>EN: SQL log input layer. It splits plain SQL files or native database logs
 * into SqlStatementRecord objects and filters system-schema/truncated noise. It
 * decides which SQL reaches parsers, not relationship or lineage semantics.
 */
package com.relationdetector.core.log;
