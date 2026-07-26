package com.relationdetector.core.log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * CN: 将工作区文件规范化为相对 source，将外部文件规范化为内容摘要 source，同时保留 ROUTINE/object 等非路径标签。
 * EN: Normalizes workspace files to relative sources and external files to content-addressed sources while
 * preserving ROUTINE and object labels.
 */
public final class SourceNameNormalizer {
    private SourceNameNormalizer() {
    }

    public static String normalize(String source) {
        if (source == null || source.isBlank()) {
            return "";
        }
        if (!looksLikePath(source)) {
            return source;
        }
        String normalized = source.replace('\\', '/');
        try {
            Path path = Path.of(normalized);
            if (path.isAbsolute()) {
                Path absolute = path.normalize();
                return isWorkspaceFile(absolute)
                        ? workspaceRelative(absolute)
                        : normalizeFile(absolute);
            }
        } catch (InvalidPathException ignored) {
            return "external/unavailable/" + basenameFromText(normalized);
        }
        int relationDetector = normalized.indexOf("/relation-detector/");
        if (relationDetector >= 0) {
            normalized = normalized.substring(relationDetector + 1);
        }
        return normalized;
    }

    public static String normalize(Path source) {
        return source == null ? "" : normalize(source.toString());
    }

    /**
     * CN: 为一个真实输入文件生成可移植source；工作区外文件以完整内容摘要寻址，读取失败时不泄露路径。
     * EN: Creates a portable source for a real input file; external files use a full content digest and unreadable
     * files never expose their host path.
     */
    public static String normalizeFile(Path source) {
        if (source == null) {
            return "";
        }
        Path absolute = source.toAbsolutePath().normalize();
        if (isWorkspaceFile(absolute)) {
            return workspaceRelative(absolute);
        }
        try {
            return externalSource(absolute, Files.readAllBytes(absolute));
        } catch (IOException error) {
            return unavailableExternalSource(absolute);
        }
    }

    /**
     * CN: 使用调用方已经读取的文本生成source，避免为外部script重复读取并消除读取前后内容竞态。
     * EN: Uses text already read by the caller so an external script is not read twice and its source cannot describe
     * different content from the parsed payload.
     */
    public static String normalizeFile(Path source, String content) {
        if (source == null) {
            return "";
        }
        Path absolute = source.toAbsolutePath().normalize();
        if (isWorkspaceFile(absolute)) {
            return workspaceRelative(absolute);
        }
        if (content == null) {
            return unavailableExternalSource(absolute);
        }
        return externalSource(absolute, content.getBytes(StandardCharsets.UTF_8));
    }

    public static String unavailableExternalSource(Path source) {
        return "external/unavailable/" + basename(source);
    }

    private static boolean isWorkspaceFile(Path source) {
        String workingDirectory = System.getProperty("user.dir", "");
        if (workingDirectory.isBlank()) {
            return false;
        }
        return source.startsWith(Path.of(workingDirectory).toAbsolutePath().normalize());
    }

    private static String workspaceRelative(Path source) {
        Path workspace = Path.of(System.getProperty("user.dir", "")).toAbsolutePath().normalize();
        return workspace.relativize(source).toString().replace('\\', '/');
    }

    private static String externalSource(Path source, byte[] bytes) {
        return "external/sha256-" + sha256(bytes) + "/" + basename(source);
    }

    private static String basename(Path source) {
        Path fileName = source == null ? null : source.getFileName();
        return fileName == null || fileName.toString().isBlank() ? "input" : fileName.toString();
    }

    private static String basenameFromText(String source) {
        int slash = Math.max(source.lastIndexOf('/'), source.lastIndexOf('\\'));
        String value = slash < 0 ? source : source.substring(slash + 1);
        return value.isBlank() ? "input" : value;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static boolean looksLikePath(String source) {
        if (source.startsWith("ROUTINE:")
                || source.startsWith("TRIGGER:")
                || source.startsWith("DATABASE:")
                || source.startsWith("PROFILE:")
                || source.startsWith("derived:")) {
            return false;
        }
        return source.contains("/") || source.contains("\\");
    }
}
