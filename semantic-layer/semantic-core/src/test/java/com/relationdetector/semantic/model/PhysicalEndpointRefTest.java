package com.relationdetector.semantic.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class PhysicalEndpointRefTest {
    @Test
    void tableFactoryPreservesEveryNamespaceComponent() {
        PhysicalEndpointRef endpoint = PhysicalEndpointRef.table("catalog.schema.orders");

        assertEquals("catalog.schema.orders", endpoint.table());
        assertFalse(endpoint.isColumnLevel());
        assertEquals("catalog.schema.orders", endpoint.displayName());
    }

    @Test
    void columnFactorySplitsOnlyTheFinalComponent() {
        PhysicalEndpointRef endpoint = PhysicalEndpointRef.column("catalog.schema.orders.id");

        assertEquals("catalog.schema.orders", endpoint.table());
        assertEquals("id", endpoint.column());
        assertTrue(endpoint.isColumnLevel());
        assertEquals("catalog.schema.orders.id", endpoint.displayName());
    }

    @Test
    void columnFactoryRejectsAmbiguousOrBlankNames() {
        assertThrows(IllegalArgumentException.class, () -> PhysicalEndpointRef.column("orders"));
        assertThrows(IllegalArgumentException.class, () -> PhysicalEndpointRef.column(" "));
        assertThrows(IllegalArgumentException.class, () -> PhysicalEndpointRef.table(" "));
    }
}
