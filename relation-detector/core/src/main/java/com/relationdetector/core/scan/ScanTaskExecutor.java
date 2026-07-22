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
 * CN: 在一次 scan 内以一个有界线程池执行已编排的 source 任务，并按输入顺序返回完整结果列表。
 * 输入由 {@link SourceCollectorPipeline} 等上游提供的无共享状态 {@link Callable} 及 scan 并发度组成；
 * 输出交回调用方合并。串行和并行路径都保留 {@link AdaptorContractException} 的错误类别。本类不解析
 * SQL、不合并事实，也不在任务失败后提交部分结果。
 *
 * <p>EN: Executes already-planned source tasks in one bounded pool per scan and returns a complete result list
 * in input order. Upstream scan coordinators provide stateless callables and the scan parallelism; downstream
 * callers perform fact assembly. Serial and parallel paths preserve {@link AdaptorContractException}. This class
 * does not parse SQL, merge facts, or commit partial task results after a failure.
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
            if (ex.getCause() instanceof AdaptorContractException contractFailure) {
                throw contractFailure;
            }
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
