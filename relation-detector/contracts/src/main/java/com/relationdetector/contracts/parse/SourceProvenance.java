package com.relationdetector.contracts.parse;

/** Parser-event source location and object provenance. */
public record SourceProvenance(
        String sourceName,
        long line,
        String statementScope,
        String sourceFile,
        String sourceStatementId,
        String sourceBlockId,
        String sourceObjectType,
        String sourceObjectName,
        boolean tokenEventNative,
        boolean fullGrammarNative,
        String fullGrammarContextSource
) {
    public SourceProvenance {
        sourceName = clean(sourceName);
        line = Math.max(1L, line);
        statementScope = clean(statementScope);
        sourceFile = clean(sourceFile);
        sourceStatementId = clean(sourceStatementId);
        sourceBlockId = clean(sourceBlockId);
        sourceObjectType = clean(sourceObjectType);
        sourceObjectName = clean(sourceObjectName);
        fullGrammarContextSource = clean(fullGrammarContextSource);
    }

    public static SourceProvenance tokenEvent(SqlStatementRecord statement, long line, String statementScope) {
        return from(statement, line, statementScope, true, false, "");
    }

    public static SourceProvenance fullGrammar(
            SqlStatementRecord statement,
            long line,
            String statementScope,
            String contextSource
    ) {
        return from(statement, line, statementScope, false, true, contextSource);
    }

    public static SourceProvenance source(String sourceName, long line) {
        return new SourceProvenance(sourceName, line, "", "", "", "", "", "",
                false, false, "");
    }

    public SourceProvenance asFullGrammar(String sourceName, String contextSource) {
        return new SourceProvenance(sourceName, line, statementScope, sourceFile,
                sourceStatementId, sourceBlockId, sourceObjectType, sourceObjectName,
                false, true, contextSource);
    }

    public SourceProvenance withSourceObjectType(String value) {
        return new SourceProvenance(sourceName, line, statementScope, sourceFile,
                sourceStatementId, sourceBlockId, value, sourceObjectName,
                tokenEventNative, fullGrammarNative, fullGrammarContextSource);
    }

    /** Rebases parser-relative provenance onto the exact script statement slice. */
    public SourceProvenance rebase(SqlStatementRecord statement) {
        long absoluteLine = Math.max(1L, statement.startLine()) + Math.max(1L, line) - 1L;
        return new SourceProvenance(
                statement.sourceName(),
                absoluteLine,
                statementScope,
                textOr(statement, "sourceFile", sourceFile),
                textOr(statement, "sourceStatementId", sourceStatementId),
                textOr(statement, "sourceBlockId", sourceBlockId),
                textOr(statement, "sourceObjectType", sourceObjectType),
                textOr(statement, "sourceObjectName", sourceObjectName),
                tokenEventNative,
                fullGrammarNative,
                fullGrammarContextSource);
    }

    private static SourceProvenance from(
            SqlStatementRecord statement,
            long line,
            String statementScope,
            boolean tokenEventNative,
            boolean fullGrammarNative,
            String contextSource
    ) {
        return new SourceProvenance(
                statement.sourceName(),
                line,
                statementScope,
                text(statement, "sourceFile"),
                text(statement, "sourceStatementId"),
                text(statement, "sourceBlockId"),
                text(statement, "sourceObjectType"),
                text(statement, "sourceObjectName"),
                tokenEventNative,
                fullGrammarNative,
                contextSource);
    }

    private static String text(SqlStatementRecord statement, String key) {
        Object value = statement.attributes().get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static String textOr(SqlStatementRecord statement, String key, String fallback) {
        String value = text(statement, key);
        return value.isBlank() ? clean(fallback) : value;
    }

    private static String clean(String value) {
        return value == null ? "" : value;
    }
}
