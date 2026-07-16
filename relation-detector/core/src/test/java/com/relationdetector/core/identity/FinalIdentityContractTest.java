package com.relationdetector.core.identity;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.TableId;

class FinalIdentityContractTest {
    @ParameterizedTest(name = "rejects forged {0}")
    @MethodSource("structuralForgeries")
    void rejectsCallerSuppliedNormalizedNameThatMasksDifferentTableComponents(
            String component,
            TableId forgedColumnTable
    ) {
        TableId endpointTable = new TableId("catalog_a", "sales", "orders", "caller_normalized_name");

        assertAll(
                () -> assertFalse(endpointTable.sameIdentity(forgedColumnTable),
                        component + " must participate independently in structural table identity"),
                () -> assertThrows(Exception.class,
                        () -> new Endpoint(endpointTable, ColumnRef.of(forgedColumnTable, "id")),
                        "an endpoint must reject a column from a structurally different table"));
    }

    private static Stream<Arguments> structuralForgeries() {
        return Stream.of(
                Arguments.of("catalog",
                        new TableId("catalog_b", "sales", "orders", "caller_normalized_name")),
                Arguments.of("schema",
                        new TableId("catalog_a", "archive", "orders", "caller_normalized_name")),
                Arguments.of("table",
                        new TableId("catalog_a", "sales", "invoices", "caller_normalized_name")));
    }
}
