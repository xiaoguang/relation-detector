package com.relationdetector.semantic.extraction.normalization;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.relationdetector.semantic.model.PhysicalEndpointRef;

/**
 * CN: 提供formal normalization中无状态的空值、endpoint与有序去重原语；输入是已通过wire边界的有界值，
 * 输出供normalizers组装typed model。上游是section normalizers，下游是model fields；本类不验证evidence、
 * 不生成identity，也不解析raw model文档。
 *
 * <p>EN: Stateless null, endpoint, and stable-deduplication primitives for formal normalization. Section normalizers
 * provide bounded wire-validated values and consume the resulting model fields. This helper does not validate
 * evidence, generate identities, or parse raw model documents.
 */
public final class SemanticNormalizationSupport {
    private SemanticNormalizationSupport() {
    }

    public static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public static String tableOf(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return "";
        }
        return PhysicalEndpointRef.column(endpoint).table();
    }

    public static List<String> mutableStrings(List<String> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }

    public static void addIfAbsent(List<String> values, String value, Set<String> linkedEntities) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!values.contains(value)) {
            values.add(value);
        }
        linkedEntities.add(value);
    }

    public static List<String> distinct(List<String> values) {
        return new ArrayList<>(new LinkedHashSet<>(values == null ? List.of() : values));
    }
}
