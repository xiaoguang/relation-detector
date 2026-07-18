package com.relationdetector.core.scan;

import java.nio.file.Path;
import java.util.List;

import com.relationdetector.contracts.Enums.LogFormatHint;

/**
 * CN: 承载 metadata、DDL、database objects 与 logs 的不可变 source 开关及已解析文件列表。
 * EN: Carries immutable metadata, DDL, database-object, and log source switches and resolved file lists.
 */
public record SourceConfig(
        boolean metadataEnabled,
        boolean ddlEnabled,
        boolean ddlFromDatabase,
        List<Path> ddlFiles,
        List<Path> ddlPaths,
        List<String> ddlIncludes,
        boolean objectsEnabled,
        boolean objectsFromDatabase,
        List<Path> objectFiles,
        List<Path> objectPaths,
        List<String> objectIncludes,
        boolean logsEnabled,
        List<Path> logFiles,
        List<Path> logPaths,
        List<String> logIncludes,
        LogFormatHint logFormatHint,
        boolean logsFilterSystemQueries,
        List<String> logSystemSchemas,
        List<String> logMetadataQueryMarkers
) {
    public SourceConfig {
        ddlFiles = immutable(ddlFiles);
        ddlPaths = immutable(ddlPaths);
        ddlIncludes = immutable(ddlIncludes);
        objectFiles = immutable(objectFiles);
        objectPaths = immutable(objectPaths);
        objectIncludes = immutable(objectIncludes);
        logFiles = immutable(logFiles);
        logPaths = immutable(logPaths);
        logIncludes = immutable(logIncludes);
        logSystemSchemas = immutable(logSystemSchemas);
        logMetadataQueryMarkers = immutable(logMetadataQueryMarkers);
        logFormatHint = logFormatHint == null ? LogFormatHint.AUTO : logFormatHint;
    }

    private static <T> List<T> immutable(List<T> values) {
        return List.copyOf(values == null ? List.of() : values);
    }
}
