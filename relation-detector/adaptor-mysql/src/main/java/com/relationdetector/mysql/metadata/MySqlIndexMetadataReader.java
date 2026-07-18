package com.relationdetector.mysql.metadata;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.relationdetector.contracts.metadata.MetadataIndexFact;
import com.relationdetector.contracts.metadata.MetadataSnapshot;
import com.relationdetector.contracts.spi.ScanScope;

/**
 * CN: 从 information_schema.STATISTICS 按 index 和 ordinal 重建完整 MySQL 索引列组；输入是规范 scope，输出 index facts，禁止把组合索引成员提升为独立唯一证据。
 * EN: Reconstructs complete ordinal-preserving MySQL index groups from information_schema.STATISTICS; composite members never become standalone unique evidence.
 */
final class MySqlIndexMetadataReader {
    void read(Connection connection, ScanScope scope, MetadataSnapshot snapshot) {
        String sql = """
                SELECT TABLE_SCHEMA, TABLE_NAME, INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX,
                       COLUMN_NAME, INDEX_TYPE, SUB_PART
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = ?
                ORDER BY TABLE_SCHEMA, TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, scope.catalog());
            Map<String, IndexBuilder> indexes = new LinkedHashMap<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    if (!MySqlMetadataReaderSupport.inScope(scope, tableName)) continue;
                    String catalog = rs.getString("TABLE_SCHEMA");
                    String indexName = rs.getString("INDEX_NAME");
                    IndexBuilder builder = indexes.computeIfAbsent(catalog + "|" + tableName + "|" + indexName,
                            ignored -> new IndexBuilder(catalog, tableName, indexName,
                                    rsBooleanUnique(rs), "PRIMARY".equalsIgnoreCase(indexName),
                                    safeString(rs, "INDEX_TYPE"), true));
                    builder.entries.add(new IndexEntry(rs.getInt("SEQ_IN_INDEX"), rs.getString("COLUMN_NAME"),
                            null, MySqlMetadataReaderSupport.stringOrNull(rs.getObject("SUB_PART"))));
                }
            }
            indexes.values().forEach(builder -> snapshot.indexFacts().add(builder.toFact()));
        } catch (Exception ex) {
            MySqlMetadataReaderSupport.warn(snapshot, "MYSQL_METADATA_INDEXES_FAILED", ex,
                    "information_schema.STATISTICS");
        }
    }

    private static boolean rsBooleanUnique(ResultSet rs) {
        try {
            return rs.getInt("NON_UNIQUE") == 0;
        } catch (Exception ex) {
            throw new IllegalStateException("failed to read index uniqueness", ex);
        }
    }

    private static String safeString(ResultSet rs, String column) {
        try {
            return rs.getString(column);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to read index metadata", ex);
        }
    }

    private static final class IndexBuilder {
        private final String catalog;
        private final String table;
        private final String name;
        private final boolean unique;
        private final boolean primary;
        private final String type;
        private final boolean visible;
        private final List<IndexEntry> entries = new ArrayList<>();

        private IndexBuilder(String catalog, String table, String name, boolean unique, boolean primary, String type,
                boolean visible) {
            this.catalog = catalog;
            this.table = table;
            this.name = name;
            this.unique = unique;
            this.primary = primary;
            this.type = type;
            this.visible = visible;
        }

        private MetadataIndexFact toFact() {
            entries.sort(Comparator.comparingInt(IndexEntry::seq));
            return new MetadataIndexFact(catalog, null, table, name, unique, primary, type, visible,
                    entries.stream().map(IndexEntry::column).filter(value -> value != null && !value.isBlank()).toList(),
                    entries.stream().map(IndexEntry::expression).filter(value -> value != null && !value.isBlank()).toList(),
                    entries.stream().map(entry -> entry.subPart() == null ? "" : entry.subPart()).toList(),
                    entries.stream().map(IndexEntry::seq).toList());
        }
    }

    private record IndexEntry(int seq, String column, String expression, String subPart) {
    }
}
