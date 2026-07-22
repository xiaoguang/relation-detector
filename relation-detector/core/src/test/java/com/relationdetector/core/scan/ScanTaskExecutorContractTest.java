package com.relationdetector.core.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.Test;

class ScanTaskExecutorContractTest {
    @Test
    void serialAndParallelExecutionPreserveAdaptorContractFailure() {
        Callable<String> failing = () -> {
            throw new AdaptorContractException("contract failed");
        };

        try (ScanTaskExecutor serial = new ScanTaskExecutor(1);
             ScanTaskExecutor parallel = new ScanTaskExecutor(2)) {
            AdaptorContractException serialFailure = assertThrows(
                    AdaptorContractException.class, () -> serial.invokeAll(List.of(failing)));
            AdaptorContractException parallelFailure = assertThrows(
                    AdaptorContractException.class,
                    () -> parallel.invokeAll(List.of(() -> "ok", failing)));

            assertEquals(serialFailure.getMessage(), parallelFailure.getMessage());
        }
    }
}
