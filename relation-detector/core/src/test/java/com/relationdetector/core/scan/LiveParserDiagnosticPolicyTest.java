package com.relationdetector.core.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.Enums.RelationSubType;
import com.relationdetector.contracts.Enums.RelationType;
import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.core.execution.StatementExecutionOutcome;

class LiveParserDiagnosticPolicyTest {
    private static final String SECRET =
            "jdbc:mysql://secret-host/private?password=hunter2 SELECT password FROM customer_secret";

    @Test
    void sanitizesOutcomeAndRelationshipAndLineageEmbeddedWarnings() {
        TableId orders = new TableId("tenant", "shop", "orders", "shop.orders");
        TableId users = new TableId("tenant", "shop", "users", "shop.users");
        RelationshipCandidate relationship = new RelationshipCandidate(
                Endpoint.column(ColumnRef.of(orders, "user_id")),
                Endpoint.column(ColumnRef.of(users, "id")),
                RelationType.FK_LIKE,
                RelationSubType.INFERRED_JOIN_FK);
        relationship.warnings().add(malicious("RELATIONSHIP_WARNING"));
        DataLineageCandidate lineage = new DataLineageCandidate(
                List.of(Endpoint.column(ColumnRef.of(orders, "user_id"))),
                Endpoint.column(ColumnRef.of(users, "id")),
                LineageFlowKind.VALUE,
                LineageTransformType.DIRECT);
        lineage.warnings().add(malicious("LINEAGE_WARNING"));
        StatementExecutionOutcome outcome = new StatementExecutionOutcome(
                List.of(relationship), List.of(lineage), List.of(), List.of(malicious("OUTCOME_WARNING")));

        LiveParserDiagnosticPolicy policy = LiveParserDiagnosticPolicy.object(
                "tenant.shop.safe_proc",
                Map.of(
                        "objectCatalog", "tenant",
                        "objectSchema", "shop",
                        "objectName", "safe_proc",
                        "objectType", "PROCEDURE"));
        StatementExecutionOutcome sanitized = policy.sanitizeOutcome(outcome);

        assertSafe(sanitized.warnings().get(0));
        assertSafe(sanitized.relationshipCandidates().get(0).warnings().get(0));
        assertSafe(sanitized.lineageCandidates().get(0).warnings().get(0));
    }

    private void assertSafe(WarningMessage warning) {
        assertEquals("Live database object parser reported a diagnostic", warning.message());
        assertEquals("tenant.shop.safe_proc", warning.source());
        assertEquals(0, warning.line());
        assertEquals(Map.of(
                "objectCatalog", "tenant",
                "objectSchema", "shop",
                "objectName", "safe_proc",
                "objectType", "PROCEDURE"), warning.attributes());
        assertFalse(warning.toString().contains(SECRET));
    }

    private WarningMessage malicious(String code) {
        return WarningMessage.warn(
                WarningType.PARSE_WARNING,
                code,
                SECRET,
                SECRET,
                99,
                Map.of(
                        "rawStatement", SECRET,
                        "objectName", "spoofed-secret-object",
                        "exceptionClass", "evil.secret.Driver"));
    }
}
