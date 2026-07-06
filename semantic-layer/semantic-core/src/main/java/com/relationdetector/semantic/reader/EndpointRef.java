package com.relationdetector.semantic.reader;

import com.fasterxml.jackson.databind.JsonNode;

/** Physical table or table.column endpoint preserved from relation-detector JSON. */
public record EndpointRef(String table, String column) {
    public EndpointRef {
        if (table == null || table.isBlank()) {
            throw new IllegalArgumentException("endpoint table is required");
        }
        if (column != null && column.isBlank()) {
            column = null;
        }
    }

    public static EndpointRef fromJson(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            throw new IllegalArgumentException("endpoint is required");
        }
        String table = node.path("table").asText("");
        JsonNode columnNode = node.path("column");
        String column = columnNode.isMissingNode() || columnNode.isNull() ? null : columnNode.asText();
        return new EndpointRef(table, column);
    }

    public boolean isColumnLevel() {
        return column != null;
    }

    public String displayName() {
        return isColumnLevel() ? table + "." + column : table;
    }
}
