package com.relationdetector.contracts.model;

/**
 * Column reference. Endpoints may omit a column for table-level CO_OCCURRENCE.
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

    public static ColumnRef of(TableId table, String columnName) {
        return new ColumnRef(table, columnName, columnName, null, true);
    }

    public String displayName() {
        return columnName == null || columnName.isBlank()
                ? table.displayName()
                : table.displayName() + "." + columnName;
    }
}
