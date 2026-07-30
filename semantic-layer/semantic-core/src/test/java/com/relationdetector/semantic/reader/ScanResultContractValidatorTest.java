package com.relationdetector.semantic.reader;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.contracts.Enums.MetadataInventoryStatus;

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
    void rejectsMissingMetadataInventory() throws Exception {
        ObjectNode root = validRoot();
        root.remove("metadataInventory");

        assertThrows(IllegalArgumentException.class, () -> read(root));
    }

    @Test
    void rejectsMetadataInventoryThatIsNotComplete() throws Exception {
        ObjectNode root = validRoot();
        ((ObjectNode) root.path("metadataInventory")).put("status", "PARTIAL");

        assertThrows(IllegalArgumentException.class, () -> read(root));
    }

    @Test
    void rejectsMetadataInventoryCountMismatch() throws Exception {
        ObjectNode root = validRoot();
        ((ObjectNode) root.path("metadataInventory").path("counts")).put("tables", 3);

        assertThrows(IllegalArgumentException.class, () -> read(root));
    }

    @Test
    void preservesTypedCompleteMetadataInventory() throws Exception {
        ScanBundle bundle = read(validRoot());

        assertEquals(MetadataInventoryStatus.COMPLETE, bundle.metadataInventory().status());
        assertEquals("shop", bundle.metadataInventory().scope().catalog());
        assertEquals(2, bundle.metadataInventory().tables().size());
        assertEquals(2, bundle.metadataInventory().columns().size());
        assertEquals("orders", bundle.metadataInventory().tables().get(0).tableName());
        assertEquals("customer_id", bundle.metadataInventory().columns().get(0).columnName());
    }

    @Test
    void acceptsClosedForeignKeyAndPrefixIndexInventory() throws Exception {
        ObjectNode root = validRoot();
        addForeignKey(root, "fk_orders_customer", "customer_id", "customers", "id");
        addIndex(root, "idx_orders_customer_prefix", "customer_id", "8", 1);

        assertDoesNotThrow(() -> read(root));
    }

    @Test
    void rejectsDanglingConstraintAndIndexColumns() throws Exception {
        ObjectNode constraint = validRoot();
        addForeignKey(constraint, "fk_orders_customer", "missing_customer_id", "customers", "id");
        assertThrows(IllegalArgumentException.class, () -> read(constraint));

        ObjectNode referenced = validRoot();
        addForeignKey(referenced, "fk_orders_customer", "customer_id", "customers", "missing_id");
        assertThrows(IllegalArgumentException.class, () -> read(referenced));

        ObjectNode index = validRoot();
        addIndex(index, "idx_orders_missing", "missing_customer_id", "", 1);
        assertThrows(IllegalArgumentException.class, () -> read(index));
    }

    @Test
    void rejectsMalformedForeignKeyAndIndexCardinality() throws Exception {
        ObjectNode foreignKey = validRoot();
        addForeignKey(foreignKey, "fk_orders_customer", "customer_id", "customers", "id");
        ((ObjectNode) foreignKey.path("metadataInventory").path("constraints").get(0))
                .withArray("referencedColumns").removeAll();
        assertThrows(IllegalArgumentException.class, () -> read(foreignKey));

        ObjectNode index = validRoot();
        addIndex(index, "idx_orders_customer", "customer_id", "8", 1);
        ((ObjectNode) index.path("metadataInventory").path("indexes").get(0))
                .withArray("subParts").add("");
        assertThrows(IllegalArgumentException.class, () -> read(index));
    }

    @Test
    void acceptsOrderedMixedIndexMembersAndRejectsAmbiguousLegacyMixedShape() throws Exception {
        ObjectNode ordered = validRoot();
        ObjectNode inventory = (ObjectNode) ordered.path("metadataInventory");
        ObjectNode index = inventory.withArray("indexes").addObject();
        index.put("catalog", "shop");
        index.putNull("schema");
        index.put("tableName", "orders");
        index.put("indexName", "idx_orders_mixed");
        index.put("unique", false);
        index.put("primary", false);
        index.put("indexType", "BTREE");
        index.put("visible", true);
        index.putArray("columns").add("customer_id");
        index.putArray("expressions").add("lower(customer_id::text)");
        index.putArray("subParts");
        index.putArray("seqInIndex").add(2);
        ObjectNode expression = index.putArray("members").addObject();
        expression.put("ordinal", 1);
        expression.put("kind", "EXPRESSION");
        expression.putNull("columnName");
        expression.put("expression", "lower(customer_id::text)");
        expression.putNull("prefixLength");
        ObjectNode column = index.withArray("members").addObject();
        column.put("ordinal", 2);
        column.put("kind", "FULL_COLUMN");
        column.put("columnName", "customer_id");
        column.putNull("expression");
        column.putNull("prefixLength");
        ((ObjectNode) inventory.path("counts")).put("indexes", 1);

        assertDoesNotThrow(() -> read(ordered));

        ObjectNode ambiguous = ordered.deepCopy();
        ((ObjectNode) ambiguous.path("metadataInventory").path("indexes").get(0)).remove("members");
        assertThrows(IllegalArgumentException.class, () -> read(ambiguous));
    }

    @Test
    void rejectsDuplicateConstraintAndIndexIdentity() throws Exception {
        ObjectNode constraints = validRoot();
        addForeignKey(constraints, "fk_orders_customer", "customer_id", "customers", "id");
        addForeignKey(constraints, "fk_orders_customer", "customer_id", "customers", "id");
        assertThrows(IllegalArgumentException.class, () -> read(constraints));

        ObjectNode indexes = validRoot();
        addIndex(indexes, "idx_orders_customer", "customer_id", "", 1);
        addIndex(indexes, "idx_orders_customer", "customer_id", "", 1);
        assertThrows(IllegalArgumentException.class, () -> read(indexes));
    }

    @Test
    void rejectsReferencedFieldsOnNonForeignKeyConstraint() throws Exception {
        ObjectNode root = validRoot();
        addForeignKey(root, "uq_orders_customer", "customer_id", "customers", "id");
        ((ObjectNode) root.path("metadataInventory").path("constraints").get(0))
                .put("constraintType", "UNIQUE");

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

    private void addForeignKey(
            ObjectNode root,
            String name,
            String sourceColumn,
            String targetTable,
            String targetColumn
    ) {
        ObjectNode inventory = (ObjectNode) root.path("metadataInventory");
        ObjectNode constraint = inventory.withArray("constraints").addObject();
        constraint.put("catalog", "shop");
        constraint.putNull("schema");
        constraint.put("tableName", "orders");
        constraint.put("constraintName", name);
        constraint.put("constraintType", "FOREIGN KEY");
        constraint.putArray("columns").add(sourceColumn);
        constraint.put("referencedCatalog", "shop");
        constraint.putNull("referencedSchema");
        constraint.put("referencedTable", targetTable);
        constraint.putArray("referencedColumns").add(targetColumn);
        constraint.put("updateRule", "NO ACTION");
        constraint.put("deleteRule", "NO ACTION");
        ((ObjectNode) inventory.path("counts")).put(
                "constraints", inventory.path("constraints").size());
    }

    private void addIndex(
            ObjectNode root,
            String name,
            String column,
            String subPart,
            int ordinal
    ) {
        ObjectNode inventory = (ObjectNode) root.path("metadataInventory");
        ObjectNode index = inventory.withArray("indexes").addObject();
        index.put("catalog", "shop");
        index.putNull("schema");
        index.put("tableName", "orders");
        index.put("indexName", name);
        index.put("unique", false);
        index.put("primary", false);
        index.put("indexType", "BTREE");
        index.put("visible", true);
        index.putArray("columns").add(column);
        index.putArray("expressions");
        index.putArray("subParts").add(subPart);
        index.putArray("seqInIndex").add(ordinal);
        ((ObjectNode) inventory.path("counts")).put(
                "indexes", inventory.path("indexes").size());
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
                  "metadataInventory": {
                    "status": "COMPLETE",
                    "scope": {
                      "catalog": "shop",
                      "schema": "",
                      "includeTables": [],
                      "excludeTables": []
                    },
                    "counts": {
                      "tables": 2,
                      "columns": 2,
                      "constraints": 0,
                      "indexes": 0
                    },
                    "tables": [
                      {"catalog": "shop", "schema": null, "tableName": "orders",
                       "tableType": "TABLE", "engine": null, "comment": null},
                      {"catalog": "shop", "schema": null, "tableName": "customers",
                       "tableType": "TABLE", "engine": null, "comment": null}
                    ],
                    "columns": [
                      {"catalog": "shop", "schema": null, "tableName": "orders",
                       "columnName": "customer_id", "dataType": "bigint", "columnType": "bigint",
                       "nullable": false, "defaultValue": null, "extra": "",
                       "generationExpression": "", "ordinalPosition": 1},
                      {"catalog": "shop", "schema": null, "tableName": "customers",
                       "columnName": "id", "dataType": "bigint", "columnType": "bigint",
                       "nullable": false, "defaultValue": null, "extra": "",
                       "generationExpression": "", "ordinalPosition": 1}
                    ],
                    "constraints": [],
                    "indexes": []
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
