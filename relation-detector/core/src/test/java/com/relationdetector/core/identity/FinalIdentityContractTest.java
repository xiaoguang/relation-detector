package com.relationdetector.core.identity;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.TableId;

class FinalIdentityContractTest {
    @Test
    void rejectsCallerSuppliedNormalizedNameThatMasksDifferentTableComponents() {
        TableId endpointTable = new TableId("catalog_a", "sales", "orders", "caller_normalized_name");
        TableId forgedColumnTable = new TableId("catalog_a", "archive", "invoices", "caller_normalized_name");

        assertAll(
                () -> assertFalse(endpointTable.sameIdentity(forgedColumnTable),
                        "identity must compare structural table components, not a caller-supplied normalized name"),
                () -> assertThrows(Exception.class,
                        () -> new Endpoint(endpointTable, ColumnRef.of(forgedColumnTable, "id")),
                        "an endpoint must reject a column from a structurally different table"));
    }
}
