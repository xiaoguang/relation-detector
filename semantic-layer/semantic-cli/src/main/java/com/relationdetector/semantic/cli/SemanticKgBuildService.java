package com.relationdetector.semantic.cli;

import java.nio.file.Path;

import java.util.List;

import com.relationdetector.semantic.reader.SemanticDiskBackedSession;

/**
 * CN: 复用唯一的 scan bundle 到 evidence graph、KG 和 artifact 写入链路；上游是 build/e2e handler，下游是 semantic-core builder/writer，禁止解析 CLI 或调用模型。
 * EN: Owns the single scan-bundle to evidence-graph, KG, and artifact-writing path shared by build and e2e handlers; it neither parses CLI arguments nor calls a model.
 */
final class SemanticKgBuildService {
    void build(List<Path> inputs, Path output) {
        try (SemanticDiskBackedSession session =
                     SemanticDiskBackedSession.openForOutput(inputs, output, "kg")) {
            session.writeKgArtifacts(output);
        }
    }
}
