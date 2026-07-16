package com.relationdetector.core.log;

import java.nio.file.Path;

/**
 *
 * Normalizes evidence source names without changing routine/object labels.
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
        String cwd = System.getProperty("user.dir", "").replace('\\', '/');
        if (!cwd.isBlank() && normalized.startsWith(cwd + "/")) {
            normalized = normalized.substring(cwd.length() + 1);
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
