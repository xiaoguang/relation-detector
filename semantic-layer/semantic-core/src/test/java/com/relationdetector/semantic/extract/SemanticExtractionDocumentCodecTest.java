package com.relationdetector.semantic.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.extract.model.SemanticExtractionDocument;

final class SemanticExtractionDocumentCodecTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void readsAndWritesTypedSemanticSectionsWithoutChangingTheirShape() throws Exception {
        JsonNode raw = JSON.readTree("""
                {
                  "entities": [
                    {
                      "id": "entity:sales_fact",
                      "name": "销售事实表",
                      "physicalName": "sales_fact",
                      "type": "分析事实表",
                      "evidenceRefs": ["relationship:fact"]
                    }
                  ],
                  "events": [
                    {
                      "name": "重建销售事实表",
                      "eventCandidateRef": "event-candidate:routine:erp.sp_rebuild_sales_fact",
                      "inputs": ["销售订单"],
                      "outputs": ["销售事实表"],
                      "evidenceRefs": ["event-candidate:routine:erp.sp_rebuild_sales_fact"]
                    }
                  ],
                  "relations": [],
                  "lineage": [],
                  "metrics": [],
                  "dimensions": [],
                  "triplets": [],
                  "reviewItems": []
                }
                """);

        SemanticExtractionDocument document = new SemanticExtractionDocumentCodec().read(raw);

        assertEquals("sales_fact", document.entities().get(0).physicalName());
        assertEquals("event-candidate:routine:erp.sp_rebuild_sales_fact",
                document.events().get(0).eventCandidateRef());

        ObjectNode roundTrip = new SemanticExtractionDocumentCodec().write(document);
        assertEquals(raw, roundTrip);
        assertTrue(roundTrip.path("relations").isArray());
    }
}
