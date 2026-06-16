package com.relationdetector.api;

import java.util.Map;

/** Runtime context available to adaptor hooks. */
public record AdaptorContext(ScanScope scope, Map<String, Object> options) {
    public AdaptorContext {
        options = options == null ? Map.of() : Map.copyOf(options);
    }
}
