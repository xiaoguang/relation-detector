package com.relationdetector.postgres.routine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
        if (descriptor.body() instanceof PlPgSqlStringBody body) {
            if (!"plpgsql".equals(descriptor.declaredLanguage())) return mismatch(descriptor, outer);
            SqlStatementRecord statement = bodyStatement(descriptor, outer, body.text(), body.startLine());
            return plPgSqlParser.parse(statement, context, embeddedSqlParser);
        }
        if (descriptor.body() instanceof SqlStringBody body) {
            SqlStatementRecord statement = bodyStatement(descriptor, outer, body.text(), body.startLine());
            if (descriptor.declaredLanguage().isBlank()) return missingLanguage(descriptor, statement);
            if (!"sql".equals(descriptor.declaredLanguage())) return mismatch(descriptor, outer);
            return parseSqlBody(statement, context, embeddedSqlParser);
        }
        if (descriptor.body() instanceof SqlAtomicBody body) {
            if (!descriptor.declaredLanguage().isBlank() && !"sql".equals(descriptor.declaredLanguage())) {
                return mismatch(descriptor, outer);
            }
            return parseAtomicBody(descriptor, outer, body, context, embeddedSqlParser);
        }
        return unsupported(descriptor, outer);
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

    private PlPgSqlParseOutcome parseAtomicBody(PostgresRoutineDescriptor descriptor,
            SqlStatementRecord outer, SqlAtomicBody body, AdaptorContext context,
            StructuredSqlParser embeddedSqlParser) {
        if (embeddedSqlParser == null) {
            return unsupported("sql atomic", bodyStatement(descriptor, outer, "", body.startLine()));
        }
        List<StructuredSqlEvent> events = new ArrayList<>();
        List<WarningMessage> warnings = new ArrayList<>();
        for (SqlStatementRecord rawStatement : body.statements()) {
            SqlStatementRecord statement = atomicStatement(descriptor, outer, rawStatement);
            var result = embeddedSqlParser.parseSql(statement, context);
            events.addAll(result.events());
            warnings.addAll(result.warnings());
        }
        return new PlPgSqlParseOutcome(events, warnings, body.statements().size(), 0);
    }

    private SqlStatementRecord atomicStatement(PostgresRoutineDescriptor descriptor,
            SqlStatementRecord outer, SqlStatementRecord statement) {
        Map<String, Object> attributes = new LinkedHashMap<>(outer.attributes());
        attributes.putAll(descriptor.provenance());
        attributes.putAll(statement.attributes());
        attributes.put("sourceObjectType", descriptor.sourceObjectType());
        attributes.put("sourceObjectName", descriptor.sourceObjectName());
        attributes.put(PostgresRoutineAttributes.EMBEDDED_SQL, true);
        attributes.put(PostgresRoutineAttributes.NON_COLUMN_IDENTIFIERS,
                PostgresRoutineAttributes.merge(attributes,
                        PostgresRoutineAttributes.triggerPseudoIdentifiers(outer)));
        return new SqlStatementRecord(statement.sql(), outer.sourceType(), outer.sourceName(),
                statement.startLine(), statement.endLine(), attributes);
    }

    private PlPgSqlParseOutcome missingLanguage(PostgresRoutineDescriptor descriptor,
            SqlStatementRecord body) {
        WarningMessage warning = WarningMessage.warn(WarningType.PARSE_WARNING,
                "POSTGRES_ROUTINE_LANGUAGE_MISSING",
                "PostgreSQL string routine body requires an explicit LANGUAGE",
                body.sourceName(), body.startLine(), warningAttributes(descriptor, body));
        return new PlPgSqlParseOutcome(List.of(), List.of(warning), 0, 1);
    }

    private PlPgSqlParseOutcome mismatch(PostgresRoutineDescriptor descriptor, SqlStatementRecord outer) {
        SqlStatementRecord body = bodyStatement(descriptor, outer, "", descriptor.body().startLine());
        WarningMessage warning = WarningMessage.warn(WarningType.PARSE_WARNING,
                "POSTGRES_ROUTINE_BODY_LANGUAGE_MISMATCH",
                "PostgreSQL routine body form does not match LANGUAGE " + descriptor.declaredLanguage(),
                body.sourceName(), body.startLine(), warningAttributes(descriptor, body));
        return new PlPgSqlParseOutcome(List.of(), List.of(warning), 0, 1);
    }

    private PlPgSqlParseOutcome unsupported(PostgresRoutineDescriptor descriptor, SqlStatementRecord outer) {
        return unsupported(descriptor.declaredLanguage(),
                bodyStatement(descriptor, outer, "", descriptor.body().startLine()));
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

    private SqlStatementRecord bodyStatement(PostgresRoutineDescriptor descriptor, SqlStatementRecord outer,
            String text, int startLine) {
        Map<String, Object> attributes = new LinkedHashMap<>(outer.attributes());
        attributes.putAll(descriptor.provenance());
        attributes.put("sourceObjectType", descriptor.sourceObjectType());
        attributes.put("sourceObjectName", descriptor.sourceObjectName());
        attributes.put(PostgresRoutineAttributes.EMBEDDED_SQL, true);
        attributes.put(PostgresRoutineAttributes.NON_COLUMN_IDENTIFIERS,
                PostgresRoutineAttributes.merge(attributes,
                        PostgresRoutineAttributes.triggerPseudoIdentifiers(outer)));
        long lineCount = Math.max(1L, text.lines().count());
        return new SqlStatementRecord(text, outer.sourceType(), outer.sourceName(),
                startLine, startLine + lineCount - 1L, attributes);
    }

    private SqlStatementRecord offsetStatement(SqlStatementRecord body, SqlStatementRecord relative) {
        long start = body.startLine() + relative.startLine() - 1L;
        long end = body.startLine() + relative.endLine() - 1L;
        Map<String, Object> attributes = new LinkedHashMap<>(body.attributes());
        attributes.putAll(relative.attributes());
        attributes.put("sourceLine", start);
        return new SqlStatementRecord(relative.sql(), body.sourceType(), body.sourceName(), start, end, attributes);
    }

    private Map<String, Object> warningAttributes(PostgresRoutineDescriptor descriptor,
            SqlStatementRecord body) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("sourceFile", String.valueOf(body.attributes().getOrDefault("sourceFile", "")));
        attributes.put("sourceObjectType", descriptor.sourceObjectType());
        attributes.put("sourceObjectName", descriptor.sourceObjectName());
        attributes.put("declaredLanguage", descriptor.declaredLanguage());
        return attributes;
    }
}
