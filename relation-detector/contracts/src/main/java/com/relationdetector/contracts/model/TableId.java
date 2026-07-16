package com.relationdetector.contracts.model;

/**
 * 跨 metadata、DDL、SQL log 和输出使用的稳定表标识。
 *
 * <p>CN: adaptor 负责 normalizedName，因为 MySQL/PostgreSQL 的 identifier 大小写规则不同。
 *
 * <p>EN: Stable table identity used across metadata, DDL, SQL logs, and output.
 * Adaptors own normalizedName because MySQL and PostgreSQL use different
 * identifier case rules.
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

    /**
     *
     * Returns whether both values identify the same catalog-qualified table.
     */
    public boolean sameIdentity(TableId other) {
        return other != null
                && empty(catalog).equals(empty(other.catalog))
                && normalizedName.equals(other.normalizedName);
    }

    public String displayName() {
        String schemaTable = schema == null || schema.isBlank() ? tableName : schema + "." + tableName;
        return catalog == null || catalog.isBlank() ? schemaTable : catalog + "." + schemaTable;
    }

    private static String empty(String value) {
        return value == null || value.isBlank() ? "" : value;
    }
}
