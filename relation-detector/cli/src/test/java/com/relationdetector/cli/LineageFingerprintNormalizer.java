package com.relationdetector.cli;

import java.util.Arrays;

final class LineageFingerprintNormalizer {
    private LineageFingerprintNormalizer() {
    }

    static String normalize(String fingerprint) {
        String[] parts = fingerprint.split(":", 3);
        if (parts.length != 3) {
            return fingerprint;
        }
        int arrow = parts[2].lastIndexOf("->");
        if (arrow < 0) {
            return fingerprint;
        }
        String sources = parts[2].substring(0, arrow);
        String target = parts[2].substring(arrow + 2);
        String normalizedSources = Arrays.stream(sources.split(","))
                .filter(source -> !source.isBlank())
                .sorted()
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        return parts[0] + ":" + parts[1] + ":" + normalizedSources + "->" + target;
    }
}
