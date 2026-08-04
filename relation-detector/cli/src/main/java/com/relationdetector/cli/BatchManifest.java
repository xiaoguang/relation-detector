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
        for (int index = 0; index < cases.size(); index++) {
            Path current = cases.get(index).artifactPath();
            rejectConflict(report, current);
            for (int previous = 0; previous < index; previous++) {
                rejectConflict(cases.get(previous).artifactPath(), current);
            }
        }
    }

    private static void rejectConflict(Path first, Path second) {
        if (first.equals(second) || first.startsWith(second) || second.startsWith(first)) {
            throw new IllegalArgumentException("artifact paths overlap: " + first + " and " + second);
        }
    }
}

record BatchCase(String id, Path config, Path output, Path outputBundle) {
    BatchCase {
        if ((output == null) == (outputBundle == null)) {
            throw new IllegalArgumentException("batch case must define exactly one output or outputBundle");
        }
    }

    Path artifactPath() {
        return output == null ? outputBundle : output;
    }
}

enum BatchFailurePolicy {
    CONTINUE,
    FAIL_FAST
}
