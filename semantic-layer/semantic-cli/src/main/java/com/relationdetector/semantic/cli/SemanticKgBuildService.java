package com.relationdetector.semantic.cli;

import java.nio.file.Path;

import java.util.List;

import com.relationdetector.semantic.reader.SemanticDiskBackedSession;
import com.relationdetector.semantic.reader.SemanticKgArtifactMode;

/**
 * CN: 通过 {@link SemanticDiskBackedSession} 把输入路径汇入全局磁盘 evidence store，并流式写出
 * evidence graph、KG 和审计 artifact；上游是 build handler，下游是 semantic-core 的磁盘 writer。
 * {@code ScanBundle} 仅是有界兼容/窗口模型，本服务禁止完整物化全量输入、解析 CLI 或调用模型。
 *
 * EN: Uses {@link SemanticDiskBackedSession} to ingest input paths into the global disk evidence store and
 * stream evidence-graph, KG, and audit artifacts. Its upstream is the build handler and its downstream is the
 * semantic-core disk writer. {@code ScanBundle} is only a bounded compatibility/window model; this service
 * must not materialize the complete input, parse CLI arguments, or call a model.
 */
final class SemanticKgBuildService {
    /**
     * CN: 打开一次磁盘后备会话并将完整输入范围流式写入目标目录；失败时由会话清理工作区，
     * 不返回或缓存全量图。
     *
     * EN: Opens one disk-backed session and streams the complete input scope to the target directory. On
     * failure the session cleans its workspace; this method neither returns nor caches the whole graph.
     */
    void build(
            List<Path> inputs,
            Path output,
            int maxInputTokens,
            SemanticKgArtifactMode mode
    ) {
        try (SemanticDiskBackedSession session =
                     SemanticDiskBackedSession.openForOutput(
                             inputs, output, "kg", maxInputTokens)) {
            session.writeKgArtifacts(output, mode);
        }
    }
}
