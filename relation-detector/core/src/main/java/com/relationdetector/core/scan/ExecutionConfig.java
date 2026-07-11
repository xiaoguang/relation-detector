package com.relationdetector.core.scan;

/** Immutable scan execution budget. */
public record ExecutionConfig(int parallelism) {
    public ExecutionConfig {
        if (parallelism <= 0) {
            throw new IllegalArgumentException("execution parallelism must be positive");
        }
    }
}
