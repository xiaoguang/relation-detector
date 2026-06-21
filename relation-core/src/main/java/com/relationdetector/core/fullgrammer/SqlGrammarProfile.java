package com.relationdetector.core.fullgrammer;

import java.util.Set;

import com.relationdetector.api.Enums.DatabaseType;

/**
 * Versioned SQL grammar profile for future full-grammer token-event parsers.
 *
 * <p>The profile identifies the dialect/version grammar surface. It does not
 * own relationship or lineage semantics; parse-tree visitors must still emit
 * the normal token-event model consumed by the production extractors.
 */
public record SqlGrammarProfile(
        String id,
        DatabaseType databaseType,
        int majorVersion,
        int minorVersion,
        Set<String> capabilities
) {
    public SqlGrammarProfile {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id is required");
        }
        if (databaseType == null) {
            throw new IllegalArgumentException("databaseType is required");
        }
        capabilities = Set.copyOf(capabilities == null ? Set.of() : capabilities);
    }
}
