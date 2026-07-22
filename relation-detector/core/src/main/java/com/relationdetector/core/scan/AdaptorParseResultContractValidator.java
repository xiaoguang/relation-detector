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
import com.relationdetector.contracts.parse.ExpressionSource;
import com.relationdetector.contracts.parse.ExpressionTrace;
import com.relationdetector.contracts.parse.PredicateEvent;
import com.relationdetector.contracts.parse.PredicateGuard;
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
    private static final Set<String> ROUTINE_OBJECT_SUBTYPES = Set.of(
            "PROCEDURE", "FUNCTION", "PACKAGE", "PACKAGE_BODY", "EVENT");

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
        return validateStructured(raw, statement.sourceName(), callbackWarnings,
                "structured SQL result", statement, statement.endLine());
    }

    public StructuredParseResult validateDdl(
            String ddl,
            String sourceName,
            StructuredParseResult raw,
            List<WarningMessage> callbackWarnings
    ) {
        require(ddl != null, "structured DDL text is null");
        return validateStructured(raw, sourceName, callbackWarnings,
                "structured DDL result", null, Long.MAX_VALUE);
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
            String boundary,
            SqlStatementRecord statement,
            long maximumLine
    ) {
        require(raw != null, boundary + " is null");
        requireText(raw.backend(), boundary + " backend");
        requireText(raw.dialect(), boundary + " dialect");
        requireText(raw.sourceName(), boundary + " source name");
        require(java.util.Objects.equals(raw.sourceName(), expectedSource),
                boundary + " source name does not match the input");
        require(raw.events() != null, boundary + " event list is null");
        List<StructuredSqlEvent> events = raw.events().stream()
                .map(event -> copyEvent(event, expectedSource, boundary + " event", statement, maximumLine))
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
            String boundary,
            SqlStatementRecord statement,
            long maximumLine
    ) {
        require(event != null, boundary + " is null");
        require(event.type() != null, boundary + " type is missing");
        require(event.provenance() != null, boundary + " provenance is missing");
        require(eventTypeMatchesRecord(event), boundary + " type does not match its sealed record");
        validateEventPayload(event, boundary);
        SourceProvenance provenance = event.provenance();
        require(java.util.Objects.equals(
                        SourceNameNormalizer.normalize(provenance.sourceName()),
                        SourceNameNormalizer.normalize(expectedSource)),
                boundary + " source name does not match the input");
        validateProvenance(provenance, statement, maximumLine, boundary);
        SourceProvenance detached = new SourceProvenance(
                provenance.sourceName(), provenance.line(), provenance.statementScope(),
                provenance.sourceFile(), provenance.sourceStatementId(), provenance.sourceBlockId(),
                provenance.sourceObjectType(), provenance.sourceObjectName(), provenance.tokenEventNative(),
                provenance.fullGrammarNative(), provenance.fullGrammarContextSource());
        return event.withProvenance(detached);
    }

    /**
     * CN: 按sealed event类型验证事实消费者真正依赖的typed payload；输入是尚未信任的event，成功时无
     * 副作用，字段缺失或类型组合冲突时抛出AdaptorContractException，且不会提交部分parser结果。
     *
     * <p>EN: Validates the typed payload required by downstream fact consumers for one untrusted sealed event.
     * It has no success-side effects and throws AdaptorContractException on missing fields or incompatible shapes,
     * before any partial parser result can be committed.
     */
    private void validateEventPayload(StructuredSqlEvent event, String boundary) {
        switch (event.type()) {
            case TABLE_REFERENCE, ROWSET_REFERENCE ->
                    require(anyText(event.qualifiedTable(), event.table()), boundary + " rowset table is missing");
            case CTE_DECLARATION, IGNORED_ROWSET ->
                    requireText(event.name(), boundary + " local rowset name");
            case LOCAL_TEMP_TABLE_DECLARATION ->
                    require(anyText(event.qualifiedTable(), event.table(), event.name()),
                            boundary + " local temporary table is missing");
            case TRIGGER_TARGET_TABLE ->
                    require(anyText(event.qualifiedTable(), event.table()), boundary + " trigger table is missing");
            case TRIGGER_PSEUDO_ROWSET -> {
                requireText(event.name(), boundary + " trigger pseudo-rowset name");
                requireText(event.targetTable(), boundary + " trigger pseudo-rowset target");
            }
            case COLUMN_EQUALITY, PREDICATE_EQUALITY, EXISTS_PREDICATE -> {
                requireExpressionSource(event.left(), boundary + " left source");
                requireExpressionSource(event.right(), boundary + " right source");
                validateGuards(event.predicateGuards(), boundary);
            }
            case JOIN_USING_COLUMNS -> {
                requireText(event.left().alias(), boundary + " USING left alias");
                requireText(event.right().alias(), boundary + " USING right alias");
                require(!event.usingColumns().isEmpty(), boundary + " USING columns are empty");
                event.usingColumns().forEach(column -> requireText(column, boundary + " USING column"));
                validateGuards(event.predicateGuards(), boundary);
            }
            case IN_SUBQUERY_PREDICATE -> {
                require(event.verifiedColumnSubquery(), boundary + " IN subquery is not verified");
                validateSources(event.outerSources(), boundary + " IN outer sources", true);
                validateSources(event.innerSources(), boundary + " IN inner sources", true);
                validateGuards(event.predicateGuards(), boundary);
            }
            case TUPLE_IN_SUBQUERY_PREDICATE -> {
                require(event.verifiedColumnSubquery(), boundary + " tuple-IN subquery is not verified");
                validateSources(event.outerSources(), boundary + " tuple-IN outer sources", true);
                validateSources(event.innerSources(), boundary + " tuple-IN inner sources", true);
                require(event.outerSources().size() == event.innerSources().size(),
                        boundary + " tuple-IN arity does not match");
                validateGuards(event.predicateGuards(), boundary);
            }
            case WRITE_TARGET ->
                    require(anyText(event.qualifiedTable(), event.table()), boundary + " write target is missing");
            case UPDATE_ASSIGNMENT, INSERT_SELECT_MAPPING, MERGE_WRITE_MAPPING -> {
                require(anyText(event.targetTable(), event.targetAlias()), boundary + " write owner is missing");
                requireText(event.targetColumn(), boundary + " write target column");
                requireText(event.mappingKind(), boundary + " write mapping kind");
                validateTrace(event.expression(), boundary + " write expression", false);
            }
            case PROJECTION_ITEM -> {
                require(anyText(event.outputColumn(), event.outputAlias()), boundary + " projection output is missing");
                validateTrace(event.expression(), boundary + " projection expression", false);
            }
            case EXPRESSION_SOURCE -> validateTrace(event.expression(), boundary + " expression source", true);
            case DDL_FOREIGN_KEY -> {
                requireText(event.sourceTable(), boundary + " foreign-key source table");
                requireText(event.sourceColumn(), boundary + " foreign-key source column");
                requireText(event.targetTable(), boundary + " foreign-key target table");
                requireText(event.targetColumn(), boundary + " foreign-key target column");
                validateComposite(event, boundary);
            }
            case DDL_INDEX -> {
                requireText(event.table(), boundary + " index table");
                requireText(event.column(), boundary + " index column");
                requireText(event.role(), boundary + " index role");
                requireText(event.kind(), boundary + " index kind");
                validateComposite(event, boundary);
            }
            case DDL_COLUMN -> {
                requireText(event.table(), boundary + " DDL column table");
                requireText(event.column(), boundary + " DDL column");
                validateComposite(event, boundary);
            }
            case DYNAMIC_SQL -> requireText(event.reason(), boundary + " dynamic SQL reason");
        }
    }

    private void validateProvenance(
            SourceProvenance provenance,
            SqlStatementRecord statement,
            long maximumLine,
            String boundary
    ) {
        long minimumLine = statement == null ? 1L : statement.startLine();
        require(provenance.line() >= minimumLine && provenance.line() <= maximumLine,
                boundary + " source line is outside the input");
        require(!(provenance.tokenEventNative() && provenance.fullGrammarNative()),
                boundary + " parser origin is contradictory");
        if (provenance.fullGrammarNative()) {
            requireText(provenance.fullGrammarContextSource(), boundary + " full-grammar context source");
        }
        if (statement == null) {
            return;
        }
        requireMatchesStatement(provenance.sourceFile(), statement, "sourceFile", true, boundary);
        requireMatchesStatement(provenance.sourceStatementId(), statement, "sourceStatementId", false, boundary);
        requireMatchesStatement(provenance.sourceBlockId(), statement, "sourceBlockId", false, boundary);
        requireSourceObjectTypeMatchesStatement(provenance.sourceObjectType(), statement, boundary);
        requireMatchesStatement(provenance.sourceObjectName(), statement, "sourceObjectName", false, boundary);
    }

    private void requireSourceObjectTypeMatchesStatement(
            String actual,
            SqlStatementRecord statement,
            String boundary
    ) {
        if (blank(actual)) {
            return;
        }
        String expected = text(statement.attributes().get("sourceObjectType"));
        require(!blank(expected), boundary + " sourceObjectType is not declared by the input");
        require(java.util.Objects.equals(actual, expected)
                        || ("ROUTINE".equals(expected) && ROUTINE_OBJECT_SUBTYPES.contains(actual)),
                boundary + " sourceObjectType does not match the input");
    }

    private void requireMatchesStatement(
            String actual,
            SqlStatementRecord statement,
            String key,
            boolean normalizePath,
            String boundary
    ) {
        if (blank(actual)) {
            return;
        }
        String expected = text(statement.attributes().get(key));
        require(!blank(expected), boundary + " " + key + " is not declared by the input");
        String comparedActual = normalizePath ? SourceNameNormalizer.normalize(actual) : actual;
        String comparedExpected = normalizePath ? SourceNameNormalizer.normalize(expected) : expected;
        require(java.util.Objects.equals(comparedActual, comparedExpected),
                boundary + " " + key + " does not match the input");
    }

    private void validateTrace(ExpressionTrace trace, String boundary, boolean requireSources) {
        require(trace != null, boundary + " is null");
        require(trace.flowKind() != null, boundary + " flow kind is missing");
        require(trace.transformType() != null, boundary + " transform type is missing");
        validateSources(trace.sources(), boundary + " sources", requireSources);
    }

    private void validateSources(List<ExpressionSource> sources, String boundary, boolean requireSources) {
        require(sources != null, boundary + " are null");
        if (requireSources) {
            require(!sources.isEmpty(), boundary + " are empty");
        }
        sources.forEach(source -> requireExpressionSource(source, boundary + " item"));
    }

    private void requireExpressionSource(ExpressionSource source, String boundary) {
        require(source != null, boundary + " is null");
        requireText(source.column(), boundary + " column");
    }

    private void validateGuards(List<PredicateGuard> guards, String boundary) {
        require(guards != null, boundary + " guards are null");
        for (PredicateGuard guard : guards) {
            require(guard != null, boundary + " guard is null");
            requireExpressionSource(guard.discriminator(), boundary + " guard discriminator");
            require("EQUALS".equals(guard.operator()), boundary + " guard operator is invalid");
        }
    }

    private void validateComposite(StructuredSqlEvent event, String boundary) {
        require(event.compositePosition() >= 1 && event.compositeSize() >= 1
                        && event.compositePosition() <= event.compositeSize(),
                boundary + " composite position is invalid");
    }

    private boolean anyText(String... values) {
        return java.util.Arrays.stream(values).anyMatch(value -> !blank(value));
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
