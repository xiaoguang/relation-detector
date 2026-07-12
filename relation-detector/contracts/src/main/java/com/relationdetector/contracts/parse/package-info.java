/**
 * SQL、DDL 和对象定义解析契约层。
 *
 * <p>CN: 本包定义 parser 输入、结构化事件和 parse result。token-event 与
 * full-grammar 都必须输出这里的统一事件形状，后续 relationship / lineage
 * 语义层才可以共享同一套抽取逻辑。
 *
 * <p>EN: Parse-contract layer for SQL, DDL, and database object definitions.
 * Both token-event and full-grammar parsers emit the unified event shape defined
 * here so relationship and lineage semantic extractors can stay shared.
 */
package com.relationdetector.contracts.parse;
