package com.relationdetector.cli.verification;

import java.nio.file.Path;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * CN: 将 release 阶段失败状态、固定安全错误和现有审计 artifact 摘要写入失败 manifest；
 * 输入来自发布脚本，输出仅供发布审计消费，不读取大结果或解释业务事实。
 * EN: Writes a release-phase failure, fixed safe error, and an existing audit artifact digest to the failure
 * manifest. Inputs come from release scripts; output serves release auditing and never interprets large fact files.
 */
final class ReleaseFailureManifestWriter {
    void write(
            Path output,
            String phase,
            int status,
            String message,
            String commit,
            String branch,
            Path artifact
    ) {
        if (phase.isBlank() || message.isBlank() || commit.isBlank() || branch.isBlank()
                || !java.nio.file.Files.isRegularFile(artifact)) {
            throw new ReleaseVerificationException("failure manifest inputs are incomplete");
        }
        ObjectNode manifest = ReleaseVerificationJson.MAPPER.createObjectNode();
        manifest.put("status", "FAIL");
        manifest.put("commit", commit);
        manifest.put("branch", branch);
        manifest.put("failedPhase", phase);
        manifest.putArray("errors").add(message);
        manifest.putObject("maven").put(phase + "Status", status);
        ArrayNode artifacts = manifest.putArray("artifacts");
        artifacts.addObject()
                .put("path", artifact.getFileName().toString())
                .put("sha256", VerificationFileSupport.sha256(artifact))
                .put("bytes", VerificationFileSupport.size(artifact));
        ReleaseVerificationJson.write(output, manifest);
    }
}
