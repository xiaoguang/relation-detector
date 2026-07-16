/**
 * CN: SQL Server client script framing 与 GO batch 处理。
 *
 * <p>EN: SQL Server client-script framing and GO-batch handling.
 * <p>Responsibility: 识别 standalone GO 与 SQL Server client batch boundaries / Frames SQL Server client scripts.
 * <p>Inputs: raw client text and source provenance / Client text and provenance.
 * <p>Outputs: exact batch statement records for parser modes / Exact statement records.
 * <p>Upstream/Downstream: files/logs 上游，token/full parser 下游 / Between source files and parser modes.
 * <p>Forbidden: 不把字符串/注释中的 GO 当 separator 或解析 T-SQL facts / Must not split false GO or emit facts.
 */
package com.relationdetector.sqlserver.script;
