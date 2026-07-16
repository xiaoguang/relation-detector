package com.relationdetector.postgres.metadata;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.RelationSubType;
import com.relationdetector.contracts.Enums.RelationType;
import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.contracts.metadata.MetadataColumnFact;
import com.relationdetector.contracts.metadata.MetadataConstraintFact;
import com.relationdetector.contracts.metadata.MetadataIndexFact;
import com.relationdetector.contracts.metadata.MetadataSnapshot;
import com.relationdetector.contracts.metadata.MetadataTableFact;
import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.scoring.DefaultEvidenceScores;
import com.relationdetector.contracts.spi.Collectors.MetadataCollector;
import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.core.diagnostics.LiveDiagnosticSanitizer;
import com.relationdetector.postgres.PostgresConstraintCatalogReader;
import com.relationdetector.postgres.PostgresConstraintCatalogReader.Constraint;
import com.relationdetector.postgres.PostgresNamespaceResolver;

/**
 * CN: 读取 PostgreSQL 表、列、约束、索引以及声明 FK，单个 catalog family 失败时保留其余结果。
 *
 * <p>EN: Reads PostgreSQL tables, columns, constraints, indexes, and declared FKs with partial success.
 */
public final class PostgresMetadataCollector implements MetadataCollector {
    private final PostgresConstraintCatalogReader constraintReader = new PostgresConstraintCatalogReader();

    @Override
    public MetadataSnapshot collect(Connection connection, ScanScope scope) {
        MetadataSnapshot snapshot = new MetadataSnapshot();
        var namespace = PostgresNamespaceResolver.resolve(connection, scope);
        collectTables(connection, scope, namespace.catalog(), namespace.schema(), snapshot);
        collectColumns(connection, scope, namespace.catalog(), namespace.schema(), snapshot);
        collectConstraints(connection, scope, namespace.catalog(), namespace.schema(), snapshot);
        collectIndexes(connection, scope, namespace.catalog(), namespace.schema(), snapshot);
        return snapshot;
    }

    private void collectTables(Connection connection, ScanScope scope, String catalog, String schema,
            MetadataSnapshot snapshot) {
        String sql = """
                /* metadata_tables */
                SELECT n.nspname AS schema_name, c.relname AS table_name,
                       CASE c.relkind WHEN 'p' THEN 'PARTITIONED TABLE' ELSE 'TABLE' END AS table_type
                FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = ? AND c.relkind IN ('r','p') ORDER BY c.relname
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String table = rs.getString("table_name");
                    if (!inScope(scope, table)) continue;
                    String rowSchema = rs.getString("schema_name");
                    snapshot.tables().add(table(catalog, rowSchema, table));
                    snapshot.tableFacts().add(new MetadataTableFact(catalog, rowSchema, table,
                            rs.getString("table_type"), null, null));
                }
            }
        } catch (Exception ex) {
            warn(snapshot, "POSTGRES_METADATA_TABLES_FAILED", "pg_catalog.pg_class", ex);
        }
    }

    private void collectColumns(Connection connection, ScanScope scope, String catalog, String schema,
            MetadataSnapshot snapshot) {
        String sql = """
                /* metadata_columns */
                SELECT n.nspname AS schema_name, c.relname AS table_name, a.attname AS column_name,
                       t.typname AS data_type, format_type(a.atttypid, a.atttypmod) AS column_type,
                       NOT a.attnotnull AS nullable, COALESCE(pg_get_expr(d.adbin, d.adrelid), '') AS default_value,
                       a.attidentity AS identity_kind, a.attgenerated AS generated_kind, a.attnum AS ordinal_position
                FROM pg_attribute a JOIN pg_class c ON c.oid = a.attrelid
                JOIN pg_namespace n ON n.oid = c.relnamespace JOIN pg_type t ON t.oid = a.atttypid
                LEFT JOIN pg_attrdef d ON d.adrelid = a.attrelid AND d.adnum = a.attnum
                WHERE n.nspname = ? AND c.relkind IN ('r','p') AND a.attnum > 0 AND NOT a.attisdropped
                ORDER BY c.relname, a.attnum
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("table_name");
                    if (!inScope(scope, tableName)) continue;
                    String rowSchema = rs.getString("schema_name");
                    String generated = rs.getString("generated_kind");
                    String defaultValue = blankToNull(rs.getString("default_value"));
                    MetadataColumnFact fact = new MetadataColumnFact(catalog, rowSchema, tableName,
                            rs.getString("column_name"), rs.getString("data_type"), rs.getString("column_type"),
                            rs.getBoolean("nullable"), generated == null || generated.isBlank() ? defaultValue : null,
                            extra(rs.getString("identity_kind"), generated),
                            generated == null || generated.isBlank() ? null : defaultValue,
                            rs.getInt("ordinal_position"));
                    snapshot.columnFacts().add(fact);
                    snapshot.columns().add(new ColumnRef(table(catalog, rowSchema, tableName), fact.columnName(),
                            fact.columnName(), fact.dataType(), fact.nullable()));
                }
            }
        } catch (Exception ex) {
            warn(snapshot, "POSTGRES_METADATA_COLUMNS_FAILED", "pg_catalog.pg_attribute", ex);
        }
    }

    private void collectConstraints(Connection connection, ScanScope scope, String catalog, String schema,
            MetadataSnapshot snapshot) {
        try {
            for (Constraint constraint : constraintReader.read(connection, schema, scope)) {
                String referencedCatalog = "FOREIGN KEY".equals(constraint.type()) ? catalog : null;
                snapshot.constraintFacts().add(new MetadataConstraintFact(catalog, constraint.schema(),
                        constraint.table(), constraint.name(), constraint.type(), constraint.columns(), referencedCatalog,
                        constraint.referencedSchema(), constraint.referencedTable(), constraint.referencedColumns(),
                        constraint.updateRule(), constraint.deleteRule()));
                if ("FOREIGN KEY".equals(constraint.type())) {
                    addForeignKey(snapshot, catalog, constraint);
                }
            }
        } catch (Exception ex) {
            warn(snapshot, "POSTGRES_METADATA_CONSTRAINTS_FAILED", "pg_catalog.pg_constraint", ex);
        }
    }

    private void addForeignKey(MetadataSnapshot snapshot, String catalog, Constraint constraint) {
        int count = Math.min(constraint.columns().size(), constraint.referencedColumns().size());
        for (int i = 0; i < count; i++) {
            RelationshipCandidate candidate = new RelationshipCandidate(
                    Endpoint.column(ColumnRef.of(table(catalog, constraint.schema(), constraint.table()),
                            constraint.columns().get(i))),
                    Endpoint.column(ColumnRef.of(table(catalog, constraint.referencedSchema(),
                            constraint.referencedTable()), constraint.referencedColumns().get(i))),
                    RelationType.FK_LIKE, RelationSubType.DECLARED_FK);
            candidate.evidence().add(Evidence.of(EvidenceType.METADATA_FOREIGN_KEY,
                    DefaultEvidenceScores.METADATA_FOREIGN_KEY, EvidenceSourceType.METADATA,
                    "pg_catalog.pg_constraint", constraint.name()));
            snapshot.relationships().add(candidate);
        }
    }

    private void collectIndexes(Connection connection, ScanScope scope, String catalog, String schema,
            MetadataSnapshot snapshot) {
        String sql = """
                /* metadata_indexes */
                SELECT n.nspname AS schema_name, t.relname AS table_name, i.relname AS index_name,
                       x.indisunique AS is_unique, x.indisprimary AS is_primary, am.amname AS index_type,
                       keys.ord AS position, a.attname AS column_name,
                       CASE WHEN keys.attnum = 0 THEN pg_get_indexdef(i.oid, keys.ord::integer, true) ELSE '' END AS expression
                FROM pg_index x JOIN pg_class t ON t.oid = x.indrelid
                JOIN pg_namespace n ON n.oid = t.relnamespace JOIN pg_class i ON i.oid = x.indexrelid
                JOIN pg_am am ON am.oid = i.relam
                JOIN unnest(x.indkey) WITH ORDINALITY keys(attnum, ord) ON true
                LEFT JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = keys.attnum
                WHERE n.nspname = ? ORDER BY t.relname, i.relname, keys.ord
                """;
        Map<String, IndexBuilder> groups = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("table_name");
                    if (!inScope(scope, tableName)) continue;
                    String key = rs.getString("schema_name") + "|" + tableName + "|" + rs.getString("index_name");
                    IndexBuilder builder = groups.computeIfAbsent(key, ignored -> new IndexBuilder(
                            string(rs, "schema_name"), tableName, string(rs, "index_name"),
                            bool(rs, "is_unique"), bool(rs, "is_primary"), string(rs, "index_type")));
                    builder.add(rs.getInt("position"), blankToEmpty(rs.getString("column_name")),
                            blankToEmpty(rs.getString("expression")));
                }
            }
            for (IndexBuilder builder : groups.values()) {
                snapshot.indexFacts().add(builder.build(catalog));
            }
        } catch (Exception ex) {
            warn(snapshot, "POSTGRES_METADATA_INDEXES_FAILED", "pg_catalog.pg_index", ex);
        }
    }

    private TableId table(String catalog, String schema, String name) {
        return new TableId(catalog, schema, name, schema + "." + name);
    }

    private boolean inScope(ScanScope scope, String table) {
        String key = table == null ? "" : table.toLowerCase(Locale.ROOT);
        boolean included = scope.includeTables().isEmpty()
                || scope.includeTables().stream().map(value -> value.toLowerCase(Locale.ROOT)).anyMatch(key::equals);
        return included && scope.excludeTables().stream().map(value -> value.toLowerCase(Locale.ROOT)).noneMatch(key::equals);
    }

    private String extra(String identity, String generated) {
        List<String> values = new ArrayList<>();
        if (identity != null && !identity.isBlank()) values.add("IDENTITY");
        if (generated != null && !generated.isBlank()) values.add("GENERATED");
        return String.join(" ", values);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String blankToEmpty(String value) {
        return value == null || "null".equalsIgnoreCase(value) ? "" : value;
    }

    private void warn(MetadataSnapshot snapshot, String code, String source, Exception ex) {
        snapshot.warnings().add(LiveDiagnosticSanitizer.jdbcWarning(code,
                LiveDiagnosticSanitizer.Operation.METADATA, source, ex, Map.of()));
    }

    private String string(ResultSet rs, String column) {
        try { return rs.getString(column); } catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    private boolean bool(ResultSet rs, String column) {
        try { return rs.getBoolean(column); } catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    private static final class IndexBuilder {
        private final String schema;
        private final String table;
        private final String name;
        private final boolean unique;
        private final boolean primary;
        private final String type;
        private final Map<Integer, String> columns = new TreeMap<>();
        private final Map<Integer, String> expressions = new TreeMap<>();

        private IndexBuilder(String schema, String table, String name, boolean unique, boolean primary, String type) {
            this.schema = schema;
            this.table = table;
            this.name = name;
            this.unique = unique;
            this.primary = primary;
            this.type = type;
        }

        private void add(int position, String column, String expression) {
            columns.put(position, column);
            expressions.put(position, expression);
        }

        private MetadataIndexFact build(String catalog) {
            List<Integer> positions = List.copyOf(columns.keySet());
            return new MetadataIndexFact(catalog, schema, table, name, unique, primary, type, true,
                    new ArrayList<>(columns.values()), new ArrayList<>(expressions.values()), List.of(), positions);
        }
    }
}
