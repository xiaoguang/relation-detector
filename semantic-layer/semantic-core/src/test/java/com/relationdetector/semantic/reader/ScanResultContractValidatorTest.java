package com.relationdetector.semantic.reader;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

final class ScanResultContractValidatorTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void rejectsMissingRequiredFactArray() throws Exception {
        ObjectNode root = validRoot();
        root.remove("relationships");

        assertThrows(IllegalArgumentException.class, () -> read(root));
    }

    @Test
    void rejectsSummaryCountThatDoesNotMatchFacts() throws Exception {
        ObjectNode root = validRoot();
        ((ObjectNode) root.path("summary")).put("directRelationshipCount", 2);

        assertThrows(IllegalArgumentException.class, () -> read(root));
    }

    @Test
    void acceptsFullySuppressedWarningsAndRejectsHiddenCountMismatch() throws Exception {
        ObjectNode suppressed = validRoot();
        assertDoesNotThrow(() -> read(suppressed));

        ObjectNode inconsistent = validRoot();
        ((ObjectNode) inconsistent.path("summary")).put("warningCount", 1);
        assertThrows(IllegalArgumentException.class, () -> read(inconsistent));
    }

    @Test
    void rejectsRelationshipWithMissingEndpointTable() throws Exception {
        ObjectNode root = validRoot();
        ((ObjectNode) root.path("relationships").get(0).path("source")).put("table", "");

        assertThrows(IllegalArgumentException.class, () -> read(root));
    }

    @Test
    void rejectsConfidenceOutsideUnitInterval() throws Exception {
        ObjectNode root = validRoot();
        ((ObjectNode) root.path("relationships").get(0)).put("confidence", 1.5d);

        assertThrows(IllegalArgumentException.class, () -> read(root));
    }

    @Test
    void rejectsDuplicateSemanticFactIdentity() throws Exception {
        ObjectNode root = validRoot();
        root.withArray("relationships").add(root.path("relationships").get(0).deepCopy());
        ((ObjectNode) root.path("summary"))
                .put("directRelationshipCount", 2)
                .put("totalRelationshipCount", 2);

        assertThrows(IllegalArgumentException.class, () -> read(root));
    }

    @Test
    void rejectsInvalidGeneratedAtAndUnknownEnums() throws Exception {
        ObjectNode invalidTime = validRoot();
        invalidTime.put("generatedAt", "not-a-timestamp");
        assertThrows(IllegalArgumentException.class, () -> read(invalidTime));

        ObjectNode invalidRelation = validRoot();
        ((ObjectNode) invalidRelation.path("relationships").get(0)).put("relationType", "FUTURE_RELATION");
        assertThrows(IllegalArgumentException.class, () -> read(invalidRelation));
    }

    @Test
    void rejectsUnknownNestedEvidenceType() throws Exception {
        ObjectNode root = validRoot();
        ((ObjectNode) root.withArray("relationships").get(0)).withArray("evidence").addObject()
                .put("type", "SQL_FROM_PLUGIN")
                .put("sourceType", "PLAIN_SQL")
                .put("score", 0.5)
                .put("source", "query.sql")
                .put("detail", "join")
                .putObject("attributes");

        assertThrows(IllegalArgumentException.class, () -> read(root));
    }

    @Test
    void readsNonEmptyDerivedLineageUsingDerivedPathShape() throws Exception {
        ObjectNode root = validRoot();
        ObjectNode derived = root.withArray("derivedDataLineages").addObject();
        derived.put("kind", "DATA_LINEAGE");
        derived.putObject("source").put("table", "orders").put("column", "customer_id");
        derived.putObject("target").put("table", "customer_summary").put("column", "customer_id");
        derived.put("pathLength", 2);
        derived.put("confidence", 0.6);
        derived.putArray("path")
                .addObject().put("table", "orders").put("column", "customer_id");
        derived.withArray("path")
                .addObject().put("table", "customer_stage").put("column", "customer_id");
        derived.withArray("path")
                .addObject().put("table", "customer_summary").put("column", "customer_id");
        derived.putArray("evidence");
        derived.putArray("rawEvidence");
        derived.putObject("attributes");
        ((ObjectNode) root.path("summary"))
                .put("derivedDataLineageCount", 1)
                .put("totalDataLineageCount", 1);

        assertDoesNotThrow(() -> read(root));
    }

    @Test
    void rejectsDerivedPathWithTooFewEndpointsOrMismatchedLength() throws Exception {
        ObjectNode tooShort = derivedLineageRoot();
        ((ObjectNode) tooShort.path("derivedDataLineages").get(0)).withArray("path").remove(1);
        assertThrows(IllegalArgumentException.class, () -> read(tooShort));

        ObjectNode wrongLength = derivedLineageRoot();
        ((ObjectNode) wrongLength.path("derivedDataLineages").get(0)).put("pathLength", 1);
        assertThrows(IllegalArgumentException.class, () -> read(wrongLength));
    }

    @Test
    void rejectsDerivedPathWhoseEndpointsDoNotMatchSourceAndTarget() throws Exception {
        ObjectNode wrongSource = derivedLineageRoot();
        ((ObjectNode) wrongSource.path("derivedDataLineages").get(0).path("source"))
                .put("table", "other_orders");
        assertThrows(IllegalArgumentException.class, () -> read(wrongSource));

        ObjectNode wrongTarget = derivedLineageRoot();
        ((ObjectNode) wrongTarget.path("derivedDataLineages").get(0).path("target"))
                .put("table", "other_summary");
        assertThrows(IllegalArgumentException.class, () -> read(wrongTarget));
    }

    private ObjectNode derivedLineageRoot() throws Exception {
        ObjectNode root = validRoot();
        ObjectNode derived = root.withArray("derivedDataLineages").addObject();
        derived.put("kind", "DATA_LINEAGE");
        derived.putObject("source").put("table", "orders").put("column", "customer_id");
        derived.putObject("target").put("table", "customer_summary").put("column", "customer_id");
        derived.put("pathLength", 2);
        derived.put("confidence", 0.6);
        derived.putArray("path")
                .addObject().put("table", "orders").put("column", "customer_id");
        derived.withArray("path")
                .addObject().put("table", "customer_stage").put("column", "customer_id");
        derived.withArray("path")
                .addObject().put("table", "customer_summary").put("column", "customer_id");
        derived.putArray("evidence");
        derived.putArray("rawEvidence");
        derived.putObject("attributes");
        ((ObjectNode) root.path("summary"))
                .put("derivedDataLineageCount", 1)
                .put("totalDataLineageCount", 1);
        return root;
    }

    private ScanBundle read(ObjectNode root) throws Exception {
        Path input = tempDir.resolve("scan-result.json");
        Files.writeString(input, JSON.writeValueAsString(root));
        return new ScanResultReader().read(input);
    }

    private ObjectNode validRoot() throws Exception {
        return (ObjectNode) JSON.readTree("""
                {
                  "database": {"type": "mysql", "catalog": "shop", "schema": ""},
                  "generatedAt": "2026-07-18T00:00:00Z",
                  "summary": {
                    "directRelationshipCount": 1,
                    "derivedRelationshipCount": 0,
                    "totalRelationshipCount": 1,
                    "directDataLineageCount": 0,
                    "derivedDataLineageCount": 0,
                    "totalDataLineageCount": 0,
                    "directNamingEvidenceCount": 0,
                    "derivedNamingEvidenceCount": 0,
                    "totalNamingEvidenceCount": 0,
                    "warningCount": 0,
                    "sources": ["logs"]
                  },
                  "relationships": [{
                    "source": {"table": "orders", "column": "customer_id"},
                    "target": {"table": "customers", "column": "id"},
                    "relationType": "FK_LIKE",
                    "relationSubType": "INFERRED_JOIN_FK",
                    "confidence": 0.8,
                    "evidence": [],
                    "rawEvidence": [],
                    "warnings": []
                  }],
                  "dataLineages": [],
                  "derivedRelationships": [],
                  "derivedDataLineages": [],
                  "namingEvidence": [],
                  "derivedNamingEvidence": [],
                  "warnings": []
                }
                """);
    }
}
