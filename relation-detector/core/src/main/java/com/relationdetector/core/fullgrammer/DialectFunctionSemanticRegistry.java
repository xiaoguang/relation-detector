package com.relationdetector.core.fullgrammer;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.relationdetector.contracts.Enums.LineageTransformType;

/** Immutable dialect function semantics keyed by exact parsed function symbols. */
public final class DialectFunctionSemanticRegistry {
    private static final Map<String, LineageTransformType> STANDARD = Map.ofEntries(
            Map.entry("sum", LineageTransformType.AGGREGATE),
            Map.entry("avg", LineageTransformType.AGGREGATE),
            Map.entry("count", LineageTransformType.AGGREGATE),
            Map.entry("min", LineageTransformType.AGGREGATE),
            Map.entry("max", LineageTransformType.AGGREGATE),
            Map.entry("row_number", LineageTransformType.WINDOW_DERIVED),
            Map.entry("rank", LineageTransformType.WINDOW_DERIVED),
            Map.entry("dense_rank", LineageTransformType.WINDOW_DERIVED),
            Map.entry("ntile", LineageTransformType.WINDOW_DERIVED),
            Map.entry("lag", LineageTransformType.WINDOW_DERIVED),
            Map.entry("lead", LineageTransformType.WINDOW_DERIVED),
            Map.entry("coalesce", LineageTransformType.COALESCE),
            Map.entry("concat", LineageTransformType.CONCAT_FORMAT),
            Map.entry("format", LineageTransformType.CONCAT_FORMAT),
            Map.entry("to_char", LineageTransformType.CONCAT_FORMAT),
            Map.entry("group_concat", LineageTransformType.CONCAT_FORMAT));

    private final Map<String, LineageTransformType> functions;

    private DialectFunctionSemanticRegistry(Map<String, LineageTransformType> functions) {
        this.functions = Map.copyOf(functions);
    }

    public static DialectFunctionSemanticRegistry standard() {
        return new DialectFunctionSemanticRegistry(STANDARD);
    }

    public DialectFunctionSemanticRegistry withExtensions(Map<String, LineageTransformType> extensions) {
        LinkedHashMap<String, LineageTransformType> merged = new LinkedHashMap<>(functions);
        extensions.forEach((name, transform) -> merged.put(normalize(name), transform));
        return new DialectFunctionSemanticRegistry(merged);
    }

    public LineageTransformType classify(String functionName) {
        if (functionName == null || functionName.isBlank()) {
            return LineageTransformType.FUNCTION_CALL;
        }
        return functions.getOrDefault(normalize(functionName), LineageTransformType.FUNCTION_CALL);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }
}
