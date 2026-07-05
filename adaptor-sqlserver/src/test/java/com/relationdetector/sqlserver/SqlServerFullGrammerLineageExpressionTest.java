package com.relationdetector.sqlserver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.model.NamingEvidenceCandidate;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.core.lineage.StructuredDataLineageExtractor;
import com.relationdetector.core.relation.DdlRelationExtractionVisitor;
import com.relationdetector.core.relation.NamingEvidenceExtractor;
import com.relationdetector.sqlserver.fullgrammer.v2025.SqlServer2025FullGrammerDialectModule;
import com.relationdetector.sqlserver.tokenevent.SqlServerTokenEventStructuredSqlParser;
import com.relationdetector.sqlserver.tokenevent.SqlServerTokenEventStructuredDdlParser;

class SqlServerFullGrammerLineageExpressionTest {
    @Test
    void fullGrammerClassifiesInsertSelectExpressionTransforms() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                INSERT INTO dbo.sales_fact (
                    sales_amount,
                    discount_amount,
                    sales_status,
                    gross_margin
                )
                SELECT
                    SUM(oi.quantity),
                    ISNULL(oi.discount_amount, 0),
                    CASE WHEN SUM(oi.quantity) > 0 THEN 'ACTIVE' ELSE 'EMPTY' END,
                    oi.quantity * oi.unit_price
                FROM dbo.sales_order_items AS oi
                GROUP BY oi.order_id, oi.discount_amount, oi.quantity, oi.unit_price;
                """, StatementSourceType.PLAIN_SQL, "sqlserver-full-lineage-expression.sql", 1, 15, Map.of());

        StructuredParseResult result = new SqlServer2025FullGrammerDialectModule()
                .sqlParser()
                .parseSql(statement, null);

        Set<String> transforms = result.events().stream()
                .filter(event -> event.type() == StructuredParseEventType.INSERT_SELECT_MAPPING)
                .map(StructuredSqlEvent::attributes)
                .map(attributes -> String.valueOf(attributes.get("transformType")))
                .collect(Collectors.toSet());

        assertTrue(transforms.contains("AGGREGATE"),
                () -> "Expected AGGREGATE mapping, transforms=" + transforms + ", events=" + result.events());
        assertTrue(transforms.contains("COALESCE"),
                () -> "Expected COALESCE mapping, transforms=" + transforms + ", events=" + result.events());
        assertTrue(transforms.contains("CASE_WHEN"),
                () -> "Expected CASE_WHEN mapping, transforms=" + transforms + ", events=" + result.events());
        assertTrue(transforms.contains("ARITHMETIC"),
                () -> "Expected ARITHMETIC mapping, transforms=" + transforms + ", events=" + result.events());
    }

    @Test
    void fullGrammerPreservesExplicitSchemaInLineageTargets() {
        SqlStatementRecord statement = schemaQualifiedInsertSelectStatement("sqlserver-full-lineage-schema.sql");
        StructuredParseResult result = new SqlServer2025FullGrammerDialectModule()
                .sqlParser()
                .parseSql(statement, null);

        assertSchemaQualifiedLineageTarget(statement, result);
    }

    @Test
    void tokenEventPreservesExplicitSchemaInLineageTargets() {
        SqlStatementRecord statement = schemaQualifiedInsertSelectStatement("sqlserver-token-lineage-schema.sql");
        StructuredParseResult result = new SqlServerTokenEventStructuredSqlParser().parseSql(statement, null);

        assertSchemaQualifiedLineageTarget(statement, result);
    }

    @Test
    void fullGrammerDdlEmitsColumnInventoryForNamingEvidence() {
        StructuredParseResult result = new SqlServer2025FullGrammerDialectModule()
                .structuredDdlParser()
                .parseDdl("""
                        CREATE TABLE dbo.orders (
                            id BIGINT PRIMARY KEY,
                            customer_id BIGINT,
                            CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES dbo.customers(id)
                        );
                        """, "sqlserver-ddl-column-inventory.sql", null);

        assertTrue(result.events().stream().anyMatch(event ->
                        event.type() == StructuredParseEventType.DDL_COLUMN
                                && "dbo.orders".equals(event.attributes().get("table"))
                                && "customer_id".equals(event.attributes().get("column"))),
                () -> "SQL Server full-grammer DDL should emit DDL_COLUMN inventory events: "
                        + result.events());
    }

    @Test
    void tokenEventDdlPreservesExplicitSchemaInRelationshipsAndNamingInventory() {
        StructuredParseResult result = new SqlServerTokenEventStructuredDdlParser().parseDdl("""
                CREATE TABLE [dbo].[departments] (
                    [id] BIGINT PRIMARY KEY
                );
                CREATE TABLE [dbo].[employees] (
                    [id] BIGINT PRIMARY KEY,
                    [department_id] BIGINT,
                    CONSTRAINT [fk_emp_dept] FOREIGN KEY ([department_id]) REFERENCES [dbo].[departments] ([id])
                );
                """, "sqlserver-token-ddl-schema.sql", null);

        assertTrue(new DdlRelationExtractionVisitor().extract("", "sqlserver-token-ddl-schema.sql", result)
                        .stream()
                        .anyMatch(relation ->
                                "dbo.employees".equals(relation.source().table().displayName())
                                        && "dbo".equals(relation.source().table().schema())
                                        && "department_id".equals(relation.source().column().columnName())
                                        && "dbo.departments".equals(relation.target().table().displayName())
                                        && "dbo".equals(relation.target().table().schema())
                                        && "id".equals(relation.target().column().columnName())),
                () -> "Expected schema-qualified DDL relationship, events=" + result.events());
        assertTrue(namingEvidence(result).stream().anyMatch(candidate ->
                        "dbo.employees".equals(candidate.source().table().displayName())
                                && "dbo".equals(candidate.source().table().schema())
                                && "department_id".equals(candidate.source().column().columnName())
                                && "dbo.departments".equals(candidate.target().table().displayName())
                                && "dbo".equals(candidate.target().table().schema())
                                && "id".equals(candidate.target().column().columnName())),
                () -> "Expected schema-qualified DDL naming evidence, events=" + result.events());
    }

    @Test
    void tokenEventDdlDoesNotInventDefaultSchemaForUnqualifiedTables() {
        StructuredParseResult result = new SqlServerTokenEventStructuredDdlParser().parseDdl("""
                CREATE TABLE [departments] (
                    [id] BIGINT PRIMARY KEY
                );
                CREATE TABLE [employees] (
                    [id] BIGINT PRIMARY KEY,
                    [department_id] BIGINT,
                    CONSTRAINT [fk_emp_dept] FOREIGN KEY ([department_id]) REFERENCES [departments] ([id])
                );
                """, "sqlserver-token-ddl-unqualified.sql", null);

        assertTrue(new DdlRelationExtractionVisitor().extract("", "sqlserver-token-ddl-unqualified.sql", result)
                        .stream()
                        .anyMatch(relation ->
                                "employees".equals(relation.source().table().displayName())
                                        && "department_id".equals(relation.source().column().columnName())
                                        && "departments".equals(relation.target().table().displayName())
                                        && "id".equals(relation.target().column().columnName())),
                () -> "Expected unqualified DDL relationship, events=" + result.events());
        assertTrue(namingEvidence(result).stream().noneMatch(candidate ->
                        candidate.source().table().schema() != null || candidate.target().table().schema() != null),
                () -> "Unqualified DDL must not invent dbo schema, events=" + result.events());
    }

    @Test
    void fullGrammerSeparatesCaseValueAndControlSources() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                INSERT INTO dbo.reconciliation_items (
                    debit_amount
                )
                SELECT
                    CASE WHEN cj.journal_type IN (N'bank_in', N'cash_in') THEN cj.amount ELSE 0 END
                FROM dbo.cashier_journals AS cj;
                """, StatementSourceType.PLAIN_SQL, "sqlserver-full-case-lineage.sql", 1, 8, Map.of());

        StructuredParseResult result = new SqlServer2025FullGrammerDialectModule()
                .sqlParser()
                .parseSql(statement, null);

        var lineages = new StructuredDataLineageExtractor().extract(
                statement,
                result,
                Set.of(TableId.of("dbo", "cashier_journals"), TableId.of("dbo", "reconciliation_items")));

        assertTrue(lineages.stream().anyMatch(lineage ->
                        lineage.flowKind() == LineageFlowKind.VALUE
                                && "debit_amount".equals(lineage.target().column().columnName())
                                && lineage.sources().stream().anyMatch(source ->
                                        "cashier_journals".equals(source.table().tableName())
                                                && "amount".equals(source.column().columnName()))),
                () -> "Expected VALUE lineage from cj.amount to debit_amount, lineages=" + lineages
                        + ", events=" + result.events());
        assertTrue(lineages.stream().anyMatch(lineage ->
                        lineage.flowKind() == LineageFlowKind.CONTROL
                                && "debit_amount".equals(lineage.target().column().columnName())
                                && lineage.sources().stream().anyMatch(source ->
                                        "cashier_journals".equals(source.table().tableName())
                                                && "journal_type".equals(source.column().columnName()))),
                () -> "Expected CONTROL lineage from cj.journal_type to debit_amount, lineages=" + lineages
                        + ", events=" + result.events());
    }

    private List<NamingEvidenceCandidate> namingEvidence(StructuredParseResult result) {
        return new NamingEvidenceExtractor().extractFromDdlEvents(result.events());
    }

    private SqlStatementRecord schemaQualifiedInsertSelectStatement(String sourceName) {
        return new SqlStatementRecord("""
                INSERT INTO [dbo].[children] ([parent_id])
                SELECT p.[id]
                FROM [dbo].[parents] AS p;
                """, StatementSourceType.PLAIN_SQL, sourceName, 1, 3, Map.of());
    }

    private void assertSchemaQualifiedLineageTarget(SqlStatementRecord statement, StructuredParseResult result) {
        var lineages = new StructuredDataLineageExtractor().extract(
                statement,
                result,
                Set.of(TableId.of("dbo", "parents"), TableId.of("dbo", "children")));

        assertTrue(lineages.stream().anyMatch(lineage ->
                        "dbo.children".equals(lineage.target().table().displayName())
                                && "dbo".equals(lineage.target().table().schema())
                                && "parent_id".equals(lineage.target().column().columnName())
                                && lineage.sources().stream().anyMatch(source ->
                                        "dbo.parents".equals(source.table().displayName())
                                                && "dbo".equals(source.table().schema())
                                                && "id".equals(source.column().columnName()))),
                () -> "Expected schema-qualified VALUE lineage target and source, lineages=" + lineages
                        + ", events=" + result.events());
    }
}
