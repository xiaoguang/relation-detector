package com.relationdetector.semantic.extract;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.SemanticFactIds;
import com.relationdetector.semantic.reader.ScanBundle;

/** Creates deterministic review candidates from diagnostics and uncertain evidence. */
final class ReviewItemCandidateGenerator {
    private static final ObjectMapper JSON = new ObjectMapper();

    ArrayNode build(ScanBundle bundle, int limit) {
        ArrayNode result = JSON.createArrayNode();
        List<JsonNode> diagnostics = bundle == null ? List.of() : bundle.diagnostics();
        for (int index = 0; index < diagnostics.size(); index++) {
            JsonNode diagnostic = diagnostics.get(index);
            String targetRef = SemanticFactIds.diagnostic(diagnostic, index);
            ObjectNode item = result.addObject();
            item.put("id", "review-candidate:" + targetRef);
            item.put("targetRef", targetRef);
            item.put("targetSection", "diagnostics");
            item.put("type", "REVIEW_NEEDED");
            item.put("severity", diagnostic.path("severity").asText("MEDIUM"));
            item.put("reason", diagnostic.path("message").asText("Diagnostic requires review."));
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
