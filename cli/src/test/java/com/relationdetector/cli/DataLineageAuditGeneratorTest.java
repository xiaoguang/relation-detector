package com.relationdetector.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class DataLineageAuditGeneratorTest {
    private static final Path WORKSPACE = workspaceRoot();
    private static final Path AUDIT = WORKSPACE.resolve("docs/parser-audit/data-lineage-full-audit.md");

    @Test
    void generatedAuditClassifiesLineageFixtureCoverage() throws Exception {
        assumeGeneratedReportTestEnabled();
        String markdown = DataLineageAuditGenerator.generate(WORKSPACE);

        assertTrue(markdown.contains("# Data Lineage Full Audit"));
        assertTrue(markdown.contains("| TOTAL | 979 |"));
        assertTrue(markdown.contains("| EXISTING_GOLD | 333 |"));
        assertTrue(markdown.contains("| SUGGESTED_GOLD | 0 |"));
        assertTrue(markdown.contains("| PENDING_REVIEW | 0 |"));
        assertTrue(markdown.contains("| NOT_APPLICABLE | 646 |"));
        assertTrue(markdown.contains("| Classification | `EXISTING_GOLD` |"));
        assertTrue(markdown.contains("| Classification | `NOT_APPLICABLE` |"));
        assertTrue(markdown.contains("mysql-user-spending-left-join-update-sql"));
        assertTrue(markdown.contains("VALUE:AGGREGATE:orders.pay_amount->users.total_spent"));
        assertTrue(markdown.contains("mysql-supply-chain-update-explicit-join-sql"));
        assertTrue(markdown.contains(
                "VALUE:AGGREGATE:supplier_manifests.supply_price,warehouse_inventory.default_unit_cost,"
                        + "order_items.quantity->order_items.estimated_cost"));
        assertTrue(markdown.contains("mysql-business-financial-asset-wash-procedure-sql"));
        assertTrue(markdown.contains("VALUE:ARITHMETIC:account_balances.max_credit_limit->account_balances.adjusted_limit"));
        assertTrue(markdown.contains(
                "VALUE:CONCAT_FORMAT:users.country_code,transaction_ledgers.created_at,"
                        + "transaction_ledgers.direction,transaction_ledgers.amount,transaction_ledgers.merchant_category"
                        + "->account_balances.compliance_notes"));
        assertTrue(markdown.contains("VALUE:COALESCE:account_balances.risk_flags->account_balances.risk_flags"));
        assertTrue(markdown.contains("VALUE:CUMULATIVE:jsh_temp_org_pdf.weight->jsh_temp_org_pdf.cdf_end"));
        assertTrue(markdown.contains("postgres-sql-update-from-aliases"));
        assertTrue(markdown.contains("write statement has no physical table.column source in Data Lineage v1"));
        assertTrue(markdown.contains("postgres-business-risk-settlement-function-sql"));
        assertTrue(markdown.contains("| Source type | `FUNCTION` |"));
        assertTrue(markdown.contains("| Reason | write statement has no physical table.column source in Data Lineage v1 |"));
        assertTrue(markdown.contains("basic-correctness-case-01-procedure-proc-generate-purchase-inbound-from-order-sql"));
        assertTrue(markdown.contains("mysql80-basic-correctness-case-01-procedure-proc-generate-purchase-inbound-from-order-sql"));
        assertTrue(markdown.contains("mysql57-basic-correctness-case-01-procedure-proc-generate-purchase-inbound-from-order-sql"));
        assertTrue(markdown.contains("postgres-basic-correctness-case-01-procedure-proc-generate-purchase-inbound-from-order-sql"));
        assertTrue(markdown.contains("postgres18-basic-correctness-case-01-procedure-proc-generate-purchase-inbound-from-order-sql"));
        assertTrue(markdown.contains("basic-correctness-case-01-procedure-proc-create-order-mock-retail-sql"));
        assertTrue(markdown.contains("basic-correctness-case-01-procedure-proc-insert-purchase-requisition-sql"));
        assertTrue(markdown.contains("local temporary table sources are excluded from Data Lineage v1"));
        assertTrue(markdown.contains("basic-correctness-case-01-procedure-proc-worker-daily-distribution-sql"));
        assertTrue(markdown.contains(
                "test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-proc-worker-daily-distribution-sql/expected-lineage.json"));
        assertTrue(markdown.contains("postgres-business-delete-orphan-left-join-sql"));
        assertTrue(markdown.contains("postgres17-merge-returning-sql"));
        assertTrue(markdown.contains("VALUE:DIRECT:staging_account_balances.balance->account_balances.balance"));
        assertTrue(markdown.contains("postgres16-postgres-business-update-users-aggregate-sql"));
        assertTrue(markdown.contains("postgres18-returning-old-new-sql"));
        assertTrue(markdown.contains("postgres-extreme-nesting-withrelation-withlineage-sql"));
        assertTrue(markdown.contains("postgres18-extreme-nesting-withrelation-withlineage-sql"));
        assertTrue(markdown.contains("oracle26ai-sample-data-full-02-procedures-01-procedures-sql"));
        assertTrue(markdown.contains(
                "test-fixtures/correctness/oracle/v26ai/oracle26ai-sample-data-full-02-procedures-01-procedures-sql/expected-lineage.json"));
        assertTrue(markdown.contains("sqlserver2025-sample-data-02-sales-fact-sql"));
        assertTrue(markdown.contains(
                "test-fixtures/correctness/sqlserver/v2025/sqlserver2025-sample-data-02-sales-fact-sql/expected-lineage.json"));
        assertTrue(markdown.contains("sqlserver2025-sample-data-03-sales-fact-procedure-sql"));
        assertTrue(markdown.contains(
                "test-fixtures/correctness/sqlserver/v2025/sqlserver2025-sample-data-03-sales-fact-procedure-sql/expected-lineage.json"));
        assertTrue(markdown.contains("VALUE:DIRECT:dbo.orders.order_id->sales_fact.order_id"));
        assertTrue(markdown.contains("VALUE:CONCAT_FORMAT:customers.risk_level,orders.status->orders.risk_note"));
        assertTrue(markdown.contains(
                "VALUE:ARITHMETIC:account_balances.balance,transaction_ledgers.amount->account_balances.balance"));
        assertTrue(markdown.contains("DELETE does not write target column values in Data Lineage v1"));
        assertTrue(markdown.contains("test-fixtures/correctness/mysql/mysql-user-spending-left-join-update-sql/input.sql"));
        assertTrue(markdown.contains("```sql"));
    }

    @Test
    void generatedAuditFileIsUpToDate() throws Exception {
        assumeGeneratedReportTestEnabled();
        String markdown = DataLineageAuditGenerator.generate(WORKSPACE);
        if (Boolean.getBoolean("updateDataLineageAudit")) {
            Files.createDirectories(AUDIT.getParent());
            Files.writeString(AUDIT, markdown);
        }

        assertEquals(markdown, Files.readString(AUDIT),
                "Generated Data Lineage audit is stale. Refresh it with: "
                        + "mvn -pl cli -Dtest=DataLineageAuditGeneratorTest "
                        + "-DupdateDataLineageAudit=true test");
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
                        || Boolean.getBoolean("updateDataLineageAudit"),
                "Data Lineage audit checks are explicit. Run with "
                        + "-DrunGeneratedReportTests=true for validation or "
                        + "-DupdateDataLineageAudit=true to refresh.");
    }
}
