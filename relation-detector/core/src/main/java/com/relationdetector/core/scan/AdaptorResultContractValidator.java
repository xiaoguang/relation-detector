package com.relationdetector.core.scan;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.WarningSeverity;
import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.contracts.metadata.MetadataColumnFact;
import com.relationdetector.contracts.metadata.MetadataConstraintFact;
import com.relationdetector.contracts.metadata.MetadataIndexFact;
import com.relationdetector.contracts.metadata.MetadataSnapshot;
import com.relationdetector.contracts.metadata.MetadataTableFact;
import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.DatabaseDdlDefinition;
import com.relationdetector.contracts.parse.DatabaseObjectDefinition;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.core.diagnostics.DiagnosticWarnings;
import com.relationdetector.core.diagnostics.LiveDiagnosticSanitizer;

/**
 * CN: 在 core 接纳外部 adaptor 的 metadata、definition 与 fallback parser 结果前执行完整、原子校验；
 * 输入是尚未信任的 SPI 对象，输出是脱离插件可变容器的副本，供 scan pipeline 后续合并。本类不解析 SQL、
 * 不评分、不执行 naming，也不把插件 message/source 直接写入 live warning。
 *
 * <p>EN: Atomically validates untrusted metadata, definition, and fallback-parser SPI results before core
 * ingestion. It returns detached copies for the scan pipeline and never parses SQL, scores facts, runs naming,
 * or forwards plugin-owned live warning messages and sources.
 */
public final class AdaptorResultContractValidator {
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final Set<EvidenceType> METADATA_EVIDENCE = Set.of(
            EvidenceType.METADATA_FOREIGN_KEY,
            EvidenceType.SOURCE_INDEX,
            EvidenceType.TARGET_UNIQUE,
            EvidenceType.COLUMN_TYPE_COMPATIBLE);
    private static final Set<EvidenceType> SQL_RELATIONSHIP_EVIDENCE = Set.of(
            EvidenceType.VIEW_JOIN,
            EvidenceType.PROCEDURE_JOIN,
            EvidenceType.TRIGGER_REFERENCE,
            EvidenceType.SQL_LOG_JOIN,
            EvidenceType.SQL_LOG_SUBQUERY_IN,
            EvidenceType.SQL_LOG_EXISTS,
            EvidenceType.SQL_LOG_COLUMN_CO_OCCURRENCE,
            EvidenceType.SQL_LOG_TABLE_CO_OCCURRENCE);
    private static final Set<String> LIVE_WARNING_ATTRIBUTES = Set.of(
            "sqlState", "vendorCode", "exceptionClass",
            "objectCatalog", "objectSchema", "objectName", "objectType");
    private final AdaptorResultDetachmentSupport detachment = new AdaptorResultDetachmentSupport();

    public MetadataSnapshot validateMetadata(MetadataSnapshot raw) {
        require(raw != null, "metadata snapshot is null");
        MetadataSnapshot result = new MetadataSnapshot();
        raw.tables().forEach(table -> result.tables().add(copyTable(table, "metadata table")));
        raw.columns().forEach(column -> result.columns().add(copyColumn(column, "metadata column")));
        raw.tableFacts().forEach(fact -> result.tableFacts().add(copyTableFact(fact)));
        raw.columnFacts().forEach(fact -> result.columnFacts().add(copyColumnFact(fact)));
        raw.constraintFacts().forEach(fact -> result.constraintFacts().add(copyConstraintFact(fact)));
        raw.indexFacts().forEach(fact -> result.indexFacts().add(copyIndexFact(fact)));
        raw.relationships().forEach(candidate -> result.relationships().add(copyMetadataCandidate(candidate)));
        raw.auxiliaryEvidence().forEach(evidence -> result.auxiliaryEvidence().add(
                copyEvidence(evidence, METADATA_EVIDENCE, EvidenceSourceType.METADATA, "metadata evidence")));
        result.warnings().addAll(validateLiveWarnings(
                raw.warnings(), LiveDiagnosticSanitizer.Operation.METADATA, "metadata"));
        return result;
    }

    public DefinitionResult<DatabaseObjectDefinition> validateObjects(
            List<DatabaseObjectDefinition> raw,
            List<WarningMessage> callbackWarnings
    ) {
        List<WarningMessage> warnings = new ArrayList<>(validateLiveWarnings(
                callbackWarnings, LiveDiagnosticSanitizer.Operation.OBJECT, "database-objects"));
        List<DatabaseObjectDefinition> definitions = new ArrayList<>();
        if (raw == null) {
            warnings.add(DiagnosticWarnings.objectDefinitionUnavailable(
                    "database-objects", null, null, null, null));
            return new DefinitionResult<>(definitions, warnings);
        }
        for (DatabaseObjectDefinition definition : raw) {
            if (definition == null) {
                warnings.add(DiagnosticWarnings.objectDefinitionUnavailable(
                        "database-objects", null, null, null, null));
            } else if (blank(definition.sql())) {
                warnings.add(DiagnosticWarnings.objectDefinitionUnavailable(
                        "database-objects", definition.catalog(), definition.schema(), definition.name(),
                        definition.type() == null ? null : definition.type().name()));
            } else {
                require(definition.type() != null, "object definition type is missing");
                requireText(definition.name(), "object definition name");
                requireText(definition.source(), "object definition source");
                definitions.add(new DatabaseObjectDefinition(
                        definition.type(), definition.catalog(), definition.schema(), definition.name(),
                        definition.sql(), definition.source()));
            }
        }
        return new DefinitionResult<>(definitions, warnings);
    }

    public DefinitionResult<DatabaseDdlDefinition> validateDatabaseDdl(
            List<DatabaseDdlDefinition> raw,
            List<WarningMessage> callbackWarnings
    ) {
        List<WarningMessage> warnings = new ArrayList<>(validateLiveWarnings(
                callbackWarnings, LiveDiagnosticSanitizer.Operation.DATABASE_DDL, "database-ddl"));
        List<DatabaseDdlDefinition> definitions = new ArrayList<>();
        if (raw == null) {
            warnings.add(DiagnosticWarnings.databaseDdlDefinitionUnavailable(
                    "database-ddl", null, null, null));
            return new DefinitionResult<>(definitions, warnings);
        }
        for (DatabaseDdlDefinition definition : raw) {
            if (definition == null) {
                warnings.add(DiagnosticWarnings.databaseDdlDefinitionUnavailable(
                        "database-ddl", null, null, null));
            } else if (blank(definition.ddl())) {
                warnings.add(DiagnosticWarnings.databaseDdlDefinitionUnavailable(
                        "database-ddl", definition.catalog(), definition.schema(), definition.name()));
            } else {
                requireText(definition.name(), "database DDL name");
                requireText(definition.source(), "database DDL source");
                definitions.add(new DatabaseDdlDefinition(
                        definition.catalog(), definition.schema(), definition.name(),
                        definition.ddl(), definition.source()));
            }
        }
        return new DefinitionResult<>(definitions, warnings);
    }

    public SqlRelationResult validateSqlRelations(
            SqlStatementRecord statement,
            List<RelationshipCandidate> rawCandidates,
            List<WarningMessage> parserWarnings
    ) {
        require(statement != null && statement.sourceType() != null, "fallback statement provenance is incomplete");
        require(rawCandidates != null, "fallback relationship list is null");
        EvidenceSourceType expectedSource = evidenceSourceType(statement.sourceType());
        List<RelationshipCandidate> candidates = rawCandidates.stream()
                .map(candidate -> copySqlCandidate(candidate, expectedSource, statement))
                .toList();
        List<WarningMessage> warnings = validateParserWarnings(parserWarnings, statement);
        return new SqlRelationResult(candidates, warnings);
    }

    private RelationshipCandidate copyMetadataCandidate(RelationshipCandidate candidate) {
        RelationshipCandidate copy = copyCandidateShell(candidate, true, "metadata relationship");
        copyEvidenceLists(candidate, copy, METADATA_EVIDENCE, EvidenceSourceType.METADATA, "metadata relationship");
        boolean declaredForeignKey = java.util.stream.Stream.concat(
                        copy.evidence().stream(), copy.rawEvidence().stream())
                .anyMatch(evidence -> evidence.type() == EvidenceType.METADATA_FOREIGN_KEY);
        require(declaredForeignKey, "metadata relationship lacks foreign-key evidence");
        copy.warnings().addAll(validateLiveWarnings(
                candidate.warnings(), LiveDiagnosticSanitizer.Operation.METADATA, "metadata"));
        return copy;
    }

    private RelationshipCandidate copySqlCandidate(
            RelationshipCandidate candidate,
            EvidenceSourceType expectedSource,
            SqlStatementRecord statement
    ) {
        RelationshipCandidate copy = copyCandidateShell(candidate, false, "fallback relationship");
        copyEvidenceLists(candidate, copy, SQL_RELATIONSHIP_EVIDENCE, expectedSource, "fallback relationship");
        copy.warnings().addAll(validateParserWarnings(candidate.warnings(), statement));
        return copy;
    }

    private RelationshipCandidate copyCandidateShell(
            RelationshipCandidate candidate,
            boolean requireColumns,
            String boundary
    ) {
        require(candidate != null, boundary + " candidate is null");
        Endpoint source = copyEndpoint(candidate.source(), requireColumns, boundary + " source");
        Endpoint target = copyEndpoint(candidate.target(), requireColumns, boundary + " target");
        require(candidate.relationType() != null, boundary + " relation type is missing");
        require(candidate.relationSubType() != null, boundary + " relation subtype is missing");
        requireScore(candidate.confidence(), ZERO, ONE, boundary + " confidence");
        require(candidate.evidence() != null && !candidate.evidence().isEmpty(), boundary + " evidence is empty");
        RelationshipCandidate copy = new RelationshipCandidate(
                source, target, candidate.relationType(), candidate.relationSubType());
        copy.confidence(candidate.confidence());
        copy.attributes().putAll(detachment.attributes(candidate.attributes(), boundary + " attributes"));
        return copy;
    }

    private void copyEvidenceLists(
            RelationshipCandidate source,
            RelationshipCandidate target,
            Set<EvidenceType> allowed,
            EvidenceSourceType expectedSource,
            String boundary
    ) {
        source.evidence().forEach(evidence -> target.evidence().add(
                copyEvidence(evidence, allowed, expectedSource, boundary + " evidence")));
        source.rawEvidence().forEach(evidence -> target.rawEvidence().add(
                copyEvidence(evidence, allowed, expectedSource, boundary + " raw evidence")));
    }

    private Evidence copyEvidence(
            Evidence evidence,
            Set<EvidenceType> allowed,
            EvidenceSourceType expectedSource,
            String boundary
    ) {
        require(evidence != null, boundary + " is null");
        require(allowed.contains(evidence.type()), boundary + " type is not allowed");
        require(evidence.sourceType() == expectedSource, boundary + " source type is invalid");
        requireScore(evidence.score(), BigDecimal.ONE.negate(), ONE, boundary + " score");
        requireText(evidence.source(), boundary + " source");
        return new Evidence(evidence.type(), evidence.score(), evidence.sourceType(), evidence.source(),
                evidence.detail(), detachment.attributes(evidence.attributes(), boundary + " attributes"));
    }

    private List<WarningMessage> validateLiveWarnings(
            List<WarningMessage> warnings,
            LiveDiagnosticSanitizer.Operation operation,
            String source
    ) {
        require(warnings != null, "live warning list is null");
        List<WarningMessage> result = new ArrayList<>(warnings.size());
        for (WarningMessage warning : warnings) {
            require(warning != null, "live warning is null");
            require(warning.type() == WarningType.LIVE_SOURCE_WARNING
                            || warning.type() == WarningType.PERMISSION_WARNING,
                    "live warning type is invalid");
            require(warning.severity() == WarningSeverity.WARN, "live warning severity is invalid");
            requireCode(warning.code(), "live warning code");
            Map<String, Object> context = validatedLiveWarningAttributes(warning.attributes());
            result.add(LiveDiagnosticSanitizer.rebuildAdaptorWarning(
                    warning.type(), warning.code(), operation, source, context));
        }
        return List.copyOf(result);
    }

    private List<WarningMessage> validateParserWarnings(
            List<WarningMessage> warnings,
            SqlStatementRecord statement
    ) {
        require(warnings != null, "fallback parser warning list is null");
        List<WarningMessage> result = new ArrayList<>(warnings.size());
        for (WarningMessage warning : warnings) {
            require(warning != null, "fallback parser warning is null");
            require(warning.type() == WarningType.PARSE_WARNING, "fallback parser warning type is invalid");
            require(warning.severity() == WarningSeverity.WARN, "fallback parser warning severity is invalid");
            requireCode(warning.code(), "fallback parser warning code");
            require(!blank(warning.message()), "fallback parser warning message is missing");
            require(warning.message().length() <= 65_536, "fallback parser warning message is too long");
            require(java.util.Objects.equals(warning.source(), statement.sourceName()),
                    "fallback parser warning source is invalid");
            require(warning.line() == 0
                            || warning.line() >= statement.startLine() && warning.line() <= statement.endLine(),
                    "fallback parser warning line is invalid");
            result.add(new WarningMessage(
                    warning.type(), warning.severity(), warning.code(), warning.message(), warning.source(),
                    warning.line(), detachment.attributes(
                            warning.attributes(), "fallback parser warning attributes")));
        }
        return List.copyOf(result);
    }

    private Map<String, Object> validatedLiveWarningAttributes(Map<String, Object> attributes) {
        require(attributes != null, "live warning attributes are null");
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : LIVE_WARNING_ATTRIBUTES) {
            Object value = attributes.get(key);
            if (value == null) continue;
            if ("vendorCode".equals(key)) {
                require(value instanceof Number, "live warning vendor code is invalid");
                long number = ((Number) value).longValue();
                require(number >= Integer.MIN_VALUE && number <= Integer.MAX_VALUE,
                        "live warning vendor code is invalid");
                result.put(key, (int) number);
            } else {
                require(value instanceof String, "live warning attribute type is invalid");
                String text = (String) value;
                int limit = "sqlState".equals(key) ? 16 : 512;
                require(text.length() <= limit && text.chars().noneMatch(Character::isISOControl),
                        "live warning attribute value is invalid");
                if ("sqlState".equals(key)) {
                    require(text.chars().allMatch(Character::isLetterOrDigit), "live warning SQLState is invalid");
                }
                if ("exceptionClass".equals(key)) {
                    require(text.chars().allMatch(AdaptorResultContractValidator::classNameCharacter),
                            "live warning exception class is invalid");
                }
                result.put(key, text);
            }
        }
        return result;
    }

    private MetadataTableFact copyTableFact(MetadataTableFact fact) {
        require(fact != null, "metadata table fact is null");
        requireText(fact.tableName(), "metadata table fact name");
        return new MetadataTableFact(
                fact.catalog(), fact.schema(), fact.tableName(), fact.tableType(), fact.engine(), fact.comment());
    }

    private MetadataColumnFact copyColumnFact(MetadataColumnFact fact) {
        require(fact != null, "metadata column fact is null");
        requireText(fact.tableName(), "metadata column fact table");
        requireText(fact.columnName(), "metadata column fact column");
        require(fact.ordinalPosition() > 0, "metadata column ordinal is invalid");
        return new MetadataColumnFact(
                fact.catalog(), fact.schema(), fact.tableName(), fact.columnName(), fact.dataType(),
                fact.columnType(), fact.nullable(), fact.defaultValue(), fact.extra(),
                fact.generationExpression(), fact.ordinalPosition());
    }

    private MetadataConstraintFact copyConstraintFact(MetadataConstraintFact fact) {
        require(fact != null, "metadata constraint fact is null");
        requireText(fact.tableName(), "metadata constraint table");
        requireText(fact.constraintName(), "metadata constraint name");
        requireText(fact.constraintType(), "metadata constraint type");
        requireNames(fact.columns(), "metadata constraint columns");
        if (fact.constraintType().toUpperCase(java.util.Locale.ROOT).contains("FOREIGN")) {
            requireText(fact.referencedTable(), "metadata foreign-key target table");
            requireNames(fact.referencedColumns(), "metadata foreign-key target columns");
            require(fact.columns().size() == fact.referencedColumns().size(),
                    "metadata foreign-key ordinality is invalid");
        }
        return new MetadataConstraintFact(
                fact.catalog(), fact.schema(), fact.tableName(), fact.constraintName(), fact.constraintType(),
                fact.columns(), fact.referencedCatalog(), fact.referencedSchema(), fact.referencedTable(),
                fact.referencedColumns(), fact.updateRule(), fact.deleteRule());
    }

    private MetadataIndexFact copyIndexFact(MetadataIndexFact fact) {
        require(fact != null, "metadata index fact is null");
        requireText(fact.tableName(), "metadata index table");
        requireText(fact.indexName(), "metadata index name");
        require(!fact.columns().isEmpty() || !fact.expressions().isEmpty(), "metadata index members are empty");
        fact.columns().forEach(value -> requireText(value, "metadata index column"));
        fact.expressions().forEach(value -> requireText(value, "metadata index expression"));
        require(!fact.seqInIndex().isEmpty()
                        && fact.seqInIndex().stream().allMatch(value -> value != null && value > 0)
                        && new LinkedHashSet<>(fact.seqInIndex()).size() == fact.seqInIndex().size(),
                "metadata index ordinality is invalid");
        return new MetadataIndexFact(
                fact.catalog(), fact.schema(), fact.tableName(), fact.indexName(), fact.unique(), fact.primary(),
                fact.indexType(), fact.visible(), fact.columns(), fact.expressions(), fact.subParts(), fact.seqInIndex());
    }

    private Endpoint copyEndpoint(Endpoint endpoint, boolean requireColumn, String boundary) {
        require(endpoint != null, boundary + " endpoint is null");
        TableId table = copyTable(endpoint.table(), boundary + " table");
        if (!endpoint.isColumnLevel()) {
            require(!requireColumn, boundary + " must be a physical column");
            return Endpoint.table(table);
        }
        ColumnRef column = endpoint.column();
        requireText(column.columnName(), boundary + " column");
        return Endpoint.column(new ColumnRef(
                table, column.columnName(), column.normalizedName(), column.dataType(), column.nullable()));
    }

    private TableId copyTable(TableId table, String boundary) {
        require(table != null, boundary + " is null");
        requireText(table.tableName(), boundary + " name");
        requireText(table.normalizedName(), boundary + " normalized name");
        return new TableId(table.catalog(), table.schema(), table.tableName(), table.normalizedName());
    }

    private ColumnRef copyColumn(ColumnRef column, String boundary) {
        require(column != null, boundary + " is null");
        requireText(column.columnName(), boundary + " name");
        requireText(column.normalizedName(), boundary + " normalized name");
        return new ColumnRef(copyTable(column.table(), boundary + " table"),
                column.columnName(), column.normalizedName(), column.dataType(), column.nullable());
    }

    private static boolean classNameCharacter(int value) {
        return Character.isLetterOrDigit(value) || value == '.' || value == '_' || value == '$';
    }

    private EvidenceSourceType evidenceSourceType(StatementSourceType sourceType) {
        return switch (sourceType) {
            case PROCEDURE, FUNCTION, VIEW, MATERIALIZED_VIEW, TRIGGER, EVENT, RULE, PACKAGE, PACKAGE_BODY ->
                    EvidenceSourceType.DATABASE_OBJECT;
            case NATIVE_LOG -> EvidenceSourceType.NATIVE_LOG;
            default -> EvidenceSourceType.PLAIN_SQL;
        };
    }

    private void requireNames(List<String> names, String boundary) {
        require(names != null && !names.isEmpty(), boundary + " are empty");
        names.forEach(name -> requireText(name, boundary));
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

    private void requireScore(BigDecimal value, BigDecimal minimum, BigDecimal maximum, String boundary) {
        require(value != null && value.compareTo(minimum) >= 0 && value.compareTo(maximum) <= 0,
                boundary + " is invalid");
    }

    private void require(boolean condition, String message) {
        if (!condition) throw violation(message);
    }

    private AdaptorContractException violation(String message) {
        return new AdaptorContractException("adaptor result contract violation: " + message);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record DefinitionResult<T>(List<T> definitions, List<WarningMessage> warnings) {
        public DefinitionResult {
            definitions = List.copyOf(definitions);
            warnings = List.copyOf(warnings);
        }
    }

    public record SqlRelationResult(
            List<RelationshipCandidate> candidates,
            List<WarningMessage> warnings
    ) {
        public SqlRelationResult {
            candidates = List.copyOf(candidates);
            warnings = List.copyOf(warnings);
        }
    }
}
