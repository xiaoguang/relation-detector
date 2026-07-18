package com.relationdetector.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CN: 将稳定排序后的 batch outcomes 汇总为 summary 和 case records，并通过 AtomicOutputWriter 写出 JSON；它保留 case error code，不执行或重试 scan。
 * EN: Serializes ordered batch outcomes into summary and case records and writes JSON through AtomicOutputWriter. It preserves per-case error codes but never executes or retries scans.
 */
final class BatchReportWriter {
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final AtomicOutputWriter outputWriter = new AtomicOutputWriter();

    void write(Path output, List<BatchCaseOutcome> outcomes) throws Exception {
        List<Map<String, Object>> cases = outcomes.stream().map(this::caseResult).toList();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("caseCount", outcomes.size());
        summary.put("successCount", count(outcomes, BatchCaseStatus.SUCCESS));
        summary.put("failedCount", count(outcomes, BatchCaseStatus.FAILED));
        summary.put("skippedCount", count(outcomes, BatchCaseStatus.SKIPPED_FAIL_FAST));
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("summary", summary);
        report.put("cases", cases);
        outputWriter.writeString(output, JSON.writeValueAsString(report) + "\n");
    }

    private Map<String, Object> caseResult(BatchCaseOutcome outcome) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", outcome.batchCase().id());
        value.put("status", outcome.status().name());
        value.put("elapsedMillis", outcome.elapsedMillis());
        value.put("output", outcome.batchCase().output().toString());
        if (outcome.batchCase().directOutput() != null) {
            value.put("directOutput", outcome.batchCase().directOutput().toString());
        }
        if (!outcome.error().isBlank()) {
            value.put("errorCode", outcome.errorCode().name());
            value.put("error", outcome.error());
        }
        return value;
    }

    private long count(List<BatchCaseOutcome> outcomes, BatchCaseStatus status) {
        return outcomes.stream().filter(outcome -> outcome.status() == status).count();
    }
}
