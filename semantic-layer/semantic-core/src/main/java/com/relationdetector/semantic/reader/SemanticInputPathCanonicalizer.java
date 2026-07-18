package com.relationdetector.semantic.reader;

import java.nio.file.Path;

/**
 * CN: 将 semantic build 输入路径规范化为 workspace-relative 或 basename label，防止 artifact 暴露本机绝对路径；不检查文件内容或存在性。
 * EN: Canonicalizes semantic-build input paths to workspace-relative labels or basenames so artifacts do not expose local absolute paths. It does not inspect file content or existence.
 */
public final class SemanticInputPathCanonicalizer {
    private SemanticInputPathCanonicalizer() {
    }

    public static String canonicalize(Path input) {
        if (input == null) {
            return "external-input";
        }
        Path normalized = input.normalize();
        if (!normalized.isAbsolute()) {
            return separators(normalized.toString());
        }
        Path workspace = Path.of("").toAbsolutePath().normalize();
        if (normalized.startsWith(workspace)) {
            return separators(workspace.relativize(normalized).toString());
        }
        Path filename = normalized.getFileName();
        return filename == null ? "external-input" : separators(filename.toString());
    }

    private static String separators(String value) {
        return value.replace('\\', '/');
    }
}
