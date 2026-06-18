package com.relationdetector.core;

import java.util.Locale;

/**
 * Runtime DDL relationship parser selection.
 *
 * <p>DDL primary switching is deliberately independent from SQL primary
 * switching. DDL inputs produce relationship evidence from schema definitions,
 * while SQL inputs infer relationships from query predicates; keeping separate
 * modes lets each chain mature and be rolled back independently.
 */
public enum DdlParserMode {
    /** Run only the current relationship-producing DDL parser. */
    SIMPLE_DDL,
    /** Return current DDL parser output, but run ANTLR DDL extraction and compare. */
    ANTLR_DDL_SHADOW,
    /** Return ANTLR DDL output when it does not miss the current DDL baseline. */
    ANTLR_DDL_PRIMARY;

    public static DdlParserMode fromConfig(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return DdlParserMode.valueOf(normalized);
    }
}
