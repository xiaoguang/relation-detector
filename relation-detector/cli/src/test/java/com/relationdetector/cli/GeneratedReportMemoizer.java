package com.relationdetector.cli;

/** Computes one deterministic generated report per test JVM. */
final class GeneratedReportMemoizer {
    private final CheckedSupplier supplier;
    private volatile String value;

    GeneratedReportMemoizer(CheckedSupplier supplier) {
        this.supplier = supplier;
    }

    String get() throws Exception {
        String current = value;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (value == null) {
                value = supplier.get();
            }
            return value;
        }
    }

    @FunctionalInterface
    interface CheckedSupplier {
        String get() throws Exception;
    }
}
