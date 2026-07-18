package com.relationdetector.semantic.model;

/**
 * CN: 表示 relation-detector 证据中的物理表或物理列。调用方必须明确选择表级或列级工厂，
 * 从而避免把 {@code schema.table} 猜成 {@code table.column}；本类型不读取 JSON，也不补全命名空间。
 *
 * EN: Represents a physical table or column from relation-detector evidence. Callers must choose
 * the table-level or column-level factory explicitly, so {@code schema.table} is never guessed to
 * mean {@code table.column}. This type neither reads JSON nor fills namespace components.
 */
public record PhysicalEndpointRef(String table, String column) {
    public PhysicalEndpointRef {
        if (table == null || table.isBlank()) {
            throw new IllegalArgumentException("endpoint table is required");
        }
        table = table.strip();
        if (column != null) {
            column = column.strip();
            if (column.isBlank()) {
                column = null;
            }
        }
    }

    public static PhysicalEndpointRef table(String qualifiedTable) {
        return new PhysicalEndpointRef(qualifiedTable, null);
    }

    public static PhysicalEndpointRef column(String qualifiedColumn) {
        if (qualifiedColumn == null || qualifiedColumn.isBlank()) {
            throw new IllegalArgumentException("column endpoint is required");
        }
        String value = qualifiedColumn.strip();
        int split = value.lastIndexOf('.');
        if (split <= 0 || split == value.length() - 1) {
            throw new IllegalArgumentException("column endpoint must contain table and column");
        }
        return new PhysicalEndpointRef(value.substring(0, split), value.substring(split + 1));
    }

    public boolean isColumnLevel() {
        return column != null;
    }

    /** Returns the unqualified table segment without changing physical identity. */
    public String bareTableName() {
        int split = table.lastIndexOf('.');
        return split < 0 ? table : table.substring(split + 1);
    }

    public String displayName() {
        return isColumnLevel() ? table + "." + column : table;
    }
}
