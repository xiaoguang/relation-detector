/**
 * CN: 方言 script framing 编排、slice planner 与来源装配。
 *
 * <p>EN: Dialect script-framing orchestration, slice planners, and provenance assembly.
 * <p>Responsibility: 根据 dialect lexemes 规划 statement slices 并装配 provenance / Frames scripts into statements.
 * <p>Inputs: script text、typed client lexemes 与 source metadata / Script text, client lexemes, and source metadata.
 * <p>Outputs: 精确字符区间、行号、object descriptor 的 records / Records with exact ranges and descriptors.
 * <p>Upstream/Downstream: file/log input 上游，SQL/DDL parser 下游 / Between source input and parsers.
 * <p>Forbidden: 不解释 SQL 结构或产生 relationship/lineage / Must not interpret SQL structure or emit facts.
 */
package com.relationdetector.core.script;
