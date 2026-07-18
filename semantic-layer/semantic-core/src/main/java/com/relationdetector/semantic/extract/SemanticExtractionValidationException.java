package com.relationdetector.semantic.extract;

/**
 * CN: 表示模型输出无法形成 evidence-backed、physical-valid、ref-closed 的 semantic artifact；normalization 原子失败并不写出部分正式结果。
 * EN: Signals that model output cannot form an evidence-backed, physically valid, reference-closed semantic artifact. Normalization fails atomically without a partial formal result.
 */
public final class SemanticExtractionValidationException extends IllegalArgumentException {
    public SemanticExtractionValidationException(String message) {
        super(message);
    }
}
