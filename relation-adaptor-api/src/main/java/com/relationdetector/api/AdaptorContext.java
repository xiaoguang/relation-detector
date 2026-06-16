package com.relationdetector.api;

import java.util.Map;
import java.util.function.Consumer;

/** Runtime context available to adaptor hooks. */
public record AdaptorContext(ScanScope scope, Map<String, Object> options, Consumer<WarningMessage> warningSink) {
    public AdaptorContext(ScanScope scope, Map<String, Object> options) {
        this(scope, options, warning -> {
        });
    }

    public AdaptorContext {
        options = options == null ? Map.of() : Map.copyOf(options);
        warningSink = warningSink == null ? warning -> {
        } : warningSink;
    }

    /**
     * Adds a non-fatal diagnostic to the current scan result.
     *
     * <p>Parsers and adaptors use this for partial-failure reporting. For
     * example, a MySQL DDL parser can catch a file-level parse exception, attach
     * the original DDL text as {@code rawStatement}, and still let the scan
     * continue with metadata, object definitions, and logs.
     */
    public void warn(WarningMessage warning) {
        warningSink.accept(warning);
    }
}
