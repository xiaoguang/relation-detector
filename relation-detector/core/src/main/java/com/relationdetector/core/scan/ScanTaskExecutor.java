package com.relationdetector.core.scan;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * One bounded worker pool shared by every source group in a scan.
 */
final class ScanTaskExecutor implements AutoCloseable {
    private static final AtomicInteger POOL_IDS = new AtomicInteger();

    private final ExecutorService executor;

    ScanTaskExecutor(int parallelism) {
        int threads = Math.max(1, parallelism);
        if (threads == 1) {
            executor = null;
            return;
        }
        int poolId = POOL_IDS.incrementAndGet();
        AtomicInteger threadIds = new AtomicInteger();
        executor = Executors.newFixedThreadPool(threads, task -> {
            Thread thread = new Thread(task,
                    "relation-detector-scan-" + poolId + "-" + threadIds.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    <T> List<T> invokeAll(List<? extends Callable<T>> tasks) {
        if (tasks.isEmpty()) {
            return List.of();
        }
        if (executor == null || tasks.size() == 1) {
            return tasks.stream().map(this::call).toList();
        }
        try {
            List<Future<T>> futures = executor.invokeAll(tasks);
            List<T> results = new ArrayList<>(futures.size());
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return List.copyOf(results);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while executing scan tasks", ex);
        } catch (ExecutionException ex) {
            throw new IllegalStateException("Unexpected scan worker failure", ex.getCause());
        }
    }

    private <T> T call(Callable<T> task) {
        try {
            return task.call();
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Unexpected scan task failure", ex);
        }
    }

    @Override
    public void close() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }
}
