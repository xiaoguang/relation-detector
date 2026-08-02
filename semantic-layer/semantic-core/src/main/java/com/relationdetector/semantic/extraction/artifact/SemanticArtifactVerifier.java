package com.relationdetector.semantic.extraction.artifact;

import com.relationdetector.semantic.extraction.normalization.SemanticExtractionValidationException;
import com.relationdetector.semantic.internal.io.SemanticFileDigest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CN: 校验manifest声明的artifact位于run根目录内，并核对文件大小与SHA-256；输入是可信根目录和不可信
 * manifest字段，输出是已验证文件路径。它不解析artifact内容，也不修复损坏manifest。
 *
 * EN: Verifies that a manifest artifact stays within its run root and matches its declared size and SHA-256. It
 * returns the verified path but neither parses artifact payloads nor repairs malformed manifests.
 */
public final class SemanticArtifactVerifier {
    private SemanticArtifactVerifier() {
    }

    public static Path verify(Path root, String relative, long size, String sha256) {
        if (root == null || relative == null || relative.isBlank() || size < 0
                || sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw invalid();
        }
        Path canonicalRoot = root.toAbsolutePath().normalize();
        Path artifact = canonicalRoot.resolve(relative).normalize();
        if (!artifact.startsWith(canonicalRoot) || !Files.isRegularFile(artifact)) {
            throw invalid();
        }
        try {
            SemanticFileDigest.Digest actual = SemanticFileDigest.compute(artifact);
            if (actual.bytes() != size || !actual.sha256().equals(sha256)) {
                throw invalid();
            }
            return artifact;
        } catch (IOException failure) {
            throw invalid();
        }
    }

    private static SemanticExtractionValidationException invalid() {
        return new SemanticExtractionValidationException("semantic artifact does not match its manifest");
    }
}
