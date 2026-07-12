package com.relationdetector.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.RelationSubType;
import com.relationdetector.contracts.Enums.RelationType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.metadata.MetadataColumnFact;
import com.relationdetector.contracts.metadata.MetadataSnapshot;
import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.NamingEvidenceCandidate;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.contracts.parse.DdlEvent;
import com.relationdetector.contracts.parse.SourceProvenance;
import com.relationdetector.contracts.scoring.DefaultEvidenceScores;
import com.relationdetector.core.parse.SqlDialect;
import com.relationdetector.core.naming.NamingEvidenceExtractor;
import com.relationdetector.core.naming.NamingRuleConfigLoader;
import com.relationdetector.core.naming.NamingMatchEvidenceEnhancer;
import com.relationdetector.core.naming.NamingEvidencePool;
import com.relationdetector.core.scan.ScanConfig;
import com.relationdetector.core.tokenevent.TokenEventStructuredDdlParser;

class NamingEvidenceExtractorTest {
    private static final YAMLMapper YAML = new YAMLMapper();
    private final NamingEvidenceExtractor extractor = new NamingEvidenceExtractor();

    @Test
    void extractsMetadataNameOnlyEvidenceWithoutCreatingRelationship() {
        MetadataSnapshot metadata = new MetadataSnapshot();
        metadata.columns().add(column("orders", "customer_id"));
        metadata.columns().add(column("customers", "id"));

        List<NamingEvidenceCandidate> evidence = extractor.extractFromMetadata(metadata);

        assertEquals(1, evidence.size());
        NamingEvidenceCandidate match = evidence.get(0);
        assertEndpoint("orders", "customer_id", match.source());
        assertEndpoint("customers", "id", match.target());
        assertEquals("TABLE_ID", match.rule());
        assertEquals("naming:orders.customer_id->customers.id:TABLE_ID", match.id());
        assertTrue(match.directionHint());
        assertEquals(EvidenceType.NAMING_MATCH, match.evidence().type());
    }

    @Test
    void extractsDdlColumnInventoryEvidenceWithoutCreatingRelationship() {
        String ddl = """
                CREATE TABLE customers (
                  id INTEGER PRIMARY KEY
                );
                CREATE TABLE orders (
                  id INTEGER PRIMARY KEY,
                  customer_id INTEGER
                );
                """;
        StructuredParseResult result = new TokenEventStructuredDdlParser(SqlDialect.GENERIC)
                .parseDdl(ddl, "schema.sql", null);

        List<NamingEvidenceCandidate> evidence = extractor.extractFromDdlEvents(result.events());

        assertEquals(1, evidence.size());
        assertEndpoint("orders", "customer_id", evidence.get(0).source());
        assertEndpoint("customers", "id", evidence.get(0).target());
        assertEquals("TABLE_ID", evidence.get(0).rule());
        assertEquals("naming:orders.customer_id->customers.id:TABLE_ID", evidence.get(0).id());
    }

    @Test
    void ddlColumnInventoryDoesNotMatchEveryIdColumnBySuffixOnly() {
        String ddl = """
                CREATE TABLE products (
                  id INTEGER PRIMARY KEY
                );
                CREATE TABLE orders (
                  id INTEGER PRIMARY KEY,
                  customer_id INTEGER
                );
                """;
        StructuredParseResult result = new TokenEventStructuredDdlParser(SqlDialect.GENERIC)
                .parseDdl(ddl, "schema.sql", null);

        List<NamingEvidenceCandidate> evidence = extractor.extractFromDdlEvents(result.events());

        assertTrue(evidence.isEmpty(),
                () -> "independent DDL naming evidence must not turn customer_id into every unrelated id column: "
                        + evidence);
    }

    @Test
    void extractsSqlPredicateNamingEvidenceAndEnhancerCanReusePool() {
        RelationshipCandidate candidate = sqlPredicate("orders", "customer_id", "customers", "id");

        List<NamingEvidenceCandidate> evidence = extractor.extractFromRelationshipCandidates(List.of(candidate));

        assertEquals(1, evidence.size());
        assertEquals("TABLE_ID", evidence.get(0).rule());
        NamingEvidencePool pool = new NamingEvidencePool();
        pool.addAll(evidence);
        new NamingMatchEvidenceEnhancer().enhance(List.of(candidate), pool);
        assertTrue(candidate.evidence().stream()
                .anyMatch(item -> item.type() == EvidenceType.NAMING_MATCH
                        && evidence.get(0).id().equals(item.attributes().get("evidenceRef"))));
    }

    @Test
    void retainsStructuralRelationshipEvidenceAsNamingRawObservation() {
        RelationshipCandidate candidate = sqlPredicate("orders", "customer_id", "customers", "id");
        Evidence structuralEvidence = candidate.evidence().get(0);

        List<NamingEvidenceCandidate> evidence = extractor.extractFromRelationshipCandidates(List.of(candidate));

        assertEquals(List.of(structuralEvidence), evidence.get(0).rawEvidence());
    }

    @Test
    void retainsDdlSourceLineAndStatementForNamingRawObservations() {
        List<StructuredSqlEvent> events = List.of(
                ddlColumn("/workspace/relation-detector/ddl/customers.sql", 3, "customers", "id"),
                ddlColumn("/workspace/relation-detector/ddl/orders.sql", 11, "orders", "customer_id"));

        List<NamingEvidenceCandidate> evidence = extractor.extractFromDdlEvents(events);

        assertEquals(1, evidence.size());
        Evidence ordersObservation = rawObservationForTable(evidence.get(0), "orders");
        assertEquals(EvidenceSourceType.DDL_FILE, ordersObservation.sourceType());
        assertEquals("relation-detector/ddl/orders.sql", ordersObservation.source());
        assertEquals(11L, ordersObservation.attributes().get("line"));
    }

    @Test
    void retainsEveryDistinctDdlObservationForTheSameEndpoint() {
        List<StructuredSqlEvent> events = List.of(
                ddlColumn("schema.sql", 3, "customers", "id"),
                ddlColumn("schema.sql", 11, "orders", "customer_id"),
                ddlColumn("alter.sql", 21, "orders", "customer_id"));

        List<NamingEvidenceCandidate> evidence = extractor.extractFromDdlEvents(events);

        assertEquals(1, evidence.size());
        assertEquals(3, evidence.get(0).rawEvidence().size(),
                "Both DDL observations for orders.customer_id must remain auditable");
        assertEquals(List.of(3L, 11L, 21L), evidence.get(0).rawEvidence().stream()
                .map(item -> item.attributes().get("line"))
                .sorted((left, right) -> Long.compare(((Number) left).longValue(), ((Number) right).longValue()))
                .toList());
    }

    @Test
    void retainsCatalogColumnIdentityForMetadataNamingRawObservations() {
        MetadataSnapshot metadata = new MetadataSnapshot();
        metadata.columnFacts().add(new MetadataColumnFact("sales", "orders", "customer_id",
                "bigint", "bigint", false, null, "", null, 3));
        metadata.columnFacts().add(new MetadataColumnFact("crm", "customers", "id",
                "bigint", "bigint", false, null, "", null, 1));

        List<NamingEvidenceCandidate> evidence = extractor.extractFromMetadata(metadata);

        assertEquals(1, evidence.size());
        Evidence ordersObservation = rawObservationForTable(evidence.get(0), "orders");
        assertEquals(EvidenceSourceType.METADATA, ordersObservation.sourceType());
        assertEquals("sales", ordersObservation.attributes().get("catalogSchema"));
        assertEquals("orders", ordersObservation.attributes().get("catalogTable"));
        assertEquals("customer_id", ordersObservation.attributes().get("catalogColumn"));
    }

    @Test
    void extractsWarehousePluralTableIdEvidence() {
        RelationshipCandidate candidate = sqlPredicate("sales_orders", "warehouse_id", "warehouses", "id");

        List<NamingEvidenceCandidate> evidence = extractor.extractFromRelationshipCandidates(List.of(candidate));

        assertEquals(1, evidence.size());
        assertEquals("TABLE_ID", evidence.get(0).rule());
        assertEndpoint("sales_orders", "warehouse_id", evidence.get(0).source());
        assertEndpoint("warehouses", "id", evidence.get(0).target());
    }

    @Test
    void extractsNamingEvidenceFromDdlForeignKeyCandidate() {
        RelationshipCandidate candidate = ddlForeignKey("ap_invoices", "purchase_order_id", "purchase_orders", "id");

        List<NamingEvidenceCandidate> evidence = extractor.extractFromRelationshipCandidates(List.of(candidate));

        assertEquals(1, evidence.size());
        assertEquals("TABLE_ID", evidence.get(0).rule());
        assertEndpoint("ap_invoices", "purchase_order_id", evidence.get(0).source());
        assertEndpoint("purchase_orders", "id", evidence.get(0).target());
    }

    @Test
    void doesNotExtractUnrelatedDdlForeignKeyNamingEvidence() {
        RelationshipCandidate candidate = ddlForeignKey("sales_order_items", "product_id", "positions", "id");

        List<NamingEvidenceCandidate> evidence = extractor.extractFromRelationshipCandidates(List.of(candidate));

        assertTrue(evidence.isEmpty(),
                () -> "DDL-backed naming evidence still must not turn product_id into positions.id: " + evidence);
    }

    @Test
    void doesNotExtractAmbiguousTwoForeignKeyStyleColumns() {
        RelationshipCandidate candidate = sqlPredicate("orders", "customer_id", "payments", "order_id");

        List<NamingEvidenceCandidate> evidence = extractor.extractFromRelationshipCandidates(List.of(candidate));

        assertTrue(evidence.isEmpty());
    }

    @Test
    void extractsRelatedIdSuffixToIdEvidence() {
        RelationshipCandidate candidate = sqlPredicate("purchase_order_items", "order_id", "purchase_orders", "id");

        List<NamingEvidenceCandidate> evidence = extractor.extractFromRelationshipCandidates(List.of(candidate));

        assertEquals(1, evidence.size());
        assertEquals("ID_SUFFIX_TO_ID", evidence.get(0).rule());
        assertEndpoint("purchase_order_items", "order_id", evidence.get(0).source());
        assertEndpoint("purchase_orders", "id", evidence.get(0).target());
    }

    @Test
    void extractsCompositeRelatedIdSuffixToIdEvidence() {
        RelationshipCandidate candidate = sqlPredicate("boms", "component_product_id", "products", "id");

        List<NamingEvidenceCandidate> evidence = extractor.extractFromRelationshipCandidates(List.of(candidate));

        assertEquals(1, evidence.size());
        assertEquals("ID_SUFFIX_TO_ID", evidence.get(0).rule());
        assertEndpoint("boms", "component_product_id", evidence.get(0).source());
        assertEndpoint("products", "id", evidence.get(0).target());
    }

    @Test
    void doesNotExtractUnrelatedIdSuffixToIdEvidence() {
        RelationshipCandidate candidate = sqlPredicate("sales_order_items", "product_id", "positions", "id");

        List<NamingEvidenceCandidate> evidence = extractor.extractFromRelationshipCandidates(List.of(candidate));

        assertTrue(evidence.isEmpty(),
                () -> "product_id must not become name-only evidence for an unrelated positions.id endpoint: "
                        + evidence);
    }

    @Test
    void doesNotExtractPolymorphicReferenceIdSuffixEvidence() {
        RelationshipCandidate candidate = sqlPredicate("cashier_journals", "reference_id", "purchase_orders", "id");

        List<NamingEvidenceCandidate> evidence = extractor.extractFromRelationshipCandidates(List.of(candidate));

        assertTrue(evidence.isEmpty(),
                () -> "reference_id is a polymorphic id and must not point to an arbitrary target table by naming alone: "
                        + evidence);
    }

    @Test
    void extractsSelfRoleIdEvidence() {
        RelationshipCandidate candidate = sqlPredicate("employees", "manager_id", "employees", "id");
        candidate.evidence().add(new Evidence(EvidenceType.SQL_LOG_COLUMN_CO_OCCURRENCE,
                java.math.BigDecimal.valueOf(DefaultEvidenceScores.SQL_LOG_COLUMN_CO_OCCURRENCE),
                EvidenceSourceType.PLAIN_SQL,
                "query.sql",
                "self join",
                java.util.Map.of("selfJoinRole", true)));

        List<NamingEvidenceCandidate> evidence = extractor.extractFromRelationshipCandidates(List.of(candidate));

        assertEquals(1, evidence.size());
        assertEquals("SELF_ROLE_ID", evidence.get(0).rule());
        assertEndpoint("employees", "manager_id", evidence.get(0).source());
        assertEndpoint("employees", "id", evidence.get(0).target());
    }

    @Test
    void extractsSelfRoleIdEvidenceFromDdlSelfForeignKey() {
        RelationshipCandidate candidate = ddlForeignKey("employees", "manager_id", "employees", "id");

        List<NamingEvidenceCandidate> evidence = extractor.extractFromRelationshipCandidates(List.of(candidate));

        assertEquals(1, evidence.size());
        assertEquals("SELF_ROLE_ID", evidence.get(0).rule());
        assertEndpoint("employees", "manager_id", evidence.get(0).source());
        assertEndpoint("employees", "id", evidence.get(0).target());
    }

    @Test
    void doesNotExtractPlainIdToIdEvidence() {
        RelationshipCandidate candidate = sqlPredicate("a", "id", "b", "id");

        List<NamingEvidenceCandidate> evidence = extractor.extractFromRelationshipCandidates(List.of(candidate));

        assertTrue(evidence.isEmpty());
    }

    @Test
    void configuredSuffixAliasRuleCreatesUserConfiguredNamingEvidence() throws Exception {
        RelationshipCandidate candidate = sqlPredicate("orders", "created_by", "employees", "id");
        ScanConfig config = configuredRules("""
                - id: created-by-user
                  rule: USER_CONFIGURED
                  appliesTo: [RELATIONSHIP_CANDIDATE]
                  sourceColumn:
                    equalsAny: [created_by, updated_by, approved_by]
                  targetTable:
                    aliases: [users, employees]
                  targetColumn:
                    equals: id
                  directionHint: true
                  description: "audit user columns point to employee/user ids"
                """);

        List<NamingEvidenceCandidate> evidence =
                extractor.extractFromRelationshipCandidates(List.of(candidate), config);

        assertEquals(1, evidence.size());
        NamingEvidenceCandidate match = evidence.get(0);
        assertEquals("USER_CONFIGURED", match.rule());
        assertEndpoint("orders", "created_by", match.source());
        assertEndpoint("employees", "id", match.target());
        assertEquals("created-by-user", match.evidence().attributes().get("configuredRuleId"));
        assertEquals("inline", match.evidence().attributes().get("ruleSource"));
    }

    @Test
    void configuredExplicitEndpointRuleCanCreateMetadataNamingEvidenceOnly() throws Exception {
        MetadataSnapshot metadata = new MetadataSnapshot();
        metadata.columns().add(column("orders", "sales_rep_id"));
        metadata.columns().add(column("employees", "id"));
        ScanConfig config = configuredRules("""
                - id: sales-rep-explicit
                  rule: USER_CONFIGURED
                  appliesTo: [METADATA]
                  sourceEndpoint: orders.sales_rep_id
                  targetEndpoint: employees.id
                  directionHint: true
                """);

        List<NamingEvidenceCandidate> evidence = extractor.extractFromMetadata(metadata, config);

        assertEquals(1, evidence.size());
        assertEquals("USER_CONFIGURED", evidence.get(0).rule());
        assertEndpoint("orders", "sales_rep_id", evidence.get(0).source());
        assertEndpoint("employees", "id", evidence.get(0).target());
    }

    @Test
    void configuredRuleIsReusedByRelationshipEnhancerThroughPool() throws Exception {
        RelationshipCandidate candidate = sqlPredicate("orders", "created_by", "employees", "id");
        ScanConfig config = configuredRules("""
                - id: created-by-user
                  rule: USER_CONFIGURED
                  appliesTo: [RELATIONSHIP_CANDIDATE]
                  sourceColumn:
                    equals: created_by
                  targetTable:
                    aliases: [employees]
                  targetColumn:
                    equals: id
                """);
        NamingEvidencePool pool = new NamingEvidencePool();
        pool.addAll(extractor.extractFromRelationshipCandidates(List.of(candidate), config));

        new NamingMatchEvidenceEnhancer().enhance(List.of(candidate), pool);

        Evidence naming = candidate.evidence().stream()
                .filter(item -> item.type() == EvidenceType.NAMING_MATCH)
                .findFirst()
                .orElseThrow();
        assertEquals("USER_CONFIGURED", naming.attributes().get("namingRule"));
        assertEquals("created-by-user", naming.attributes().get("configuredRuleId"));
        assertTrue(String.valueOf(naming.attributes().get("evidenceRef")).endsWith(":USER_CONFIGURED"));
    }

    private ScanConfig configuredRules(String yaml) throws Exception {
        ScanConfig config = new ScanConfig();
        config.namingMatchSystemRulesEnabled = false;
        config.namingMatchRules.addAll(new NamingRuleConfigLoader().readInlineRules(YAML.readTree(yaml)));
        return config;
    }

    private RelationshipCandidate sqlPredicate(String leftTable, String leftColumn, String rightTable, String rightColumn) {
        RelationshipCandidate candidate = new RelationshipCandidate(
                Endpoint.column(column(leftTable, leftColumn)),
                Endpoint.column(column(rightTable, rightColumn)),
                RelationType.CO_OCCURRENCE,
                RelationSubType.COLUMN_CO_OCCURRENCE);
        candidate.evidence().add(Evidence.of(EvidenceType.SQL_LOG_JOIN,
                DefaultEvidenceScores.SQL_LOG_JOIN,
                EvidenceSourceType.PLAIN_SQL,
                "query.sql",
                leftTable + "." + leftColumn + " = " + rightTable + "." + rightColumn));
        return candidate;
    }

    private RelationshipCandidate ddlForeignKey(
            String leftTable,
            String leftColumn,
            String rightTable,
            String rightColumn
    ) {
        RelationshipCandidate candidate = new RelationshipCandidate(
                Endpoint.column(column(leftTable, leftColumn)),
                Endpoint.column(column(rightTable, rightColumn)),
                RelationType.FK_LIKE,
                RelationSubType.DDL_DECLARED_FK);
        candidate.evidence().add(Evidence.of(EvidenceType.DDL_FOREIGN_KEY,
                DefaultEvidenceScores.DDL_FOREIGN_KEY,
                EvidenceSourceType.DDL_FILE,
                "schema.sql",
                leftTable + "." + leftColumn + " references " + rightTable + "." + rightColumn));
        return candidate;
    }

    private ColumnRef column(String tableName, String columnName) {
        return ColumnRef.of(TableId.of(null, tableName), columnName);
    }

    private StructuredSqlEvent ddlColumn(String source, long line, String table, String column) {
        return new DdlEvent(StructuredParseEventType.DDL_COLUMN,
                SourceProvenance.source(source, line), "", "", "", "",
                table, column, "", "", 1, 1);
    }

    private Evidence rawObservationForTable(NamingEvidenceCandidate candidate, String table) {
        return candidate.rawEvidence().stream()
                .filter(evidence -> table.equals(evidence.attributes().get("catalogTable"))
                        || table.equals(evidence.attributes().get("table")))
                .findFirst()
                .orElseThrow();
    }

    private void assertEndpoint(String table, String column, Endpoint endpoint) {
        assertEquals(table, endpoint.table().tableName());
        assertEquals(column, endpoint.column().columnName());
    }
}
