package com.relationdetector.mysql.metadata;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import com.relationdetector.contracts.metadata.MetadataColumnFact;
import com.relationdetector.contracts.metadata.MetadataSnapshot;
import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.spi.ScanScope;

/**
 * CN: 从 information_schema.COLUMNS 读取有序列清单及类型、默认值和生成表达式元数据；输入是规范 scope，输出 column facts，不负责表达式解析。
 * EN: Reads ordered column inventory, types, defaults, and generation metadata from information_schema.COLUMNS; it does not parse expressions.
 */
final class MySqlColumnMetadataReader {
    void read(Connection connection, ScanScope scope, MetadataSnapshot snapshot) {
        String sql = """
                SELECT TABLE_SCHEMA, TABLE_NAME, COLUMN_NAME, DATA_TYPE, COLUMN_TYPE,
                       IS_NULLABLE, COLUMN_DEFAULT, EXTRA, GENERATION_EXPRESSION, ORDINAL_POSITION
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = ?
                ORDER BY TABLE_SCHEMA, TABLE_NAME, ORDINAL_POSITION
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, scope.catalog());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    if (!MySqlMetadataReaderSupport.inScope(scope, tableName)) {
                        continue;
                    }
                    MetadataColumnFact fact = new MetadataColumnFact(
                            rs.getString("TABLE_SCHEMA"), null, tableName, rs.getString("COLUMN_NAME"),
                            rs.getString("DATA_TYPE"), rs.getString("COLUMN_TYPE"),
                            "YES".equalsIgnoreCase(rs.getString("IS_NULLABLE")),
                            MySqlMetadataReaderSupport.stringOrNull(rs.getObject("COLUMN_DEFAULT")),
                            rs.getString("EXTRA"), rs.getString("GENERATION_EXPRESSION"),
                            rs.getInt("ORDINAL_POSITION"));
                    snapshot.columnFacts().add(fact);
                    snapshot.columns().add(new ColumnRef(
                            MySqlMetadataReaderSupport.table(fact.catalog(), fact.tableName()), fact.columnName(),
                            fact.columnName(), fact.dataType(), fact.nullable()));
                }
            }
        } catch (Exception ex) {
            MySqlMetadataReaderSupport.warn(snapshot, "MYSQL_METADATA_COLUMNS_FAILED", ex,
                    "information_schema.COLUMNS");
        }
    }
}
