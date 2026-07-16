/**
 * CN: PostgreSQL client script framing 与 dollar-quote 边界处理。
 *
 * <p>EN: PostgreSQL client-script framing and dollar-quote boundary handling.
 * <p>Responsibility: 识别 dollar quote 与 PostgreSQL client statement boundaries / Frames PostgreSQL scripts.
 * <p>Inputs: raw script text and source provenance / Raw script text and provenance.
 * <p>Outputs: exact statement records including routine declarations / Exact statement records for parsers.
 * <p>Upstream/Downstream: files/logs 上游，outer SQL parser 下游 / Between sources and outer SQL parsers.
 * <p>Forbidden: 不解析 dollar body 或推断 routine LANGUAGE / Must not parse bodies or infer routine language.
 */
package com.relationdetector.postgres.script;
