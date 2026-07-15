package com.relationdetector.core.identity;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.TableId;

class EndpointCatalogIdentityTest {
    @Test
    void rejectsColumnFromSameNamedTableInDifferentCatalog() {
        TableId endpointTable = table("catalog_a");
        ColumnRef foreignColumn = ColumnRef.of(table("catalog_b"), "customer_id");

        assertThrows(IllegalArgumentException.class, () -> new Endpoint(endpointTable, foreignColumn));
    }

    @Test
    void acceptsColumnFromSameCatalogAndNormalizedTable() {
        TableId endpointTable = table("catalog_a");
        ColumnRef matchingColumn = ColumnRef.of(table("catalog_a"), "customer_id");

        assertDoesNotThrow(() -> new Endpoint(endpointTable, matchingColumn));
    }

    private TableId table(String catalog) {
        return new TableId(catalog, "sales", "orders", "sales.orders");
    }
}
