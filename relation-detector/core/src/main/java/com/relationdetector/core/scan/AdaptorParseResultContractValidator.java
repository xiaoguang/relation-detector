package com.relationdetector.core.scan;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.Enums.WarningSeverity;
import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.DdlEvent;
import com.relationdetector.contracts.parse.DynamicSqlEvent;
import com.relationdetector.contracts.parse.PredicateEvent;
import com.relationdetector.contracts.parse.ProjectionEvent;
import com.relationdetector.contracts.parse.RowsetEvent;
import com.relationdetector.contracts.parse.ScriptFrameRequest;
import com.relationdetector.contracts.parse.ScriptFrameResult;
import com.relationdetector.contracts.parse.SourceProvenance;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.contracts.parse.WriteEvent;
import com.relationdetector.core.log.SourceNameNormalizer;

/**
 * CN: 在core接纳外部framer、log extractor或structured parser结果前，原子校验statement、typed event、
 * provenance、warning和嵌套attributes，并返回脱离插件容器的副本。输入来自尚未信任的SPI边界，输出供
 * script、parser和scan编排器消费；本类不解析SQL、不改变event语义，也不直接写入scan状态。
 *
 * <p>EN: Atomically validates statements, typed events, provenance, warnings, and nested attributes returned by
 * external framers, log extractors, and structured parsers. It returns detached copies for script, parser, and scan
 * orchestration without parsing SQL, changing event semantics, or mutating scan state.
 */
public final class AdaptorParseResultContractValidator {
    private static final Set<StructuredParseEventType> ROWSET_EVENTS = EnumSet.of(
            StructuredParseEventType.TABLE_REFERENCE,
            StructuredParseEventType.ROWSET_REFERENCE,
            StructuredParseEventType.CTE_DECLARATION,
            StructuredParseEventType.IGNORED_ROWSET,
            StructuredParseEventType.LOCAL_TEMP_TABLE_DECLARATION,
            StructuredParseEventType.TRIGGER_TARGET_TABLE,
            StructuredParseEventType.TRIGGER_PSEUDO_ROWSET);
    private static final Set<StructuredParseEventType> PREDICATE_EVENTS = EnumSet.of(
            StructuredParseEventType.COLUMN_EQUALITY,
            StructuredParseEventType.PREDICATE_EQUALITY,
            StructuredParseEventType.JOIN_USING_COLUMNS,
            StructuredParseEventType.EXISTS_PREDICATE,
            StructuredParseEventType.IN_SUBQUERY_PREDICATE,
            StructuredParseEventType.TUPLE_IN_SUBQUERY_PREDICATE);
    private static final Set<StructuredParseEventType> WRITE_EVENTS = EnumSet.of(
            StructuredParseEventType.WRITE_TARGET,
            StructuredParseEventType.UPDATE_ASSIGNMENT,
            StructuredParseEventType.INSERT_SELECT_MAPPING,
            StructuredParseEventType.MERGE_WRITE_MAPPING);
    private static final Set<StructuredParseEventType> PROJECTION_EVENTS = EnumSet.of(
            StructuredParseEventType.PROJECTION_ITEM,
            StructuredParseEventType.EXPRESSION_SOURCE);
    private static final Set<StructuredParseEventType> DDL_EVENTS = EnumSet.of(
            StructuredParseEventType.DDL_FOREIGN_KEY,
            StructuredParseEventType.DDL_INDEX,
            StructuredParseEventType.DDL_COLUMN);
    private static final Set<StructuredParseEventType> DYNAMIC_EVENTS = EnumSet.of(
            StructuredParseEventType.DYNAMIC_SQL);

    private final AdaptorResultDetachmentSupport detachment = new AdaptorResultDetachmentSupport();

    public ScriptFrameResult validateFrame(ScriptFrameRequest request, ScriptFrameResult raw) {
        require(request != null, "script frame request is null");
        require(raw != null, "script frame result is null");
        require(request.defaultSourceType() != null, "script frame source type is missing");
        String sourceFile = normalizedSource(request.sourceFile(), "script frame source file");
        long lineCount = lineCount(request.text());
        List<SqlStatementRecord> statements = copyStatements(
                raw.statements(), sourceFile, lineCount, "script frame statement");
        List<WarningMessage> warnings = validateWarnings(
                raw.warnings(), sourceFile, "script frame warning");
        return new ScriptFrameResult(statements, warnings);
    }

    public StatementResult validateLog(
            Path file,
            List<SqlStatementRecord> rawStatements,
            List<WarningMessage> rawWarnings
    ) {
        require(file != null, "log source file is null");
        String sourceFile = normalizedSource(file.toString(), "log source file");
        return new StatementResult(
                copyStatements(rawStatements, sourceFile, Long.MAX_VALUE, "log statement"),
                validateWarnings(rawWarnings, sourceFile, "log warning"));
    }

    public StructuredParseResult validateSql(
            SqlStatementRecord statement,
            StructuredParseResult raw,
            List<WarningMessage> callbackWarnings
    ) {
        require(statement != null, "structured SQL statement is null");
        return validateStructured(raw, statement.sourceName(), callbackWarnings, "structured SQL result");
    }

    public StructuredParseResult validateDdl(
            String ddl,
            String sourceName,
            StructuredParseResult raw,
            List<WarningMessage> callbackWarnings
    ) {
        require(ddl != null, "structured DDL text is null");
        return validateStructured(raw, sourceName, callbackWarnings, "structured DDL result");
    }

    List<WarningMessage> validateParserWarnings(
            List<WarningMessage> warnings,
            String expectedSource,
            String boundary
    ) {
        return validateWarnings(warnings, expectedSource, boundary);
    }

    private StructuredParseResult validateStructured(
            StructuredParseResult raw,
            String expectedSource,
            List<WarningMessage> callbackWarnings,
            String boundary
    ) {
        require(raw != null, boundary + " is null");
        requireText(raw.backend(), boundary + " backend");
        requireText(raw.dialect(), boundary + " dialect");
        requireText(raw.sourceName(), boundary + " source name");
        require(java.util.Objects.equals(raw.sourceName(), expectedSource),
                boundary + " source name does not match the input");
        require(raw.events() != null, boundary + " event list is null");
        List<StructuredSqlEvent> events = raw.events().stream()
                .map(event -> copyEvent(event, expectedSource, boundary + " event"))
                .toList();
        List<WarningMessage> warnings = new ArrayList<>();
        warnings.addAll(validateWarnings(callbackWarnings, expectedSource, boundary + " callback warning"));
        warnings.addAll(validateWarnings(raw.warnings(), expectedSource, boundary + " warning"));
        return new StructuredParseResult(
                raw.backend(), raw.dialect(), raw.sourceName(), events, warnings,
                detachment.attributes(raw.attributes(), boundary + " attributes"));
    }

    private List<SqlStatementRecord> copyStatements(
            List<SqlStatementRecord> statements,
            String sourceFile,
            long lineCount,
            String boundary
    ) {
        require(statements != null, boundary + " list is null");
        return statements.stream()
                .map(statement -> copyStatement(statement, sourceFile, lineCount, boundary))
                .toList();
    }

    private SqlStatementRecord copyStatement(
            SqlStatementRecord statement,
            String sourceFile,
            long lineCount,
            String boundary
    ) {
        require(statement != null, boundary + " is null");
        requireText(statement.sql(), boundary + " SQL");
        require(statement.sourceType() != null, boundary + " source type is missing");
        requireText(statement.sourceName(), boundary + " source name");
        require(statement.startLine() >= 1 && statement.endLine() >= statement.startLine(),
                boundary + " line range is invalid");
        require(lineCount == Long.MAX_VALUE || statement.endLine() <= lineCount,
                boundary + " line range exceeds the input");
        Map<String, Object> attributes = detachment.attributes(
                statement.attributes(), boundary + " attributes");
        require(java.util.Objects.equals(sourceFile, text(attributes.get("sourceFile"))),
                boundary + " sourceFile does not match the input");
        requireText(text(attributes.get("sourceStatementId")), boundary + " sourceStatementId");
        return new SqlStatementRecord(
                statement.sql(), statement.sourceType(), statement.sourceName(),
                statement.startLine(), statement.endLine(), attributes);
    }

    private StructuredSqlEvent copyEvent(
            StructuredSqlEvent event,
            String expectedSource,
            String boundary
    ) {
        require(event != null, boundary + " is null");
        require(event.type() != null, boundary + " type is missing");
        require(event.provenance() != null, boundary + " provenance is missing");
        require(eventTypeMatchesRecord(event), boundary + " type does not match its sealed record");
        SourceProvenance provenance = event.provenance();
        require(java.util.Objects.equals(provenance.sourceName(), expectedSource),
                boundary + " source name does not match the input");
        SourceProvenance detached = new SourceProvenance(
                provenance.sourceName(), provenance.line(), provenance.statementScope(),
                provenance.sourceFile(), provenance.sourceStatementId(), provenance.sourceBlockId(),
                provenance.sourceObjectType(), provenance.sourceObjectName(), provenance.tokenEventNative(),
                provenance.fullGrammarNative(), provenance.fullGrammarContextSource());
        return event.withProvenance(detached);
    }

    private boolean eventTypeMatchesRecord(StructuredSqlEvent event) {
        if (event instanceof RowsetEvent) return ROWSET_EVENTS.contains(event.type());
        if (event instanceof PredicateEvent) return PREDICATE_EVENTS.contains(event.type());
        if (event instanceof WriteEvent) return WRITE_EVENTS.contains(event.type());
        if (event instanceof ProjectionEvent) return PROJECTION_EVENTS.contains(event.type());
        if (event instanceof DdlEvent) return DDL_EVENTS.contains(event.type());
        return event instanceof DynamicSqlEvent && DYNAMIC_EVENTS.contains(event.type());
    }

    private List<WarningMessage> validateWarnings(
            List<WarningMessage> warnings,
            String expectedSource,
            String boundary
    ) {
        require(warnings != null, boundary + " list is null");
        List<WarningMessage> result = new ArrayList<>(warnings.size());
        for (WarningMessage warning : warnings) {
            require(warning != null, boundary + " is null");
            require(warning.type() == WarningType.PARSE_WARNING, boundary + " type is invalid");
            require(warning.severity() == WarningSeverity.WARN, boundary + " severity is invalid");
            requireCode(warning.code(), boundary + " code");
            requireText(warning.message(), boundary + " message");
            require(warning.message().length() <= 65_536, boundary + " message is too long");
            require(java.util.Objects.equals(warning.source(), expectedSource),
                    boundary + " source does not match the input");
            require(warning.line() >= 0, boundary + " line is invalid");
            result.add(new WarningMessage(
                    warning.type(), warning.severity(), warning.code(), warning.message(),
                    warning.source(), warning.line(),
                    detachment.attributes(warning.attributes(), boundary + " attributes")));
        }
        return List.copyOf(result);
    }

    private String normalizedSource(String source, String boundary) {
        requireText(source, boundary);
        return SourceNameNormalizer.normalize(source);
    }

    private long lineCount(String text) {
        if (text == null || text.isEmpty()) return 1L;
        return Math.max(1L, text.lines().count());
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private void requireCode(String code, String boundary) {
        require(!blank(code) && code.length() <= 128
                        && code.chars().allMatch(value -> value == '_'
                        || value >= 'A' && value <= 'Z' || value >= '0' && value <= '9'),
                boundary + " is invalid");
    }

    private void requireText(String value, String boundary) {
        require(!blank(value), boundary + " is missing");
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new AdaptorContractException("adaptor parse-result contract violation: " + message);
        }
    }

    public record StatementResult(
            List<SqlStatementRecord> statements,
            List<WarningMessage> warnings
    ) {
        public StatementResult {
            statements = List.copyOf(statements);
            warnings = List.copyOf(warnings);
        }
    }
}
