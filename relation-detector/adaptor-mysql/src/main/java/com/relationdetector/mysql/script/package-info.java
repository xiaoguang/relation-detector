/**
 * CN: MySQL client script framing 与 DELIMITER 边界处理。
 *
 * <p>EN: MySQL client-script framing and DELIMITER boundary handling.
 * <p>Responsibility: 词法识别 MySQL DELIMITER 和 client statement boundaries / Frames MySQL client scripts.
 * <p>Inputs: raw client script text and source provenance / Client script text and provenance.
 * <p>Outputs: exact statement ranges passed to current parser mode / Exact statement records for parsers.
 * <p>Upstream/Downstream: files/logs 上游，token/full parser 下游 / Between source files and parser modes.
 * <p>Forbidden: 不解析 routine SQL 或生成 facts / Must not parse routine semantics or emit facts.
 */
package com.relationdetector.mysql.script;
