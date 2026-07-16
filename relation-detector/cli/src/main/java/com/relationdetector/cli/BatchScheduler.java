package com.relationdetector.cli;

import com.relationdetector.contracts.Enums.ErrorCode;
import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.core.scan.ResolvedScanConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

final class BatchScheduler {
    List<BatchCaseOutcome> run(
            List<PreparedBatchCase> cases,
            int caseParallelism,
            int maxWorkerThreads,
            BatchFailurePolicy failurePolicy,
            BatchCaseExecutor caseExecutor
    ) {
        Semaphore workerBudget = new Semaphore(maxWorkerThreads, true);
        AtomicBoolean failed = new AtomicBoolean(false);
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(caseParallelism, cases.size()));
        try {
            List<Future<BatchCaseOutcome>> futures = new ArrayList<>();
            for (PreparedBatchCase item : cases) {
                futures.add(pool.submit(task(item, workerBudget, failed, failurePolicy, caseExecutor)));
            }
            return futures.stream().map(BatchScheduler::outcome).toList();
        } finally {
            pool.shutdownNow();
        }
    }

    private Callable<BatchCaseOutcome> task(
            PreparedBatchCase item,
            Semaphore workerBudget,
            AtomicBoolean failed,
            BatchFailurePolicy failurePolicy,
            BatchCaseExecutor executor
    ) {
        return () -> {
            long started = System.nanoTime();
            if (failurePolicy == BatchFailurePolicy.FAIL_FAST && failed.get()) {
                return BatchCaseOutcome.skipped(item.batchCase());
            }
            boolean acquired = false;
            try {
                workerBudget.acquire(item.workerThreads());
                acquired = true;
                if (failurePolicy == BatchFailurePolicy.FAIL_FAST && failed.get()) {
                    return BatchCaseOutcome.skipped(item.batchCase());
                }
                executor.execute(item);
                return BatchCaseOutcome.success(item.batchCase(), elapsedMillis(started));
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                failed.set(true);
                return BatchCaseOutcome.failed(item.batchCase(), elapsedMillis(started), error);
            } catch (Exception error) {
                failed.set(true);
                return BatchCaseOutcome.failed(item.batchCase(), elapsedMillis(started), error);
            } finally {
                if (acquired) {
                    workerBudget.release(item.workerThreads());
                }
            }
        };
    }

    private static BatchCaseOutcome outcome(Future<BatchCaseOutcome> future) {
        try {
            return future.get();
        } catch (Exception error) {
            throw new IllegalStateException("batch scheduler worker failed", error);
        }
    }

    private static long elapsedMillis(long started) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }
}

record PreparedBatchCase(
        BatchCase batchCase,
        ResolvedScanConfig config,
        DatabaseAdaptor adaptor,
        int workerThreads
) {
}

@FunctionalInterface
interface BatchCaseExecutor {
    void execute(PreparedBatchCase item) throws Exception;
}

enum BatchCaseStatus {
    SUCCESS,
    FAILED,
    SKIPPED_FAIL_FAST
}

record BatchCaseOutcome(
        BatchCase batchCase,
        BatchCaseStatus status,
        long elapsedMillis,
        ErrorCode errorCode,
        String error
) {
    static BatchCaseOutcome success(BatchCase batchCase, long elapsedMillis) {
        return new BatchCaseOutcome(batchCase, BatchCaseStatus.SUCCESS, elapsedMillis, null, "");
    }

    static BatchCaseOutcome failed(BatchCase batchCase, long elapsedMillis, Exception error) {
        ErrorCode code = error instanceof Main.CliFailure failure
                ? failure.code()
                : ErrorCode.SCAN_RUNTIME_ERROR;
        return new BatchCaseOutcome(batchCase, BatchCaseStatus.FAILED, elapsedMillis, code,
                new Main.CliFailure(code).message());
    }

    static BatchCaseOutcome skipped(BatchCase batchCase) {
        return new BatchCaseOutcome(batchCase, BatchCaseStatus.SKIPPED_FAIL_FAST, 0L, null, "");
    }
}
