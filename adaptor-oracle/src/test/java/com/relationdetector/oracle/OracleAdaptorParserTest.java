package com.relationdetector.oracle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.core.fullgrammer.FullGrammerDialectModule;
import com.relationdetector.core.lineage.TokenEventDataLineageExtractor;
import com.relationdetector.oracle.fullgrammer.v26ai.OracleFullGrammerDialectModule;

class OracleAdaptorParserTest {
    @Test
    void serviceLoaderDiscoversOracleAdaptor() {
        List<DatabaseAdaptor> adaptors = ServiceLoader.load(DatabaseAdaptor.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .filter(adaptor -> adaptor.supportedDatabaseTypes().contains(DatabaseType.ORACLE))
                .toList();

        assertTrue(adaptors.stream().anyMatch(adaptor -> adaptor.id().equals("oracle")));
    }

    @Test
    void oracleIdentifierRulesUppercaseUnquotedIdentifiers() {
        OracleDatabaseAdaptor adaptor = new OracleDatabaseAdaptor();

        assertEquals("CUSTOMERS", adaptor.identifierRules().normalize("customers"));
        assertEquals("MixedCase", adaptor.identifierRules().normalize("\"MixedCase\""));
    }

    @Test
    void tokenEventParserEmitsPortableJoinAndLineageEvents() {
        OracleDatabaseAdaptor adaptor = new OracleDatabaseAdaptor();
        var result = adaptor.structuredSqlParser().orElseThrow().parseSql(statement("""
                INSERT INTO sales_summary (customer_id, total_amount)
                SELECT c.id, SUM(o.amount)
                FROM customers c
                JOIN orders o ON o.customer_id = c.id
                GROUP BY c.id
                """), null);

        assertTrue(result.events().stream().anyMatch(event ->
                event.type() == StructuredParseEventType.PREDICATE_EQUALITY));
        assertTrue(result.events().stream().anyMatch(event ->
                event.type() == StructuredParseEventType.INSERT_SELECT_MAPPING));
        assertEquals("ORACLE", result.dialect());
    }

    @Test
    void oracleFullGrammerModuleCarriesVersionProfileAttributes() {
        FullGrammerDialectModule module = new OracleFullGrammerDialectModule();
        var result = module.sqlParser().parseSql(statement("SELECT c.id FROM customers c"), null);

        assertEquals("oracle-26ai", module.profile().id());
        assertEquals("ORACLE_FULL_GRAMMER_PARSE_TREE", result.backend());
        assertEquals("oracle-26ai", result.attributes().get("fullGrammerProfile"));
        assertEquals("INCOMPLETE_VERSIONED", result.attributes().get("grammarCoverage"));
    }

    @Test
    void oracleFullGrammerParsesProcedureInsertSelectLineageEvents() {
        FullGrammerDialectModule module = new OracleFullGrammerDialectModule();
        var result = module.sqlParser().parseSql(statement("""
                CREATE OR REPLACE PROCEDURE sp_create_reconciliation()
                AS
                BEGIN
                    INSERT INTO reconciliation_items (journal_id, transaction_date, description, debit_amount)
                    SELECT
                        cj.id,
                        cj.journal_date,
                        cj.journal_type || ' - ' || COALESCE(cj.counterparty, ''),
                        CASE WHEN cj.journal_type IN ('bank_in', 'cash_in') THEN cj.amount ELSE 0 END
                    FROM cashier_journals cj
                    WHERE cj.account_id = p_account_id
                      AND cj.journal_date BETWEEN p_period_start AND p_period_end;
                END;
                /
                """), null);

        assertEquals(0, result.attributes().get("syntaxErrors"));
        assertTrue(result.events().stream().anyMatch(event ->
                event.type() == StructuredParseEventType.INSERT_SELECT_MAPPING
                        && "reconciliation_items".equals(event.attributes().get("targetTable"))
                        && "journal_id".equals(event.attributes().get("targetColumn"))));
        assertTrue(result.events().stream().anyMatch(event ->
                event.type() == StructuredParseEventType.INSERT_SELECT_MAPPING
                        && "reconciliation_items".equals(event.attributes().get("targetTable"))
                        && "debit_amount".equals(event.attributes().get("targetColumn"))));
    }

    @Test
    void oracleFullGrammerDdlParserEmitsForeignKeyEvents() {
        FullGrammerDialectModule module = new OracleFullGrammerDialectModule();
        var result = module.structuredDdlParser().parseDdl("""
                CREATE TABLE customers (
                    id NUMBER(19) GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY
                );
                CREATE TABLE orders (
                    id NUMBER(19) GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    customer_id NUMBER(19),
                    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES customers(id)
                );
                """, "oracle-ddl-test.sql", null);

        assertEquals(0, result.attributes().get("syntaxErrors"));
        assertTrue(result.events().stream().anyMatch(event ->
                event.type() == StructuredParseEventType.DDL_FOREIGN_KEY
                        && "orders".equals(event.attributes().get("sourceTable"))
                        && "customer_id".equals(event.attributes().get("sourceColumn"))
                        && "customers".equals(event.attributes().get("targetTable"))
                        && "id".equals(event.attributes().get("targetColumn"))));
    }

    @Test
    void oracleDdlParsersAcceptCommentOnTableAndColumnWithoutRelationshipEvents() {
        String ddl = """
                CREATE TABLE "person_EXTEND" (
                    "psn_code" NUMBER(18),
                    "psn_version" VARCHAR2(17),
                    "psn_xml" XMLTYPE,
                    comment VARCHAR2(500),
                    CONSTRAINT "PK_person_EXTEND" PRIMARY KEY ("psn_code")
                );
                COMMENT ON COLUMN "person_EXTEND"."psn_xml" IS '人员xml';
                COMMENT ON TABLE "person_EXTEND" IS '人员扩展表';
                """;
        List<StructuredDdlParser> parsers = List.of(
                new OracleDatabaseAdaptor().structuredDdlParser().orElseThrow(),
                new com.relationdetector.oracle.fullgrammer.v12c.OracleFullGrammerDialectModule().structuredDdlParser(),
                new com.relationdetector.oracle.fullgrammer.v19c.OracleFullGrammerDialectModule().structuredDdlParser(),
                new com.relationdetector.oracle.fullgrammer.v21c.OracleFullGrammerDialectModule().structuredDdlParser(),
                new OracleFullGrammerDialectModule().structuredDdlParser());

        for (StructuredDdlParser parser : parsers) {
            var result = parser.parseDdl(ddl, "oracle-comment-ddl.sql", null);

            assertEquals(0, result.attributes().get("syntaxErrors"),
                    result.backend() + " should parse Oracle COMMENT ON DDL");
            assertTrue(result.events().stream().noneMatch(event ->
                            event.type() == StructuredParseEventType.DDL_FOREIGN_KEY),
                    result.backend() + " should not turn comment-only metadata into relationships");
        }
    }

    @Test
    void oracleTokenEventMergeUpdateEmitsLineageMappings() {
        var parser = new OracleDatabaseAdaptor().structuredSqlParser().orElseThrow();
        var statement = statement(oracleMergeUpdateSql());
        var result = parser.parseSql(statement, null);

        assertEquals(0, result.attributes().get("syntaxErrors"));
        assertTrue(result.events().stream().anyMatch(event ->
                event.type() == StructuredParseEventType.MERGE_WRITE_MAPPING
                        && "commission_amount".equals(event.attributes().get("targetColumn"))));

        Set<String> lineages = lineage(statement, result);
        assertTrue(lineages.contains("VALUE:ARITHMETIC:sales_commissions.commission_amount,"
                + "sales_commissions.base_amount->sales_commissions.commission_amount"), () -> lineages.toString());
        assertTrue(lineages.contains("VALUE:ARITHMETIC:sales_commissions.bonus->sales_commissions.bonus"),
                () -> lineages.toString());
    }

    @Test
    void oracleFullGrammerMergeUpdateEmitsLineageMappingsForEveryVersion() {
        List<FullGrammerDialectModule> modules = List.of(
                new com.relationdetector.oracle.fullgrammer.v12c.OracleFullGrammerDialectModule(),
                new com.relationdetector.oracle.fullgrammer.v19c.OracleFullGrammerDialectModule(),
                new com.relationdetector.oracle.fullgrammer.v21c.OracleFullGrammerDialectModule(),
                new OracleFullGrammerDialectModule());
        var statement = statement(oracleMergeUpdateSql());

        for (FullGrammerDialectModule module : modules) {
            var result = module.sqlParser().parseSql(statement, null);

            assertEquals(0, result.attributes().get("syntaxErrors"),
                    module.profile().id() + " should parse Oracle MERGE UPDATE");
            assertTrue(result.events().stream().anyMatch(event ->
                    event.type() == StructuredParseEventType.MERGE_WRITE_MAPPING
                            && "commission_amount".equals(event.attributes().get("targetColumn"))),
                    module.profile().id() + " should emit MERGE_WRITE_MAPPING");

            Set<String> lineages = lineage(statement, result);
            assertTrue(lineages.contains("VALUE:ARITHMETIC:sales_commissions.commission_amount,"
                    + "sales_commissions.base_amount->sales_commissions.commission_amount"),
                    () -> module.profile().id() + " " + lineages);
            assertTrue(lineages.contains("VALUE:ARITHMETIC:sales_commissions.bonus->sales_commissions.bonus"),
                    () -> module.profile().id() + " " + lineages);
        }
    }

    @Test
    void oracleFullGrammerCommissionProcedureBlockEmitsInsertAndMergeLineageForEveryVersion() {
        List<FullGrammerDialectModule> modules = List.of(
                new com.relationdetector.oracle.fullgrammer.v12c.OracleFullGrammerDialectModule(),
                new com.relationdetector.oracle.fullgrammer.v19c.OracleFullGrammerDialectModule(),
                new com.relationdetector.oracle.fullgrammer.v21c.OracleFullGrammerDialectModule(),
                new OracleFullGrammerDialectModule());
        var statement = statement(oracleCommissionInsertAndMergeSql());

        for (FullGrammerDialectModule module : modules) {
            var result = module.sqlParser().parseSql(statement, null);

            assertEquals(0, result.attributes().get("syntaxErrors"),
                    module.profile().id() + " should parse commission INSERT SELECT plus MERGE");

            Set<String> lineages = lineage(statement, result);
            assertTrue(lineages.contains("VALUE:DIRECT:sales_orders.salesperson_id->sales_commissions.employee_id"),
                    () -> module.profile().id() + " " + lineages);
            assertTrue(lineages.contains("VALUE:COALESCE:commission_rules.commission_rate->sales_commissions.commission_rate"),
                    () -> module.profile().id() + " " + lineages);
            assertTrue(lineages.contains("VALUE:ARITHMETIC:sales_commissions.commission_amount,"
                    + "sales_commissions.base_amount->sales_commissions.commission_amount"),
                    () -> module.profile().id() + " " + lineages);
        }
    }

    @Test
    void oracleFullGrammerEnforcesVersionSpecificSyntaxBoundaries() {
        String memoptimize19c = """
                CREATE TABLE fast_lookup (
                    id NUMBER PRIMARY KEY,
                    code VARCHAR2(64)
                ) MEMOPTIMIZE FOR READ
                """;
        String sqlMacro21c = """
                CREATE OR REPLACE FUNCTION active_customer_filter
                RETURN VARCHAR2 SQL_MACRO(SCALAR)
                IS
                BEGIN
                    RETURN 'status = ''ACTIVE''';
                END;
                """;
        String vector26ai = """
                CREATE TABLE product_embeddings (
                    product_id NUMBER PRIMARY KEY,
                    embedding VECTOR(3, FLOAT32)
                )
                """;

        assertSyntaxErrors(new com.relationdetector.oracle.fullgrammer.v12c.OracleFullGrammerDialectModule(),
                memoptimize19c);
        assertParses(new com.relationdetector.oracle.fullgrammer.v19c.OracleFullGrammerDialectModule(),
                memoptimize19c);

        assertSyntaxErrors(new com.relationdetector.oracle.fullgrammer.v12c.OracleFullGrammerDialectModule(),
                sqlMacro21c);
        assertSyntaxErrors(new com.relationdetector.oracle.fullgrammer.v19c.OracleFullGrammerDialectModule(),
                sqlMacro21c);
        assertParses(new com.relationdetector.oracle.fullgrammer.v21c.OracleFullGrammerDialectModule(),
                sqlMacro21c);

        assertSyntaxErrors(new com.relationdetector.oracle.fullgrammer.v12c.OracleFullGrammerDialectModule(),
                vector26ai);
        assertSyntaxErrors(new com.relationdetector.oracle.fullgrammer.v19c.OracleFullGrammerDialectModule(),
                vector26ai);
        assertSyntaxErrors(new com.relationdetector.oracle.fullgrammer.v21c.OracleFullGrammerDialectModule(),
                vector26ai);
        assertParses(new com.relationdetector.oracle.fullgrammer.v26ai.OracleFullGrammerDialectModule(),
                vector26ai);
    }

    @Test
    void oracleFullGrammerRejectsPostgresAndMysqlStructuralSyntaxForEveryVersion() {
        List<FullGrammerDialectModule> modules = List.of(
                new com.relationdetector.oracle.fullgrammer.v12c.OracleFullGrammerDialectModule(),
                new com.relationdetector.oracle.fullgrammer.v19c.OracleFullGrammerDialectModule(),
                new com.relationdetector.oracle.fullgrammer.v21c.OracleFullGrammerDialectModule(),
                new OracleFullGrammerDialectModule());
        List<String> nonOracleSql = List.of(
                "CREATE UNLOGGED TABLE audit_buffer (id NUMBER)",
                "CREATE TABLE IF NOT EXISTS customers_shadow (id NUMBER)",
                "CREATE INDEX CONCURRENTLY idx_orders_customer ON orders (customer_id)",
                """
                MERGE INTO customers c
                USING customer_stage s
                ON (c.id = s.id)
                WHEN NOT MATCHED THEN DO NOTHING
                """);

        for (FullGrammerDialectModule module : modules) {
            for (String sql : nonOracleSql) {
                assertSyntaxErrors(module, sql);
            }
        }
    }

    private SqlStatementRecord statement(String sql) {
        return new SqlStatementRecord(sql, StatementSourceType.PLAIN_SQL, "oracle-test.sql", 1, 1,
                java.util.Map.of());
    }

    private Set<String> lineage(SqlStatementRecord statement, com.relationdetector.contracts.parse.StructuredParseResult result) {
        return new TokenEventDataLineageExtractor().extract(statement, result).stream()
                .map(this::lineageFingerprint)
                .collect(Collectors.toCollection(java.util.TreeSet::new));
    }

    private String lineageFingerprint(DataLineageCandidate lineage) {
        return lineage.flowKind() + ":"
                + lineage.transformType() + ":"
                + lineage.sources().stream()
                .map(com.relationdetector.contracts.model.Endpoint::displayName)
                .collect(Collectors.joining(","))
                + "->" + lineage.target().displayName();
    }

    private String oracleMergeUpdateSql() {
        return """
                MERGE INTO sales_commissions sc
                USING (
                    SELECT employee_id, SUM(base_amount) AS total_sales
                    FROM sales_commissions
                    WHERE period = p_period
                    GROUP BY employee_id
                    HAVING SUM(base_amount) > 300000
                ) top_sales
                ON (sc.employee_id = top_sales.employee_id AND sc.period = p_period)
                WHEN MATCHED THEN UPDATE SET
                    commission_amount = sc.commission_amount + ROUND(sc.base_amount * 0.02, 2),
                    bonus = sc.bonus + 5000
                """;
    }

    private String oracleCommissionInsertAndMergeSql() {
        return """
                INSERT INTO sales_commissions (employee_id, order_id, order_item_id, period,
                        base_amount, commission_rate, commission_amount, bonus, status, calculated_at)
                    SELECT
                        so.salesperson_id,
                        so.id,
                        soi.id,
                        p_period,
                        soi.amount,
                        COALESCE(cr.commission_rate, 0.02),
                        ROUND(soi.amount * COALESCE(cr.commission_rate, 0.02), 2),
                        COALESCE(cr.bonus, 0),
                        'calculated',
                        SYSTIMESTAMP
                    FROM sales_order_items soi
                    JOIN sales_orders so ON soi.order_id = so.id
                    JOIN products p ON soi.product_id = p.id
                    LEFT JOIN commission_rules cr ON (
                        (cr.product_category_id IS NULL OR cr.product_category_id = p.category_id)
                        AND soi.amount >= cr.min_amount
                        AND soi.amount < cr.max_amount
                        AND cr.status = 'active'
                        AND cr.effective_date <= so.order_date
                        AND (cr.expiry_date IS NULL OR cr.expiry_date >= so.order_date)
                    )
                    WHERE so.order_date BETWEEN v_start_date AND v_end_date
                      AND so.status NOT IN ('draft', 'cancelled');

                MERGE INTO sales_commissions sc
                USING (
                    SELECT employee_id, SUM(base_amount) AS total_sales
                    FROM sales_commissions
                    WHERE period = p_period
                    GROUP BY employee_id
                    HAVING SUM(base_amount) > 300000
                ) top_sales
                ON (sc.employee_id = top_sales.employee_id AND sc.period = p_period)
                WHEN MATCHED THEN UPDATE SET
                    commission_amount = sc.commission_amount + ROUND(sc.base_amount * 0.02, 2),
                    bonus = sc.bonus + 5000
                """;
    }

    private void assertParses(FullGrammerDialectModule module, String sql) {
        var result = module.sqlParser().parseSql(statement(sql), null);
        assertEquals(0, result.attributes().get("syntaxErrors"), module.profile().id() + " should parse " + sql);
    }

    private void assertSyntaxErrors(FullGrammerDialectModule module, String sql) {
        var result = module.sqlParser().parseSql(statement(sql), null);
        assertTrue((Integer) result.attributes().get("syntaxErrors") > 0,
                module.profile().id() + " should reject " + sql);
    }
}
