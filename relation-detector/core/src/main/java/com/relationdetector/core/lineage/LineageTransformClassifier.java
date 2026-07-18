package com.relationdetector.core.lineage;

import java.util.Locale;
import java.util.Map;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;

/**
 * CN: 提供无状态、跨方言的 expression lineage transform 优先级与精确 function-symbol 分类。
 * EN: Provides stateless cross-dialect expression-lineage transform priority and exact function-symbol classification.
 */
public final class LineageTransformClassifier {
    private static final Map<String, LineageTransformType> COMMON_FUNCTIONS = Map.ofEntries(
            Map.entry("sum", LineageTransformType.CUMULATIVE),
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
            Map.entry("format", LineageTransformType.CONCAT_FORMAT));

    private LineageTransformClassifier() {
    }

    public static LineageTransformType dominant(LineageTransformType... transforms) {
        LineageTransformType dominant = LineageTransformType.DIRECT;
        for (LineageTransformType transform : transforms) {
            if (priority(transform) > priority(dominant)) {
                dominant = transform;
            }
        }
        return dominant;
    }

    /**
     *
     * Applies common transform priority to value flow. Control callers provide
     * the dependency-role transform first so child value expressions cannot
     * overwrite CASE, locator, grouping, or window semantics.
     */
    public static LineageTransformType dominantForFlow(
            LineageFlowKind flowKind,
        LineageTransformType... transforms
    ) {
        if (flowKind == LineageFlowKind.CONTROL) {
            return transforms.length > 0 && transforms[0] != null
                    ? transforms[0]
                    : LineageTransformType.CASE_WHEN;
        }
        return dominant(transforms);
    }

    public static LineageTransformType classifyFunction(String functionName, boolean windowed) {
        return classifyFunction(functionName, windowed, Map.of());
    }

    /**
     *
     * Classifies a parser-provided function name. A {@code CUMULATIVE} mapping
     * denotes an aggregate that becomes cumulative only when windowed.
     */
    public static LineageTransformType classifyFunction(
            String functionName,
            boolean windowed,
            Map<String, LineageTransformType> dialectExtensions
    ) {
        String normalized = normalize(functionName);
        LineageTransformType classified = extensionTransform(normalized, dialectExtensions);
        if (classified == null) {
            classified = COMMON_FUNCTIONS.get(normalized);
        }
        if (classified == LineageTransformType.CUMULATIVE) {
            return windowed ? LineageTransformType.CUMULATIVE : LineageTransformType.AGGREGATE;
        }
        if (classified != null) {
            return classified;
        }
        return windowed ? LineageTransformType.WINDOW_DERIVED : LineageTransformType.FUNCTION_CALL;
    }

    private static LineageTransformType extensionTransform(
            String normalized,
            Map<String, LineageTransformType> dialectExtensions
    ) {
        LineageTransformType direct = dialectExtensions.get(normalized);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, LineageTransformType> entry : dialectExtensions.entrySet()) {
            if (normalized.equals(normalize(entry.getKey()))) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String normalize(String functionName) {
        return functionName == null ? "" : functionName.strip().toLowerCase(Locale.ROOT);
    }

    private static int priority(LineageTransformType transform) {
        return switch (transform) {
            // CASE_WHEN is forced for predicate/control sources by the callers.
            // For value expressions, preserve the outer data transformation.
            case CUMULATIVE -> 8;
            case AGGREGATE -> 7;
            case WINDOW_DERIVED -> 6;
            case ARITHMETIC -> 5;
            case COALESCE -> 4;
            case CONCAT_FORMAT -> 3;
            case FUNCTION_CALL -> 2;
            case CASE_WHEN -> 1;
            default -> 0;
        };
    }
}
