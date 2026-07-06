package com.relationdetector.contracts.spi;

import java.util.List;

/**
 * CLI/config 请求的扫描范围。
 *
 * <p>CN: core 将 catalog、schema、include/exclude tables 统一传给 metadata、
 * object、database DDL 和 profiler collector。
 *
 * <p>EN: Scan scope requested by CLI/config and passed to adaptors for
 * metadata, object, database DDL, and profiling collection.
 */
public record ScanScope(
        String catalog,
        String schema,
        List<String> includeTables,
        List<String> excludeTables
) {
    public ScanScope {
        includeTables = includeTables == null ? List.of() : List.copyOf(includeTables);
        excludeTables = excludeTables == null ? List.of() : List.copyOf(excludeTables);
    }
}
