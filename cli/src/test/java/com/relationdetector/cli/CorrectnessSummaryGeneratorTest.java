package com.relationdetector.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class CorrectnessSummaryGeneratorTest {
    private static final Path WORKSPACE = workspaceRoot();
    private static final Path SUMMARY = WORKSPACE.resolve("docs/generated/correctness-test-summary.md");

    @Test
    void generatedSummaryCapturesFixtureCountsAndInputPreview() throws Exception {
        assumeGeneratedReportTestEnabled();
        String markdown = CorrectnessSummaryGenerator.generate(WORKSPACE);

        assertTrue(markdown.contains("| Total correctness fixtures | 1194 |"));
        assertTrue(markdown.contains("| SQL fixtures | 981 |"));
        assertTrue(markdown.contains("| DDL fixtures | 213 |"));
        assertTrue(markdown.contains("| Fixtures with expected lineage | 429 |"));
        assertTrue(markdown.contains("| MySQL directory fixtures | 261 |"));
        assertTrue(markdown.contains("| PostgreSQL directory fixtures | 449 |"));
        assertTrue(markdown.contains("| Oracle directory fixtures | 212 |"));
        assertTrue(markdown.contains("| SQL Server directory fixtures | 233 |"));
        assertTrue(markdown.contains("| MYSQL | 300 | 241 | 59 |"));
        assertTrue(markdown.contains("| POSTGRESQL | 449 | 371 | 78 |"));
        assertTrue(markdown.contains("| ORACLE | 212 | 172 | 40 |"));
        assertTrue(markdown.contains("| SQLSERVER | 233 | 197 | 36 |"));
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
                "VALUE:CONCAT_FORMAT:users.country_code,transaction_ledgers.created_at,"
                        + "transaction_ledgers.direction,transaction_ledgers.amount,transaction_ledgers.merchant_category"
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
        assertTrue(markdown.contains(
                "test-fixtures/correctness/mysql/v5_7/basic-correctness-case-01-procedure-proc-generate-purchase-inbound-from-order-sql/expected-lineage.json"));
        assertTrue(markdown.contains(
                "test-fixtures/correctness/postgres/postgres-basic-correctness-case-01-procedure-proc-generate-purchase-inbound-from-order-sql/expected-lineage.json"));
        assertTrue(markdown.contains(
                "test-fixtures/correctness/postgres/v18/postgres18-basic-correctness-case-01-procedure-proc-generate-purchase-inbound-from-order-sql/expected-lineage.json"));
        assertTrue(markdown.contains("sample-data/mysql/8.0/04-queries/09-real-world-scenarios.sql"));
        assertTrue(markdown.contains(
                "test-fixtures/correctness/postgres/v18/postgres18-sample-data-pg18-specific-sql/expected-relations.json"));
        assertTrue(markdown.contains("sample-data/oracle/26ai/01-schema/01-tables.sql"));
        assertTrue(markdown.contains(
                "test-fixtures/correctness/oracle/v26ai/oracle26ai-sample-data-full-02-procedures-01-procedures-sql/expected-lineage.json"));
        assertTrue(markdown.contains("sample-data/sqlserver/2025/04-queries/01-complex-queries.sql"));
        assertTrue(markdown.contains(
                "test-fixtures/correctness/sqlserver/v2025/sqlserver2025-sample-data-full-02-procedures-13-erp-deep-scenario-procedures-sql/expected-lineage.json"));
        assertTrue(markdown.contains(
                "test-fixtures/correctness/sqlserver/v2025/sqlserver2025-sample-data-full-04-queries-09-real-world-scenarios-sql/expected-lineage.json"));
        assertTrue(markdown.contains("FK_LIKE:sales_orders.customer_id->customers.id:SQL_LOG_JOIN,NAMING_MATCH"));
        assertTrue(markdown.contains("```sql"));
        assertFalse(markdown.contains("SHOW CREATE TABLE `jsh_account_head`;"),
                "large fixture SQL should not be fully embedded in the lightweight report");
    }

    @Test
    void generatedSummaryFileIsUpToDate() throws Exception {
        assumeGeneratedReportTestEnabled();
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

    private static void assumeGeneratedReportTestEnabled() {
        assumeTrue(Boolean.getBoolean("runGeneratedReportTests")
                        || Boolean.getBoolean("updateCorrectnessSummary"),
                "Generated correctness summary checks are explicit. Run with "
                        + "-DrunGeneratedReportTests=true for validation or "
                        + "-DupdateCorrectnessSummary=true to refresh.");
    }
}
