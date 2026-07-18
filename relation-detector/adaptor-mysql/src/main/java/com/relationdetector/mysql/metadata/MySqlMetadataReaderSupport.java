package com.relationdetector.mysql.metadata;

import java.util.Locale;
import java.util.Map;

import com.relationdetector.contracts.metadata.MetadataSnapshot;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.core.diagnostics.LiveDiagnosticSanitizer;

/**
 * CN: 为 MySQL metadata reader 提供一致的 catalog 身份、scope 过滤和脱敏 warning；输入是已规范化 scope，输出仅为小型值转换，禁止持有连接或跨 reader 状态。
 * EN: Supplies consistent catalog identity, scope filtering, and sanitized warnings to MySQL metadata readers without owning connections or cross-reader state.
 */
final class MySqlMetadataReaderSupport {
    private MySqlMetadataReaderSupport() {
    }

    static TableId table(String catalog, String tableName) {
        return new TableId(catalog, null, tableName, tableName);
    }

    static boolean inScope(ScanScope scope, String tableName) {
        String normalized = normalize(tableName);
        boolean included = scope.includeTables().isEmpty()
                || scope.includeTables().stream().map(MySqlMetadataReaderSupport::normalize).anyMatch(normalized::equals);
        boolean excluded = scope.excludeTables().stream()
                .map(MySqlMetadataReaderSupport::normalize).anyMatch(normalized::equals);
        return included && !excluded;
    }

    static boolean equalsIgnoreCase(String left, String right) {
        return normalize(left).equals(normalize(right));
    }

    static String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    static void warn(MetadataSnapshot snapshot, String code, Exception failure, String source) {
        snapshot.warnings().add(LiveDiagnosticSanitizer.jdbcWarning(
                code, LiveDiagnosticSanitizer.Operation.METADATA, source, failure, Map.of()));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
