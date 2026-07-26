package com.relationdetector.semantic.extract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.reader.ScanBundle;
import com.relationdetector.semantic.reader.ScanDiagnosticFact;

/**
 * CN: 将完整 bundle 中的 diagnostic facts 一对一转换为确定性 review candidates；不裁剪输入，也不决定正式审核状态。
 * EN: Converts diagnostic facts from the complete bundle one-for-one into deterministic review candidates. It does
 * not truncate input or decide formal governance status.
 */
final class ReviewItemCandidateGenerator {
    private static final ObjectMapper JSON = new ObjectMapper();

    ArrayNode build(ScanBundle bundle) {
        ArrayNode result = JSON.createArrayNode();
        java.util.List<ScanDiagnosticFact> diagnostics = bundle == null ? java.util.List.of() : bundle.diagnostics();
        for (ScanDiagnosticFact diagnostic : diagnostics) {
            String targetRef = diagnostic.id();
            ObjectNode item = result.addObject();
            item.put("id", "review-candidate:" + targetRef);
            item.put("targetRef", targetRef);
            item.put("targetSection", "diagnostics");
            item.put("type", "REVIEW_NEEDED");
            item.put("severity", diagnostic.severity().isBlank() ? "MEDIUM" : diagnostic.severity());
            item.put("reason", diagnostic.message().isBlank() ? "Diagnostic requires review." : diagnostic.message());
            item.putArray("evidenceRefs").add(targetRef);
        }
        return result;
    }
}
