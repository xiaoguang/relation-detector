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
import com.relationdetector.core.relation.NamingEvidencePool;
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

    private DataLineageCandidate lineage(Endpoint source, Endpoint target, LineageFlowKind flowKind) {
        DataLineageCandidate candidate = new DataLineageCandidate(
                List.of(source),
                target,
                flowKind,
                LineageTransformType.DIRECT);
        candidate.confidence(BigDecimal.valueOf(0.80d));
        candidate.evidence().add(new DataLineageEvidence(LineageTransformType.DIRECT,
                BigDecimal.valueOf(0.80d),
                EvidenceSourceType.PLAIN_SQL,
                "test.sql",
                source.displayName() + " -> " + target.displayName(),
                Map.of()));
        return candidate;
    }

    private boolean hasNaming(List<NamingEvidenceCandidate> evidence, Endpoint source, Endpoint target) {
        return evidence.stream()
                .anyMatch(item -> item.source().equals(source) && item.target().equals(target));
    }
}
