package com.relationdetector.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.Enums.RelationSubType;
import com.relationdetector.contracts.Enums.RelationType;
import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.model.DataLineageEvidence;
import com.relationdetector.contracts.model.DerivedPathCandidate;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.NamingEvidenceCandidate;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.scoring.DefaultEvidenceScores;
import com.relationdetector.core.derived.DerivedPathInferenceResult;
import com.relationdetector.core.derived.DerivedPathInferenceService;
import com.relationdetector.core.naming.NamingEvidencePool;
import com.relationdetector.core.scan.EvidenceEnhancementService;
import com.relationdetector.core.scan.ScanConfig;

class DerivedPathInferenceServiceTest {
    private final DerivedPathInferenceService service = new DerivedPathInferenceService();

    @Test
    void derivesTransitiveNamingEvidenceUpToFiveHops() {
        ScanConfig config = enabledConfig();
        NamingEvidencePool pool = new NamingEvidencePool();
        Endpoint a = col("a", "r");
        Endpoint b = col("b", "s");
        Endpoint c = col("c", "t");
        Endpoint d = col("d", "u");
        Endpoint e = col("e", "v");
        Endpoint f = col("f", "w");
        Endpoint g = col("g", "x");
        pool.add(naming(a, b));
        pool.add(naming(b, c));
        pool.add(naming(c, d));
        pool.add(naming(d, e));
        pool.add(naming(e, f));
        pool.add(naming(f, g));

        List<NamingEvidenceCandidate> derived = service.deriveNamingEvidence(pool.merged(), config);

        assertTrue(hasNaming(derived, a, c));
        assertTrue(hasNaming(derived, a, d));
        assertTrue(hasNaming(derived, a, e));
        assertTrue(hasNaming(derived, a, f), "five-hop naming path should be retained");
        assertTrue(!hasNaming(derived, a, g), "six-hop naming path should be excluded");
        NamingEvidenceCandidate aToF = derived.stream()
                .filter(item -> item.source().equals(a) && item.target().equals(f))
                .findFirst()
                .orElseThrow();
        assertEquals("TRANSITIVE_NAMING_PATH", aToF.rule());
        assertEquals(EvidenceSourceType.INFERENCE, aToF.evidence().sourceType());
        assertEquals(true, aToF.evidence().attributes().get("derived"));
        assertEquals(5, aToF.evidence().attributes().get("pathLength"));
    }

    @Test
    void keepsAllPathsWhenPathLimitIsUnlimited() {
        ScanConfig config = enabledConfig();
        config.derivedMaxPathsPerPair = 0;
        NamingEvidencePool pool = new NamingEvidencePool();
        Endpoint a = col("a", "id");
        Endpoint b = col("b", "id");
        Endpoint c = col("c", "id");
        Endpoint d = col("d", "id");
        pool.add(naming(a, b));
        pool.add(naming(a, c));
        pool.add(naming(b, d));
        pool.add(naming(c, d));

        NamingEvidencePool merged = new NamingEvidencePool();
        merged.addAll(service.deriveNamingEvidence(pool.merged(), config));
        NamingEvidenceCandidate aToD = merged.merged().stream()
                .filter(item -> item.source().equals(a) && item.target().equals(d))
                .findFirst()
                .orElseThrow();

        assertEquals(2, aToD.rawEvidence().size(),
                "unlimited maxPathsPerPair should preserve both a-b-d and a-c-d observations");
    }

    @Test
    void pureNamingPathDoesNotCreateDerivedRelationship() {
        ScanConfig config = enabledConfig();
        Endpoint a = col("a", "r");
        Endpoint b = col("b", "s");
        Endpoint c = col("c", "t");
        NamingEvidencePool pool = new NamingEvidencePool();
        pool.add(naming(a, b));
        pool.add(naming(b, c));

        DerivedPathInferenceResult result = service.infer(List.of(), List.of(), pool.merged(), config);

        assertTrue(result.derivedRelationships().isEmpty(),
                "naming-only paths are evidence hints, not physical relationships");
    }

    @Test
    void conditionalRelationshipAndNamingEvidenceDoNotParticipateInDerivedInference() {
        ScanConfig config = enabledConfig();
        Endpoint contractPartyId = col("contracts", "party_id");
        Endpoint customersId = col("customers", "id");
        Endpoint customersRegionId = col("customers", "region_id");
        Endpoint regionsId = col("regions", "id");

        RelationshipCandidate conditional = conditionalFk(contractPartyId, customersId);
        NamingEvidenceCandidate conditionalNaming = conditionalNaming(contractPartyId, customersId);

        DerivedPathInferenceResult result = service.infer(
                List.of(conditional, fk(customersRegionId, regionsId)),
                List.of(),
                List.of(conditionalNaming),
                config);

        assertTrue(result.derivedRelationships().stream().noneMatch(candidate ->
                        candidate.source().equals(contractPartyId)),
                "A conditional direct relationship must not seed relationship closure");
        assertTrue(result.derivedNamingEvidence().stream().noneMatch(candidate ->
                        candidate.source().equals(contractPartyId)),
                "Conditional naming evidence must not seed transitive naming closure");
    }

    @Test
    void relationshipPathMayUseNamingEdgeButMustContainRelationshipEdge() {
        ScanConfig config = enabledConfig();
        Endpoint a = col("a", "r");
        Endpoint b = col("b", "s");
        Endpoint c = col("c", "t");
        Endpoint d = col("d", "u");
        RelationshipCandidate relation = fk(a, b);
        RelationshipCandidate tailRelation = fk(c, d);
        NamingEvidencePool pool = new NamingEvidencePool();
        pool.add(naming(b, c));

        DerivedPathInferenceResult result = service.infer(List.of(relation, tailRelation), List.of(), pool.merged(), config);

        DerivedPathCandidate derived = result.derivedRelationships().stream()
                .filter(candidate -> candidate.source().equals(a) && candidate.target().equals(d))
                .findFirst()
                .orElseThrow();
        assertEquals(true, derived.attributes().get("containsNamingEdge"));
    }

    @Test
    void relationshipPathCanTraverseTableIdentityBridgeBetweenForeignKeys() {
        ScanConfig config = enabledConfig();
        Endpoint orderItemOrderId = col("order_items", "order_id");
        Endpoint ordersId = col("orders", "id");
        Endpoint ordersCustomerId = col("orders", "customer_id");
        Endpoint customersId = col("customers", "id");

        DerivedPathInferenceResult result = service.infer(List.of(
                fk(orderItemOrderId, ordersId),
                fk(ordersCustomerId, customersId)
        ), List.of(), List.of(), config);

        assertTrue(result.derivedRelationships().stream().anyMatch(candidate ->
                        candidate.source().equals(orderItemOrderId)
                                && candidate.target().equals(customersId)
                                && Boolean.TRUE.equals(candidate.attributes().get("containsTableIdentityBridge"))),
                "DDL FK graph should derive order_items.order_id -> customers.id via orders.id -> orders.customer_id");
    }

    @Test
    void relationshipVariantsMergeIntoOneCanonicalDerivedPathWithBothRawObservations() {
        ScanConfig config = enabledConfig();
        Endpoint orderItemOrderId = col("order_items", "order_id");
        Endpoint ordersId = col("orders", "id");
        Endpoint ordersCustomerId = col("orders", "customer_id");
        Endpoint customersId = col("customers", "id");
        RelationshipCandidate firstVariant = fk(orderItemOrderId, ordersId);
        RelationshipCandidate secondVariant = fk(orderItemOrderId, ordersId);
        secondVariant.evidence().clear();
        secondVariant.evidence().add(new Evidence(EvidenceType.SQL_LOG_JOIN,
                BigDecimal.valueOf(DefaultEvidenceScores.SQL_LOG_JOIN),
                EvidenceSourceType.PLAIN_SQL,
                "second.sql",
                orderItemOrderId.displayName() + " = " + ordersId.displayName(),
                Map.of("sourceLine", 9)));

        DerivedPathInferenceResult result = service.infer(List.of(
                firstVariant, secondVariant, fk(ordersCustomerId, customersId)
        ), List.of(), List.of(), config);

        List<DerivedPathCandidate> paths = result.derivedRelationships().stream()
                .filter(candidate -> candidate.source().equals(orderItemOrderId)
                        && candidate.target().equals(customersId))
                .toList();
        assertEquals(1, paths.size());
        assertEquals(2, paths.get(0).rawEvidence().size());
    }

    @Test
    void relationshipPathDoesNotBridgeSameNamedTablesAcrossCatalogs() {
        ScanConfig config = enabledConfig();
        Endpoint orderItemOrderId = catalogCol("tenant_a", "sales", "order_items", "order_id");
        Endpoint tenantAOrdersId = catalogCol("tenant_a", "sales", "orders", "id");
        Endpoint tenantBOrdersCustomerId = catalogCol("tenant_b", "sales", "orders", "customer_id");
        Endpoint customersId = catalogCol("tenant_b", "sales", "customers", "id");

        DerivedPathInferenceResult result = service.infer(List.of(
                fk(orderItemOrderId, tenantAOrdersId),
                fk(tenantBOrdersCustomerId, customersId)
        ), List.of(), List.of(), config);

        assertTrue(result.derivedRelationships().stream().noneMatch(candidate ->
                        candidate.source().equals(orderItemOrderId)
                                && candidate.target().equals(customersId)),
                "tenant_a.sales.orders must not bridge to tenant_b.sales.orders");
    }

    @Test
    void lineageAndNamingPathsDoNotJoinSameNamedEndpointsAcrossCatalogs() {
        ScanConfig config = enabledConfig();
        Endpoint lineageSource = catalogCol("tenant_a", "sales", "orders", "customer_id");
        Endpoint tenantAStage = catalogCol("tenant_a", "sales", "stage", "customer_id");
        Endpoint tenantBStage = catalogCol("tenant_b", "sales", "stage", "customer_id");
        Endpoint lineageTarget = catalogCol("tenant_b", "sales", "facts", "customer_id");

        NamingEvidencePool namingPool = new NamingEvidencePool();
        namingPool.add(naming(lineageSource, tenantAStage));
        namingPool.add(naming(tenantBStage, lineageTarget));

        DerivedPathInferenceResult result = service.infer(
                List.of(),
                List.of(
                        lineage(lineageSource, tenantAStage, LineageFlowKind.VALUE),
                        lineage(tenantBStage, lineageTarget, LineageFlowKind.VALUE)),
                namingPool.merged(),
                config);

        assertTrue(result.derivedDataLineages().isEmpty(),
                "lineage traversal must preserve catalog identity at every hop");
        assertTrue(result.derivedNamingEvidence().isEmpty(),
                "naming traversal must preserve catalog identity at every hop");
    }

    @Test
    void relationshipPathTraversesReferencedByButOutputsForwardFkLikeDirection() {
        ScanConfig config = enabledConfig();
        Endpoint cashierAccountId = col("cashier_journals", "account_id");
        Endpoint accountsId = col("accounts", "id");
        Endpoint reconciliationCashierJournalId = col("reconciliation_items", "cashier_journal_id");
        Endpoint cashierJournalsId = col("cashier_journals", "id");

        DerivedPathInferenceResult result = service.infer(List.of(
                fk(cashierAccountId, accountsId),
                fk(reconciliationCashierJournalId, cashierJournalsId)
        ), List.of(), List.of(), config);

        DerivedPathCandidate derived = result.derivedRelationships().stream()
                .filter(candidate -> candidate.source().equals(reconciliationCashierJournalId)
                        && candidate.target().equals(accountsId))
                .findFirst()
                .orElseThrow();

        assertEquals(List.of(reconciliationCashierJournalId, cashierJournalsId, cashierAccountId, accountsId),
                derived.path(),
                "Output path should be FK-like forward even though traversal walked referenced-by internally");
        assertEquals("REVERSE_REFERENCED_BY", derived.attributes().get("traversalMode"));
        assertEquals("FK_LIKE_FORWARD", derived.attributes().get("outputDirection"));
        assertTrue(derived.attributes().containsKey("traversalPath"),
                "Auditing should preserve the internal referenced-by traversal path");
        assertTrue(result.derivedRelationships().stream().noneMatch(candidate ->
                        candidate.source().equals(accountsId)
                                && candidate.target().equals(reconciliationCashierJournalId)),
                "Derived relationship output must not expose downstream/referenced-by direction");
    }

    @Test
    void directRelationshipIsNotRepeatedAsDerivedRelationship() {
        ScanConfig config = enabledConfig();
        Endpoint cashierAccountId = col("cashier_journals", "account_id");
        Endpoint accountsId = col("accounts", "id");

        DerivedPathInferenceResult result = service.infer(List.of(
                fk(cashierAccountId, accountsId)
        ), List.of(), List.of(), config);

        assertTrue(result.derivedRelationships().isEmpty(),
                "A direct FK-like edge should not be emitted again as a derived relationship");
    }

    @Test
    void relationshipPathCreatesDerivedNamingEvidenceAndRelationshipReference() {
        ScanConfig config = enabledConfig();
        Endpoint cashierAccountId = col("cashier_journals", "account_id");
        Endpoint accountsId = col("accounts", "id");
        Endpoint reconciliationCashierJournalId = col("reconciliation_items", "cashier_journal_id");
        Endpoint cashierJournalsId = col("cashier_journals", "id");

        NamingEvidencePool namingPool = new NamingEvidencePool();
        namingPool.add(naming(cashierAccountId, accountsId));
        namingPool.add(naming(reconciliationCashierJournalId, cashierJournalsId));
        RelationshipCandidate cashierToAccount = fk(cashierAccountId, accountsId);
        RelationshipCandidate reconciliationToCashier = fk(reconciliationCashierJournalId, cashierJournalsId);
        new EvidenceEnhancementService().enhance(
                List.of(cashierToAccount, reconciliationToCashier),
                namingPool,
                null,
                config);

        DerivedPathInferenceResult result = service.infer(
                List.of(cashierToAccount, reconciliationToCashier),
                List.of(),
                namingPool.merged(),
                config);

        NamingEvidenceCandidate derivedNaming = result.derivedNamingEvidence().stream()
                .filter(candidate -> candidate.source().equals(reconciliationCashierJournalId)
                        && candidate.target().equals(accountsId))
                .findFirst()
                .orElseThrow();
        DerivedPathCandidate derivedRelationship = result.derivedRelationships().stream()
                .filter(candidate -> candidate.source().equals(reconciliationCashierJournalId)
                        && candidate.target().equals(accountsId))
                .findFirst()
                .orElseThrow();

        assertEquals("TRANSITIVE_NAMING_PATH", derivedNaming.rule());
        assertTrue(derivedRelationship.evidence().stream().anyMatch(evidence ->
                        evidence.type() == EvidenceType.NAMING_MATCH
                                && derivedNaming.id().equals(evidence.attributes().get("evidenceRef"))),
                "Derived relationship should reference the top-level derived naming evidence");
    }

    @Test
    void relationshipPathUsesTopLevelNamingPoolEvenWhenRelationshipHasNoNamingEvidence() {
        ScanConfig config = enabledConfig();
        Endpoint salesFactCategoryId = col("dbo", "sales_fact", "category_dim_id");
        Endpoint categoryDimId = col("dbo", "category_dim", "id");
        Endpoint categoryDimSourceId = col("dbo", "category_dim", "source_category_id");
        Endpoint productCategoriesId = col("dbo", "product_categories", "id");
        NamingEvidencePool namingPool = new NamingEvidencePool();
        namingPool.add(naming(salesFactCategoryId, categoryDimId));

        DerivedPathInferenceResult result = service.infer(
                List.of(
                        fk(salesFactCategoryId, categoryDimId),
                        fk(categoryDimSourceId, productCategoriesId)),
                List.of(),
                namingPool.merged(),
                config);

        NamingEvidenceCandidate derivedNaming = result.derivedNamingEvidence().stream()
                .filter(candidate -> candidate.source().equals(salesFactCategoryId)
                        && candidate.target().equals(productCategoriesId))
                .findFirst()
                .orElseThrow();
        assertEquals("TRANSITIVE_NAMING_PATH", derivedNaming.rule());
        assertTrue(result.derivedRelationships().stream()
                .filter(candidate -> candidate.source().equals(salesFactCategoryId)
                        && candidate.target().equals(productCategoriesId))
                .flatMap(candidate -> candidate.evidence().stream())
                .anyMatch(evidence -> derivedNaming.id().equals(evidence.attributes().get("evidenceRef"))));
    }

    @Test
    void relationshipEmbeddedNamingEvidenceCannotReplaceTopLevelNamingPool() {
        ScanConfig config = enabledConfig();
        Endpoint orderItemOrderId = col("order_items", "order_id");
        Endpoint ordersId = col("orders", "id");
        Endpoint ordersCustomerId = col("orders", "customer_id");
        Endpoint customersId = col("customers", "id");
        RelationshipCandidate first = fk(orderItemOrderId, ordersId);
        first.evidence().add(naming(orderItemOrderId, ordersId).evidence());

        DerivedPathInferenceResult result = service.infer(
                List.of(first, fk(ordersCustomerId, customersId)),
                List.of(),
                List.of(),
                config);

        assertTrue(result.derivedNamingEvidence().isEmpty(),
                "Relationship-local NAMING_MATCH must not bypass the top-level naming evidence pool");
    }

    @Test
    void relationshipCanReferenceDerivedNamingEvidenceFromTopLevelPool() {
        ScanConfig config = enabledConfig();
        Endpoint a = col("a", "r");
        Endpoint b = col("b", "s");
        Endpoint c = col("c", "t");
        NamingEvidencePool pool = new NamingEvidencePool();
        pool.add(naming(a, b));
        pool.add(naming(b, c));
        RelationshipCandidate candidate = new RelationshipCandidate(a, c,
                RelationType.CO_OCCURRENCE, RelationSubType.COLUMN_CO_OCCURRENCE);
        candidate.evidence().add(new Evidence(EvidenceType.SQL_LOG_JOIN,
                BigDecimal.valueOf(DefaultEvidenceScores.SQL_LOG_JOIN),
                EvidenceSourceType.PLAIN_SQL,
                "query.sql",
                "a.r = c.t",
                Map.of()));

        new EvidenceEnhancementService().enhance(List.of(candidate), pool, null, config);

        Evidence naming = candidate.evidence().stream()
                .filter(item -> item.type() == EvidenceType.NAMING_MATCH)
                .findFirst()
                .orElseThrow();
        assertTrue(String.valueOf(naming.attributes().get("evidenceRef"))
                        .endsWith(":TRANSITIVE_NAMING_PATH"),
                "Relationship NAMING_MATCH should reference derived top-level naming evidence");
        assertTrue(pool.merged().stream()
                        .anyMatch(item -> item.id().equals(naming.attributes().get("evidenceRef"))),
                "Relationship evidenceRef must resolve inside the naming evidence pool");
    }

    @Test
    void lineagePathUsesOnlyValueLineageEdges() {
        ScanConfig config = enabledConfig();
        Endpoint a = col("a", "r");
        Endpoint b = col("b", "s");
        Endpoint c = col("c", "t");
        Endpoint d = col("d", "u");

        DerivedPathInferenceResult result = service.infer(List.of(), List.of(
                lineage(a, b, LineageFlowKind.VALUE),
                lineage(b, c, LineageFlowKind.VALUE),
                lineage(c, d, LineageFlowKind.CONTROL)
        ), List.of(), config);

        assertEquals(1, result.derivedDataLineages().size());
        assertEquals(a, result.derivedDataLineages().get(0).source());
        assertEquals(c, result.derivedDataLineages().get(0).target());
    }

    @Test
    void lineageVariantsMergeIntoOneCanonicalDerivedPath() {
        ScanConfig config = enabledConfig();
        Endpoint a = col("a", "amount");
        Endpoint b = col("b", "amount");
        Endpoint c = col("c", "amount");

        DerivedPathInferenceResult result = service.infer(List.of(), List.of(
                lineage(a, b, LineageFlowKind.VALUE, LineageTransformType.DIRECT,
                        Map.of("mappingKind", "INSERT_SELECT")),
                lineage(a, b, LineageFlowKind.VALUE, LineageTransformType.COALESCE,
                        Map.of("mappingKind", "INSERT_SELECT")),
                lineage(b, c, LineageFlowKind.VALUE)
        ), List.of(), config);

        List<DerivedPathCandidate> aToC = result.derivedDataLineages().stream()
                .filter(candidate -> candidate.source().equals(a) && candidate.target().equals(c))
                .toList();
        assertEquals(1, aToC.size(), "One canonical endpoint path should produce one derived fact");
        assertEquals(2, aToC.get(0).rawEvidence().size(),
                "Distinct edge variants should remain available as raw path observations");
    }

    @Test
    void identicalDerivedLineagePathObservationsAreFolded() {
        ScanConfig config = enabledConfig();
        Endpoint a = col("a", "amount");
        Endpoint b = col("b", "amount");
        Endpoint c = col("c", "amount");

        DerivedPathInferenceResult result = service.infer(List.of(), List.of(
                lineage(a, b, LineageFlowKind.VALUE, LineageTransformType.ARITHMETIC,
                        Map.of("mappingKind", "INSERT_SELECT")),
                lineage(a, b, LineageFlowKind.VALUE, LineageTransformType.ARITHMETIC,
                        Map.of("mappingKind", "UPDATE_SET")),
                lineage(b, c, LineageFlowKind.VALUE)
        ), List.of(), config);

        DerivedPathCandidate aToC = result.derivedDataLineages().stream()
                .filter(candidate -> candidate.source().equals(a) && candidate.target().equals(c))
                .findFirst()
                .orElseThrow();
        assertEquals(1, aToC.rawEvidence().size(),
                "Semantically identical path observations must be folded");
        assertEquals(2, aToC.rawEvidence().get(0).attributes().get("occurrenceCount"),
                "The folded observation must retain the number of contributing edge variants");
    }

    @Test
    void lineagePathDoesNotConnectSchemaQualifiedAndUnqualifiedEndpoints() {
        ScanConfig config = enabledConfig();
        Endpoint customerName = col("dbo", "customers", "name");
        Endpoint cashierCounterpartyUnqualified = col("cashier_journals", "counterparty");
        Endpoint cashierCounterpartyQualified = col("dbo", "cashier_journals", "counterparty");
        Endpoint reconciliationDescription = col("reconciliation_items", "description");

        DerivedPathInferenceResult result = service.infer(List.of(), List.of(
                lineage(customerName, cashierCounterpartyUnqualified, LineageFlowKind.VALUE),
                lineage(cashierCounterpartyQualified, reconciliationDescription, LineageFlowKind.VALUE)
        ), List.of(), config);

        assertTrue(result.derivedDataLineages().isEmpty(),
                "schema-qualified and unqualified endpoints must not be treated as the same table");
    }

    @Test
    void pureIdentitySelfLineageDoesNotParticipateInDerivedLineage() {
        ScanConfig config = enabledConfig();
        Endpoint departmentId = col("departments", "id");
        Endpoint employeeId = col("employees", "id");

        DerivedPathInferenceResult result = service.infer(List.of(), List.of(
                lineage(departmentId, departmentId, LineageFlowKind.VALUE),
                lineage(departmentId, employeeId, LineageFlowKind.VALUE)
        ), List.of(), config);

        assertTrue(result.derivedDataLineages().isEmpty(),
                "A pure id -> id no-op lineage edge should not seed transitive lineage paths");
    }

    @Test
    void nonTrivialSelfUpdateLineageCanStillParticipateInDerivedLineage() {
        ScanConfig config = enabledConfig();
        Endpoint quantity = col("inventory", "quantity");
        Endpoint beforeQty = col("inventory_transactions", "before_qty");
        Endpoint auditQty = col("inventory_audit", "before_qty");

        DerivedPathInferenceResult result = service.infer(List.of(), List.of(
                lineage(quantity, quantity, LineageFlowKind.VALUE, LineageTransformType.ARITHMETIC,
                        Map.of("mappingKind", "UPDATE_SET")),
                lineage(quantity, beforeQty, LineageFlowKind.VALUE),
                lineage(beforeQty, auditQty, LineageFlowKind.VALUE)
        ), List.of(), config);

        assertTrue(result.derivedDataLineages().stream().anyMatch(candidate ->
                        candidate.source().equals(quantity) && candidate.target().equals(auditQty)),
                "Non-trivial self updates are meaningful value-flow edges and may support derived lineage");
    }

    @Test
    void lineagePathDoesNotReenterNonAdjacentEndpoint() {
        ScanConfig config = enabledConfig();
        Endpoint cashierAmount = col("dbo", "cashier_journals", "amount");
        Endpoint paymentAmount = col("dbo", "payments", "amount");
        Endpoint salesOrderPaidAmount = col("dbo", "sales_orders", "paid_amount");
        Endpoint paymentReceiptAmount = col("dbo", "payment_receipts", "amount");
        Endpoint salesFactPaidAmount = col("dbo", "sales_fact", "paid_amount");

        DerivedPathInferenceResult result = service.infer(List.of(), List.of(
                lineage(cashierAmount, paymentAmount, LineageFlowKind.VALUE),
                lineage(paymentAmount, salesOrderPaidAmount, LineageFlowKind.VALUE),
                lineage(salesOrderPaidAmount, paymentReceiptAmount, LineageFlowKind.VALUE),
                lineage(paymentReceiptAmount, paymentAmount, LineageFlowKind.VALUE),
                lineage(paymentAmount, salesFactPaidAmount, LineageFlowKind.VALUE)
        ), List.of(), config);

        assertTrue(result.derivedDataLineages().stream().noneMatch(candidate ->
                        hasNonAdjacentRepeatedEndpoint(candidate.path())),
                () -> "A path must not revisit an endpoint after leaving it: " + result.derivedDataLineages());
    }

    @Test
    void relationshipPathDoesNotBridgeSchemaQualifiedAndUnqualifiedTables() {
        ScanConfig config = enabledConfig();
        Endpoint accountingClosedBy = col("accounting_periods", "closed_by");
        Endpoint employeesId = col("employees", "id");
        Endpoint dboEmployeesDepartmentId = col("dbo", "employees", "department_id");
        Endpoint dboDepartmentsId = col("dbo", "departments", "id");

        DerivedPathInferenceResult result = service.infer(List.of(
                fk(accountingClosedBy, employeesId),
                fk(dboEmployeesDepartmentId, dboDepartmentsId)
        ), List.of(), List.of(), config);

        assertTrue(result.derivedRelationships().stream().noneMatch(candidate ->
                        candidate.source().equals(accountingClosedBy)
                                && candidate.target().equals(dboDepartmentsId)),
                "employees.id must not bridge into dbo.employees.department_id");
    }

    @Test
    void cyclesDoNotCreateSelfLoops() {
        ScanConfig config = enabledConfig();
        Endpoint a = col("a", "r");
        Endpoint b = col("b", "s");
        Endpoint c = col("c", "t");

        DerivedPathInferenceResult result = service.infer(List.of(fk(a, b), fk(b, c), fk(c, a)), List.of(), List.of(), config);

        assertTrue(result.derivedRelationships().stream()
                        .noneMatch(item -> item.source().equals(item.target())),
                "cycle traversal must never emit self-loop derived facts");
    }

    private ScanConfig enabledConfig() {
        ScanConfig config = new ScanConfig();
        config.derivedPathsEnabled = true;
        return config;
    }

    private Endpoint col(String table, String column) {
        return Endpoint.column(ColumnRef.of(TableId.of(null, table), column));
    }

    private Endpoint col(String schema, String table, String column) {
        return Endpoint.column(ColumnRef.of(TableId.of(schema, table), column));
    }

    private Endpoint catalogCol(String catalog, String schema, String table, String column) {
        TableId tableId = new TableId(catalog, schema, table, schema + "." + table);
        return Endpoint.column(ColumnRef.of(tableId, column));
    }

    private NamingEvidenceCandidate naming(Endpoint source, Endpoint target) {
        return new NamingEvidenceCandidate(
                source,
                target,
                new Evidence(EvidenceType.NAMING_MATCH,
                        BigDecimal.valueOf(DefaultEvidenceScores.NAMING_MATCH),
                        EvidenceSourceType.NAMING_HEURISTIC,
                        "test",
                        source.displayName() + " names " + target.displayName(),
                        Map.of(
                                "namingRule", "TABLE_ID",
                                "suggestedSourceEndpoint", source.normalizedKey(),
                                "suggestedTargetEndpoint", target.normalizedKey(),
                                "directionHint", true)),
                "TABLE_ID",
                true);
    }

    private NamingEvidenceCandidate conditionalNaming(Endpoint source, Endpoint target) {
        Evidence evidence = new Evidence(EvidenceType.NAMING_MATCH,
                BigDecimal.valueOf(DefaultEvidenceScores.NAMING_MATCH),
                EvidenceSourceType.NAMING_HEURISTIC,
                "test",
                source.displayName() + " conditionally names " + target.displayName(),
                Map.of("conditional", true));
        return new NamingEvidenceCandidate(source, target, evidence, "TABLE_ID", true, List.of(evidence));
    }

    private RelationshipCandidate fk(Endpoint source, Endpoint target) {
        RelationshipCandidate candidate = new RelationshipCandidate(source, target,
                RelationType.FK_LIKE, RelationSubType.INFERRED_JOIN_FK);
        candidate.confidence(BigDecimal.valueOf(0.80d));
        candidate.evidence().add(new Evidence(EvidenceType.SQL_LOG_JOIN,
                BigDecimal.valueOf(DefaultEvidenceScores.SQL_LOG_JOIN),
                EvidenceSourceType.PLAIN_SQL,
                "test.sql",
                source.displayName() + " = " + target.displayName(),
                Map.of()));
        return candidate;
    }

    private RelationshipCandidate conditionalFk(Endpoint source, Endpoint target) {
        RelationshipCandidate candidate = new RelationshipCandidate(source, target,
                RelationType.FK_LIKE, RelationSubType.INFERRED_JOIN_FK);
        candidate.confidence(BigDecimal.valueOf(0.80d));
        candidate.evidence().add(new Evidence(EvidenceType.SQL_LOG_JOIN,
                BigDecimal.valueOf(DefaultEvidenceScores.SQL_LOG_JOIN),
                EvidenceSourceType.PLAIN_SQL,
                "test.sql",
                source.displayName() + " = " + target.displayName(),
                Map.of(
                        "conditional", true,
                        "polymorphic", true,
                        "discriminatorEndpoint", "contracts.party_type",
                        "discriminatorOperator", "EQUALS",
                        "discriminatorValue", "customer")));
        return candidate;
    }

    private DataLineageCandidate lineage(Endpoint source, Endpoint target, LineageFlowKind flowKind) {
        return lineage(source, target, flowKind, LineageTransformType.DIRECT, Map.of());
    }

    private DataLineageCandidate lineage(
            Endpoint source,
            Endpoint target,
            LineageFlowKind flowKind,
            LineageTransformType transformType,
            Map<String, Object> attributes
    ) {
        DataLineageCandidate candidate = new DataLineageCandidate(
                List.of(source),
                target,
                flowKind,
                transformType);
        candidate.confidence(BigDecimal.valueOf(0.80d));
        candidate.evidence().add(new DataLineageEvidence(transformType,
                BigDecimal.valueOf(0.80d),
                EvidenceSourceType.PLAIN_SQL,
                "test.sql",
                source.displayName() + " -> " + target.displayName(),
                attributes));
        return candidate;
    }

    private boolean hasNaming(List<NamingEvidenceCandidate> evidence, Endpoint source, Endpoint target) {
        return evidence.stream()
                .anyMatch(item -> item.source().equals(source) && item.target().equals(target));
    }

    private boolean hasNonAdjacentRepeatedEndpoint(List<Endpoint> path) {
        for (int left = 0; left < path.size(); left++) {
            for (int right = left + 2; right < path.size(); right++) {
                if (path.get(left).normalizedKey().equals(path.get(right).normalizedKey())) {
                    return true;
                }
            }
        }
        return false;
    }
}
