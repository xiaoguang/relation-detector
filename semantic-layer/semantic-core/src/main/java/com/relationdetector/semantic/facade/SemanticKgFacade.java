package com.relationdetector.semantic.facade;

import java.nio.file.Path;
import java.util.List;

import com.relationdetector.semantic.extraction.runtime.SemanticProcessingSession;
import com.relationdetector.semantic.kg.store.SemanticKgArtifactMode;

/**
 * CN: 作为 semantic-core 的 KG 构建入口，把已校验的输入路径送入磁盘后备处理会话并流式发布 KG；
 * 输入是 scan artifacts、目标目录和预算，输出是目标目录中的 evidence graph/KG artifacts。上游是 CLI，
 * 下游是 ingest、evidence 与 KG store；本类不解析参数、不调用模型，也不暴露内部 session。
 *
 * <p>EN: Core facade for KG construction. It sends validated scan-artifact paths through a disk-backed processing
 * session and streams evidence-graph/KG artifacts to the target directory. CLI is upstream and ingest/evidence/KG
 * stores are downstream; this facade does not parse arguments, call a model, or expose its internal session.
 */
public final class SemanticKgFacade {
    public void build(
            List<Path> inputs,
            Path output,
            int maxInputTokens,
            SemanticKgArtifactMode mode
    ) {
        try (SemanticProcessingSession session = SemanticProcessingSession.openForOutput(
                inputs, output, "kg", maxInputTokens)) {
            session.writeKgArtifacts(output, mode);
        }
    }
}
