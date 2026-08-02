package com.relationdetector.semantic.ingest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.relationdetector.contracts.metadata.MetadataColumnFact;
import com.relationdetector.contracts.metadata.MetadataTableFact;
import com.relationdetector.contracts.spi.ScanScope;

/** Test-only builder for an explicit, closed metadata inventory. */
public final class ScanMetadataInventoryFixture {
    private ScanMetadataInventoryFixture() {
    }

    public static ScanMetadataInventory complete(
            String catalog,
            String schema,
            String... physicalColumns
    ) {
        Map<String, Set<String>> columnsByTable = new LinkedHashMap<>();
        for (String endpoint : physicalColumns) {
            int separator = endpoint.lastIndexOf('.');
            if (separator <= 0 || separator == endpoint.length() - 1) {
                throw new IllegalArgumentException("test inventory column must be table-qualified");
            }
            columnsByTable.computeIfAbsent(endpoint.substring(0, separator), ignored -> new LinkedHashSet<>())
                    .add(endpoint.substring(separator + 1));
        }
        List<MetadataTableFact> tables = new ArrayList<>();
        List<MetadataColumnFact> columns = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : columnsByTable.entrySet()) {
            tables.add(new MetadataTableFact(
                    emptyToNull(catalog), emptyToNull(schema), entry.getKey(), "BASE TABLE", null, null));
            int ordinal = 1;
            for (String column : entry.getValue()) {
                columns.add(new MetadataColumnFact(
                        emptyToNull(catalog), emptyToNull(schema), entry.getKey(), column,
                        "unknown", "unknown", true, null, "", "", ordinal++));
            }
        }
        return ScanMetadataInventory.complete(
                new ScanScope(emptyToNull(catalog), emptyToNull(schema), List.of(), List.of()),
                tables, columns, List.of(), List.of());
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
