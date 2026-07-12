package com.relationdetector.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.ScriptFrameRequest;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.sqlserver.script.SqlServerScriptFramer;
import com.relationdetector.sqlserver.tokenevent.SqlServerRelationSqlLexer;
import com.relationdetector.sqlserver.tokenevent.SqlServerRelationSqlParser;
import com.relationdetector.sqlserver.tokenevent.SqlServerTokenEventStructuredDdlParser;

class SqlServerSeedDataConsistencyTest {
    private static final Path ROOT = TestWorkspacePaths.relationDetectorRoot();
    private static final List<String> VERSIONS = List.of("2016", "2017", "2019", "2022", "2025");

    @Test
    void versionedThirdBatchSeedUsesOneCanonicalBaseline() throws Exception {
        String canonical = Files.readString(thirdBatch("2016"));
        for (String version : VERSIONS) {
            assertEquals(canonical, Files.readString(thirdBatch(version)),
                    () -> "SQL Server " + version + " third-batch seed drifted from the 2016-compatible baseline");
        }
    }

    @Test
    void thirdBatchForeignKeysReferenceRowsInsertedNoLaterThanTheirSource() throws Exception {
        SeedInventory inventory = seedInventory("2025");
        List<ForeignKey> foreignKeys = foreignKeys("2025");
        List<String> failures = new ArrayList<>();

        for (SeedRow row : inventory.rowsFrom(thirdBatch("2025"))) {
            assertTrue(row.identityEnabled() || !row.values().containsKey("id"),
                    () -> row.table() + " explicitly supplies id without IDENTITY_INSERT at " + row.source());
            for (ForeignKey fk : foreignKeys) {
                if (!fk.sourceTable().equals(row.table()) || !row.values().containsKey(fk.sourceColumn())) {
                    continue;
                }
                Object value = row.values().get(fk.sourceColumn());
                if (value == null) {
                    continue;
                }
                SeedRow target = inventory.find(fk.targetTable(), fk.targetColumn(), value);
                if (target == null) {
                    failures.add(row.source() + " " + fk.sourceTable() + "." + fk.sourceColumn()
                            + "=" + value + " has no seeded " + fk.targetTable() + "." + fk.targetColumn());
                } else if (target.sequence() > row.sequence()) {
                    failures.add(row.source() + " references " + target.source() + " before the target is inserted");
                }
            }
        }
        assertTrue(failures.isEmpty(), () -> "Invalid SQL Server seed foreign keys: " + failures);
    }

    @Test
    void thirdBatchRowsSatisfyBusinessArithmeticInvariants() throws Exception {
        SeedInventory inventory = seedInventory("2025");

        for (String table : List.of("dbo.ar_aging_snapshots", "dbo.ap_aging_snapshots")) {
            for (SeedRow row : inventory.rows(table)) {
                assertDecimal(row, "outstanding_amount",
                        decimal(row, "invoice_amount").subtract(decimal(row, "paid_amount")));
            }
        }
        for (SeedRow row : inventory.rows("dbo.tax_invoices")) {
            BigDecimal tax = decimal(row, "amount_excluding_tax")
                    .multiply(decimal(row, "tax_rate"))
                    .setScale(2, RoundingMode.HALF_UP);
            assertDecimal(row, "tax_amount", tax);
            assertDecimal(row, "amount_including_tax", decimal(row, "amount_excluding_tax").add(tax));
        }
        for (SeedRow row : inventory.rows("dbo.inspection_reports")) {
            BigDecimal inspected = decimal(row, "inspected_qty");
            BigDecimal defective = decimal(row, "defective_qty");
            assertDecimal(row, "inspected_qty", decimal(row, "qualified_qty").add(defective));
            assertDecimal(row, "defect_rate", defective.multiply(BigDecimal.valueOf(100))
                    .divide(inspected, 2, RoundingMode.HALF_UP));
        }
        for (SeedRow row : inventory.rows("dbo.cash_flow_forecasts")) {
            BigDecimal net = decimal(row, "expected_collections").add(decimal(row, "other_income"))
                    .subtract(decimal(row, "expected_payments"))
                    .subtract(decimal(row, "expected_salary"))
                    .subtract(decimal(row, "expected_tax"))
                    .subtract(decimal(row, "other_expense"));
            assertDecimal(row, "net_cash_flow", net);
            assertDecimal(row, "ending_balance", decimal(row, "beginning_balance").add(net));
        }
        for (SeedRow row : inventory.rows("dbo.consignment_inventory")) {
            assertDecimal(row, "available_qty",
                    decimal(row, "consigned_qty").subtract(decimal(row, "consumed_qty")));
        }
    }

    @Test
    void thirdBatchUsesBusinessMeaningfulApprovalAndQualityValues() throws Exception {
        SeedInventory inventory = seedInventory("2025");
        assertEquals(Set.of("PURCHASE_APPROVAL", "CONTRACT_APPROVAL"),
                inventory.rows("dbo.approval_workflows").stream()
                        .map(row -> String.valueOf(row.values().get("workflow_code")))
                        .collect(java.util.stream.Collectors.toSet()));
        assertTrue(inventory.rows("dbo.approval_instances").stream()
                        .allMatch(row -> decimal(row, "total_nodes").compareTo(BigDecimal.valueOf(10)) < 0),
                "Approval total_nodes must describe workflow size, not synthetic ids");
        assertTrue(inventory.rows("dbo.inspection_reports").stream()
                        .allMatch(row -> Set.of("qualified", "rejected")
                                .contains(String.valueOf(row.values().get("inspection_result")))),
                "Inspection result must use business outcomes");
    }

    private SeedInventory seedInventory(String version) throws Exception {
        List<Path> files;
        try (Stream<Path> stream = Files.walk(ROOT.resolve("sample-data/sqlserver/" + version + "/03-data"))) {
            files = stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".sql"))
                    .sorted()
                    .toList();
        }
        List<SeedRow> rows = new ArrayList<>();
        Map<String, Boolean> identityEnabled = new LinkedHashMap<>();
        String[] activeIdentityTable = {null};
        long[] sequence = {0L};
        SqlServerScriptFramer splitter = new SqlServerScriptFramer();
        for (Path file : files) {
            for (var statement : splitter.frame(new ScriptFrameRequest(
                    Files.readString(file), file.toString(), StatementSourceType.PLAIN_SQL)).statements()) {
                SqlServerRelationSqlParser.Tsql_fileContext root = parse(statement.sql());
                for (SqlServerRelationSqlParser.StatementContext context : root.statement()) {
                    if (context.set_identity_insert() != null) {
                        String table = table(context.set_identity_insert().full_table_name().getText());
                        boolean enabled = context.set_identity_insert().ON() != null;
                        if (enabled) {
                            assertTrue(activeIdentityTable[0] == null,
                                    () -> "IDENTITY_INSERT already enabled for " + activeIdentityTable[0]
                                            + " before " + table + " at " + statement.sourceName());
                            activeIdentityTable[0] = table;
                        } else {
                            assertEquals(table, activeIdentityTable[0],
                                    () -> "IDENTITY_INSERT OFF does not match active table at " + statement.sourceName());
                            activeIdentityTable[0] = null;
                        }
                        identityEnabled.put(table, enabled);
                    }
                    if (context.insert_statement() != null) {
                        SeedRow row = seedRow(context.insert_statement(), file, statement.startLine(),
                                ++sequence[0], identityEnabled);
                        if (row != null) {
                            rows.add(row);
                        }
                    }
                }
            }
        }
        assertEquals(null, activeIdentityTable[0], "IDENTITY_INSERT must be disabled after all seed files");
        return new SeedInventory(rows);
    }

    private SeedRow seedRow(
            SqlServerRelationSqlParser.Insert_statementContext insert,
            Path file,
            long sourceLine,
            long sequence,
            Map<String, Boolean> identityEnabled
    ) {
        var value = insert.insert_statement_value();
        if (value == null || value.expression_list_() == null) {
            return null;
        }
        String table = table(insert.ddl_object().getText());
        List<String> columns = insert.insert_column_name_list().column_name_list().id_().stream()
                .map(identifier -> identifier(identifier.getText()))
                .toList();
        List<SqlServerRelationSqlParser.ExpressionContext> expressions = value.expression_list_().expression();
        assertEquals(columns.size(), expressions.size(), () -> "Column/value mismatch in " + file + ":" + sourceLine);
        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < columns.size(); index++) {
            values.put(columns.get(index), literal(expressions.get(index).getText()));
        }
        return new SeedRow(table, values, file, sourceLine, sequence,
                Boolean.TRUE.equals(identityEnabled.get(table)));
    }

    private List<ForeignKey> foreignKeys(String version) throws Exception {
        List<ForeignKey> result = new ArrayList<>();
        SqlServerTokenEventStructuredDdlParser parser = new SqlServerTokenEventStructuredDdlParser();
        try (Stream<Path> stream = Files.walk(ROOT.resolve("sample-data/sqlserver/" + version + "/01-schema"))) {
            for (Path file : stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".sql"))
                    .sorted().toList()) {
                for (StructuredSqlEvent event : parser.parseDdl(Files.readString(file), file.toString(), null).events()) {
                    if (event.type() == StructuredParseEventType.DDL_FOREIGN_KEY) {
                        result.add(new ForeignKey(
                                table(event.sourceTable()),
                                identifier(event.sourceColumn()),
                                table(event.targetTable()),
                                identifier(event.targetColumn())));
                    }
                }
            }
        }
        return result;
    }

    private SqlServerRelationSqlParser.Tsql_fileContext parse(String sql) {
        SqlServerRelationSqlLexer lexer = new SqlServerRelationSqlLexer(CharStreams.fromString(sql));
        SqlServerRelationSqlParser parser = new SqlServerRelationSqlParser(new CommonTokenStream(lexer));
        return parser.tsql_file();
    }

    private Object literal(String text) {
        String value = text.strip();
        if (value.equalsIgnoreCase("NULL")) {
            return null;
        }
        if ((value.startsWith("'") || value.startsWith("N'")) && value.endsWith("'")) {
            int start = value.startsWith("N'") ? 2 : 1;
            return value.substring(start, value.length() - 1).replace("''", "'");
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ignored) {
            return value;
        }
    }

    private void assertDecimal(SeedRow row, String column, BigDecimal expected) {
        assertTrue(decimal(row, column).compareTo(expected) == 0,
                () -> row.source() + " expected " + column + "=" + expected + " but was " + row.values().get(column));
    }

    private static BigDecimal decimal(SeedRow row, String column) {
        Object value = row.values().get(column);
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(String.valueOf(value));
    }

    private Path thirdBatch(String version) {
        return ROOT.resolve("sample-data/sqlserver/" + version + "/03-data/03-third-batch-data.sql");
    }

    private String table(String text) {
        String normalized = text.replace("[", "").replace("]", "").replace("\"", "")
                .toLowerCase(Locale.ROOT);
        return normalized.contains(".") ? normalized : "dbo." + normalized;
    }

    private String identifier(String text) {
        return text.replace("[", "").replace("]", "").replace("\"", "")
                .toLowerCase(Locale.ROOT);
    }

    private record ForeignKey(String sourceTable, String sourceColumn, String targetTable, String targetColumn) {
    }

    private record SeedRow(
            String table,
            Map<String, Object> values,
            Path file,
            long line,
            long sequence,
            boolean identityEnabled
    ) {
        String source() {
            return file.getFileName() + ":" + line;
        }
    }

    private static final class SeedInventory {
        private final List<SeedRow> rows;
        private final Map<String, List<SeedRow>> byTable = new LinkedHashMap<>();

        SeedInventory(List<SeedRow> rows) {
            this.rows = List.copyOf(rows);
            for (SeedRow row : rows) {
                byTable.computeIfAbsent(row.table(), ignored -> new ArrayList<>()).add(row);
            }
        }

        List<SeedRow> rows(String table) {
            return byTable.getOrDefault(table, List.of());
        }

        List<SeedRow> rowsFrom(Path file) {
            return rows.stream().filter(row -> row.file().equals(file)).toList();
        }

        SeedRow find(String table, String column, Object value) {
            return rows(table).stream()
                    .filter(row -> valueKey(row.values().get(column)).equals(valueKey(value)))
                    .min(Comparator.comparingLong(SeedRow::sequence))
                    .orElse(null);
        }

        private String valueKey(Object value) {
            return value instanceof BigDecimal decimal
                    ? decimal.stripTrailingZeros().toPlainString()
                    : String.valueOf(value);
        }
    }
}
