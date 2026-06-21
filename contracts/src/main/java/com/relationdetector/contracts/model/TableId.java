package com.relationdetector.contracts.model;

/**
 * Stable table identity used across metadata, DDL, SQL logs, and output.
 *
 * <p>Design mapping: Phase 2 TableId. Adaptors own normalization because MySQL
 * and PostgreSQL have different identifier case rules.
 */
public record TableId(String catalog, String schema, String tableName, String normalizedName) {
    public TableId {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("tableName is required");
        }
        if (normalizedName == null || normalizedName.isBlank()) {
            normalizedName = tableName;
        }
    }

    public static TableId of(String schema, String tableName) {
        String normalized = schema == null || schema.isBlank()
                ? tableName
                : schema + "." + tableName;
        return new TableId(null, schema, tableName, normalized);
    }

    public String displayName() {
        return schema == null || schema.isBlank() ? tableName : schema + "." + tableName;
    }
}
