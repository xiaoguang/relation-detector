package com.relationdetector.postgres.routine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.ScriptFrameRequest;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.postgres.script.PostgresScriptFramer;

public final class PostgresRoutineLanguageDispatcher {
    private final PlPgSqlBodyParser plPgSqlParser;

    public PostgresRoutineLanguageDispatcher(PlPgSqlBodyParser plPgSqlParser) {
        this.plPgSqlParser = java.util.Objects.requireNonNull(plPgSqlParser, "plPgSqlParser");
    }

    public PlPgSqlParseOutcome dispatch(PostgresRoutineDescriptor descriptor, SqlStatementRecord outer,
            AdaptorContext context, StructuredSqlParser embeddedSqlParser) {
        if (descriptor.body().isBlank()) return PlPgSqlParseOutcome.empty();
        SqlStatementRecord body = bodyStatement(descriptor, outer);
        return switch (descriptor.kind()) {
            case PLPGSQL -> plPgSqlParser.parse(body, context, embeddedSqlParser);
            case SQL_STRING -> parseSqlBody(body, context, embeddedSqlParser);
            case SQL_ATOMIC -> parseEmbedded(body, context, embeddedSqlParser);
            case UNSUPPORTED_LANGUAGE -> unsupported(descriptor, outer);
        };
    }

    private PlPgSqlParseOutcome parseSqlBody(SqlStatementRecord body, AdaptorContext context,
            StructuredSqlParser embeddedSqlParser) {
        if (embeddedSqlParser == null) return unsupported("sql", body);
        var framed = new PostgresScriptFramer().frame(new ScriptFrameRequest(
                body.sql(), String.valueOf(body.attributes().getOrDefault("sourceFile", body.sourceName())),
                body.sourceType()));
        List<StructuredSqlEvent> events = new ArrayList<>();
        List<WarningMessage> warnings = new ArrayList<>(framed.warnings());
        for (SqlStatementRecord relative : framed.statements()) {
            SqlStatementRecord statement = offsetStatement(body, relative);
            var result = embeddedSqlParser.parseSql(statement, context);
            events.addAll(result.events());
            warnings.addAll(result.warnings());
        }
        return new PlPgSqlParseOutcome(events, warnings, framed.statements().size(), 0);
    }

    private PlPgSqlParseOutcome parseEmbedded(SqlStatementRecord body, AdaptorContext context,
            StructuredSqlParser embeddedSqlParser) {
        if (embeddedSqlParser == null) return unsupported("sql atomic", body);
        var result = embeddedSqlParser.parseSql(body, context);
        return new PlPgSqlParseOutcome(result.events(), result.warnings(), 1, 0);
    }

    private PlPgSqlParseOutcome unsupported(PostgresRoutineDescriptor descriptor, SqlStatementRecord outer) {
        return unsupported(descriptor.declaredLanguage(), bodyStatement(descriptor, outer));
    }

    private PlPgSqlParseOutcome unsupported(String language, SqlStatementRecord body) {
        WarningMessage warning = WarningMessage.warn(WarningType.PARSE_WARNING,
                "POSTGRES_ROUTINE_LANGUAGE_UNSUPPORTED",
                "PostgreSQL routine language is unsupported: " + language,
                body.sourceName(), body.startLine(), Map.of(
                        "sourceFile", String.valueOf(body.attributes().getOrDefault("sourceFile", "")),
                        "sourceObjectType", String.valueOf(body.attributes().getOrDefault("sourceObjectType", "")),
                        "sourceObjectName", String.valueOf(body.attributes().getOrDefault("sourceObjectName", "")),
                        "declaredLanguage", language == null ? "" : language));
        return new PlPgSqlParseOutcome(List.of(), List.of(warning), 0, 1);
    }

    private SqlStatementRecord bodyStatement(PostgresRoutineDescriptor descriptor, SqlStatementRecord outer) {
        Map<String, Object> attributes = new java.util.LinkedHashMap<>(outer.attributes());
        attributes.put("sourceObjectType", descriptor.sourceObjectType());
        attributes.put("sourceObjectName", descriptor.sourceObjectName());
        attributes.put(PostgresRoutineAttributes.EMBEDDED_SQL, true);
        attributes.put(PostgresRoutineAttributes.NON_COLUMN_IDENTIFIERS,
                PostgresRoutineAttributes.merge(attributes,
                        PostgresRoutineAttributes.triggerPseudoIdentifiers(outer)));
        long lineCount = Math.max(1L, descriptor.body().lines().count());
        return new SqlStatementRecord(descriptor.body(), outer.sourceType(), outer.sourceName(),
                descriptor.bodyStartLine(), descriptor.bodyStartLine() + lineCount - 1L,
                attributes);
    }

    private SqlStatementRecord offsetStatement(SqlStatementRecord body, SqlStatementRecord relative) {
        long start = body.startLine() + relative.startLine() - 1L;
        long end = body.startLine() + relative.endLine() - 1L;
        Map<String, Object> attributes = new java.util.LinkedHashMap<>(body.attributes());
        attributes.putAll(relative.attributes());
        attributes.put("sourceLine", start);
        return new SqlStatementRecord(relative.sql(), body.sourceType(), body.sourceName(), start, end, attributes);
    }
}
