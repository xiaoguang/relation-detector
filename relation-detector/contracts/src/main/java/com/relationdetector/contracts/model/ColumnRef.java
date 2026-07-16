package com.relationdetector.contracts.model;

/**
 * 列级 endpoint 的稳定列标识。
 *
 * <p>CN: ColumnRef 始终绑定 TableId；当 relationship 退化为表级共现时，
 * Endpoint 可以不携带 ColumnRef。
 *
 * <p>EN: Stable column identity for column-level endpoints. ColumnRef is always
 * bound to a TableId; table-level co-occurrence endpoints omit the column.
 */
public record ColumnRef(
        TableId table,
        String columnName,
        String normalizedName,
        String dataType,
        boolean nullable
) {
    public ColumnRef {
        if (table == null) {
            throw new IllegalArgumentException("table is required");
        }
        if (columnName != null && (normalizedName == null || normalizedName.isBlank())) {
            normalizedName = columnName;
        }
    }

    /**
     *
     * 用默认 normalizedName 创建列引用。
     *
     * <p>EN: Creates a column reference using the column name as the default normalized name.
     */
    public static ColumnRef of(TableId table, String columnName) {
        return new ColumnRef(table, columnName, columnName, null, true);
    }

    public String displayName() {
        return columnName == null || columnName.isBlank()
                ? table.displayName()
                : table.displayName() + "." + columnName;
    }
}
