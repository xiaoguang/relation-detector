package com.relationdetector.semantic.reader;

/**
 * CN: 表示 relation-detector JSON 违反 semantic reader 的 current wire contract；reader 原子失败并不返回部分 ScanBundle。
 * EN: Signals that relation-detector JSON violates the semantic reader's current wire contract. Ingestion fails atomically and returns no partial ScanBundle.
 */
public final class ScanResultContractException extends IllegalArgumentException {
    public ScanResultContractException(String message) {
        super(message);
    }
}
