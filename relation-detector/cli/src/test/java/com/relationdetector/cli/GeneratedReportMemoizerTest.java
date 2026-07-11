package com.relationdetector.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

final class GeneratedReportMemoizerTest {
    @Test
    void computesReportOnceAcrossRepeatedAndConcurrentReads() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        GeneratedReportMemoizer memoizer = new GeneratedReportMemoizer(() -> {
            calls.incrementAndGet();
            return "report";
        });

        assertEquals("report", memoizer.get());
        var executor = Executors.newFixedThreadPool(4);
        try {
            for (var result : executor.invokeAll(java.util.stream.IntStream.range(0, 16)
                    .mapToObj(index -> (java.util.concurrent.Callable<String>) memoizer::get)
                    .toList())) {
                assertEquals("report", result.get());
            }
        } finally {
            executor.shutdownNow();
        }
        assertEquals(1, calls.get());
    }
}
