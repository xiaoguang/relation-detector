package com.relationdetector.semantic.reader;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * CN: 保存 wire-contract 已验证的 parser/scan diagnostic、位置和不可变 JSON payload；下游只用于审计，不把 warning 转成语义事实。
 * EN: Holds a wire-contract-validated parser or scan diagnostic with location and immutable JSON payload. Downstream consumers audit it but never turn warnings into semantic facts.
 */
public record ScanDiagnosticFact(
        String id,
        String code,
        String severity,
        String message,
        String source,
        int line,
        JsonNode document
) implements ScanFact {
    public ScanDiagnosticFact {
        if (document == null || !document.isObject()) {
            throw new IllegalArgumentException("diagnostic fact must be a JSON object");
        }
        document = document.deepCopy();
    }
}
