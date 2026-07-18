package com.relationdetector.semantic.extract;

import java.util.LinkedHashMap;
import java.util.Map;

/** Enforces one global owner for every normalized semantic object id. */
final class SemanticOwnerIdRegistry {
    private final Map<String, String> sectionsById = new LinkedHashMap<>();

    void register(String section, String id) {
        String previous = sectionsById.putIfAbsent(id, section);
        if (previous != null) {
            throw new SemanticExtractionValidationException(
                    "duplicate semantic owner id " + id + " in " + previous + " and " + section);
        }
    }
}
