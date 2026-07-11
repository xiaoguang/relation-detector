package com.relationdetector.sqlserver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.core.lineage.StructuredDataLineageExtractor;
import com.relationdetector.sqlserver.fullgrammer.v2016.SqlServer2016FullGrammerDialectModule;
import com.relationdetector.sqlserver.fullgrammer.v2017.SqlServer2017FullGrammerDialectModule;
import com.relationdetector.sqlserver.fullgrammer.v2019.SqlServer2019FullGrammerDialectModule;
import com.relationdetector.sqlserver.fullgrammer.v2022.SqlServer2022FullGrammerDialectModule;
import com.relationdetector.sqlserver.fullgrammer.v2025.SqlServer2025FullGrammerDialectModule;
import com.relationdetector.sqlserver.tokenevent.SqlServerTokenEventStructuredSqlParser;

class SqlServerTransformConsistencyTest {
    @Test
    void caseValuesControlsAndArithmeticMatchAcrossParsers() {
        SqlStatementRecord statement = statement("""
                UPDATE so
                SET [paid_amount] = so.[paid_amount] + p.[amount],
                    [status] = CASE WHEN so.[paid_amount] + p.[amount] >= so.[total_amount]
                                    THEN 'paid' ELSE so.[status] END
                FROM [dbo].[sales_orders] AS so
                INNER JOIN [dbo].[payments] AS p ON p.[order_id] = so.[id];
                """);

        for (NamedParser parser : parsers()) {
            List<DataLineageCandidate> lineages = extract(parser.parser(), statement);
            assertLineage(parser.name(), lineages, "dbo.sales_orders.paid_amount", "dbo.sales_orders.paid_amount",
                    LineageFlowKind.VALUE, LineageTransformType.ARITHMETIC);
            assertLineage(parser.name(), lineages, "dbo.payments.amount", "dbo.sales_orders.paid_amount",
                    LineageFlowKind.VALUE, LineageTransformType.ARITHMETIC);
            assertLineage(parser.name(), lineages, "dbo.sales_orders.status", "dbo.sales_orders.status",
                    LineageFlowKind.VALUE, LineageTransformType.CASE_WHEN);
            assertLineage(parser.name(), lineages, "dbo.sales_orders.total_amount", "dbo.sales_orders.status",
                    LineageFlowKind.CONTROL, LineageTransformType.CASE_WHEN);
        }
    }

    @Test
    void concatCastAndArithmeticProjectionTransformsMatchAcrossParsers() {
        SqlStatementRecord statement = statement("""
                INSERT INTO [dbo].[projection_results] ([report_no], [effective_from], [gross_margin])
                SELECT CONCAT('DMG-', w.[code]),
                       CAST(pc.[created_at] AS DATE),
                       soi.[amount] - COALESCE(ce.[cogs_amount], 0)
                FROM [dbo].[warehouses] AS w
                JOIN [dbo].[product_categories] AS pc ON pc.[id] = w.[id]
                JOIN [dbo].[sales_order_items] AS soi ON soi.[id] = pc.[id]
                LEFT JOIN [dbo].[cogs_entries] AS ce ON ce.[order_item_id] = soi.[id];
                """);

        for (NamedParser parser : parsers()) {
            List<DataLineageCandidate> lineages = extract(parser.parser(), statement);
            assertLineage(parser.name(), lineages, "dbo.warehouses.code", "dbo.projection_results.report_no",
                    LineageFlowKind.VALUE, LineageTransformType.CONCAT_FORMAT);
            assertLineage(parser.name(), lineages, "dbo.product_categories.created_at",
                    "dbo.projection_results.effective_from", LineageFlowKind.VALUE,
                    LineageTransformType.FUNCTION_CALL);
            assertLineage(parser.name(), lineages, "dbo.sales_order_items.amount",
                    "dbo.projection_results.gross_margin", LineageFlowKind.VALUE,
                    LineageTransformType.ARITHMETIC);
            assertLineage(parser.name(), lineages, "dbo.cogs_entries.cogs_amount",
                    "dbo.projection_results.gross_margin", LineageFlowKind.VALUE,
                    LineageTransformType.ARITHMETIC);
        }
    }

    private List<NamedParser> parsers() {
        return List.of(
                new NamedParser("token-event", new SqlServerTokenEventStructuredSqlParser()),
                new NamedParser("sqlserver/2016", new SqlServer2016FullGrammerDialectModule().sqlParser()),
                new NamedParser("sqlserver/2017", new SqlServer2017FullGrammerDialectModule().sqlParser()),
                new NamedParser("sqlserver/2019", new SqlServer2019FullGrammerDialectModule().sqlParser()),
                new NamedParser("sqlserver/2022", new SqlServer2022FullGrammerDialectModule().sqlParser()),
                new NamedParser("sqlserver/2025", new SqlServer2025FullGrammerDialectModule().sqlParser()));
    }

    private List<DataLineageCandidate> extract(StructuredSqlParser parser, SqlStatementRecord statement) {
        return new StructuredDataLineageExtractor().extract(statement, parser.parseSql(statement, null));
    }

    private void assertLineage(
            String parser,
            List<DataLineageCandidate> lineages,
            String source,
            String target,
            LineageFlowKind flow,
            LineageTransformType transform
    ) {
        assertTrue(lineages.stream().anyMatch(lineage -> lineage.flowKind() == flow
                        && lineage.transformType() == transform
                        && lineage.target().displayName().equals(target)
                        && lineage.sources().stream().anyMatch(endpoint -> endpoint.displayName().equals(source))),
                () -> parser + " missing " + flow + "/" + transform + " " + source + " -> " + target
                        + "; actual=" + describe(lineages));
    }

    private List<String> describe(List<DataLineageCandidate> lineages) {
        return lineages.stream().map(lineage -> lineage.flowKind() + "/" + lineage.transformType() + ":"
                + lineage.sources().stream().map(endpoint -> endpoint.displayName()).toList()
                + "->" + lineage.target().displayName()).toList();
    }

    private SqlStatementRecord statement(String sql) {
        return new SqlStatementRecord(sql, StatementSourceType.PLAIN_SQL, "sqlserver-transform-consistency.sql", 1, 1,
                Map.of());
    }

    private record NamedParser(String name, StructuredSqlParser parser) {
    }
}
