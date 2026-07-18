package com.relationdetector.semantic.reader;

/** Signals that relation-detector JSON does not satisfy the semantic ingestion contract. */
public final class ScanResultContractException extends IllegalArgumentException {
    public ScanResultContractException(String message) {
        super(message);
    }
}
