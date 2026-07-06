package com.relationdetector.oracle.fullgrammer.common;

/**
 * Column reference extracted from an Oracle typed expression context.
 */
public record OracleColumnRead(String alias, String column) {
}
