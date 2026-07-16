/**
 * CN: Oracle client script framing 与 slash terminator 处理。
 *
 * <p>EN: Oracle client-script framing and slash-terminator handling.
 * <p>Responsibility: 识别 Oracle slash terminator 与 object statement boundaries / Frames Oracle client scripts.
 * <p>Inputs: raw SQL*Plus-style text and source provenance / Client text and source provenance.
 * <p>Outputs: exact statement ranges for token/full parsers / Exact statement records for parser modes.
 * <p>Upstream/Downstream: files/logs 上游，Oracle parsers 下游 / Between source files and Oracle parsers.
 * <p>Forbidden: 不解释 PL/SQL body 或产生 facts / Must not interpret PL/SQL bodies or emit facts.
 */
package com.relationdetector.oracle.script;
