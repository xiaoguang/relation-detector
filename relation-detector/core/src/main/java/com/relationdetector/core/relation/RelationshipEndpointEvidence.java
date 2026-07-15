package com.relationdetector.core.relation;

import java.util.LinkedHashMap;
import java.util.Map;

import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.Endpoint;

final class RelationshipEndpointEvidence {
    private RelationshipEndpointEvidence() {
    }

    static Evidence normalizeSide(Evidence evidence, Endpoint source, Endpoint target) {
        Map<String, Object> attributes = new LinkedHashMap<>(evidence.attributes());
        String endpoint = explicitEndpoint(attributes);
        if (endpoint.isBlank()) return evidence;
        if (endpoint.equals(source.normalizedKey())) attributes.put("endpointSide", "source");
        else if (endpoint.equals(target.normalizedKey())) attributes.put("endpointSide", "target");
        else attributes.remove("endpointSide");
        return new Evidence(evidence.type(), evidence.score(), evidence.sourceType(), evidence.source(),
                evidence.detail(), attributes);
    }

    private static String explicitEndpoint(Map<String, Object> attributes) {
        for (String key : new String[]{"uniqueEndpoint", "indexEndpoint", "indexedEndpoint"}) {
            Object value = attributes.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value).trim().toLowerCase(java.util.Locale.ROOT);
            }
        }
        return "";
    }
}
