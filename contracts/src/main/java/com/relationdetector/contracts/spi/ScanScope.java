package com.relationdetector.contracts.spi;

import java.util.List;

/** Scope requested by CLI/config and passed to adaptors. */
public record ScanScope(
        String catalog,
        String schema,
        List<String> includeTables,
        List<String> excludeTables
) {
    public ScanScope {
        includeTables = includeTables == null ? List.of() : List.copyOf(includeTables);
        excludeTables = excludeTables == null ? List.of() : List.copyOf(excludeTables);
    }
}
