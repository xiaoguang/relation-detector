package com.relationdetector.postgres.common;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.OptionalInt;

import org.junit.jupiter.api.Test;

class PostgresSetProjectionLayoutTest {
    @Test
    void unknownWildcardArityDoesNotCreateAMismatch() {
        PostgresSetProjectionLayout layout = PostgresSetProjectionLayout.resolve(
                List.of("id", "name"),
                List.of(),
                List.of(OptionalInt.of(2), OptionalInt.empty()));

        assertTrue(layout.arityMatches());
        assertTrue(PostgresSetProjectionLayout.branchArity(2, true).isEmpty());
    }

    @Test
    void knownUnequalBranchArityIsRejected() {
        PostgresSetProjectionLayout layout = PostgresSetProjectionLayout.resolve(
                List.of(),
                List.of("id", "name"),
                List.of(OptionalInt.of(2), OptionalInt.of(3)));

        assertFalse(layout.arityMatches());
        assertTrue(PostgresSetProjectionLayout.branchArity(2, false).isPresent());
    }

    @Test
    void entirelyUnknownArityRemainsValidButUnresolved() {
        PostgresSetProjectionLayout layout = PostgresSetProjectionLayout.resolve(
                List.of(),
                List.of(),
                List.of(OptionalInt.empty(), OptionalInt.empty()));

        assertTrue(layout.columns().isEmpty());
        assertTrue(layout.arityMatches());
    }
}
