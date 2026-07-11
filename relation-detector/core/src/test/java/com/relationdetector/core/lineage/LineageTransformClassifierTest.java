package com.relationdetector.core.lineage;

import static com.relationdetector.contracts.Enums.LineageTransformType.AGGREGATE;
import static com.relationdetector.contracts.Enums.LineageTransformType.ARITHMETIC;
import static com.relationdetector.contracts.Enums.LineageTransformType.CASE_WHEN;
import static com.relationdetector.contracts.Enums.LineageTransformType.COALESCE;
import static com.relationdetector.contracts.Enums.LineageTransformType.CONCAT_FORMAT;
import static com.relationdetector.contracts.Enums.LineageTransformType.CUMULATIVE;
import static com.relationdetector.contracts.Enums.LineageTransformType.DIRECT;
import static com.relationdetector.contracts.Enums.LineageTransformType.FUNCTION_CALL;
import static com.relationdetector.contracts.Enums.LineageTransformType.UNKNOWN_EXPRESSION;
import static com.relationdetector.contracts.Enums.LineageTransformType.WINDOW_DERIVED;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;

class LineageTransformClassifierTest {
    @Test
    void appliesThePlannedDominantTransformPriority() {
        assertAll(
                () -> assertEquals(CUMULATIVE,
                        LineageTransformClassifier.dominant(CUMULATIVE, AGGREGATE)),
                () -> assertEquals(CUMULATIVE,
                        LineageTransformClassifier.dominant(AGGREGATE, CUMULATIVE)),
                () -> assertEquals(AGGREGATE,
                        LineageTransformClassifier.dominant(WINDOW_DERIVED, AGGREGATE)),
                () -> assertEquals(ARITHMETIC,
                        LineageTransformClassifier.dominant(COALESCE, ARITHMETIC)),
                () -> assertEquals(COALESCE,
                        LineageTransformClassifier.dominant(CONCAT_FORMAT, COALESCE)),
                () -> assertEquals(ARITHMETIC,
                        LineageTransformClassifier.dominant(CONCAT_FORMAT, ARITHMETIC)),
                () -> assertEquals(COALESCE,
                        LineageTransformClassifier.dominant(FUNCTION_CALL, COALESCE)),
                () -> assertEquals(FUNCTION_CALL,
                        LineageTransformClassifier.dominant(DIRECT, FUNCTION_CALL)),
                () -> assertEquals(CASE_WHEN,
                        LineageTransformClassifier.dominant(DIRECT, CASE_WHEN)),
                () -> assertEquals(CASE_WHEN,
                        LineageTransformClassifier.dominantForFlow(
                                LineageFlowKind.CONTROL, ARITHMETIC, CASE_WHEN)),
                () -> assertEquals(ARITHMETIC,
                        LineageTransformClassifier.dominantForFlow(
                                LineageFlowKind.VALUE, ARITHMETIC, CASE_WHEN)),
                () -> assertEquals(DIRECT,
                        LineageTransformClassifier.dominant(UNKNOWN_EXPRESSION, DIRECT)));
    }

    @Test
    void classifiesCommonFunctionTransformsWithoutInspectingSqlText() {
        assertAll(
                () -> assertEquals(AGGREGATE,
                        LineageTransformClassifier.classifyFunction("SUM", false)),
                () -> assertEquals(CUMULATIVE,
                        LineageTransformClassifier.classifyFunction("sum", true)),
                () -> assertEquals(WINDOW_DERIVED,
                        LineageTransformClassifier.classifyFunction("row_number", true)),
                () -> assertEquals(COALESCE,
                        LineageTransformClassifier.classifyFunction("CoAlEsCe", false)),
                () -> assertEquals(CONCAT_FORMAT,
                        LineageTransformClassifier.classifyFunction("concat", false)),
                () -> assertEquals(CONCAT_FORMAT,
                        LineageTransformClassifier.classifyFunction("FORMAT", false)),
                () -> assertEquals(FUNCTION_CALL,
                        LineageTransformClassifier.classifyFunction("dateadd", false)));
    }

    @Test
    void acceptsCaseInsensitiveDialectFunctionExtensions() {
        Map<String, LineageTransformType> extensions = Map.of(
                "NVL", COALESCE,
                "LISTAGG", CONCAT_FORMAT,
                "TOTAL", AGGREGATE,
                "RUNNING_TOTAL", CUMULATIVE);

        assertAll(
                () -> assertEquals(COALESCE,
                        LineageTransformClassifier.classifyFunction("nvl", false, extensions)),
                () -> assertEquals(CONCAT_FORMAT,
                        LineageTransformClassifier.classifyFunction("listagg", false, extensions)),
                () -> assertEquals(AGGREGATE,
                        LineageTransformClassifier.classifyFunction("total", false, extensions)),
                () -> assertEquals(AGGREGATE,
                        LineageTransformClassifier.classifyFunction("running_total", false, extensions)),
                () -> assertEquals(CUMULATIVE,
                        LineageTransformClassifier.classifyFunction("running_total", true, extensions)));
    }
}
