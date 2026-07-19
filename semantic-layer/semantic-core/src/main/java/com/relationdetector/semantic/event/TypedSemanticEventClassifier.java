package com.relationdetector.semantic.event;

import java.util.Locale;

/**
 * CN: 将 parser 已发出的 typed source object 与 write mapping 枚举映射为 semantic event 分类；上游提供
 * provenance，event extractor 消费结果，本类不读取 SQL、路径、名称或 evidence detail，也不推测业务事件类型。
 * EN: Maps parser-issued typed source-object and write-mapping values to semantic event classifications. Provenance
 * is the input and the event extractor consumes the result; SQL, paths, names, and evidence detail are never used to
 * infer structure or business event kinds.
 */
final class TypedSemanticEventClassifier {
    private static final String DEFAULT_SOURCE_TYPE = "SQL_WRITE";
    private static final String DEFAULT_OPERATION_KIND = "WRITE";
    private static final String EVENT_KIND = "SQL_WRITE_OPERATION";

    String sourceType(String sourceObjectType) {
        return switch (normalized(sourceObjectType)) {
            case "PROCEDURE", "FUNCTION", "PACKAGE", "PACKAGE_BODY", "ROUTINE" -> "ROUTINE";
            case "TRIGGER" -> "TRIGGER";
            case "DATA_LOAD" -> "DATA_LOAD";
            case "QUERY" -> "QUERY";
            case "SQL_WRITE" -> "SQL_WRITE";
            default -> DEFAULT_SOURCE_TYPE;
        };
    }

    String sourceObjectType(String sourceObjectType) {
        String normalized = normalized(sourceObjectType);
        return normalized.isBlank() ? DEFAULT_SOURCE_TYPE : normalized;
    }

    String operationKind(String mappingKind) {
        return switch (normalized(mappingKind)) {
            case "INSERT_SELECT", "INSERT_VALUES", "INSERT_CONTROL", "INSERT_GROUP_BY" -> "INSERT";
            case "UPDATE_SET", "UPDATE_LOCATOR", "UPDATE_WHERE" -> "UPDATE";
            case "MERGE_INSERT", "MERGE_UPDATE", "MERGE_UPDATE_SET", "MERGE_ON", "MERGE_OR_USING" -> "MERGE";
            case "DELETE" -> "DELETE";
            default -> DEFAULT_OPERATION_KIND;
        };
    }

    String eventKind() {
        return EVENT_KIND;
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
