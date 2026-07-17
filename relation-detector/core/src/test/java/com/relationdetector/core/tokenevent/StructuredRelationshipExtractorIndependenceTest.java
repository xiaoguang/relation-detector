package com.relationdetector.core.tokenevent;

import com.relationdetector.core.lineage.*;
import com.relationdetector.core.relation.*;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.contracts.parse.ExpressionSource;
import com.relationdetector.contracts.parse.ExpressionTrace;
import com.relationdetector.contracts.parse.PredicateEvent;
import com.relationdetector.contracts.parse.PredicateGuard;
import com.relationdetector.contracts.parse.RowsetEvent;
import com.relationdetector.contracts.parse.SourceProvenance;
import com.relationdetector.contracts.parse.WriteEvent;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.Enums.RelationType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.spi.IdentifierRules;
import com.relationdetector.core.identity.NamespaceContext;

/**
 * Guards the most important token-event extraction boundary.
 *
 * <p>These tests deliberately give {@link StructuredRelationshipExtractor} structured
 * events that do not match the raw SQL text. Relationship extraction must stay
 * inside the token-event pipeline; empty events must not trigger raw SQL
 * rescan or any removed parser.
 */
class StructuredRelationshipExtractorIndependenceTest {

    @Test
    void foldsLocalTemporaryInSubqueryToUniqueDirectPhysicalSource() {
        SqlStatementRecord statement = record(
                "typed routine with local temporary bridge",
                Map.of("localTempTables", List.of("tmp_available_categories")));
        StructuredParseResult structured = structured(List.of(
                new RowsetEvent(StructuredParseEventType.LOCAL_TEMP_TABLE_DECLARATION,
                        scopedProvenance(1, "declare"), "CREATE TEMPORARY TABLE",
                        "tmp_available_categories", "tmp_available_categories", "",
                        "tmp_available_categories", "", ""),
                scopedTable("FROM", "jsh_material", "m", 2, "populate"),
                new WriteEvent(StructuredParseEventType.INSERT_SELECT_MAPPING,
                        scopedProvenance(2, "populate"), "", "", "", "",
                        "tmp_available_categories", "cat_id", "INSERT_SELECT",
                        ExpressionTrace.of(List.of("m"), List.of("category_id"),
                                LineageFlowKind.VALUE, LineageTransformType.DIRECT)),
                scopedTable("FROM", "jsh_temp_category_pdf", "pdf", 3, "consume"),
                new PredicateEvent(StructuredParseEventType.IN_SUBQUERY_PREDICATE,
                        scopedProvenance(3, "consume"),
                        new ExpressionSource("pdf", "id"),
                        new ExpressionSource("", "cat_id"),
                        List.of(new ExpressionSource("pdf", "id")),
                        List.of(new ExpressionSource("", "cat_id")),
                        "tmp_available_categories", "IN_SUBQUERY", List.of(), true)));

        List<RelationshipCandidate> relations = new StructuredRelationshipExtractor()
                .extract(statement, structured);

        assertEquals(1, relations.size(), () -> "Expected folded physical relation, got: " + relations);
        assertEquals("jsh_material.category_id", relations.get(0).source().displayName());
        assertEquals("jsh_temp_category_pdf.id", relations.get(0).target().displayName());
        assertEquals(EvidenceType.SQL_LOG_SUBQUERY_IN, relations.get(0).evidence().get(0).type());
    }

    @Test
    void foldsRecursiveDirectLocalTemporaryCopiesToPhysicalSource() {
        SqlStatementRecord statement = record("typed recursive local bridge",
                Map.of("localTempTables", List.of("tmp_first", "tmp_second")));
        StructuredParseResult structured = structured(List.of(
                localTemp("tmp_first", 1, "declare-first"),
                scopedTable("FROM", "jsh_material", "m", 2, "populate-first"),
                directWrite("tmp_first", "category_id", "m", "category_id", 2, "populate-first"),
                localTemp("tmp_second", 3, "declare-second"),
                scopedTable("FROM", "tmp_first", "f", 4, "populate-second"),
                directWrite("tmp_second", "category_id", "f", "category_id", 4, "populate-second"),
                scopedTable("FROM", "jsh_temp_category_pdf", "pdf", 5, "consume"),
                inPredicate("pdf", "id", "tmp_second", "category_id", 5, "consume")));

        List<RelationshipCandidate> relations = new StructuredRelationshipExtractor()
                .extract(statement, structured);

        assertEquals(1, relations.size(), () -> "Expected recursive folded relation, got: " + relations);
        assertEquals("jsh_material.category_id", relations.get(0).source().displayName());
        assertEquals("jsh_temp_category_pdf.id", relations.get(0).target().displayName());
    }

    @Test
    void nonDirectWriteMakesLocalTemporaryColumnAmbiguous() {
        SqlStatementRecord statement = record("typed mixed local bridge",
                Map.of("localTempTables", List.of("tmp_ids")));
        StructuredParseResult structured = structured(List.of(
                localTemp("tmp_ids", 1, "declare"),
                scopedTable("FROM", "jsh_material", "m", 2, "populate-direct"),
                directWrite("tmp_ids", "category_id", "m", "category_id", 2, "populate-direct"),
                scopedTable("FROM", "jsh_material", "m", 3, "populate-expression"),
                new WriteEvent(StructuredParseEventType.INSERT_SELECT_MAPPING,
                        scopedProvenance(3, "populate-expression"), "", "", "", "",
                        "tmp_ids", "category_id", "INSERT_SELECT",
                        ExpressionTrace.of(List.of("m"), List.of("category_id"),
                                LineageFlowKind.VALUE, LineageTransformType.ARITHMETIC)),
                scopedTable("FROM", "jsh_temp_category_pdf", "pdf", 4, "consume"),
                inPredicate("pdf", "id", "tmp_ids", "category_id", 4, "consume")));

        assertTrue(new StructuredRelationshipExtractor().extract(statement, structured).isEmpty(),
                "A transformed write must prevent direct local-rowset folding");
    }

    @Test
    void redeclaringLocalTemporaryTableResetsEarlierProjectionSource() {
        SqlStatementRecord statement = record("typed redeclared local bridge",
                Map.of("localTempTables", List.of("tmp_ids")));
        StructuredParseResult structured = structured(List.of(
                localTemp("tmp_ids", 1, "declare-first"),
                scopedTable("FROM", "jsh_material", "m", 2, "populate-first"),
                directWrite("tmp_ids", "category_id", "m", "category_id", 2, "populate-first"),
                localTemp("tmp_ids", 3, "declare-second"),
                scopedTable("FROM", "jsh_temp_category_pdf", "pdf", 4, "consume"),
                inPredicate("pdf", "id", "tmp_ids", "category_id", 4, "consume")));

        assertTrue(new StructuredRelationshipExtractor().extract(statement, structured).isEmpty(),
                "A new local-table declaration must discard mappings from the previous lifetime");
    }

    @Test
    void foldsTupleInLocalTemporaryColumnToPhysicalSource() {
        SqlStatementRecord statement = record("typed tuple local bridge",
                Map.of("localTempTables", List.of("tmp_ids")));
        StructuredParseResult structured = structured(List.of(
                localTemp("tmp_ids", 1, "declare"),
                scopedTable("FROM", "jsh_material", "m", 2, "populate"),
                directWrite("tmp_ids", "category_id", "m", "category_id", 2, "populate"),
                scopedTable("FROM", "jsh_temp_category_pdf", "pdf", 3, "consume"),
                new PredicateEvent(StructuredParseEventType.TUPLE_IN_SUBQUERY_PREDICATE,
                        scopedProvenance(3, "consume"), ExpressionSource.EMPTY, ExpressionSource.EMPTY,
                        List.of(new ExpressionSource("pdf", "id")),
                        List.of(new ExpressionSource("", "category_id")),
                        "tmp_ids", "TUPLE_IN_SUBQUERY", List.of(), true)));

        List<RelationshipCandidate> relations = new StructuredRelationshipExtractor()
                .extract(statement, structured);

        assertEquals(1, relations.size(), () -> "Expected tuple-IN folded relation, got: " + relations);
        assertEquals("jsh_material.category_id", relations.get(0).source().displayName());
        assertEquals("jsh_temp_category_pdf.id", relations.get(0).target().displayName());
    }

    @Test
    void multiplePhysicalSourcesMakeLocalTemporaryColumnAmbiguous() {
        SqlStatementRecord statement = record("typed ambiguous local bridge",
                Map.of("localTempTables", List.of("tmp_ids")));
        StructuredParseResult structured = structured(List.of(
                localTemp("tmp_ids", 1, "declare"),
                scopedTable("FROM", "jsh_material", "m", 2, "populate-material"),
                directWrite("tmp_ids", "category_id", "m", "category_id", 2, "populate-material"),
                scopedTable("FROM", "product_categories", "pc", 3, "populate-category"),
                directWrite("tmp_ids", "category_id", "pc", "id", 3, "populate-category"),
                scopedTable("FROM", "jsh_temp_category_pdf", "pdf", 4, "consume"),
                inPredicate("pdf", "id", "tmp_ids", "category_id", 4, "consume")));

        assertTrue(new StructuredRelationshipExtractor().extract(statement, structured).isEmpty(),
                "Different physical VALUE sources must make the local projection ambiguous");
    }

    @Test
    void writeAfterPredicateDoesNotBackfillLocalTemporarySource() {
        SqlStatementRecord statement = record("typed future local bridge",
                Map.of("localTempTables", List.of("tmp_ids")));
        StructuredParseResult structured = structured(List.of(
                localTemp("tmp_ids", 1, "declare"),
                scopedTable("FROM", "jsh_temp_category_pdf", "pdf", 2, "consume"),
                inPredicate("pdf", "id", "tmp_ids", "category_id", 2, "consume"),
                scopedTable("FROM", "jsh_material", "m", 3, "populate-later"),
                directWrite("tmp_ids", "category_id", "m", "category_id", 3, "populate-later")));

        assertTrue(new StructuredRelationshipExtractor().extract(statement, structured).isEmpty(),
                "A later write cannot establish the source of an earlier predicate");
    }

    @Test
    void recursiveLocalCopyThatReturnsToSamePhysicalColumnStaysSuppressed() {
        SqlStatementRecord statement = record("typed self local bridge",
                Map.of("localTempTables", List.of("tmp_first", "tmp_second")));
        StructuredParseResult structured = structured(List.of(
                localTemp("tmp_first", 1, "declare-first"),
                scopedTable("FROM", "jsh_material", "m", 2, "populate-first"),
                directWrite("tmp_first", "material_id", "m", "id", 2, "populate-first"),
                localTemp("tmp_second", 3, "declare-second"),
                scopedTable("FROM", "tmp_first", "f", 4, "populate-second"),
                directWrite("tmp_second", "material_id", "f", "material_id", 4, "populate-second"),
                scopedTable("FROM", "jsh_material", "m2", 5, "consume"),
                inPredicate("m2", "id", "tmp_second", "material_id", 5, "consume")));

        assertTrue(new StructuredRelationshipExtractor().extract(statement, structured).isEmpty(),
                "A folded physical self relation must remain suppressed");
    }

    @Test
    void localProjectionCycleDoesNotProduceRelationship() {
        SqlStatementRecord statement = record("typed cyclic local bridge",
                Map.of("localTempTables", List.of("tmp_first", "tmp_second")));
        StructuredParseResult structured = structured(List.of(
                localTemp("tmp_first", 1, "declare-first"),
                localTemp("tmp_second", 1, "declare-second"),
                scopedTable("FROM", "tmp_second", "s", 2, "copy-to-first"),
                directWrite("tmp_first", "id", "s", "id", 2, "copy-to-first"),
                scopedTable("FROM", "tmp_first", "f", 3, "copy-to-second"),
                directWrite("tmp_second", "id", "f", "id", 3, "copy-to-second"),
                scopedTable("FROM", "jsh_temp_category_pdf", "pdf", 4, "consume"),
                inPredicate("pdf", "id", "tmp_second", "id", 4, "consume")));

        assertTrue(new StructuredRelationshipExtractor().extract(statement, structured).isEmpty(),
                "A local projection cycle must remain unresolved");
    }

    @Test
    void transformedLocalWritesNeverBecomePhysicalRelationshipBridges() {
        for (LineageTransformType transform : List.of(
                LineageTransformType.FUNCTION_CALL,
                LineageTransformType.ARITHMETIC,
                LineageTransformType.AGGREGATE,
                LineageTransformType.CASE_WHEN,
                LineageTransformType.COALESCE)) {
            SqlStatementRecord statement = record("typed transformed local bridge " + transform,
                    Map.of("localTempTables", List.of("tmp_ids")));
            StructuredParseResult structured = structured(List.of(
                    localTemp("tmp_ids", 1, "declare"),
                    scopedTable("FROM", "jsh_material", "m", 2, "populate"),
                    new WriteEvent(StructuredParseEventType.INSERT_SELECT_MAPPING,
                            scopedProvenance(2, "populate"), "", "", "", "",
                            "tmp_ids", "category_id", "INSERT_SELECT",
                            ExpressionTrace.of(List.of("m"), List.of("category_id"),
                                    LineageFlowKind.VALUE, transform)),
                    scopedTable("FROM", "jsh_temp_category_pdf", "pdf", 3, "consume"),
                    inPredicate("pdf", "id", "tmp_ids", "category_id", 3, "consume")));

            assertTrue(new StructuredRelationshipExtractor().extract(statement, structured).isEmpty(),
                    () -> transform + " must not be treated as a direct local-rowset projection");
        }
    }

    @Test
    void explicitlyNamespacedTableIsNotShadowedByBareLocalTemporaryName() {
        SqlStatementRecord statement = record("typed namespaced physical table",
                Map.of("localTempTables", List.of("tmp_ids")));
        StructuredParseResult structured = structured(List.of(
                localTemp("tmp_ids", 1, "declare"),
                scopedTable("FROM", "jsh_temp_category_pdf", "pdf", 2, "consume"),
                scopedTable("FROM", "shop.tmp_ids", "stored", 2, "consume"),
                new PredicateEvent(StructuredParseEventType.IN_SUBQUERY_PREDICATE,
                        scopedProvenance(2, "consume"),
                        new ExpressionSource("pdf", "id"),
                        new ExpressionSource("stored", "category_id"),
                        List.of(new ExpressionSource("pdf", "id")),
                        List.of(new ExpressionSource("stored", "category_id")),
                        "shop.tmp_ids", "IN_SUBQUERY", List.of(), true)));

        List<RelationshipCandidate> relations = new StructuredRelationshipExtractor()
                .extract(statement, structured);

        assertEquals(1, relations.size());
        assertEquals("jsh_temp_category_pdf.id", relations.get(0).source().displayName());
        assertEquals("shop.tmp_ids.category_id", relations.get(0).target().displayName());
    }

    @Test
    void resolvesTypedPredicateGuardIntoConditionalRelationshipEvidence() {
        List<StructuredSqlEvent> events = List.of(
                table("FROM", "contracts", "c", 10),
                table("FROM", "customers", "cu", 11),
                new PredicateEvent(StructuredParseEventType.PREDICATE_EQUALITY,
                        provenance(12), new ExpressionSource("c", "party_id"),
                        new ExpressionSource("cu", "id"), List.of(), List.of(),
                        "", "SCALAR_SUBQUERY", List.of(), false,
                        List.of(new PredicateGuard(
                                new ExpressionSource("c", "party_type"), "EQUALS", "customer"))));

        RelationshipCandidate candidate = new StructuredRelationshipExtractor()
                .extract(record("typed conditional query"), structured(events)).get(0);

        Evidence evidence = candidate.evidence().get(0);
        assertEquals(true, evidence.attributes().get("conditional"));
        assertEquals("contracts.party_type", evidence.attributes().get("discriminatorEndpoint"));
        assertEquals("EQUALS", evidence.attributes().get("discriminatorOperator"));
        assertEquals("customer", evidence.attributes().get("discriminatorValue"));
    }
    @Test
    void triggerPseudoRowsetAliasInFromClauseKeepsPhysicalBinding() {
        StructuredParseResult structured = structured(List.of(
                new RowsetEvent(StructuredParseEventType.TRIGGER_PSEUDO_ROWSET, provenance(1),
                        "TRIGGER", "dbo.inventory", "inventory", "inserted", "inserted",
                        "dbo.inventory", ""),
                table("FROM", "inserted", "i", 2),
                table("JOIN", "dbo.product_batches", "pb", 2),
                equality("i", "batch_id", "pb", "id", 2)));

        List<RelationshipCandidate> relations = new StructuredRelationshipExtractor()
                .extract(record("SELECT 1"), structured);

        assertEquals(1, relations.size());
        assertEquals("dbo.inventory.batch_id", relations.get(0).source().displayName());
        assertEquals("dbo.product_batches.id", relations.get(0).target().displayName());
    }

    @Test
    void extractsRelationsFromStructuredEventsWhenRawSqlHasNoJoin() {
        SqlStatementRecord statement = record("SELECT 1");
        StructuredParseResult structured = structured(List.of(
                table("FROM", "orders", "o", 1),
                table("JOIN", "users", "u", 1),
                equality("o", "user_id", "u", "id", 1)
        ));

        List<RelationshipCandidate> relations = new StructuredRelationshipExtractor().extract(statement, structured);

        assertEquals(1, relations.size(), () -> "Expected event-derived relation, got: " + relations);
        RelationshipCandidate relation = relations.get(0);
        assertEquals(RelationType.CO_OCCURRENCE, relation.relationType());
        assertEquals("orders.user_id", relation.source().displayName());
        assertEquals("users.id", relation.target().displayName());
        assertEquals(EvidenceType.SQL_LOG_JOIN, relation.evidence().get(0).type());
        assertEquals("typed column equality", relation.evidence().get(0).detail());
    }

    @Test
    void copiesStatementAndEventProvenanceIntoRelationshipEvidence() {
        SqlStatementRecord statement = new SqlStatementRecord(
                "SELECT * FROM orders o JOIN users u ON o.user_id = u.id",
                StatementSourceType.PROCEDURE,
                "ROUTINE:sp_sync_orders",
                20,
                28,
                Map.of(
                        "sourceFile", "sample-data/mysql/8.0/02-procedures/sync.sql",
                        "sourceStatementId", "sp_sync_orders",
                        "sourceBlockId", "procedure:sp_sync_orders:1",
                        "sourceObjectType", "ROUTINE",
                        "sourceObjectName", "sp_sync_orders"));
        StructuredParseResult structured = structured(List.of(
                table("FROM", "orders", "o", 24),
                table("JOIN", "users", "u", 24),
                equality("o", "user_id", "u", "id", 24)
        ));

        var evidence = new StructuredRelationshipExtractor().extract(statement, structured)
                .get(0).evidence().get(0);

        assertEquals("sample-data/mysql/8.0/02-procedures/sync.sql",
                evidence.attributes().get("sourceFile"));
        assertEquals("sp_sync_orders", evidence.attributes().get("sourceStatementId"));
        assertEquals("procedure:sp_sync_orders:1", evidence.attributes().get("sourceBlockId"));
        assertEquals("ROUTINE", evidence.attributes().get("sourceObjectType"));
        assertEquals("sp_sync_orders", evidence.attributes().get("sourceObjectName"));
        assertEquals(24L, evidence.attributes().get("sourceLine"));
    }

    @Test
    void ambientPredicateCopiedAcrossScopesProducesOneObservation() {
        SqlStatementRecord statement = record("SELECT 1");
        StructuredParseResult structured = structured(List.of(
                table("FROM", "orders", "o", 4),
                table("JOIN", "users", "u", 4),
                equality("o", "user_id", "u", "id", 4),
                scopedTable("FROM", "audit_log", "a", 8, "scope-1"),
                scopedTable("FROM", "order_items", "i", 12, "scope-2")
        ));

        List<RelationshipCandidate> relations = new StructuredRelationshipExtractor().extract(statement, structured);

        assertEquals(1, relations.size(),
                () -> "An ambient predicate copied into multiple scopes is still one SQL observation: " + relations);
    }

    @Test
    void sameEndpointAtDifferentSourceLinesKeepsBothObservations() {
        SqlStatementRecord statement = record("SELECT 1");
        StructuredParseResult structured = structured(List.of(
                table("FROM", "orders", "o", 4),
                table("JOIN", "users", "u", 4),
                equality("o", "user_id", "u", "id", 4),
                equality("o", "user_id", "u", "id", 9)
        ));

        List<RelationshipCandidate> relations = new StructuredRelationshipExtractor().extract(statement, structured);

        assertEquals(2, relations.size(),
                () -> "Equal endpoint facts observed at distinct SQL positions must remain separately auditable: " + relations);
    }

    @Test
    void sqlOnlyIdShapeDoesNotInferFkDirection() {
        SqlStatementRecord statement = record("SELECT * FROM invoices i JOIN accounts a ON i.account_id = a.id");
        StructuredParseResult structured = structured(List.of(
                table("FROM", "invoices", "i", 1),
                table("JOIN", "accounts", "a", 1),
                equality("i", "account_id", "a", "id", 1)
        ));

        List<RelationshipCandidate> relations = new StructuredRelationshipExtractor().extract(statement, structured);

        assertEquals(1, relations.size(), () -> "Expected SQL-only equality to remain as column co-occurrence: " + relations);
        RelationshipCandidate relation = relations.get(0);
        assertEquals(RelationType.CO_OCCURRENCE, relation.relationType());
        assertEquals(EvidenceType.SQL_LOG_JOIN, relation.evidence().get(0).type());
        assertEquals("accounts.id", relation.source().displayName());
        assertEquals("invoices.account_id", relation.target().displayName());
    }

    @Test
    void nonDirectionalOutputOrderDoesNotDependOnDialectCaseFolding() {
        SqlStatementRecord statement = record("SELECT * FROM employees e JOIN employee_salary_log esl ON e.id = esl.approved_by");
        StructuredParseResult structured = structured(List.of(
                table("FROM", "employees", "e", 1),
                table("JOIN", "employee_salary_log", "esl", 1),
                equality("e", "id", "esl", "approved_by", 1)
        ));

        RelationshipCandidate relation = new StructuredRelationshipExtractor(
                value -> value == null ? "" : value.toUpperCase(Locale.ROOT))
                .extract(statement, structured).get(0);

        assertEquals("employee_salary_log.approved_by", relation.source().displayName());
        assertEquals("employees.id", relation.target().displayName());
    }

    @Test
    void sameAliasSameColumnEqualityDoesNotProduceCoOccurrence() {
        SqlStatementRecord statement = record("SELECT * FROM employees e WHERE e.id = e.id");
        StructuredParseResult structured = structured(List.of(
                table("FROM", "employees", "e", 1),
                equality("e", "id", "e", "id", 1)
        ));

        List<RelationshipCandidate> relations = new StructuredRelationshipExtractor().extract(statement, structured);

        assertTrue(relations.isEmpty(), () -> "Same alias self-comparison must not produce a relation: " + relations);
    }

    @Test
    void differentAliasSamePhysicalColumnSelfJoinKeepsRoleCoOccurrence() {
        SqlStatementRecord statement = record("SELECT * FROM employees e JOIN employees manager ON e.id = manager.id");
        StructuredParseResult structured = structured(List.of(
                table("FROM", "employees", "e", 1),
                table("JOIN", "employees", "manager", 1),
                equality("e", "id", "manager", "id", 1)
        ));

        List<RelationshipCandidate> relations = new StructuredRelationshipExtractor().extract(statement, structured);

        assertEquals(1, relations.size(), () -> "Different aliases of the same physical column still express a self-join role: " + relations);
        RelationshipCandidate relation = relations.get(0);
        assertEquals(RelationType.CO_OCCURRENCE, relation.relationType());
        assertEquals("employees.id", relation.source().displayName());
        assertEquals("employees.id", relation.target().displayName());
        assertEquals(true, relation.evidence().get(0).attributes().get("selfJoinRole"));
        assertEquals("e", relation.evidence().get(0).attributes().get("leftAlias"));
        assertEquals("manager", relation.evidence().get(0).attributes().get("rightAlias"));
    }

    @Test
    void differentAliasSamePhysicalColumnInSubqueryKeepsRoleCoOccurrence() {
        SqlStatementRecord statement = record("SELECT * FROM employees e WHERE e.id IN (SELECT e2.id FROM employees e2)");
        StructuredParseResult structured = structured(List.of(
                table("FROM", "employees", "e", 1),
                table("FROM", "employees", "e2", 1),
                new PredicateEvent(StructuredParseEventType.IN_SUBQUERY_PREDICATE,
                        provenance(1), new ExpressionSource("e", "id"),
                        new ExpressionSource("e2", "id"),
                        List.of(new ExpressionSource("e", "id")),
                        List.of(new ExpressionSource("e2", "id")),
                        "", "", List.of(), true)
        ));

        List<RelationshipCandidate> relations = new StructuredRelationshipExtractor().extract(statement, structured);

        assertEquals(1, relations.size(), () -> "Different aliases of the same physical column should keep role context through IN: " + relations);
        assertEquals(EvidenceType.SQL_LOG_SUBQUERY_IN, relations.get(0).evidence().get(0).type());
        assertEquals(true, relations.get(0).evidence().get(0).attributes().get("selfJoinRole"));
    }

    @Test
    void ambiguousAliasDoesNotResolveAcrossIndependentQueryBlocks() {
        SqlStatementRecord statement = record("""
                WITH sales_stats AS (
                    SELECT soi.product_id
                    FROM sales_order_items soi
                    JOIN products p ON soi.product_id = p.id
                )
                SELECT e.id, p.name
                FROM employees e
                JOIN positions p ON e.position_id = p.id
                """);
        StructuredParseResult structured = structured(List.of(
                table("FROM", "sales_order_items", "soi", 1),
                table("JOIN", "products", "p", 1),
                table("FROM", "employees", "e", 6),
                table("JOIN", "positions", "p", 7),
                equality("soi", "product_id", "p", "id", 3),
                equality("e", "position_id", "p", "id", 7)
        ));

        List<RelationshipCandidate> relations = new StructuredRelationshipExtractor().extract(statement, structured);

        assertEquals(2, relations.size(),
                () -> "Alias p should resolve to the nearest query-block rowset without cross-block leakage: " + relations);
        assertTrue(relations.stream().anyMatch(relation ->
                        relation.source().displayName().equals("products.id")
                                && relation.target().displayName().equals("sales_order_items.product_id")),
                () -> "CTE predicate should resolve p to products: " + relations);
        assertTrue(relations.stream().anyMatch(relation ->
                        relation.source().displayName().equals("employees.position_id")
                                && relation.target().displayName().equals("positions.id")),
                () -> "Outer predicate should resolve p to positions: " + relations);
        assertTrue(relations.stream().noneMatch(relation ->
                        relation.source().displayName().equals("positions.id")
                                && relation.target().displayName().equals("sales_order_items.product_id")),
                () -> "Alias p must not leak from the outer query into the CTE predicate: " + relations);
    }

    @Test
    void resolvesEquivalentQualifiedAndUnqualifiedBindingsWithinNamespace() {
        StructuredParseResult structured = structured(List.of(
                table("FROM", "orders", "o", 1),
                table("FROM", "catalog_a.sales.orders", "o", 1),
                table("JOIN", "customers", "c", 1),
                equality("o", "customer_id", "c", "id", 1)
        ));

        List<RelationshipCandidate> relations = new StructuredRelationshipExtractor(
                value -> value == null ? "" : value.toLowerCase(Locale.ROOT),
                new NamespaceContext("catalog_a", "sales", List.of()))
                .extract(record("SELECT 1"), structured);

        assertEquals(1, relations.size(),
                () -> "Equivalent bindings must be deduplicated by canonical namespace identity: " + relations);
        assertEquals(List.of(
                        "catalog_a.sales.customers.id",
                        "catalog_a.sales.orders.customer_id"),
                List.of(relations.get(0).source().displayName(), relations.get(0).target().displayName()));
    }

    @Test
    void treatsSameAliasAcrossCatalogsAsAmbiguous() {
        StructuredParseResult structured = structured(List.of(
                table("FROM", "catalog_a.sales.orders", "o", 1),
                table("FROM", "catalog_b.sales.orders", "o", 1),
                table("JOIN", "catalog_a.sales.customers", "c", 1),
                equality("o", "customer_id", "c", "id", 1)
        ));

        List<RelationshipCandidate> relations = new StructuredRelationshipExtractor(
                value -> value == null ? "" : value.toLowerCase(Locale.ROOT),
                NamespaceContext.empty())
                .extract(record("SELECT 1"), structured);

        assertTrue(relations.isEmpty(),
                () -> "A reused alias cannot select one of two same-named tables from different catalogs: "
                        + relations);
    }

    @Test
    void emptyStructuredEventsDoNotRescanRawSql() {
        SqlStatementRecord statement = record("SELECT * FROM orders o JOIN users u ON o.user_id = u.id");
        StructuredParseResult structured = structured(List.of());

        List<RelationshipCandidate> relations = new StructuredRelationshipExtractor().extract(statement, structured);

        assertTrue(relations.isEmpty(), () -> "Empty token-event events must not parse raw SQL: " + relations);
    }

    @Test
    void statementScopedLocalTemporaryTablesAreNotRelationshipEndpoints() {
        SqlStatementRecord statement = record(
                "INSERT INTO order_facts SELECT tr.customer_id FROM tmp_rollup tr JOIN customers c ON tr.customer_id = c.id",
                Map.of("localTempTables", List.of("tmp_rollup")));
        StructuredParseResult structured = structured(List.of(
                table("FROM", "tmp_rollup", "tr", 1),
                table("JOIN", "customers", "c", 1),
                equality("tr", "customer_id", "c", "id", 1)
        ));

        List<RelationshipCandidate> relations = new StructuredRelationshipExtractor().extract(statement, structured);

        assertTrue(relations.isEmpty(),
                () -> "Procedure-local temporary tables must not become relationship endpoints: " + relations);
    }

    @Test
    void namespaceQualificationDoesNotPromoteLocalTemporaryTablesToPhysicalEndpoints() {
        SqlStatementRecord statement = record(
                "SELECT * FROM tmp_rollup tr JOIN customers c ON tr.customer_id = c.id",
                Map.of("localTempTables", List.of("tmp_rollup")));
        StructuredParseResult structured = structured(List.of(
                table("FROM", "tmp_rollup", "tr", 1),
                table("JOIN", "customers", "c", 1),
                equality("tr", "customer_id", "c", "id", 1)
        ));

        List<RelationshipCandidate> relations = new StructuredRelationshipExtractor(
                value -> value == null ? "" : value.toLowerCase(Locale.ROOT),
                new NamespaceContext("", "shop", List.of()))
                .extract(statement, structured);

        assertTrue(relations.isEmpty(),
                () -> "A scan namespace must not turn a local temporary table into a physical endpoint: "
                        + relations);
    }

    @Test
    void explicitlyQualifiedPhysicalTableIsNotShadowedByBareTemporaryName() {
        SqlStatementRecord statement = record(
                "SELECT * FROM shop.tmp_rollup pr JOIN customers c ON pr.customer_id = c.id",
                Map.of("localTempTables", List.of("tmp_rollup")));
        StructuredParseResult structured = structured(List.of(
                new RowsetEvent(StructuredParseEventType.ROWSET_REFERENCE, provenance(1),
                        "FROM", "shop.tmp_rollup", "tmp_rollup", "pr", "", "", ""),
                table("JOIN", "customers", "c", 1),
                equality("pr", "customer_id", "c", "id", 1)
        ));

        List<RelationshipCandidate> relations = new StructuredRelationshipExtractor(
                value -> value == null ? "" : value.toLowerCase(Locale.ROOT),
                new NamespaceContext("", "shop", List.of()))
                .extract(statement, structured);

        assertEquals(1, relations.size(),
                () -> "An explicit physical namespace must win over a bare temporary name: " + relations);
        assertTrue(relations.stream().anyMatch(relation ->
                        relation.source().displayName().contains("shop.tmp_rollup.customer_id")
                                || relation.target().displayName().contains("shop.tmp_rollup.customer_id")),
                () -> "The physical endpoint must remain qualified: " + relations);
    }

    @Test
    void catalogQualifiedPhysicalTableIsNotShadowedByBareTemporaryName() {
        SqlStatementRecord statement = record(
                "SELECT * FROM shop.tmp_rollup pr JOIN shop.customers c ON pr.customer_id = c.id",
                Map.of("localTempTables", List.of("tmp_rollup")));
        StructuredParseResult structured = structured(List.of(
                new RowsetEvent(StructuredParseEventType.ROWSET_REFERENCE, provenance(1),
                        "FROM", "shop.tmp_rollup", "tmp_rollup", "pr", "", "", ""),
                new RowsetEvent(StructuredParseEventType.ROWSET_REFERENCE, provenance(1),
                        "JOIN", "shop.customers", "customers", "c", "", "", ""),
                equality("pr", "customer_id", "c", "id", 1)
        ));

        List<RelationshipCandidate> relations = new StructuredRelationshipExtractor(
                mysqlIdentifierRules(), new NamespaceContext("shop", null, List.of()))
                .extract(statement, structured);

        assertEquals(1, relations.size(),
                () -> "A catalog-qualified physical table must not match a bare temporary identity: " + relations);
    }

    @Test
    void bareLocalTemporaryTableInSubqueryFallbackRemainsIgnoredAfterNamespaceResolution() {
        SqlStatementRecord statement = record(
                "SELECT pdf.id FROM category_pdf pdf "
                        + "WHERE pdf.id IN (SELECT cat_id FROM tmp_available_categories)",
                Map.of("localTempTables", List.of("tmp_available_categories")));
        StructuredParseResult structured = structured(List.of(
                table("FROM", "category_pdf", "pdf", 1),
                new PredicateEvent(StructuredParseEventType.IN_SUBQUERY_PREDICATE,
                        provenance(1), new ExpressionSource("pdf", "id"),
                        new ExpressionSource("", "cat_id"),
                        List.of(new ExpressionSource("pdf", "id")),
                        List.of(new ExpressionSource("", "cat_id")),
                        "tmp_available_categories", "", List.of(), true)
        ));

        List<RelationshipCandidate> relations = new StructuredRelationshipExtractor(
                value -> value == null ? "" : value.toLowerCase(Locale.ROOT),
                new NamespaceContext("", "case_01", List.of()))
                .extract(statement, structured);

        assertTrue(relations.isEmpty(),
                () -> "A bare temporary inner table must be rejected before namespace materialization: "
                        + relations);
    }

    @Test
    void explicitlyQualifiedPhysicalTableInSubqueryFallbackIsNotShadowedByBareTemporaryName() {
        SqlStatementRecord statement = record(
                "SELECT pdf.id FROM category_pdf pdf "
                        + "WHERE pdf.id IN (SELECT cat_id FROM archive.tmp_available_categories)",
                Map.of("localTempTables", List.of("tmp_available_categories")));
        StructuredParseResult structured = structured(List.of(
                table("FROM", "category_pdf", "pdf", 1),
                new PredicateEvent(StructuredParseEventType.IN_SUBQUERY_PREDICATE,
                        provenance(1), new ExpressionSource("pdf", "id"),
                        new ExpressionSource("", "cat_id"),
                        List.of(new ExpressionSource("pdf", "id")),
                        List.of(new ExpressionSource("", "cat_id")),
                        "archive.tmp_available_categories", "", List.of(), true)
        ));

        List<RelationshipCandidate> relations = new StructuredRelationshipExtractor(
                value -> value == null ? "" : value.toLowerCase(Locale.ROOT),
                new NamespaceContext("", "case_01", List.of()))
                .extract(statement, structured);

        assertEquals(1, relations.size(),
                () -> "An explicitly qualified inner table must remain physical: " + relations);
    }

    @Test
    void tokenEventExtractorDoesNotOwnMysqlOnlyStraightJoinCompatibility() {
        SqlStatementRecord statement = record("SELECT * FROM orders o STRAIGHT_JOIN users u ON o.user_id = u.id");
        StructuredParseResult structured = structured(List.of());

        List<RelationshipCandidate> relations = new StructuredRelationshipExtractor().extract(statement, structured);

        assertTrue(relations.isEmpty(),
                () -> "MySQL-only STRAIGHT_JOIN rowset extraction belongs in MySQL token-event typed visitor: "
                        + relations);
    }

    @Test
    void tokenEventExtractorDoesNotOwnPostgresOnlyOnlyCompatibility() {
        SqlStatementRecord statement = record("SELECT * FROM ONLY orders o JOIN users u ON o.user_id = u.id");
        StructuredParseResult structured = structured(List.of());

        List<RelationshipCandidate> relations = new StructuredRelationshipExtractor().extract(statement, structured);

        assertTrue(relations.isEmpty(),
                () -> "PostgreSQL-only ONLY rowset extraction belongs in PostgreSQL token-event typed visitor: "
                        + relations);
    }

    @Test
    void tokenEventExtractorDoesNotOwnMysqlOnlyOdbcIndexHintJsonTableOrPartitionCompatibility() {
        List<String> mysqlOnlySql = List.of(
                "SELECT * FROM { OJ orders o LEFT OUTER JOIN users u ON o.user_id = u.id }",
                "SELECT * FROM orders o FORCE INDEX FOR JOIN (idx_orders_user) JOIN users u ON o.user_id = u.id",
                "SELECT * FROM orders PARTITION (p202501) o JOIN users u ON o.user_id = u.id",
                """
                SELECT *
                FROM JSON_TABLE(payload, '$[*]' COLUMNS (user_id BIGINT PATH '$.user_id')) jt
                JOIN users u ON jt.user_id = u.id
                """
        );

        for (String sql : mysqlOnlySql) {
            List<RelationshipCandidate> relations = new StructuredRelationshipExtractor().extract(record(sql), structured(List.of()));

            assertTrue(relations.isEmpty(),
                    () -> "MySQL-only rowset extraction belongs in MySQL token-event typed visitor. SQL: " + sql
                            + " Actual: " + relations);
        }
    }

    private SqlStatementRecord record(String sql) {
        return new SqlStatementRecord(sql, StatementSourceType.PLAIN_SQL, "independence.sql", 1, 1, Map.of());
    }

    private SqlStatementRecord record(String sql, Map<String, Object> attributes) {
        return new SqlStatementRecord(sql, StatementSourceType.PLAIN_SQL, "independence.sql", 1, 1, attributes);
    }

    private StructuredParseResult structured(List<StructuredSqlEvent> events) {
        return new StructuredParseResult("ANTLR_TOKEN_EVENT", "MYSQL", "independence.sql", events, List.of(), Map.of());
    }

    private StructuredSqlEvent table(String keyword, String table, String alias, long line) {
        return new RowsetEvent(StructuredParseEventType.ROWSET_REFERENCE, provenance(line),
                keyword, table, table, alias, "", "", "");
    }

    private StructuredSqlEvent localTemp(String table, long line, String statementScope) {
        return new RowsetEvent(StructuredParseEventType.LOCAL_TEMP_TABLE_DECLARATION,
                scopedProvenance(line, statementScope), "CREATE TEMPORARY TABLE",
                table, table, "", table, "", "");
    }

    private StructuredSqlEvent directWrite(
            String targetTable,
            String targetColumn,
            String sourceAlias,
            String sourceColumn,
            long line,
            String statementScope
    ) {
        return new WriteEvent(StructuredParseEventType.INSERT_SELECT_MAPPING,
                scopedProvenance(line, statementScope), "", "", "", "",
                targetTable, targetColumn, "INSERT_SELECT",
                ExpressionTrace.of(List.of(sourceAlias), List.of(sourceColumn),
                        LineageFlowKind.VALUE, LineageTransformType.DIRECT));
    }

    private StructuredSqlEvent inPredicate(
            String outerAlias,
            String outerColumn,
            String innerTable,
            String innerColumn,
            long line,
            String statementScope
    ) {
        return new PredicateEvent(StructuredParseEventType.IN_SUBQUERY_PREDICATE,
                scopedProvenance(line, statementScope),
                new ExpressionSource(outerAlias, outerColumn),
                new ExpressionSource("", innerColumn),
                List.of(new ExpressionSource(outerAlias, outerColumn)),
                List.of(new ExpressionSource("", innerColumn)),
                innerTable, "IN_SUBQUERY", List.of(), true);
    }

    private StructuredSqlEvent scopedTable(
            String keyword,
            String table,
            String alias,
            long line,
            String statementScope
    ) {
        return new RowsetEvent(StructuredParseEventType.ROWSET_REFERENCE,
                scopedProvenance(line, statementScope),
                keyword, table, table, alias, "", "", "");
    }

    private SourceProvenance scopedProvenance(long line, String statementScope) {
        return new SourceProvenance(
                "independence.sql", line, statementScope, "", "", "", "", "",
                false, false, "");
    }

    private StructuredSqlEvent equality(String leftAlias, String leftColumn, String rightAlias, String rightColumn, long line) {
        return new PredicateEvent(StructuredParseEventType.PREDICATE_EQUALITY,
                provenance(line), new ExpressionSource(leftAlias, leftColumn),
                new ExpressionSource(rightAlias, rightColumn), List.of(), List.of(),
                "", "WHERE_OR_UNKNOWN", List.of(), false);
    }

    private SourceProvenance provenance(long line) {
        return SourceProvenance.source("independence.sql", line);
    }

    private IdentifierRules mysqlIdentifierRules() {
        return new IdentifierRules() {
            @Override
            public String normalize(String identifier) {
                return identifier == null ? "" : identifier.toLowerCase(Locale.ROOT);
            }

            @Override
            public QualifiedNameSemantics qualifiedNameSemantics() {
                return QualifiedNameSemantics.CATALOG_TABLE;
            }
        };
    }
}
