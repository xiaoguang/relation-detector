package com.relationdetector.semantic.ingest;

import java.nio.file.Path;
import java.util.List;

/**
 * CN: 为生产链路打开流式、磁盘后备的 SemanticInputStore。它要求 COMPLETE inventory
 * 和一致 database identity，不提供将整份 scan JSON 物化为内存模型的兼容入口。
 *
 * EN: Opens the streaming, disk-backed SemanticInputStore used by production. It requires a COMPLETE inventory and
 * consistent database identity and intentionally exposes no whole-file in-memory compatibility reader.
 */
public final class ScanResultReader {
    public SemanticInputStore open(List<Path> scanResultPaths, Path workspace) {
        return SemanticInputStore.open(scanResultPaths, workspace);
    }

}
