package com.relationdetector.core.fullgrammar;

import java.util.LinkedHashMap;
import java.util.Map;

import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.core.scan.AdaptorContractException;

/**
 * CN: 包装已选中的 versioned full-grammar parser，补充 profile diagnostics，不用 token-event 填补 partial events。
 * EN: Wraps a selected versioned full-grammar parser with profile diagnostics and never fills partial events from token-event.
 *
 * <p>This class wraps the concrete profile parser and adds profile diagnostics.
 * Token-event fallback is selected by the parser factory before this wrapper is
 * created; it is not used here to fill full-grammar events.
 */
public final class FullGrammarStructuredSqlParser implements StructuredSqlParser {
    private final SqlGrammarProfileSelection profileSelection;
    private final StructuredSqlParser fullGrammarParser;
    private final String implementation;

    public FullGrammarStructuredSqlParser(
            SqlGrammarProfile profile,
            StructuredSqlParser fullGrammarParser
    ) {
        this(new SqlGrammarProfileSelection(profile, false, "", "", "CONFIG"),
                fullGrammarParser,
                "FULL_GRAMMAR_PROFILE_PARSER");
    }

    public FullGrammarStructuredSqlParser(
            SqlGrammarProfile profile,
            StructuredSqlParser fullGrammarParser,
            String implementation
    ) {
        this(new SqlGrammarProfileSelection(profile, false, "", "", "CONFIG"), fullGrammarParser, implementation);
    }

    public FullGrammarStructuredSqlParser(
            SqlGrammarProfileSelection profileSelection,
            StructuredSqlParser fullGrammarParser,
            String implementation
    ) {
        if (profileSelection == null || profileSelection.profile() == null) {
            throw new IllegalArgumentException("profileSelection with profile is required");
        }
        if (fullGrammarParser == null) {
            throw new IllegalArgumentException("fullGrammarParser is required");
        }
        if (implementation == null || implementation.isBlank()) {
            throw new IllegalArgumentException("implementation is required");
        }
        this.profileSelection = profileSelection;
        this.fullGrammarParser = fullGrammarParser;
        this.implementation = implementation;
    }

    @Override
    public StructuredParseResult parseSql(SqlStatementRecord statement, AdaptorContext context) {
        StructuredParseResult result = fullGrammarParser.parseSql(statement, context);
        if (result == null) {
            throw new AdaptorContractException(
                    "adaptor parse-result contract violation: full-grammar SQL result is null");
        }
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
        attributes.put("fullGrammarPrimary", true);
        attributes.put("fullGrammarImplementation", implementation);
        return new StructuredParseResult(
                "FULL_GRAMMAR_PROFILE_PRIMARY",
                result.dialect(),
                result.sourceName(),
                result.events(),
                result.warnings(),
                attributes);
    }
}
