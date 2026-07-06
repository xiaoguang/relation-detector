package com.relationdetector.core.tokenevent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.parse.StructuredSqlEvent;

class VisitorThreadSafetyTest {
    @Test
    void commonTokenEventParserKeepsParseStatePerInvocationUnderConcurrency() throws Exception {
        CommonTokenEventStructuredSqlParser parser = new CommonTokenEventStructuredSqlParser();
        SqlStatementRecord statement = new SqlStatementRecord("""
                WITH customer_totals AS (
                    SELECT o.customer_id, SUM(p.amount) AS total_paid
                    FROM orders o
                    JOIN payments p ON p.order_id = o.id
                    GROUP BY o.customer_id
                )
                INSERT INTO customer_summary (customer_id, total_paid)
                SELECT ct.customer_id, ct.total_paid
                FROM customer_totals ct
                JOIN customers c ON c.id = ct.customer_id
                """, StatementSourceType.PLAIN_SQL, "thread-safety.sql", 1, 12, Map.of());

        List<String> expected = fingerprints(parser.parseSql(statement, null));
        List<Callable<List<String>>> tasks = IntStream.range(0, 32)
                .mapToObj(ignored -> (Callable<List<String>>) () -> fingerprints(parser.parseSql(statement, null)))
                .toList();

        var executor = Executors.newFixedThreadPool(8);
        try {
            for (var future : executor.invokeAll(tasks)) {
                assertEquals(expected, future.get(),
                        "Concurrent parse invocations should not share mutable visitor state");
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private List<String> fingerprints(StructuredParseResult result) {
        return result.events().stream()
                .map(this::fingerprint)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private String fingerprint(StructuredSqlEvent event) {
        return event.type() + "|" + event.line() + "|" + event.attributes();
    }
}
