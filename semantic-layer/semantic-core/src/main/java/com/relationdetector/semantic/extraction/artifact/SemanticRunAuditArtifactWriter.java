package com.relationdetector.semantic.extraction.artifact;

import com.relationdetector.semantic.extraction.runtime.SemanticModelCallResult;

import com.relationdetector.semantic.extraction.prompt.SemanticExtractionPrompt;

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
public final class SemanticRunAuditArtifactWriter {
    private final SemanticRequestArtifactWriter requests = new SemanticRequestArtifactWriter();
    private final RunArtifactFileStore files;

    public SemanticRunAuditArtifactWriter(RunArtifactFileStore files) {
        this.files = files;
    }

    public void writeShard(
            Path output,
            String shardId,
            Path externalAuditSidecar,
            SemanticExtractionPrompt prompt,
            SemanticModelCallResult result,
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

    public void writeReconciliationPatch(
            Path output,
            SemanticExtractionPrompt prompt,
            SemanticModelCallResult result,
            JsonNode patch
    ) {
        Path target = output.resolve("reconciliation");
        Path temporary = output.resolve(".reconciliation.tmp-" + UUID.randomUUID());
        files.writeDirectoryAtomically(temporary, target, directory -> {
            writePromptArtifacts(directory, prompt, result, patch, "patch.json");
        });
    }

    private void writePromptArtifacts(
            Path directory,
            SemanticExtractionPrompt prompt,
            SemanticModelCallResult result,
            JsonNode normalized,
            String normalizedFileName
    ) {
        requests.writePromptArtifacts(directory, prompt);
        files.copyFile(
                result.request().path(),
                directory.resolve("semantic-extraction-request.json"),
                "failed to persist semantic model request");
        files.copyFile(
                result.response().path(),
                directory.resolve("semantic-extraction-response.json"),
                "failed to persist semantic model response");
        files.copyFile(
                result.output().path(),
                directory.resolve("semantic-extraction-result-raw.json"),
                "failed to persist semantic model output");
        files.writeJson(directory.resolve(normalizedFileName), normalized);
    }
}
