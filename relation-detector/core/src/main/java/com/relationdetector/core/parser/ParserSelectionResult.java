package com.relationdetector.core.parser;

import java.util.LinkedHashMap;
import java.util.Map;

import com.relationdetector.core.fullgrammar.SqlGrammarProfileSelection;

/**
 * ParserBundle 选择结果。
 *
 * <p>CN: 该 record 是对外诊断口径的唯一来源，避免 SQL runner 与 DDL runner
 * 分别拼装 parser mode/profile/fallback attributes。
 *
 * <p>EN: Selection result for a ParserBundle. It is the single source for
 * parser-mode/profile/fallback diagnostics so SQL and DDL runners do not build
 * those attributes independently.
 */
public record ParserSelectionResult(
        SqlGrammarProfileSelection profileSelection,
        String requestedMode,
        String selectedMode,
        String selectedGrammarProfile,
        String requestedDatabaseVersion,
        String versionSource,
        String fallbackReason,
        boolean profileFallback
) {
    public Map<String, Object> attributes() {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("parserModeRequested", blankToDefault(requestedMode, "auto"));
        attributes.put("parserModeSelected", blankToDefault(selectedMode, "token-event"));
        attributes.put("selectedGrammarProfile", nullToBlank(selectedGrammarProfile));
        attributes.put("requestedDatabaseVersion", nullToBlank(requestedDatabaseVersion));
        attributes.put("versionSource", blankToDefault(versionSource, "UNKNOWN"));
        attributes.put("parserFallbackReason", nullToBlank(fallbackReason));
        attributes.put("profileFallback", profileFallback);
        return attributes;
    }

    public ParserSelectionResult runtimeFallback(String reason) {
        return new ParserSelectionResult(
                profileSelection,
                requestedMode,
                "token-event",
                "",
                requestedDatabaseVersion,
                versionSource,
                reason,
                true);
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
