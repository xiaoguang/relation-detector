package com.relationdetector.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class CorrectnessSummaryGeneratorTest {
    private static final Path WORKSPACE = workspaceRoot();
    private static final Path SUMMARY = WORKSPACE.resolve("docs/generated/correctness-test-summary.md");

    @Test
    void generatedSummaryCapturesFixtureCountsAndInputPreview() throws Exception {
        String markdown = CorrectnessSummaryGenerator.generate(WORKSPACE);

        assertTrue(markdown.contains("| Total correctness fixtures | 413 |"));
        assertTrue(markdown.contains("| SQL fixtures | 339 |"));
        assertTrue(markdown.contains("| DDL fixtures | 74 |"));
        assertTrue(markdown.contains("| Fixtures with expected lineage | 105 |"));
        assertTrue(markdown.contains("| MySQL directory fixtures | 122 |"));
        assertTrue(markdown.contains("| PostgreSQL directory fixtures | 289 |"));
        assertTrue(markdown.contains("| MYSQL | 124 | 100 | 24 |"));
        assertTrue(markdown.contains("| POSTGRESQL | 289 | 239 | 50 |"));
        assertTrue(markdown.contains("Lightweight index report. Full SQL/DDL is available in each input file."));
        assertTrue(markdown.contains("test-fixtures/correctness/mysql/mysql-commerce-promotion-update-explicit-join-sql/input.sql"));
        assertTrue(markdown.contains("test-fixtures/correctness/mysql/mysql-user-spending-left-join-update-sql/expected-lineage.json"));
        assertTrue(markdown.contains("**Input Preview**"));
        assertTrue(markdown.contains("**Expected Data Lineage Fingerprints**"));
        assertTrue(markdown.contains("VALUE:AGGREGATE:orders.pay_amount->users.total_spent"));
        assertTrue(markdown.contains("VALUE:CUMULATIVE:jsh_temp_org_pdf.weight->jsh_temp_org_pdf.cdf_end"));
        assertTrue(markdown.contains("VALUE:ARITHMETIC:account_balances.max_credit_limit"
                + "->account_balances.adjusted_limit"));
        assertTrue(markdown.contains(
                "VALUE:CONCAT_FORMAT:users.country_code,user_financial_snapshot.last_activity_time,"
                        + "user_financial_snapshot.net_cash_flow,transaction_ledgers.merchant_category"
                        + "->account_balances.compliance_notes"));
        assertTrue(markdown.contains("UPDATE products p"));
        assertTrue(markdown.contains("Preview truncated; see input file for full content."));
        assertTrue(markdown.contains("test-fixtures/correctness/postgres/postgres-business-risk-ledger-update-cte-comma-sql/input.sql"));
        assertTrue(markdown.contains("test-fixtures/correctness/postgres/v16/postgres-business-risk-ledger-update-cte-comma-sql/input.sql"));
        assertTrue(markdown.contains("test-fixtures/correctness/postgres/v17/postgres17-json-table-sql/input.sql"));
        assertTrue(markdown.contains("test-fixtures/correctness/postgres/v18/postgres18-temporal-constraints-ddl/input.sql"));
        assertTrue(markdown.contains("test-fixtures/correctness/postgres/postgres-extreme-nesting-withrelation-sql/input.sql"));
        assertTrue(markdown.contains(
                "test-fixtures/correctness/postgres/v18/postgres18-extreme-nesting-withrelation-withlineage-sql/expected-lineage.json"));
        assertTrue(markdown.contains(
                "test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-proc-generate-purchase-inbound-from-order-sql/expected-lineage.json"));
        assertTrue(markdown.contains(
                "test-fixtures/correctness/mysql/v8_0/basic-correctness-case-01-procedure-proc-generate-purchase-inbound-from-order-sql/expected-lineage.json"));
        assertTrue(markdown.contains("sample-data/mysql/8.0/04-queries/09-real-world-scenarios.sql"));
        assertTrue(markdown.contains(
                "test-fixtures/correctness/postgres/v18/postgres18-sample-data-pg18-specific-sql/expected-relations.json"));
        assertTrue(markdown.contains("FK_LIKE:products.shop_id->shops.id:SQL_LOG_JOIN"));
        assertTrue(markdown.contains("```sql"));
        assertFalse(markdown.contains("SHOW CREATE TABLE `jsh_account_head`;"),
                "large fixture SQL should not be fully embedded in the lightweight report");
    }

    @Test
    void generatedSummaryFileIsUpToDate() throws Exception {
        String markdown = CorrectnessSummaryGenerator.generate(WORKSPACE);
        if (Boolean.getBoolean("updateCorrectnessSummary")) {
            Files.createDirectories(SUMMARY.getParent());
            Files.writeString(SUMMARY, markdown);
        }

        assertEquals(markdown, Files.readString(SUMMARY),
                "Generated correctness summary is stale. Refresh it with: "
                        + "mvn -pl cli -Dtest=CorrectnessSummaryGeneratorTest "
                        + "-DupdateCorrectnessSummary=true test");
    }

    private static Path workspaceRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml")) && Files.exists(current.resolve("test-fixtures"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate relation-detector workspace root");
    }
}
