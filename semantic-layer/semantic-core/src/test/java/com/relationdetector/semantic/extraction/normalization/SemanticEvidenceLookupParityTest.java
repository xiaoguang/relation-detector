package com.relationdetector.semantic.extraction.normalization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.relationdetector.semantic.evidence.SemanticEvidenceStore;
import com.relationdetector.semantic.extraction.artifact.SemanticReconstructedEvidenceLookup;
import com.relationdetector.semantic.ingest.ScanResultReader;
import com.relationdetector.semantic.ingest.SemanticInputStore;

final class SemanticEvidenceLookupParityTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void liveAndReconstructedLookupsUseTheSameTypedAndPhysicalMembership() throws Exception {
        Path scan = tempDir.resolve("scan.json");
        JSON.writeValue(scan.toFile(), JSON.readTree(scanResult()));
        Path bundle = tempDir.resolve("full-evidence-bundle.json");

        try (SemanticInputStore input = new ScanResultReader().open(
                     List.of(scan), tempDir.resolve("input-work"));
             SemanticEvidenceStore evidence = new SemanticEvidenceStore(
                     input, tempDir.resolve("evidence-work"), 1024 * 1024)) {
            AtomicReference<String> tableFact = firstId(
                    evidence, SemanticEvidenceStore.Section.METADATA_TABLES);
            AtomicReference<String> columnFact = firstId(
                    evidence, SemanticEvidenceStore.Section.METADATA_COLUMNS);
            AtomicReference<String> evidenceRef = firstId(
                    evidence, SemanticEvidenceStore.Section.EVIDENCE);
            assertNotNull(tableFact.get());
            assertNotNull(columnFact.get());
            assertNotNull(evidenceRef.get());
            evidence.writeBundle(bundle);

            SemanticEvidenceLookup live = SemanticEvidenceLookup.from(evidence);
            try (SemanticReconstructedEvidenceLookup reconstructed =
                         new SemanticReconstructedEvidenceLookup(
                                 bundle, tempDir.resolve("reconstructed-lookup"))) {
                for (SemanticEvidenceLookup lookup : List.of(live, reconstructed)) {
                    assertTrue(lookup.containsReference(tableFact.get()));
                    assertTrue(lookup.containsReference(
                            SemanticEvidenceStore.Section.METADATA_TABLES, tableFact.get()));
                    assertFalse(lookup.containsReference(
                            SemanticEvidenceStore.Section.METADATA_COLUMNS, tableFact.get()));
                    assertTrue(lookup.containsReference(
                            SemanticEvidenceStore.Section.METADATA_COLUMNS, columnFact.get()));
                    assertTrue(lookup.findEvidence(evidenceRef.get()).isPresent());
                    assertTrue(lookup.containsPhysicalTable("shop.orders"));
                    assertFalse(lookup.containsPhysicalTable("orders"));
                    assertFalse(lookup.containsPhysicalTable("other.orders"));
                    assertTrue(lookup.containsPhysicalColumn("shop.orders.id"));
                    assertFalse(lookup.containsPhysicalColumn("orders.id"));
                    assertFalse(lookup.containsPhysicalColumn("other.orders.id"));
                }
                assertEquals(
                        live.containsPhysicalColumn("shop.orders.id"),
                        reconstructed.containsPhysicalColumn("shop.orders.id"));
            }
        }
    }

    private AtomicReference<String> firstId(
            SemanticEvidenceStore store,
            SemanticEvidenceStore.Section section
    ) {
        AtomicReference<String> result = new AtomicReference<>();
        store.forEachDescriptor(section, (id, ignored) -> result.compareAndSet(null, id));
        return result;
    }

    private String scanResult() {
        return """
                {
                  "database": {"type": "mysql", "catalog": "shop", "schema": ""},
                  "generatedAt": "2026-08-04T12:00:00Z",
                  "summary": {
                    "directRelationshipCount": 0,
                    "derivedRelationshipCount": 0,
                    "totalRelationshipCount": 0,
                    "directDataLineageCount": 0,
                    "derivedDataLineageCount": 0,
                    "totalDataLineageCount": 0,
                    "directNamingEvidenceCount": 0,
                    "derivedNamingEvidenceCount": 0,
                    "totalNamingEvidenceCount": 0,
                    "warningCount": 0,
                    "sources": ["metadata"]
                  },
                  "metadataInventory": {
                    "status": "COMPLETE",
                    "basis": "LIVE_METADATA",
                    "scope": {
                      "catalog": "shop",
                      "schema": "",
                      "includeTables": [],
                      "excludeTables": []
                    },
                    "counts": {"tables": 1, "columns": 1, "constraints": 0, "indexes": 0},
                    "tables": [
                      {"catalog": "shop", "schema": null, "tableName": "orders",
                       "tableType": "BASE TABLE", "engine": "InnoDB", "comment": ""}
                    ],
                    "columns": [
                      {"catalog": "shop", "schema": null, "tableName": "orders",
                       "columnName": "id", "dataType": "bigint", "columnType": "bigint",
                       "nullable": false, "defaultValue": null, "extra": "",
                       "generationExpression": "", "ordinalPosition": 1}
                    ],
                    "constraints": [],
                    "indexes": []
                  },
                  "relationships": [],
                  "dataLineages": [],
                  "derivedRelationships": [],
                  "derivedDataLineages": [],
                  "namingEvidence": [],
                  "derivedNamingEvidence": [],
                  "warnings": []
                }
                """;
    }
}
