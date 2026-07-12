package com.relationdetector.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class SampleDataParserComparisonDocumentationTest {
    private static final Map<String, String> DISPLAY_NAMES = displayNames();

    @Test
    void parserComparisonTablesMatchLatestCliSummaryWhenAvailable() throws Exception {
        Path root = TestWorkspacePaths.repositoryRoot();
        Path summary = root.resolve(
                "relation-detector/target/sample-data-parser-cli/summary-with-derived.tsv");
        Assumptions.assumeTrue(Files.isRegularFile(summary),
                "Run run-all-sample-data-parsers.sh before the documentation consistency check");

        String document = Files.readString(root.resolve("docs/parser-audit/parser-comparison-summary.md"));
        List<String> rows = Files.readAllLines(summary);
        for (String row : rows.subList(1, rows.size())) {
            if (row.isBlank()) {
                continue;
            }
            String[] values = row.split("\\t", -1);
            String displayName = DISPLAY_NAMES.get(values[0]);
            assertTrue(displayName != null, () -> "Missing display name for parser " + values[0]);

            String direct = String.format("| %s | %s | %s | %s | %s | %s | %s |",
                    displayName, values[1], values[2], values[3], values[4], values[5], values[6]);
            String derived = String.format("| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |",
                    displayName, values[1], values[2], values[3], values[4], values[5], values[6],
                    values[7], values[8], values[9]);
            assertTrue(document.contains(direct), () -> "Stale direct parser comparison row: " + direct);
            assertTrue(document.contains(derived), () -> "Stale derived parser comparison row: " + derived);
        }
    }

    private static Map<String, String> displayNames() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("common-token-event-sample-data", "common token-event sample-data");
        result.put("mysql-token-event-root", "MySQL token-event root sample-data");
        result.put("mysql-v5_7-full", "MySQL full-grammar v5_7 sample-data");
        result.put("mysql-v8_0-full", "MySQL full-grammar v8_0 sample-data");
        result.put("postgres-token-event-root", "PostgreSQL token-event root sample-data");
        result.put("postgres-v16-full", "PostgreSQL full-grammar v16 sample-data");
        result.put("postgres-v17-full", "PostgreSQL full-grammar v17 sample-data");
        result.put("postgres-v18-full", "PostgreSQL full-grammar v18 sample-data");
        result.put("oracle-token-event-root", "Oracle token-event root sample-data");
        result.put("oracle-v12c-full", "Oracle full-grammar v12c sample-data");
        result.put("oracle-v19c-full", "Oracle full-grammar v19c sample-data");
        result.put("oracle-v21c-full", "Oracle full-grammar v21c sample-data");
        result.put("oracle-v26ai-full", "Oracle full-grammar v26ai sample-data");
        result.put("sqlserver-token-event-root", "SQL Server token-event root sample-data");
        result.put("sqlserver-v2016-full", "SQL Server full-grammar v2016 sample-data");
        result.put("sqlserver-v2017-full", "SQL Server full-grammar v2017 sample-data");
        result.put("sqlserver-v2019-full", "SQL Server full-grammar v2019 sample-data");
        result.put("sqlserver-v2022-full", "SQL Server full-grammar v2022 sample-data");
        result.put("sqlserver-v2025-full", "SQL Server full-grammar v2025 sample-data");
        return Map.copyOf(result);
    }
}
