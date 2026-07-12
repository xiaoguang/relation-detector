package com.relationdetector.oracle.fullgrammar.common;

/**
 * Column reference extracted from an Oracle typed expression context.
 */
public record OracleColumnRead(String alias, String column) {
}
