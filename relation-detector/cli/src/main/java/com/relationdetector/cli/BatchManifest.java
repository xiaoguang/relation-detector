package com.relationdetector.cli;

import java.nio.file.Path;
import java.util.List;

record BatchManifest(
        int caseParallelism,
        int maxWorkerThreads,
        BatchFailurePolicy failurePolicy,
        Path report,
        List<BatchCase> cases
) {
    BatchManifest {
        cases = List.copyOf(cases);
    }
}

record BatchCase(String id, Path config, Path output, Path directOutput) {
}

enum BatchFailurePolicy {
    CONTINUE,
    FAIL_FAST
}
