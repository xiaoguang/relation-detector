package com.relationdetector.semantic.extraction.runtime;

import com.relationdetector.semantic.extraction.normalization.SemanticBoundedJsonReader;

import java.nio.file.Path;
import java.util.Set;

/**
 * CN: 为一次模型调用声明应用私有 scratch 根、固定 request/response/output 文件与本阶段输出上限。
 * EN: Declares the application-private scratch root, fixed request/response/output files, and phase output limit for
 * one model call. A model client may write only these files and may not choose artifact locations.
 */
public record SemanticModelCallContext(
        Path scratchRoot,
        Path requestPath,
        Path responsePath,
        Path outputPath,
        int maxOutputTokens
) {
    public SemanticModelCallContext {
        if (scratchRoot == null || requestPath == null || responsePath == null || outputPath == null
                || maxOutputTokens <= 0) {
            throw new IllegalArgumentException("semantic model call context is incomplete");
        }
        scratchRoot = scratchRoot.toAbsolutePath().normalize();
        requestPath = contained(scratchRoot, requestPath, "request");
        responsePath = contained(scratchRoot, responsePath, "response");
        outputPath = contained(scratchRoot, outputPath, "output");
        if (Set.of(requestPath, responsePath, outputPath).size() != 3) {
            throw new IllegalArgumentException("semantic model artifact paths must be distinct");
        }
    }

    public static long responseEnvelopeByteLimit(int maxOutputTokens) {
        return SemanticBoundedJsonReader.responseEnvelopeByteLimit(maxOutputTokens);
    }

    public long responseEnvelopeByteLimit() {
        return responseEnvelopeByteLimit(maxOutputTokens);
    }

    public long outputByteLimit() {
        return SemanticBoundedJsonReader.tokenDerivedByteLimit(maxOutputTokens);
    }

    private static Path contained(Path root, Path value, String label) {
        Path resolved = value.toAbsolutePath().normalize();
        if (resolved.equals(root) || !resolved.startsWith(root)) {
            throw new IllegalArgumentException("semantic model " + label + " path escapes scratch");
        }
        return resolved;
    }
}
