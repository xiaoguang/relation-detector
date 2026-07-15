package com.relationdetector.contracts.model;

/**
 * relationship 或 lineage 的端点。
 *
 * <p>CN: table 必填；column 为空时表示表级 relationship，例如 TABLE_CO_OCCURRENCE。
 *
 * <p>EN: Endpoint for relationships or lineage. The table is required; a null
 * column represents a table-level relationship such as TABLE_CO_OCCURRENCE.
 */
public record Endpoint(TableId table, ColumnRef column) {
    public Endpoint {
        if (table == null) {
            throw new IllegalArgumentException("table is required");
        }
        if (column != null && !column.table().sameIdentity(table)) {
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
        String catalog = table.catalog() == null || table.catalog().isBlank()
                ? ""
                : table.catalog() + ".";
        return catalog + table.normalizedName() + "." + (isColumnLevel() ? column.normalizedName() : "*");
    }
}
