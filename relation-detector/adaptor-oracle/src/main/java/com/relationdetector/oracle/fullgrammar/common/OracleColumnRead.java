package com.relationdetector.oracle.fullgrammar.common;

/**
 * CN: 表示 Oracle typed expression 中已确认的 qualifier/column read；routine symbols 在构造前已过滤，该值对象不解析文本或绑定物理表。
 * EN: Represents a qualifier and column read confirmed by an Oracle typed expression context. Routine symbols are filtered before construction; this value object neither parses text nor binds physical tables.
 */
public record OracleColumnRead(String alias, String column) {
}
