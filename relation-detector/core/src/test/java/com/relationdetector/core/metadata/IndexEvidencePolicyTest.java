package com.relationdetector.core.metadata;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.metadata.MetadataIndexFact;

class IndexEvidencePolicyTest {
    private final IndexEvidencePolicy policy = new IndexEvidencePolicy();

    @Test
    void prefixUniqueIndexSupportsLookupButDoesNotProveFullColumnUnique() {
        MetadataIndexFact index = index(
                true,
                List.of("email"),
                List.of(),
                List.of("8"),
                List.of(1));

        assertTrue(policy.supportsLeadingColumnLookup(index, "email"));
        assertFalse(policy.provesSingleColumnUnique(index, "email"));
    }

    @Test
    void fullSingleColumnUniqueIndexStillProvesUniqueness() {
        MetadataIndexFact index = index(
                true,
                List.of("email"),
                List.of(),
                List.of(""),
                List.of(1));

        assertTrue(policy.supportsLeadingColumnLookup(index, "email"));
        assertTrue(policy.provesSingleColumnUnique(index, "email"));
    }

    @Test
    void malformedIndexShapesDoNotProvideEvidence() {
        MetadataIndexFact missingOrdinal = index(
                true,
                List.of("email"),
                List.of(),
                List.of(),
                List.of(2));
        MetadataIndexFact misalignedPrefix = index(
                true,
                List.of("email"),
                List.of(),
                List.of("8", ""),
                List.of(1));

        assertFalse(policy.supportsLeadingColumnLookup(missingOrdinal, "email"));
        assertFalse(policy.provesSingleColumnUnique(missingOrdinal, "email"));
        assertFalse(policy.supportsLeadingColumnLookup(misalignedPrefix, "email"));
        assertFalse(policy.provesSingleColumnUnique(misalignedPrefix, "email"));
    }

    private MetadataIndexFact index(
            boolean unique,
            List<String> columns,
            List<String> expressions,
            List<String> subParts,
            List<Integer> positions
    ) {
        return new MetadataIndexFact(
                "shop",
                null,
                "users",
                "idx_users_email",
                unique,
                false,
                "BTREE",
                true,
                columns,
                expressions,
                subParts,
                positions);
    }
}
