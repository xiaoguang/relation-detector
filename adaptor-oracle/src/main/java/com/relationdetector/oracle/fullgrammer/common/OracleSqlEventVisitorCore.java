package com.relationdetector.oracle.fullgrammer.common;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared Oracle SQL event helper surface.
 *
 * <p>The generated version visitors remain typed adapters. Common event
 * assembly belongs here so version packages can stay thin as Oracle grammar
 * coverage grows.
 */
public final class OracleSqlEventVisitorCore {
    private OracleSqlEventVisitorCore() {
    }

    public static Map<String, Object> fullGrammerAttributes() {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("fullGrammerNative", true);
        return attrs;
    }
}
