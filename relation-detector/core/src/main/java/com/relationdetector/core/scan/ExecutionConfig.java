package com.relationdetector.core.scan;

/**
 * CN: 承载一次 scan 的不可变并发预算，不创建额外 scheduler 或 thread pool。
 * EN: Carries an immutable scan parallelism budget without creating an additional scheduler or thread pool.
 */
public record ExecutionConfig(int parallelism) {
    public ExecutionConfig {
        if (parallelism <= 0) {
            throw new IllegalArgumentException("execution parallelism must be positive");
        }
    }
}
