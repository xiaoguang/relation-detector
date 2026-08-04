package com.relationdetector.semantic.extraction.artifact;

import java.nio.file.Path;

/**
 * CN: 描述一个落盘 semantic artifact 的不可变路径、字节数与 SHA-256 声明；消费者必须独立复核声明。
 * EN: Declares the immutable path, byte length, and SHA-256 of one file-backed semantic artifact. Every consumer
 * must independently verify the declaration before trusting the file.
 */
public record SemanticArtifactRef(Path path, long bytes, String sha256) {
    public SemanticArtifactRef {
        if (path == null || bytes < 0 || sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("semantic artifact reference is invalid");
        }
        path = path.toAbsolutePath().normalize();
    }
}
