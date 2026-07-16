package com.relationdetector.postgres;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import com.relationdetector.contracts.spi.ScanScope;

/**
 * CN: 从 pg_constraint 按 ordinality 读取 PK、UNIQUE 与组合 FK。
 *
 * <p>EN: Reads PK, UNIQUE, and composite FK definitions from pg_constraint using ordinal pairing.
 */
public final class PostgresConstraintCatalogReader {
    private static final String SQL = """
            /* metadata_constraints */
            SELECT ns.nspname AS schema_name, child.relname AS table_name,
                   con.conname AS constraint_name,
                   CASE con.contype WHEN 'p' THEN 'PRIMARY KEY' WHEN 'u' THEN 'UNIQUE' ELSE 'FOREIGN KEY' END AS constraint_type,
                   child_cols.ord AS position, child_col.attname AS column_name,
                   parent_ns.nspname AS referenced_schema, parent.relname AS referenced_table,
                   parent_col.attname AS referenced_column,
                   CASE con.confupdtype WHEN 'c' THEN 'CASCADE' WHEN 'r' THEN 'RESTRICT'
                     WHEN 'n' THEN 'SET NULL' WHEN 'd' THEN 'SET DEFAULT' ELSE 'NO ACTION' END AS update_rule,
                   CASE con.confdeltype WHEN 'c' THEN 'CASCADE' WHEN 'r' THEN 'RESTRICT'
                     WHEN 'n' THEN 'SET NULL' WHEN 'd' THEN 'SET DEFAULT' ELSE 'NO ACTION' END AS delete_rule
            FROM pg_constraint con
            JOIN pg_class child ON child.oid = con.conrelid
            JOIN pg_namespace ns ON ns.oid = child.relnamespace
            JOIN unnest(con.conkey) WITH ORDINALITY child_cols(attnum, ord) ON true
            JOIN pg_attribute child_col ON child_col.attrelid = child.oid AND child_col.attnum = child_cols.attnum
            LEFT JOIN pg_class parent ON parent.oid = con.confrelid
            LEFT JOIN pg_namespace parent_ns ON parent_ns.oid = parent.relnamespace
            LEFT JOIN unnest(con.confkey) WITH ORDINALITY parent_cols(attnum, ord)
              ON parent_cols.ord = child_cols.ord
            LEFT JOIN pg_attribute parent_col
              ON parent_col.attrelid = parent.oid AND parent_col.attnum = parent_cols.attnum
            WHERE con.contype IN ('p','u','f') AND ns.nspname = ?
            ORDER BY child.relname, con.conname, child_cols.ord
            """;

    /**
     * CN: 读取指定 schema 的约束，并仅对来源表应用 include/exclude scope。
     *
     * <p>EN: Reads constraints for a schema and applies include/exclude scope to child tables only.
     */
    public List<Constraint> read(Connection connection, String schema, ScanScope scope) throws Exception {
        Map<String, Builder> groups = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(SQL)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String table = rs.getString("table_name");
                    if (!inScope(scope, table)) {
                        continue;
                    }
                    String key = rs.getString("schema_name") + "|" + table + "|" + rs.getString("constraint_name");
                    Builder builder = groups.computeIfAbsent(key, ignored -> new Builder(
                            string(rs, "schema_name"), table, string(rs, "constraint_name"),
                            string(rs, "constraint_type"), emptyToNull(string(rs, "referenced_schema")),
                            emptyToNull(string(rs, "referenced_table")), string(rs, "update_rule"),
                            string(rs, "delete_rule")));
                    int position = rs.getInt("position");
                    builder.columns.put(position, string(rs, "column_name"));
                    String referenced = emptyToNull(string(rs, "referenced_column"));
                    if (referenced != null) {
                        builder.referencedColumns.put(position, referenced);
                    }
                }
            }
        }
        return groups.values().stream().map(Builder::build).toList();
    }

    private boolean inScope(ScanScope scope, String table) {
        String key = normalize(table);
        boolean included = scope.includeTables().isEmpty()
                || scope.includeTables().stream().map(this::normalize).anyMatch(key::equals);
        return included && scope.excludeTables().stream().map(this::normalize).noneMatch(key::equals);
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private String string(ResultSet rs, String column) {
        try {
            return rs.getString(column);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() || "null".equalsIgnoreCase(value) ? null : value;
    }

    /** CN: 一个按列序稳定排列的约束。EN: One constraint with columns ordered by catalog ordinal. */
    public record Constraint(
            String schema,
            String table,
            String name,
            String type,
            List<String> columns,
            String referencedSchema,
            String referencedTable,
            List<String> referencedColumns,
            String updateRule,
            String deleteRule
    ) {
    }

    private static final class Builder {
        private final String schema;
        private final String table;
        private final String name;
        private final String type;
        private final String referencedSchema;
        private final String referencedTable;
        private final String updateRule;
        private final String deleteRule;
        private final Map<Integer, String> columns = new TreeMap<>();
        private final Map<Integer, String> referencedColumns = new TreeMap<>();

        private Builder(String schema, String table, String name, String type, String referencedSchema,
                String referencedTable, String updateRule, String deleteRule) {
            this.schema = schema;
            this.table = table;
            this.name = name;
            this.type = type;
            this.referencedSchema = referencedSchema;
            this.referencedTable = referencedTable;
            this.updateRule = updateRule;
            this.deleteRule = deleteRule;
        }

        private Constraint build() {
            return new Constraint(schema, table, name, type, new ArrayList<>(columns.values()),
                    referencedSchema, referencedTable, new ArrayList<>(referencedColumns.values()),
                    updateRule, deleteRule);
        }
    }
}
