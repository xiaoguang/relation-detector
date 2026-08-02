package com.relationdetector.semantic.ingest;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

final class ScanResultContractValidatorTest {
    private static final ObjectMapper JSON = new ObjectMapper();

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
    void rejectsCompleteMetadataInventoryWithoutEvidenceBasis() throws Exception {
        ObjectNode root = validRoot();
        ((ObjectNode) root.path("metadataInventory")).put("basis", "NONE");

        assertThrows(IllegalArgumentException.class, () -> read(root));
    }

    @Test
    void rejectsMetadataInventoryCountMismatch() throws Exception {
        ObjectNode root = validRoot();
        ((ObjectNode) root.path("metadataInventory").path("counts")).put("tables", 3);

        assertThrows(IllegalArgumentException.class, () -> read(root));
    }

    @Test
    void acceptsTypedCompleteMetadataInventory() throws Exception {
        ObjectNode root = validRoot();

        assertDoesNotThrow(() -> read(root));
        assertEquals("COMPLETE", root.path("metadataInventory").path("status").asText());
        assertEquals("DDL_DECLARATIONS", root.path("metadataInventory").path("basis").asText());
        assertEquals("shop", root.path("metadataInventory").path("scope").path("catalog").asText());
        assertEquals(2, root.path("metadataInventory").path("tables").size());
        assertEquals(2, root.path("metadataInventory").path("columns").size());
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
        addEvidenceSet(derived);
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

    @Test
    void rejectsLegacyRawEvidenceAndInvalidEvidenceSetCombinationCount() throws Exception {
        ObjectNode legacy = derivedLineageRoot();
        ((ObjectNode) legacy.path("derivedDataLineages").get(0)).putArray("rawEvidence");
        assertThrows(IllegalArgumentException.class, () -> read(legacy));

        ObjectNode invalid = derivedLineageRoot();
        ((ObjectNode) invalid.path("derivedDataLineages").get(0)
                .path("evidenceSets").get(0)).put("combinationCount", 9);
        assertThrows(IllegalArgumentException.class, () -> read(invalid));
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
        addEvidenceSet(derived);
        derived.putObject("attributes");
        ((ObjectNode) root.path("summary"))
                .put("derivedDataLineageCount", 1)
                .put("totalDataLineageCount", 1);
        return root;
    }

    private void addEvidenceSet(ObjectNode derived) {
        ObjectNode set = derived.putArray("evidenceSets").addObject();
        var hops = set.putArray("hops");
        ObjectNode first = hops.addObject();
        first.put("ordinal", 1);
        first.set("source", derived.path("path").get(0).deepCopy());
        first.set("target", derived.path("path").get(1).deepCopy());
        first.put("kind", "LINEAGE");
        first.putArray("evidenceRefs").add("lineage:first");
        ObjectNode second = hops.addObject();
        second.put("ordinal", 2);
        second.set("source", derived.path("path").get(1).deepCopy());
        second.set("target", derived.path("path").get(2).deepCopy());
        second.put("kind", "LINEAGE");
        second.putArray("evidenceRefs").add("lineage:second");
        set.put("combinationCount", 1);
        set.put("confidence", derived.path("confidence").decimalValue());
    }

    private void read(ObjectNode root) {
        new ScanResultContractValidator().validate(root);
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
                    "basis": "DDL_DECLARATIONS",
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
