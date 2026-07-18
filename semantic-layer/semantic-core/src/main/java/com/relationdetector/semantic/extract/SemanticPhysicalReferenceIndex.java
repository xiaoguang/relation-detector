package com.relationdetector.semantic.extract;

import java.util.LinkedHashSet;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.relationdetector.semantic.model.PhysicalEndpointRef;

/** Exact physical table and column registry derived from the supplied evidence bundle. */
final class SemanticPhysicalReferenceIndex {
    private static final Set<String> ENDPOINT_SECTIONS = Set.of(
            "relationships", "derivedRelationships", "namingEvidence");
    private static final Set<String> LINEAGE_SECTIONS = Set.of("lineage", "derivedLineage");

    private final Set<String> tables;
    private final Set<String> columns;

    private SemanticPhysicalReferenceIndex(Set<String> tables, Set<String> columns) {
        this.tables = Set.copyOf(tables);
        this.columns = Set.copyOf(columns);
    }

    static SemanticPhysicalReferenceIndex from(JsonNode bundle) {
        Set<String> tables = new LinkedHashSet<>();
        JsonNode tableValues = bundle.path("tables");
        if (!tableValues.isArray()) {
            throw new IllegalArgumentException("semantic evidence bundle tables must be an array");
        }
        tableValues.forEach(value -> addText(tables, value));

        Set<String> endpointValues = new LinkedHashSet<>();
        for (String section : ENDPOINT_SECTIONS) {
            for (JsonNode item : bundle.path(section)) {
                addText(endpointValues, item.path("source"));
                addText(endpointValues, item.path("target"));
            }
        }
        for (String section : LINEAGE_SECTIONS) {
            for (JsonNode item : bundle.path(section)) {
                item.path("sources").forEach(value -> addText(endpointValues, value));
                addText(endpointValues, item.path("source"));
                addText(endpointValues, item.path("target"));
            }
        }
        for (JsonNode event : bundle.path("eventCandidates")) {
            event.path("inputEndpoints").forEach(value -> addText(endpointValues, value));
            event.path("outputEndpoints").forEach(value -> addText(endpointValues, value));
        }

        Set<String> columns = new LinkedHashSet<>();
        for (String endpoint : endpointValues) {
            if (tables.contains(endpoint)) {
                continue;
            }
            String table = PhysicalEndpointRef.column(endpoint).table();
            if (tables.contains(table)) {
                columns.add(endpoint);
            }
        }
        return new SemanticPhysicalReferenceIndex(tables, columns);
    }

    boolean containsTable(String table) {
        return table != null && tables.contains(table.trim());
    }

    boolean containsColumn(String column) {
        return column != null && columns.contains(column.trim());
    }

    private static void addText(Set<String> values, JsonNode value) {
        if (value != null && value.isTextual() && !value.asText().isBlank()) {
            values.add(value.asText().trim());
        }
    }

}
