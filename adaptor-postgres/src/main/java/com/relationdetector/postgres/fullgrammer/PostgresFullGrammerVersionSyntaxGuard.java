package com.relationdetector.postgres.fullgrammer;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.contracts.model.WarningMessage;

/**
 * PostgreSQL full-grammer version boundary guard.
 *
 * <p>CN: 这个类只负责 major-version 语法边界，不做 relationship / lineage 解析。
 * 当 vendored grammar 比目标 PostgreSQL major 更宽松时，这里用官方版本关键字/短语拒绝
 * 高版本专属语法，避免低版本 profile 静默接受未来语法。
 *
 * <p>EN: This class enforces PostgreSQL major-version syntax boundaries only.
 * It does not extract relationship or lineage semantics. When the vendored
 * grammar is broader than the target PostgreSQL major, this guard rejects
 * higher-version syntax using official version keywords/phrases.
 */
public final class PostgresFullGrammerVersionSyntaxGuard {
    public static final String WARNING_CODE = "FULL_GRAMMAR_VERSION_UNSUPPORTED_SYNTAX";

    private static final Pattern JSON_TABLE = Pattern.compile("\\bJSON_TABLE\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern MERGE_ACTION = Pattern.compile("\\bMERGE_ACTION\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern NOT_MATCHED_BY_SOURCE_OR_TARGET =
            Pattern.compile("\\bWHEN\\s+NOT\\s+MATCHED\\s+BY\\s+(SOURCE|TARGET)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern RETURNING_OLD_NEW =
            Pattern.compile("\\bRETURNING\\b[\\s\\S]*\\b(OLD|NEW)\\s*\\.", Pattern.CASE_INSENSITIVE);
    private static final Pattern VIRTUAL_GENERATED =
            Pattern.compile("\\bGENERATED\\s+ALWAYS\\s+AS\\b[\\s\\S]*\\bVIRTUAL\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern WITHOUT_OVERLAPS =
            Pattern.compile("\\bWITHOUT\\s+OVERLAPS\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PERIOD_FOREIGN_KEY =
            Pattern.compile("\\bFOREIGN\\s+KEY\\b[\\s\\S]*\\bPERIOD\\b", Pattern.CASE_INSENSITIVE);

    private PostgresFullGrammerVersionSyntaxGuard() {
    }

    public static Optional<WarningMessage> sqlWarning(int majorVersion, String sql, String sourceName, long startLine) {
        return unsupportedFeature(majorVersion, sql)
                .map(feature -> warning(sourceName, startLine, feature, sql));
    }

    public static Optional<WarningMessage> ddlWarning(int majorVersion, String ddl, String sourceName) {
        return unsupportedFeature(majorVersion, ddl)
                .map(feature -> warning(sourceName, 0, feature, ddl));
    }

    private static Optional<String> unsupportedFeature(int majorVersion, String sql) {
        String text = sql == null ? "" : sql;
        if (majorVersion < 17) {
            if (JSON_TABLE.matcher(text).find()) {
                return Optional.of("PostgreSQL 17 JSON_TABLE()");
            }
            if (MERGE_ACTION.matcher(text).find()) {
                return Optional.of("PostgreSQL 17 MERGE RETURNING / merge_action()");
            }
            if (NOT_MATCHED_BY_SOURCE_OR_TARGET.matcher(text).find()) {
                return Optional.of("PostgreSQL 17 MERGE WHEN NOT MATCHED BY SOURCE/TARGET");
            }
        }
        if (majorVersion < 18) {
            if (RETURNING_OLD_NEW.matcher(text).find()) {
                return Optional.of("PostgreSQL 18 RETURNING old/new");
            }
            if (VIRTUAL_GENERATED.matcher(text).find()) {
                return Optional.of("PostgreSQL 18 virtual generated columns");
            }
            if (WITHOUT_OVERLAPS.matcher(text).find()) {
                return Optional.of("PostgreSQL 18 WITHOUT OVERLAPS constraints");
            }
            if (PERIOD_FOREIGN_KEY.matcher(text).find()) {
                return Optional.of("PostgreSQL 18 PERIOD foreign keys");
            }
        }
        return Optional.empty();
    }

    private static WarningMessage warning(String sourceName, long line, String feature, String sql) {
        return WarningMessage.warn(WarningType.PARSE_WARNING,
                WARNING_CODE,
                "full-grammer profile does not support " + feature,
                sourceName,
                line,
                Map.of("unsupportedFeature", feature,
                        "rawStatement", sql == null ? "" : sql.strip(),
                        "versionBoundary", feature.toLowerCase(Locale.ROOT)));
    }
}
