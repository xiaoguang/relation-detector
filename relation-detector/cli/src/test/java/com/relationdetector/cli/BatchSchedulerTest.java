package com.relationdetector.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BatchSchedulerTest {
    @Test
    void enforcesWeightedThreadBudgetAndKeepsManifestOrder() {
        List<PreparedBatchCase> cases = List.of(
                prepared("first", 2),
                prepared("second", 2),
                prepared("third", 1));
        AtomicInteger activeWeight = new AtomicInteger();
        AtomicInteger maximumWeight = new AtomicInteger();

        List<BatchCaseOutcome> outcomes = new BatchScheduler().run(
                cases,
                3,
                3,
                BatchFailurePolicy.CONTINUE,
                item -> {
                    int active = activeWeight.addAndGet(item.workerThreads());
                    maximumWeight.accumulateAndGet(active, Math::max);
                    Thread.sleep(30);
                    activeWeight.addAndGet(-item.workerThreads());
                });

        assertTrue(maximumWeight.get() <= 3);
        assertEquals(List.of("first", "second", "third"),
                outcomes.stream().map(outcome -> outcome.batchCase().id()).toList());
        assertTrue(outcomes.stream().allMatch(outcome -> outcome.status() == BatchCaseStatus.SUCCESS));
    }

    @Test
    void failFastSkipsCasesThatHaveNotStarted() {
        List<PreparedBatchCase> cases = List.of(prepared("fails", 1), prepared("skipped", 1));

        List<BatchCaseOutcome> outcomes = new BatchScheduler().run(
                cases,
                1,
                1,
                BatchFailurePolicy.FAIL_FAST,
                item -> {
                    if (item.batchCase().id().equals("fails")) {
                        throw new IllegalStateException("expected failure");
                    }
                });

        assertEquals(BatchCaseStatus.FAILED, outcomes.get(0).status());
        assertEquals(BatchCaseStatus.SKIPPED_FAIL_FAST, outcomes.get(1).status());
    }

    private PreparedBatchCase prepared(String id, int workers) {
        BatchCase batchCase = new BatchCase(id, Path.of(id + ".yml"), Path.of(id + ".json"), null);
        return new PreparedBatchCase(batchCase, null, null, workers);
    }
}
