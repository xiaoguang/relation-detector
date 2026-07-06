/**
 * Oracle token-event parser entry points.
 *
 * <p>CN: Oracle token-event 是 fallback 路径，使用 typed structural grammar
 * 入口生成统一 StructuredSqlEvent。它不能使用 scanner、regex 或特殊表/列名过滤
 * 判断 SQL 结构。
 *
 * <p>EN: Oracle token-event fallback parser entry points. They emit shared
 * StructuredSqlEvent objects from structured parsing and must not rely on
 * scanner, regex, or name-specific SQL-structure inference.
 */
package com.relationdetector.oracle.tokenevent;
