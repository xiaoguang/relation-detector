package com.relationdetector.cli;

import java.util.Set;

/** Keeps parser-level fixture goldens independent from scan-supplied namespaces. */
final class FixtureFingerprintNormalizer {
    private FixtureFingerprintNormalizer() {
    }

    static String withoutConfiguredNamespace(String fingerprint, String configuredNamespace) {
        if (configuredNamespace == null || configuredNamespace.isBlank()) {
            return fingerprint;
        }
        String needle = configuredNamespace + ".";
        StringBuilder result = new StringBuilder(fingerprint.length());
        int cursor = 0;
        while (cursor < fingerprint.length()) {
            if (isEndpointBoundary(fingerprint, cursor)
                    && cursor + needle.length() <= fingerprint.length()
                    && fingerprint.regionMatches(true, cursor, needle, 0, needle.length())) {
                cursor += needle.length();
            } else {
                result.append(fingerprint.charAt(cursor++));
            }
        }
        return result.toString();
    }

    private static boolean isEndpointBoundary(String fingerprint, int index) {
        if (index == 0) {
            return true;
        }
        char previous = fingerprint.charAt(index - 1);
        return previous == ':' || previous == ',' || previous == '>';
    }

    static String preferExpectedNamespaceForm(
            String fingerprint,
            String configuredNamespace,
            Set<String> expected
    ) {
        if (expected.contains(fingerprint)) {
            return fingerprint;
        }
        String stripped = withoutConfiguredNamespace(fingerprint, configuredNamespace);
        if (expected.contains(stripped)) {
            return stripped;
        }
        Set<String> equivalentExpected = expected.stream()
                .filter(value -> withoutConfiguredNamespace(value, configuredNamespace)
                        .equalsIgnoreCase(stripped))
                .collect(java.util.stream.Collectors.toSet());
        return equivalentExpected.size() == 1
                ? equivalentExpected.iterator().next()
                : stripped;
    }
}
