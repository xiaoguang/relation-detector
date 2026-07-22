package com.relationdetector.core.output;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.RelationSubType;
import com.relationdetector.contracts.Enums.RelationType;
import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.core.scan.ScanResult;

import org.junit.jupiter.api.Test;

class TableResultWriterTest {

    @Test
    void preservesRelationshipOrderAndFirstEvidenceTypeOrder() {
        ScanResult result = new ScanResult("mysql", "shop");
        RelationshipCandidate first = relationship("orders", "customer_id", "customers", "id");
        first.evidence().add(evidence(EvidenceType.SQL_LOG_JOIN));
        first.evidence().add(evidence(EvidenceType.TARGET_UNIQUE));
        first.evidence().add(evidence(EvidenceType.SQL_LOG_JOIN));
        RelationshipCandidate second = relationship("payments", "order_id", "orders", "id");
        second.evidence().add(evidence(EvidenceType.SQL_LOG_SUBQUERY_IN));
        result.relationships().add(first);
        result.relationships().add(second);

        String rendered = new TableResultWriter().write(result);

        assertTrue(rendered.indexOf("shop.orders.customer_id") < rendered.indexOf("shop.payments.order_id"),
                rendered);
        assertTrue(rendered.contains("SQL_LOG_JOIN,TARGET_UNIQUE"), rendered);
        assertFalse(rendered.contains("SQL_LOG_JOIN,TARGET_UNIQUE,SQL_LOG_JOIN"), rendered);
    }

    @Test
    void rendersLongEndpointsAndEvidenceWithoutTruncationOrWrapping() {
        String sourceTable = "source_table_with_a_name_longer_than_the_fixed_display_column";
        String sourceColumn = "source_column_with_a_name_that_must_remain_complete";
        String targetTable = "target_table_with_a_name_longer_than_the_fixed_display_column";
        String targetColumn = "target_column_with_a_name_that_must_remain_complete";
        ScanResult result = new ScanResult("mysql", "audit_schema");
        RelationshipCandidate relation = relationship(sourceTable, sourceColumn, targetTable, targetColumn);
        relation.evidence().add(evidence(EvidenceType.SQL_LOG_COLUMN_CO_OCCURRENCE));
        relation.evidence().add(evidence(EvidenceType.VALUE_CONTAINMENT_HIGH));
        relation.evidence().add(evidence(EvidenceType.REPEATED_OBSERVATION));
        result.relationships().add(relation);

        String rendered = new TableResultWriter().write(result);

        assertTrue(rendered.contains("shop." + sourceTable + "." + sourceColumn), rendered);
        assertTrue(rendered.contains("shop." + targetTable + "." + targetColumn), rendered);
        assertTrue(rendered.contains(
                "SQL_LOG_COLUMN_CO_OCCURRENCE,VALUE_CONTAINMENT_HIGH,REPEATED_OBSERVATION"), rendered);
    }

    @Test
    void emptyRelationshipResultStillRendersWarningSummaryAndDetails() {
        ScanResult result = new ScanResult("mysql", "shop");
        result.warnings().add(WarningMessage.warn(
                WarningType.PARSE_WARNING,
                "SQL_PARSE_RECOVERED",
                "Parser recovered after an unsupported statement",
                "queries.sql",
                42));

        String rendered = new TableResultWriter().write(result);

        assertTrue(rendered.startsWith("No relationships detected.\n"), rendered);
        assertTrue(rendered.contains("Warnings: 1"), rendered);
        assertTrue(rendered.contains(
                "- queries.sql:42 Parser recovered after an unsupported statement"), rendered);
    }

    @Test
    void renderingDoesNotMutateScanResultOrRelationshipEvidence() {
        ScanResult result = new ScanResult("mysql", "shop");
        RelationshipCandidate relation = relationship("orders", "customer_id", "customers", "id");
        relation.evidence().add(evidence(EvidenceType.SQL_LOG_JOIN));
        relation.evidence().add(evidence(EvidenceType.TARGET_UNIQUE));
        result.relationships().add(relation);
        result.warnings().add(WarningMessage.warn(
                WarningType.CONFIG_WARNING, "CONFIG_NOTICE", "Configuration notice", "config.yml", 1));
        List<RelationshipCandidate> relationshipsBefore = List.copyOf(result.relationships());
        List<Evidence> evidenceBefore = List.copyOf(relation.evidence());
        List<WarningMessage> warningsBefore = List.copyOf(result.warnings());

        new TableResultWriter().write(result);

        assertEquals(relationshipsBefore, result.relationships());
        assertEquals(evidenceBefore, relation.evidence());
        assertEquals(warningsBefore, result.warnings());
    }

    private RelationshipCandidate relationship(
            String sourceTable,
            String sourceColumn,
            String targetTable,
            String targetColumn
    ) {
        Endpoint source = Endpoint.column(ColumnRef.of(TableId.of("shop", sourceTable), sourceColumn));
        Endpoint target = Endpoint.column(ColumnRef.of(TableId.of("shop", targetTable), targetColumn));
        RelationshipCandidate candidate = new RelationshipCandidate(
                source, target, RelationType.FK_LIKE, RelationSubType.INFERRED_JOIN_FK);
        candidate.confidence(BigDecimal.valueOf(0.85d));
        return candidate;
    }

    private Evidence evidence(EvidenceType type) {
        return new Evidence(
                type,
                BigDecimal.valueOf(0.55d),
                EvidenceSourceType.PLAIN_SQL,
                "queries.sql",
                "typed SQL observation",
                Map.of());
    }
}
