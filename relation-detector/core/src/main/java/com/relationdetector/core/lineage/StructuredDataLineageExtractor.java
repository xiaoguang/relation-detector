package com.relationdetector.core.lineage;


import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.model.DataLineageEvidence;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.spi.IdentifierRules;
import com.relationdetector.core.identity.AliasSymbolTable;
import com.relationdetector.core.identity.CanonicalIdentifierResolver;
import com.relationdetector.core.identity.NamespaceContext;
import com.relationdetector.core.lineage.model.AssignmentMapping;
import com.relationdetector.core.lineage.model.ExpressionSourceSet;
import com.relationdetector.core.log.SourceNameNormalizer;
import com.relationdetector.core.provenance.EvidenceProvenanceMapper;

/**
 * SQL Data Lineage 语义抽取器。
 *
 * <p>CN: 本类消费 token-event 与 full-grammer 共享的写入/projection 结构事件，输出
 * 数据库内部字段血缘。它不做 Parameter Binding，不把参数、literal、JSON path 或局部变量
 * 作为 source。
 *
 * <p>EN: SQL Data Lineage semantic extractor. It consumes write/projection
 * structured events shared by token-event and full-grammer and emits
 * database-internal field lineage. It does not perform parameter binding and
 * does not treat parameters, literals, JSON paths, or local variables as sources.
 */
public final class StructuredDataLineageExtractor {
    private final CanonicalIdentifierResolver identifiers;
    private final NamespaceContext namespace;

    public StructuredDataLineageExtractor() {
        this(defaultIdentifierRules(), NamespaceContext.empty());
    }

    public StructuredDataLineageExtractor(IdentifierRules identifierRules) {
        this(identifierRules, NamespaceContext.empty());
    }

    public StructuredDataLineageExtractor(IdentifierRules identifierRules, NamespaceContext namespace) {
        this.identifiers = new CanonicalIdentifierResolver(identifierRules);
        this.namespace = namespace == null ? NamespaceContext.empty() : namespace;
    }

    /**
     * 从结构化 SQL events 抽取字段血缘。
     *
     * <p>EN: Extracts field lineage from structured SQL events.
     */
    public List<DataLineageCandidate> extract(SqlStatementRecord statement, StructuredParseResult structured) {
        return extractNative(statement, structured, Set.of());
    }

    public List<DataLineageCandidate> extract(
            SqlStatementRecord statement,
            StructuredParseResult structured,
            Set<TableId> knownPhysicalTables
    ) {
        return extractNative(statement, structured, knownPhysicalTables == null ? Set.of() : knownPhysicalTables);
    }

    private List<DataLineageCandidate> extractNative(
            SqlStatementRecord statement,
            StructuredParseResult structured,
            Set<TableId> knownPhysicalTables
    ) {
        List<DataLineageCandidate> candidates = new ArrayList<>();
        Set<String> allLocalTempTables = localTempTables(statement, structured.events());
        for (List<StructuredSqlEvent> events : scopedEventGroups(structured.events())) {
            candidates.addAll(extractFromEvents(statement, events, knownPhysicalTables, allLocalTempTables));
        }
        return candidates;
    }

    private List<DataLineageCandidate> extractFromEvents(
            SqlStatementRecord statement,
            List<StructuredSqlEvent> events,
            Set<TableId> knownPhysicalTables,
            Set<String> allLocalTempTables
    ) {
        AliasSymbolTable aliases = aliases(events);
        Set<String> localTempTables = new java.util.LinkedHashSet<>(allLocalTempTables);
        localTempTables.addAll(localTempTables(statement, events));
        Set<String> ignoredRowsets = ignoredRowsets(events);
        ProjectionTraceResolver projectionTraces = ProjectionTraceResolver.fromEvents(
                events, aliases, ignoredRowsets);
        List<DataLineageCandidate> candidates = new ArrayList<>();
        for (StructuredSqlEvent event : events) {
            if (event.type() != StructuredParseEventType.UPDATE_ASSIGNMENT
                    && event.type() != StructuredParseEventType.INSERT_SELECT_MAPPING
                    && event.type() != StructuredParseEventType.MERGE_WRITE_MAPPING) {
                continue;
            }
            ColumnRef target = targetColumn(event, aliases);
            if (target == null
                    || isLocalTemp(target.table(), localTempTables)
                    || !isKnownPhysical(target.table(), knownPhysicalTables)) {
                continue;
            }
            for (ProjectionTraceResolver.SourceResolution sourceResolution
                    : projectionTraces.resolveSources(event, aliases, ignoredRowsets)) {
                List<Endpoint> sources = sourceResolution.sources().stream()
                        .filter(source -> source.column() != null)
                        .filter(source -> !isLocalTemp(source.column().table(), localTempTables))
                        .filter(source -> !isIgnoredRowsetTable(source.column().table(), ignoredRowsets))
                        .filter(source -> isKnownPhysical(source.column().table(), knownPhysicalTables))
                        .distinct()
                        .toList();
                if (sources.isEmpty()) {
                    continue;
                }
                LineageTransformType transform = ProjectionTraceResolver.effectiveTransform(
                        event.expression().transformType().name(),
                        sourceResolution.transforms(), sourceResolution.flowKind());
                LineageFlowKind flowKind = sourceResolution.flowKind();
                AssignmentMapping mapping = assignmentMapping(event, target, sources, transform);
                DataLineageCandidate candidate = new DataLineageCandidate(
                        mapping.expressionSources().sources(),
                        mapping.target(),
                        flowKind,
                        transform);
                BigDecimal score = score(transform, flowKind);
                candidate.confidence(score);
                Map<String, Object> attributes = new LinkedHashMap<>();
                attributes.put("mappingKind", event.mappingKind());
                EvidenceProvenanceMapper.copy(statement, event, attributes);
                candidate.attributes().putAll(attributes);
                candidate.evidence().add(new DataLineageEvidence(
                        transform,
                        score,
                        sourceType(statement.sourceType()),
                        SourceNameNormalizer.normalize(statement.sourceName()),
                        "typed SQL write mapping",
                        attributes));
                candidates.add(candidate);
            }
        }
        return candidates;
    }

    private List<List<StructuredSqlEvent>> scopedEventGroups(List<StructuredSqlEvent> events) {
        Map<String, List<StructuredSqlEvent>> scoped = new LinkedHashMap<>();
        List<StructuredSqlEvent> ambient = new ArrayList<>();
        for (StructuredSqlEvent event : events) {
            String scope = event.statementScope();
            if (scope.isBlank()) {
                ambient.add(event);
            } else {
                scoped.computeIfAbsent(scope, ignored -> new ArrayList<>()).add(event);
            }
        }
        if (scoped.isEmpty()) {
            return List.of(events);
        }
        List<List<StructuredSqlEvent>> groups = new ArrayList<>();
        for (List<StructuredSqlEvent> scopeEvents : scoped.values()) {
            List<StructuredSqlEvent> group = new ArrayList<>(ambient.size() + scopeEvents.size());
            group.addAll(ambient);
            group.addAll(scopeEvents);
            groups.add(group);
        }
        return groups;
    }

    private AssignmentMapping assignmentMapping(
            StructuredSqlEvent event,
            ColumnRef target,
            List<Endpoint> sources,
            LineageTransformType transform
    ) {
        return new AssignmentMapping(
                Endpoint.column(target),
                new ExpressionSourceSet(sources, transform.name()),
                event.mappingKind());
    }

    private AliasSymbolTable aliases(List<StructuredSqlEvent> events) {
        // Preserve the identifier exactly as SQL wrote it. The scan namespace is
        // applied only when comparing this endpoint with cross-source facts.
        AliasSymbolTable aliases = new AliasSymbolTable(identifiers, NamespaceContext.empty());
        for (StructuredSqlEvent event : events) {
            if (event.type() == StructuredParseEventType.ROWSET_REFERENCE || event.type() == StructuredParseEventType.WRITE_TARGET) {
                String table = event.qualifiedTable();
                if (table.isBlank()) {
                    table = event.table();
                }
                if (table.isBlank()) {
                    continue;
                }
                TableId tableId = tableId(table);
                aliases.bind(event.qualifiedTable(), tableId);
                aliases.bind(event.table(), tableId);
                String alias = event.alias();
                if (!alias.isBlank()) {
                    aliases.bind(alias, tableId);
                }
            }
        }
        return aliases;
    }

    private ColumnRef targetColumn(StructuredSqlEvent event, AliasSymbolTable aliases) {
        String targetColumn = event.targetColumn();
        if (targetColumn.isBlank()) {
            return null;
        }
        TableId table = aliases.resolve(event.targetAlias()).orElse(null);
        if (table != null) {
            return column(table, targetColumn);
        }
        String targetTable = event.targetTable();
        if (!targetTable.isBlank()) {
            return column(tableId(targetTable), targetColumn);
        }
        return table == null ? null : column(table, targetColumn);
    }

    private Set<String> ignoredRowsets(List<StructuredSqlEvent> events) {
        Set<String> result = new java.util.LinkedHashSet<>();
        for (StructuredSqlEvent event : events) {
            if (event.type() == StructuredParseEventType.TRIGGER_PSEUDO_ROWSET) {
                addIgnored(result, event.name());
                continue;
            }
            if (event.type() != StructuredParseEventType.IGNORED_ROWSET
                    && event.type() != StructuredParseEventType.CTE_DECLARATION
                    && event.type() != StructuredParseEventType.LOCAL_TEMP_TABLE_DECLARATION) {
                continue;
            }
            addIgnored(result, event.name());
            addIgnored(result, event.table());
            addIgnored(result, event.qualifiedTable());
        }
        return result;
    }

    private void addIgnored(Set<String> ignored, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        ignored.add(normalize(raw));
    }

    private Set<String> localTempTables(SqlStatementRecord statement, List<StructuredSqlEvent> events) {
        Set<String> result = new java.util.LinkedHashSet<>();
        for (String table : stringList(statement.attributes().get("localTempTables"))) {
            addIgnored(result, table);
        }
        for (StructuredSqlEvent event : events) {
            if (event.type() != StructuredParseEventType.LOCAL_TEMP_TABLE_DECLARATION) {
                continue;
            }
            String table = event.qualifiedTable();
            if (table.isBlank()) {
                table = event.table();
            }
            if (!table.isBlank()) {
                result.add(normalize(table));
            }
        }
        return result;
    }

    private boolean isLocalTemp(TableId table, Set<String> localTempTables) {
        if (table.schema() != null && !table.schema().isBlank()) {
            return localTempTables.contains(normalize(table.schema() + "." + table.tableName()));
        }
        return localTempTables.contains(normalize(table.tableName()));
    }

    private boolean isIgnoredRowsetTable(TableId table, Set<String> ignoredRowsets) {
        if (table.schema() != null && !table.schema().isBlank()) {
            return ignoredRowsets.contains(normalize(table.schema() + "." + table.tableName()));
        }
        return ignoredRowsets.contains(normalize(table.tableName()));
    }

    private boolean isKnownPhysical(TableId table, Set<TableId> knownPhysicalTables) {
        if (knownPhysicalTables == null || knownPhysicalTables.isEmpty()) {
            return true;
        }
        for (TableId known : knownPhysicalTables) {
            if (sameTable(table, known)) {
                return true;
            }
        }
        return false;
    }

    private boolean sameTable(TableId left, TableId right) {
        return canonicalTableKey(left).equals(canonicalTableKey(right));
    }

    private String canonicalTableKey(TableId table) {
        return identifiers.tableKey(table, namespace);
    }

    private List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private LineageFlowKind flowKind(String value) {
        try {
            return LineageFlowKind.valueOf(value);
        } catch (RuntimeException ignored) {
            return LineageFlowKind.VALUE;
        }
    }

    private BigDecimal score(LineageTransformType transform, LineageFlowKind flowKind) {
        if (flowKind == LineageFlowKind.CONTROL) {
            return BigDecimal.valueOf(0.55d);
        }
        return switch (transform) {
            case DIRECT -> BigDecimal.valueOf(0.90d);
            case AGGREGATE, CUMULATIVE -> BigDecimal.valueOf(0.80d);
            case COALESCE, ARITHMETIC -> BigDecimal.valueOf(0.75d);
            case CONCAT_FORMAT -> BigDecimal.valueOf(0.70d);
            case CASE_WHEN, FUNCTION_CALL -> BigDecimal.valueOf(0.65d);
            case WINDOW_DERIVED -> BigDecimal.valueOf(0.50d);
            case UNKNOWN_EXPRESSION -> BigDecimal.valueOf(0.35d);
        };
    }

    private EvidenceSourceType sourceType(StatementSourceType sourceType) {
        return switch (sourceType) {
            case NATIVE_LOG -> EvidenceSourceType.NATIVE_LOG;
            case PROCEDURE, FUNCTION, VIEW, MATERIALIZED_VIEW, TRIGGER, EVENT, RULE, PACKAGE, PACKAGE_BODY ->
                    EvidenceSourceType.DATABASE_OBJECT;
            default -> EvidenceSourceType.PLAIN_SQL;
        };
    }

    private TableId tableId(String qualified) {
        return identifiers.resolveQualified(qualified, NamespaceContext.empty());
    }

    private ColumnRef column(TableId table, String name) {
        String clean = clean(name);
        return new ColumnRef(table, clean, identifiers.normalize(clean), null, true);
    }

    private String clean(String value) {
        String result = value == null ? "" : value.trim();
        if ((result.startsWith("`") && result.endsWith("`")) || (result.startsWith("\"") && result.endsWith("\""))) {
            return result.substring(1, result.length() - 1);
        }
        return result;
    }

    private String normalize(String value) {
        return identifiers.normalize(clean(value));
    }

    private static IdentifierRules defaultIdentifierRules() {
        return value -> value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

}
