package com.relationdetector.semantic.extract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.reader.ScanBundle;
import com.relationdetector.semantic.reader.ScanDiagnosticFact;

/** Creates deterministic review candidates from diagnostics and uncertain evidence. */
final class ReviewItemCandidateGenerator {
    private static final ObjectMapper JSON = new ObjectMapper();

    ArrayNode build(ScanBundle bundle, int limit) {
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
            if (limited(limit) && result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private boolean limited(int limit) {
        return limit > 0;
    }
}
