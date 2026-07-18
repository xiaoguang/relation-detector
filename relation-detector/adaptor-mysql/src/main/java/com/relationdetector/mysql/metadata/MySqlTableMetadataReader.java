package com.relationdetector.mysql.metadata;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import com.relationdetector.contracts.metadata.MetadataSnapshot;
import com.relationdetector.contracts.metadata.MetadataTableFact;
import com.relationdetector.contracts.spi.ScanScope;

/**
 * CN: 从 information_schema.TABLES 读取当前 MySQL catalog 的表清单；输入是连接和规范 scope，输出 table inventory，失败只追加该 catalog family 的脱敏 warning。
 * EN: Reads the current MySQL catalog table inventory from information_schema.TABLES; failure adds only this catalog family's sanitized warning.
 */
final class MySqlTableMetadataReader {
    void read(Connection connection, ScanScope scope, MetadataSnapshot snapshot) {
        String sql = """
                SELECT TABLE_SCHEMA, TABLE_NAME, TABLE_TYPE, ENGINE, TABLE_COMMENT
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, scope.catalog());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    if (!MySqlMetadataReaderSupport.inScope(scope, tableName)) {
                        continue;
                    }
                    String catalog = rs.getString("TABLE_SCHEMA");
                    snapshot.tableFacts().add(new MetadataTableFact(catalog, null, tableName,
                            rs.getString("TABLE_TYPE"), rs.getString("ENGINE"), rs.getString("TABLE_COMMENT")));
                    snapshot.tables().add(MySqlMetadataReaderSupport.table(catalog, tableName));
                }
            }
        } catch (Exception ex) {
            MySqlMetadataReaderSupport.warn(snapshot, "MYSQL_METADATA_TABLES_FAILED", ex,
                    "information_schema.TABLES");
        }
    }
}
