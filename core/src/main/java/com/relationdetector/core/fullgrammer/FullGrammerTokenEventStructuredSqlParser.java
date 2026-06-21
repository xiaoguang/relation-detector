package com.relationdetector.core.fullgrammer;

import java.util.LinkedHashMap;
import java.util.Map;

import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;

/**
 * Parser entry point for versioned full-grammer SQL profiles.
 *
 * <p>This class wraps the concrete profile parser and adds profile diagnostics.
 * Token-event fallback is selected by the parser factory before this wrapper is
 * created; it is not used here to fill full-grammer events.
 */
public final class FullGrammerTokenEventStructuredSqlParser implements StructuredSqlParser {
    private final SqlGrammarProfileSelection profileSelection;
    private final StructuredSqlParser fullGrammerParser;
    private final String implementation;

    public FullGrammerTokenEventStructuredSqlParser(
            SqlGrammarProfile profile,
            StructuredSqlParser fullGrammerParser
    ) {
        this(new SqlGrammarProfileSelection(profile, false, "", "", "CONFIG"),
                fullGrammerParser,
                "FULL_GRAMMER_PROFILE_PARSER");
    }

    public FullGrammerTokenEventStructuredSqlParser(
            SqlGrammarProfile profile,
            StructuredSqlParser fullGrammerParser,
            String implementation
    ) {
        this(new SqlGrammarProfileSelection(profile, false, "", "", "CONFIG"), fullGrammerParser, implementation);
    }

    public FullGrammerTokenEventStructuredSqlParser(
            SqlGrammarProfileSelection profileSelection,
            StructuredSqlParser fullGrammerParser,
            String implementation
    ) {
        if (profileSelection == null || profileSelection.profile() == null) {
            throw new IllegalArgumentException("profileSelection with profile is required");
        }
        if (fullGrammerParser == null) {
            throw new IllegalArgumentException("fullGrammerParser is required");
        }
        if (implementation == null || implementation.isBlank()) {
            throw new IllegalArgumentException("implementation is required");
        }
        this.profileSelection = profileSelection;
        this.fullGrammerParser = fullGrammerParser;
        this.implementation = implementation;
    }

    @Override
    public StructuredParseResult parseSql(SqlStatementRecord statement, AdaptorContext context) {
        StructuredParseResult result = fullGrammerParser.parseSql(statement, context);
        Map<String, Object> attributes = new LinkedHashMap<>(result.attributes());
        SqlGrammarProfile profile = profileSelection.profile();
        attributes.put("grammarProfile", profile.id());
        attributes.put("selectedGrammarProfile", profile.id());
        attributes.put("grammarProfileDatabaseType", profile.databaseType().name());
        attributes.put("grammarProfileMajorVersion", profile.majorVersion());
        attributes.put("grammarProfileMinorVersion", profile.minorVersion());
        attributes.put("grammarProfileCapabilities", profile.capabilities().stream().sorted().toList());
        attributes.put("requestedDatabaseVersion", profileSelection.requestedDatabaseVersion());
        attributes.put("versionSource", profileSelection.versionSource());
        attributes.put("profileFallback", profileSelection.usedFallback());
        if (!profileSelection.diagnostic().isBlank()) {
            attributes.put("grammarProfileDiagnostic", profileSelection.diagnostic());
        }
        attributes.put("fullGrammerShadow", true);
        attributes.put("fullGrammerImplementation", implementation);
        return new StructuredParseResult(
                "FULL_GRAMMAR_TOKEN_EVENT_SHADOW",
                result.dialect(),
                result.sourceName(),
                result.events(),
                result.warnings(),
                attributes);
    }
}
