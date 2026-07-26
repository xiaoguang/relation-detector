package com.relationdetector.cli.verification;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CN: 将当前 sample-data 紧凑 TSV 同步到受版本控制的 parser 对比表；
 * 输入必须完整覆盖19类 parser，缺行、重复行或未知类别均失败。
 * EN: Synchronizes the compact sample-data TSV into the tracked parser comparison tables. The input must cover
 * all 19 parser categories exactly; missing, duplicate, or unknown rows fail.
 */
final class ParserComparisonSummarySynchronizer {
    private static final Map<String, String> DISPLAY_NAMES = displayNames();

    void synchronize(Path summary, Path document, boolean update) {
        String current = read(document);
        String generated = render(current, parse(summary));
        if (!current.equals(generated)) {
            if (!update) {
                throw new ReleaseVerificationException(
                        "parser comparison summary is stale; run parser-summary --update");
            }
            write(document, generated);
        }
    }

    private Map<String, Rows> parse(Path summary) {
        List<String> lines;
        try {
            lines = Files.readAllLines(summary);
        } catch (IOException error) {
            throw new ReleaseVerificationException("failed to read parser summary", error);
        }
        if (lines.isEmpty() || !lines.get(0).startsWith("Parser\tFix\t")) {
            throw new ReleaseVerificationException("sample-data summary header is invalid");
        }
        Map<String, Rows> rows = new LinkedHashMap<>();
        for (int index = 1; index < lines.size(); index++) {
            if (lines.get(index).isBlank()) {
                continue;
            }
            String[] values = lines.get(index).split("\\t", -1);
            if (values.length != 10 || !DISPLAY_NAMES.containsKey(values[0])) {
                throw new ReleaseVerificationException("sample-data summary row is invalid");
            }
            String name = DISPLAY_NAMES.get(values[0]);
            String direct = "| " + name + " | " + String.join(" | ", List.of(values).subList(1, 7))
                    + " |";
            String derived = "| " + name + " | " + String.join(" | ", List.of(values).subList(1, 10))
                    + " |";
            if (rows.putIfAbsent(name, new Rows(direct, derived)) != null) {
                throw new ReleaseVerificationException("duplicate parser summary row");
            }
        }
        if (!rows.keySet().equals(new java.util.LinkedHashSet<>(DISPLAY_NAMES.values()))) {
            throw new ReleaseVerificationException("sample-data summary coverage is incomplete");
        }
        return rows;
    }

    private String render(String document, Map<String, Rows> rows) {
        List<String> lines = new java.util.ArrayList<>(document.lines().toList());
        Map<String, Integer> directCounts = new LinkedHashMap<>();
        Map<String, Integer> derivedCounts = new LinkedHashMap<>();
        rows.keySet().forEach(name -> {
            directCounts.put(name, 0);
            derivedCounts.put(name, 0);
        });
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            for (Map.Entry<String, Rows> entry : rows.entrySet()) {
                if (!line.startsWith("| " + entry.getKey() + " |")) {
                    continue;
                }
                long separators = line.chars().filter(value -> value == '|').count();
                if (separators == 8) {
                    lines.set(index, entry.getValue().direct());
                    directCounts.merge(entry.getKey(), 1, Integer::sum);
                } else if (separators == 11) {
                    lines.set(index, entry.getValue().derived());
                    derivedCounts.merge(entry.getKey(), 1, Integer::sum);
                }
            }
        }
        for (String name : rows.keySet()) {
            if (directCounts.get(name) != 1 || derivedCounts.get(name) != 1) {
                throw new ReleaseVerificationException(
                        "parser comparison document row coverage is invalid");
            }
        }
        return String.join("\n", lines) + (document.endsWith("\n") ? "\n" : "");
    }

    private String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException error) {
            throw new ReleaseVerificationException("failed to read parser comparison document", error);
        }
    }

    private void write(Path path, String value) {
        try {
            Files.writeString(path, value);
        } catch (IOException error) {
            throw new ReleaseVerificationException("failed to update parser comparison document", error);
        }
    }

    private static Map<String, String> displayNames() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("common-token-event-sample-data", "common token-event sample-data");
        values.put("mysql-token-event-root", "MySQL token-event root sample-data");
        values.put("mysql-v5_7-full", "MySQL full-grammar v5_7 sample-data");
        values.put("mysql-v8_0-full", "MySQL full-grammar v8_0 sample-data");
        values.put("postgres-token-event-root", "PostgreSQL token-event root sample-data");
        values.put("postgres-v16-full", "PostgreSQL full-grammar v16 sample-data");
        values.put("postgres-v17-full", "PostgreSQL full-grammar v17 sample-data");
        values.put("postgres-v18-full", "PostgreSQL full-grammar v18 sample-data");
        values.put("oracle-token-event-root", "Oracle token-event root sample-data");
        values.put("oracle-v12c-full", "Oracle full-grammar v12c sample-data");
        values.put("oracle-v19c-full", "Oracle full-grammar v19c sample-data");
        values.put("oracle-v21c-full", "Oracle full-grammar v21c sample-data");
        values.put("oracle-v26ai-full", "Oracle full-grammar v26ai sample-data");
        values.put("sqlserver-token-event-root", "SQL Server token-event root sample-data");
        values.put("sqlserver-v2016-full", "SQL Server full-grammar v2016 sample-data");
        values.put("sqlserver-v2017-full", "SQL Server full-grammar v2017 sample-data");
        values.put("sqlserver-v2019-full", "SQL Server full-grammar v2019 sample-data");
        values.put("sqlserver-v2022-full", "SQL Server full-grammar v2022 sample-data");
        values.put("sqlserver-v2025-full", "SQL Server full-grammar v2025 sample-data");
        return Map.copyOf(values);
    }

    private record Rows(String direct, String derived) {
    }
}
