package com.relationdetector.semantic.extract;

/** Signals that model output cannot be emitted as a ref-closed semantic artifact. */
public final class SemanticExtractionValidationException extends IllegalArgumentException {
    public SemanticExtractionValidationException(String message) {
        super(message);
    }
}
