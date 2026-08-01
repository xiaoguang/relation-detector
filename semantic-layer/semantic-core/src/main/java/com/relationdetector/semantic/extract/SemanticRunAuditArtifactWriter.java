package com.relationdetector.semantic.extract;

import java.nio.file.Path;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * CN: 将单个 shard 或 reconciliation 的请求、原始响应和规范化结果写入临时目录后原子发布。
 * 输入是已验证的有界模型产物，输出是完整审计目录；上游是两类 run writer，下游是文件事务原语。
 * 本类不执行模型、不构造 manifest，也不发布整个 run。
 * EN: Atomically publishes one shard or reconciliation audit directory from validated, bounded model artifacts.
 * Run writers call it above the file transaction layer; it neither invokes a model nor builds manifests or publishes
 * an entire run.
 */
final class SemanticRunAuditArtifactWriter {
    private final SemanticRequestArtifactWriter requests = new SemanticRequestArtifactWriter();
    private final RunArtifactFileStore files;

    SemanticRunAuditArtifactWriter(RunArtifactFileStore files) {
        this.files = files;
    }

    void writeShard(
            Path output,
            String shardId,
            SemanticExtractionPrompt prompt,
            SemanticExtractionResult result,
            JsonNode normalized
    ) {
        writeShard(output, shardId, null, prompt, result, normalized);
    }

    void writeShard(
            Path output,
            String shardId,
            Path externalAuditSidecar,
            SemanticExtractionPrompt prompt,
            SemanticExtractionResult result,
            JsonNode normalized
    ) {
        Path parent = output.resolve("shards");
        Path target = parent.resolve(shardId);
        Path temporary = parent.resolve("." + shardId + ".tmp-" + UUID.randomUUID());
        files.writeDirectoryAtomically(
                temporary,
                target,
                directory -> {
                    writePromptArtifacts(
                            directory, prompt, result, normalized, "semantic-extraction-result.json");
                    if (externalAuditSidecar != null) {
                        files.copyFile(
                                externalAuditSidecar,
                                directory.resolve("external-audit-refs.tsv"),
                                "failed to persist semantic external audit references");
                    }
                });
    }

    void writeReconciliationWithResult(
            Path output,
            SemanticExtractionPrompt prompt,
            SemanticExtractionResult result,
            JsonNode patch
    ) {
        writeReconciliation(output, prompt, result, patch, true);
    }

    void writeReconciliationPatch(
            Path output,
            SemanticExtractionPrompt prompt,
            SemanticExtractionResult result,
            JsonNode patch
    ) {
        writeReconciliation(output, prompt, result, patch, false);
    }

    private void writeReconciliation(
            Path output,
            SemanticExtractionPrompt prompt,
            SemanticExtractionResult result,
            JsonNode patch,
            boolean includeNormalizedResult
    ) {
        Path target = output.resolve("reconciliation");
        Path temporary = output.resolve(".reconciliation.tmp-" + UUID.randomUUID());
        files.writeDirectoryAtomically(temporary, target, directory -> {
            writePromptArtifacts(directory, prompt, result, patch, "patch.json");
            if (includeNormalizedResult) {
                files.writeJson(directory.resolve("semantic-extraction-result.json"), patch);
            }
        });
    }

    private void writePromptArtifacts(
            Path directory,
            SemanticExtractionPrompt prompt,
            SemanticExtractionResult result,
            JsonNode normalized,
            String normalizedFileName
    ) {
        requests.writeRequestOnly(directory, prompt, result.requestJson());
        files.writeText(directory.resolve("semantic-extraction-response.json"), result.responseJson());
        files.writeText(directory.resolve("semantic-extraction-result-raw.json"), result.outputText());
        files.writeJson(directory.resolve(normalizedFileName), normalized);
    }
}
