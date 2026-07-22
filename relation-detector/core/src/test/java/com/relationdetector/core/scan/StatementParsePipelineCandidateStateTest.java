package com.relationdetector.core.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

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
import com.relationdetector.core.identity.CanonicalIdentifierResolver;
import com.relationdetector.core.identity.NamespaceContext;

class StatementParsePipelineCandidateStateTest {
    @Test
    void liveDdlNamespaceQualificationPreservesCompleteCandidateState() throws Exception {
        RelationshipCandidate candidate = new RelationshipCandidate(
                Endpoint.column(ColumnRef.of(TableId.of(null, "orders"), "customer_id")),
                Endpoint.column(ColumnRef.of(TableId.of(null, "customers"), "id")),
                RelationType.FK_LIKE,
                RelationSubType.DDL_DECLARED_FK);
        candidate.confidence(BigDecimal.valueOf(0.91d));
        candidate.evidence().add(Evidence.of(EvidenceType.DDL_FOREIGN_KEY, 0.92d,
                EvidenceSourceType.DATABASE_DDL, "SHOW CREATE TABLE", "fk"));
        candidate.rawEvidence().add(Evidence.of(EvidenceType.SOURCE_INDEX, 0.55d,
                EvidenceSourceType.DATABASE_DDL, "SHOW CREATE TABLE", "index"));
        candidate.warnings().add(WarningMessage.warn(
                WarningType.PARSE_WARNING, "DDL_WARNING", "warning", "SHOW CREATE TABLE", 1));
        candidate.attributes().put("conditional", true);
        candidate.attributes().put("conditions", List.of(Map.of("operator", "EQUALS", "value", "customer")));

        Method qualify = StatementParsePipeline.class.getDeclaredMethod(
                "qualifyDatabaseDdlCandidate",
                RelationshipCandidate.class,
                CanonicalIdentifierResolver.class,
                NamespaceContext.class);
        qualify.setAccessible(true);
        RelationshipCandidate qualified = (RelationshipCandidate) qualify.invoke(
                new StatementParsePipeline(),
                candidate,
                new CanonicalIdentifierResolver(value -> value == null ? "" : value.toLowerCase()),
                new NamespaceContext("catalog_a", "shop", List.of()));

        assertEquals("catalog_a.shop.orders.customer_id", qualified.source().displayName());
        assertEquals("catalog_a.shop.customers.id", qualified.target().displayName());
        assertEquals(candidate.confidence(), qualified.confidence());
        assertEquals(candidate.evidence(), qualified.evidence());
        assertEquals(candidate.rawEvidence(), qualified.rawEvidence());
        assertEquals(candidate.warnings(), qualified.warnings());
        assertEquals(candidate.attributes(), qualified.attributes());
    }
}
