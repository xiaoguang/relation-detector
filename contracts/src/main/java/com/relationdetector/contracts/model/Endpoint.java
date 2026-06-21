package com.relationdetector.contracts.model;

/** A relationship endpoint. column is nullable for table-level relationships. */
public record Endpoint(TableId table, ColumnRef column) {
    public Endpoint {
        if (table == null) {
            throw new IllegalArgumentException("table is required");
        }
        if (column != null && !column.table().normalizedName().equals(table.normalizedName())) {
            throw new IllegalArgumentException("column table must match endpoint table");
        }
    }

    public static Endpoint table(TableId table) {
        return new Endpoint(table, null);
    }

    public static Endpoint column(ColumnRef column) {
        return new Endpoint(column.table(), column);
    }

    public boolean isColumnLevel() {
        return column != null && column.columnName() != null;
    }

    public String displayName() {
        return isColumnLevel() ? column.displayName() : table.displayName();
    }

    public String normalizedKey() {
        return table.normalizedName() + "." + (isColumnLevel() ? column.normalizedName() : "*");
    }
}
