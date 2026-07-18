package com.relationdetector.semantic.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.relationdetector.semantic.model.PhysicalEndpointRef;

final class PhysicalEndpointJsonReaderTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void preservesCatalogSchemaTableAndColumnFromStructuredJson() throws Exception {
        PhysicalEndpointRef endpoint = PhysicalEndpointJsonReader.read(
                JSON.readTree("""
                        {"table":"catalog.schema.orders","column":"customer_id"}
                        """));

        assertEquals("catalog.schema.orders", endpoint.table());
        assertEquals("customer_id", endpoint.column());
        assertEquals("catalog.schema.orders.customer_id", endpoint.displayName());
    }

    @Test
    void readsTableEndpointWithoutGuessingItsFinalSegmentAsAColumn() throws Exception {
        PhysicalEndpointRef endpoint = PhysicalEndpointJsonReader.read(
                JSON.readTree("""
                        {"table":"catalog.schema.orders"}
                        """));

        assertEquals("catalog.schema.orders", endpoint.table());
        assertEquals(null, endpoint.column());
    }

    @Test
    void rejectsMissingOrBlankStructuredEndpoint() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> PhysicalEndpointJsonReader.read(null));
        assertThrows(IllegalArgumentException.class, () -> PhysicalEndpointJsonReader.read(JSON.readTree("{}")));
        assertThrows(IllegalArgumentException.class,
                () -> PhysicalEndpointJsonReader.read(JSON.readTree("{\"table\":\" \"}")));
    }
}
