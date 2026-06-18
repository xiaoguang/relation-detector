package com.relationdetector.core;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.relationdetector.api.ColumnRef;
import com.relationdetector.api.DefaultEvidenceScores;
import com.relationdetector.api.Endpoint;
import com.relationdetector.api.Evidence;
import com.relationdetector.api.RelationshipCandidate;
import com.relationdetector.api.SqlStatementRecord;
import com.relationdetector.api.StructuredParseResult;
import com.relationdetector.api.StructuredSqlEvent;
import com.relationdetector.api.TableId;
import com.relationdetector.api.Enums.EvidenceSourceType;
import com.relationdetector.api.Enums.EvidenceType;
import com.relationdetector.api.Enums.RelationSubType;
import com.relationdetector.api.Enums.RelationType;
import com.relationdetector.api.Enums.StatementSourceType;
import com.relationdetector.api.Enums.StructuredParseEventType;

/**
 * Converts structured ANTLR parser events into relationship candidates.
 *
 * <p>Call flow:
 *
 * <pre>{@code
 * AntlrStructuredSqlParser.parseSql(...)
 *   -> StructuredSqlEventVisitor.extractEvents(...)
 *   -> RelationExtractionVisitor.extract(...)
 *   -> ShadowSqlRelationParser / SqlRelationParserRunner
 * }</pre>
 *
 * <p>This class is intentionally independent from {@link SimpleSqlRelationParser}.
 * It may reuse shared semantic helpers such as {@link SqlLineageResolver}, but
 * it must not call the legacy parser's parse method. That boundary is what lets
 * shadow mode honestly report whether ANTLR is ready to become primary.
 */
public class RelationExtractionVisitor {
    private static final Pattern CTE_NAME = Pattern.compile(
            "(?is)(?:\\bwith\\s+(?:recursive\\s+)?|,\\s*)([`\"\\w]+)(?:\\s*\\([^)]*\\))?\\s+as\\s*\\(");
    private static final Pattern TEMP_TABLE_NAME = Pattern.compile(
            "(?is)\\bcreate\\s+(?:temporary|temp)\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?([`\"\\w.]+)");
    private static final Pattern JOIN_USING = Pattern.compile(
            "(?is)\\bjoin\\s+([`\"\\w.]+)(?:\\s+(?:as\\s+)?([`\"\\w]+))?\\s+using\\s*\\(([^)]*)\\)");
    private static final Pattern ROWSET_TEXT_REFERENCE = Pattern.compile(
            "(?is)\\b(?:from|join)\\s+([`\"\\w.]+)");
    private static final Pattern ROWSET_TEXT_REFERENCE_WITH_ALIAS = Pattern.compile(
            "(?is)\\b(?:from|join)\\s+([`\"\\w.]+)(?:\\s+(?:as\\s+)?([`\"\\w]+))?");
    private static final Pattern RAW_EQUALITY = Pattern.compile(
            "([`\"\\w]+)\\.([`\"\\w]+)\\s*=\\s*([`\"\\w]+)\\.([`\"\\w]+)");
    private static final Pattern IN_SUBQUERY = Pattern.compile(
            "(?is)([`\"\\w]+)\\.([`\"\\w]+)\\s+in\\s*\\(\\s*select\\s+([`\"\\w]+)\\.([`\"\\w]+)\\s+from\\s+([`\"\\w.]+)\\s+([`\"\\w]+)");
    private static final Pattern TUPLE_IN_SUBQUERY = Pattern.compile(
            "(?is)\\(([^()]+)\\)\\s+in\\s*\\(\\s*select\\s+(.+?)\\s+from\\s+([`\"\\w.]+)\\s+([`\"\\w]+)");
    private static final Pattern COLUMN_TOKEN = Pattern.compile("\\s*([`\"\\w]+)\\.([`\"\\w]+)\\s*");

    /**
     * Main ANTLR relationship extraction entry point.
     *
     * <p>The loop is deliberately small and explicit:
     *
     * <ol>
     *   <li>read table-reference events into an alias map;</li>
     *   <li>resolve equality event aliases to physical columns, using lineage
     *       when the alias belongs to a CTE or derived table;</li>
     *   <li>choose FK-like direction only when naming evidence makes one side
     *       clearly look like the referencing column;</li>
     *   <li>otherwise preserve the signal as weak table co-occurrence.</li>
     * </ol>
     */
    public List<RelationshipCandidate> extract(SqlStatementRecord statement, StructuredParseResult result) {
        String sql = stripComments(statement.sql());
        Set<String> ignoredRowsets = ignoredRowsets(sql);
        Set<String> systemSchemas = SqlLogNoiseFilter.systemSchemasFromStatementOrDialect(statement, result.dialect());
        SqlLineageResolver lineage = SqlLineageResolver.analyze(sql, ignoredRowsets);
        Map<String, TableId> aliases = aliasesFromEvents(statement.sql(), result.events(), ignoredRowsets, systemSchemas);

        List<RelationshipCandidate> candidates = new ArrayList<>();
        for (StructuredSqlEvent event : result.events()) {
            if (event.type() != StructuredParseEventType.COLUMN_EQUALITY) {
                continue;
            }
            String leftAlias = text(event, "leftAlias");
            String leftColumnName = text(event, "leftColumn");
            String rightAlias = text(event, "rightAlias");
            String rightColumnName = text(event, "rightColumn");
            ColumnRef left = resolveColumn(leftAlias, leftColumnName, aliases, lineage);
            ColumnRef right = resolveColumn(rightAlias, rightColumnName, aliases, lineage);
            if (unusableColumnPair(left, right)) {
                continue;
            }

            boolean leftLooksSource = looksLikeForeignKey(left, right);
            boolean rightLooksSource = looksLikeForeignKey(right, left);
            String joinKind = textOrDefault(event, "joinKind", "WHERE_OR_UNKNOWN");
            boolean lineageResolved = lineage.hasLineage(leftAlias, leftColumnName)
                    || lineage.hasLineage(rightAlias, rightColumnName);
            String predicate = leftAlias + "." + leftColumnName + " = " + rightAlias + "." + rightColumnName;

            if (!leftLooksSource && !rightLooksSource) {
                candidates.add(coOccurrence(statement, Endpoint.table(left.table()), Endpoint.table(right.table()),
                        "ANTLR ambiguous equality: " + predicate,
                        Map.of("joinKind", joinKind, "lineageResolved", lineageResolved)));
                continue;
            }

            ColumnRef sourceColumn = leftLooksSource && !rightLooksSource ? left : right;
            ColumnRef targetColumn = leftLooksSource && !rightLooksSource ? right : left;
            RelationshipCandidate candidate = new RelationshipCandidate(
                    Endpoint.column(sourceColumn), Endpoint.column(targetColumn),
                    RelationType.FK_LIKE, RelationSubType.INFERRED_JOIN_FK);
            candidate.evidence().add(new Evidence(joinEvidenceType(statement.sourceType()),
                    BigDecimal.valueOf(scoreForJoinSource(statement.sourceType())),
                    sourceType(statement.sourceType()),
                    statement.sourceName(),
                    "ANTLR equality: " + predicate,
                    Map.of("joinKind", joinKind, "lineageResolved", lineageResolved)));
            candidates.add(candidate);
        }
        candidates.addAll(extractTupleInSubqueries(statement, sql, aliases, lineage));
        candidates.addAll(extractScalarInSubqueries(statement, sql, aliases, lineage));
        addRawEqualityCandidates(statement, sql, aliases, lineage, candidates);
        addUsingJoinCoOccurrences(statement, sql, result.events(), ignoredRowsets, systemSchemas, candidates);
        addTableCoOccurrenceBaseline(statement, sql, result.events(), result.dialect(), ignoredRowsets, systemSchemas, candidates);
        return candidates;
    }

    /**
     * Builds a physical alias map from ANTLR table-reference events.
     *
     * <p>Complete SQL shape represented by the events:
     *
     * <pre>{@code
     * SELECT *
     * FROM public.orders o
     * JOIN users u ON o.user_id = u.id
     * }</pre>
     *
     * The map stores both {@code orders -> public.orders} and
     * {@code o -> public.orders}. CTE/local-temp names are skipped here because
     * they are rowsets inside the current statement body, not physical tables.
     */
    private Map<String, TableId> aliasesFromEvents(
            String sql,
            List<StructuredSqlEvent> events,
            Set<String> ignoredRowsets,
            Set<String> systemSchemas
    ) {
        Map<String, TableId> aliases = new LinkedHashMap<>();
        for (StructuredSqlEvent event : events) {
            if (event.type() != StructuredParseEventType.TABLE_REFERENCE) {
                continue;
            }
            String qualifiedTable = text(event, "qualifiedTable");
            String tableName = cleanTable(qualifiedTable);
            if (!usablePhysicalTable(sql, qualifiedTable, tableName, ignoredRowsets, systemSchemas)) {
                continue;
            }
            TableId table = TableId.of(schemaName(qualifiedTable), tableName);
            aliases.put(table.tableName(), table);
            String alias = clean(text(event, "alias"));
            if (!alias.isBlank()) {
                aliases.put(alias, table);
            }
        }
        Matcher matcher = ROWSET_TEXT_REFERENCE_WITH_ALIAS.matcher(sql);
        while (matcher.find()) {
            String qualifiedTable = matcher.group(1);
            String tableName = cleanTable(qualifiedTable);
            if (isDeleteTargetAliasReference(sql, matcher.start(), matcher.end(1), tableName)) {
                continue;
            }
            if (!usablePhysicalTable(sql, qualifiedTable, tableName, ignoredRowsets, systemSchemas)) {
                continue;
            }
            TableId table = TableId.of(schemaName(qualifiedTable), tableName);
            aliases.putIfAbsent(table.tableName(), table);
            String alias = clean(matcher.group(2));
            if (!alias.isBlank() && !isKeyword(alias)) {
                aliases.putIfAbsent(alias, table);
            }
        }
        return aliases;
    }

    private ColumnRef resolveColumn(
            String alias,
            String columnName,
            Map<String, TableId> aliases,
            SqlLineageResolver lineage
    ) {
        return lineage.resolve(alias, columnName)
                .orElseGet(() -> {
                    TableId table = aliases.get(clean(alias));
                    return table == null ? null : ColumnRef.of(table, clean(columnName));
                });
    }

    private RelationshipCandidate coOccurrence(
            SqlStatementRecord record,
            Endpoint source,
            Endpoint target,
            String detail,
            Map<String, Object> attributes
    ) {
        RelationshipCandidate candidate = new RelationshipCandidate(
                source, target, RelationType.CO_OCCURRENCE, RelationSubType.TABLE_CO_OCCURRENCE);
        candidate.evidence().add(new Evidence(EvidenceType.SQL_LOG_TABLE_CO_OCCURRENCE,
                BigDecimal.valueOf(DefaultEvidenceScores.SQL_LOG_TABLE_CO_OCCURRENCE),
                sourceType(record.sourceType()), record.sourceName(), detail, attributes));
        return candidate;
    }

    /**
     * Preserves SimpleSqlRelationParser's weak table-level baseline.
     *
     * <p>Complete SQL example:
     *
     * <pre>{@code
     * UPDATE orders o, users u
     * JOIN accounts a ON u.account_id = a.id
     * SET o.reviewed_at = CURRENT_TIMESTAMP
     * WHERE o.user_id = u.id
     * }</pre>
     *
     * The equality events produce column-level FK-like candidates for
     * {@code orders.user_id -> users.id} and
     * {@code users.account_id -> accounts.id}. Simple also keeps a weak
     * {@code orders -> accounts} co-occurrence signal because those physical
     * tables jointly appear in one statement even though their direct predicate
     * is not visible. This method rebuilds that conservative baseline from
     * ANTLR {@code TABLE_REFERENCE} events so {@code missingSimpleRelations}
     * can stay at zero while ANTLR matures.
     *
     * <p>The baseline is only used when no stronger relationship has already
     * been extracted from the statement, except for MySQL where the legacy
     * multi-table {@code UPDATE t1, t2 JOIN t3 ...} baseline is already part of
     * the accepted fixture contract. Once equality or {@code JOIN USING}
     * evidence exists in PostgreSQL, adding every remaining table pair becomes
     * noisy in {@code antlr-primary}; for example a three-table query with two
     * explicit joins should not invent extra table-level links between unrelated
     * tables.
     *
     * <p>CTEs, local temp tables, derived aliases, and function rowsets are
     * filtered before this method sees them by {@link #physicalTablesFromEvents};
     * therefore the weak evidence is never emitted for rowsets like
     * {@code recent_orders}, {@code projected_user}, or {@code unnest(...)}.
     */
    private void addTableCoOccurrenceBaseline(
            SqlStatementRecord statement,
            String sql,
            List<StructuredSqlEvent> events,
            String dialect,
            Set<String> ignoredRowsets,
            Set<String> systemSchemas,
            List<RelationshipCandidate> candidates
    ) {
        if (!candidates.isEmpty() && !"MYSQL".equalsIgnoreCase(dialect)) {
            return;
        }
        List<TableId> physicalTables = physicalTablesFromEvents(sql, events, ignoredRowsets, systemSchemas);
        Set<String> existing = candidates.stream()
                .map(relation -> relation.source().table().normalizedName() + "->" + relation.target().table().normalizedName())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        for (int i = 0; i < physicalTables.size(); i++) {
            for (int j = i + 1; j < physicalTables.size(); j++) {
                TableId source = physicalTables.get(i);
                TableId target = physicalTables.get(j);
                if (source.normalizedName().equals(target.normalizedName())) {
                    continue;
                }
                String key = source.normalizedName() + "->" + target.normalizedName();
                if (existing.contains(key)) {
                    continue;
                }
                candidates.add(coOccurrence(statement, Endpoint.table(source), Endpoint.table(target),
                        "ANTLR table co-occurrence from structured table references",
                        Map.of("parser", "ANTLR", "source", "TABLE_REFERENCE")));
                existing.add(key);
            }
        }
    }

    /**
     * Preserves the weak signal from {@code JOIN ... USING (...)}.
     *
     * <p>Complete SQL example:
     *
     * <pre>{@code
     * SELECT *
     * FROM orders o
     * JOIN order_tags ot USING (order_id)
     * }</pre>
     *
     * {@code order_id} is a column list, not a table reference, and it does not
     * tell us direction. We therefore emit only table-level co-occurrence with
     * explicit attributes that explain why the two tables were connected.
     */
    private void addUsingJoinCoOccurrences(
            SqlStatementRecord statement,
            String sql,
            List<StructuredSqlEvent> events,
            Set<String> ignoredRowsets,
            Set<String> systemSchemas,
            List<RelationshipCandidate> candidates
    ) {
        List<TableId> physicalTables = physicalTablesFromEvents(sql, events, ignoredRowsets, systemSchemas);
        if (physicalTables.size() < 2) {
            return;
        }
        Set<String> existing = candidates.stream()
                .map(relation -> relation.source().table().normalizedName() + "->" + relation.target().table().normalizedName())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Matcher matcher = JOIN_USING.matcher(statement.sql());
        while (matcher.find()) {
            String joinedTableName = cleanTable(matcher.group(1));
            TableId target = physicalTables.stream()
                    .filter(table -> table.tableName().equalsIgnoreCase(joinedTableName))
                    .findFirst()
                    .orElse(null);
            int targetIndex = target == null ? -1 : physicalTables.indexOf(target);
            if (targetIndex <= 0) {
                continue;
            }
            TableId source = physicalTables.get(targetIndex - 1);
            String key = source.normalizedName() + "->" + target.normalizedName();
            if (existing.contains(key)) {
                continue;
            }
            candidates.add(coOccurrence(statement, Endpoint.table(source), Endpoint.table(target),
                    "ANTLR table co-occurrence from JOIN USING columns",
                    Map.of("parser", "ANTLR",
                            "source", "JOIN_USING",
                            "joinKind", "USING_JOIN",
                            "usingColumns", usingColumns(matcher.group(3)))));
            existing.add(key);
        }
    }

    /**
     * Returns distinct physical tables in statement order.
     *
     * <p>{@code TABLE_REFERENCE} events may include both real tables and rowsets
     * that are only visible inside the current SQL body. We skip ignored names
     * discovered from CTE/local-temp declarations here, and the tokenizer-level
     * visitor avoids adding aliases for parenthesized derived tables/functions.
     */
    private List<TableId> physicalTablesFromEvents(
            String sql,
            List<StructuredSqlEvent> events,
            Set<String> ignoredRowsets,
            Set<String> systemSchemas
    ) {
        List<TableId> tables = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (StructuredSqlEvent event : events) {
            if (event.type() != StructuredParseEventType.TABLE_REFERENCE) {
                continue;
            }
            String qualifiedTable = text(event, "qualifiedTable");
            String tableName = cleanTable(qualifiedTable);
            if (!usablePhysicalTable(sql, qualifiedTable, tableName, ignoredRowsets, systemSchemas)) {
                continue;
            }
            TableId table = TableId.of(schemaName(qualifiedTable), tableName);
            if (seen.add(table.normalizedName())) {
                tables.add(table);
            }
        }
        Matcher matcher = ROWSET_TEXT_REFERENCE.matcher(sql);
        while (matcher.find()) {
            String qualifiedTable = matcher.group(1);
            String tableName = cleanTable(qualifiedTable);
            if (isDeleteTargetAliasReference(sql, matcher.start(), matcher.end(1), tableName)) {
                continue;
            }
            if (!usablePhysicalTable(sql, qualifiedTable, tableName, ignoredRowsets, systemSchemas)) {
                continue;
            }
            TableId table = TableId.of(schemaName(qualifiedTable), tableName);
            if (seen.add(table.normalizedName())) {
                tables.add(table);
            }
        }
        return tables;
    }

    private boolean isDeleteTargetAliasReference(String sql, int rowsetKeywordStart, int tableEnd, String tableName) {
        if (tableName.isBlank()) {
            return false;
        }
        String before = sql.substring(0, rowsetKeywordStart).stripTrailing().toLowerCase(Locale.ROOT);
        if (!before.endsWith("delete")) {
            return false;
        }
        String afterTarget = sql.substring(tableEnd);
        if (!Pattern.compile("(?is)^\\s+using\\b").matcher(afterTarget).find()) {
            return false;
        }
        String alias = Pattern.quote(tableName);
        return Pattern.compile("(?is)\\b(?:using|join)\\s+[`\"\\w.]+\\s+(?:as\\s+)?" + alias + "\\b")
                .matcher(afterTarget)
                .find();
    }

    private boolean usablePhysicalTable(
            String sql,
            String qualifiedTable,
            String tableName,
            Set<String> ignoredRowsets,
            Set<String> systemSchemas
    ) {
        if (tableName.isBlank() || containsIgnoreCase(ignoredRowsets, tableName)) {
            return false;
        }
        if (tableName.equalsIgnoreCase("lateral")
                || tableName.equalsIgnoreCase("unnest")
                || tableName.equalsIgnoreCase("json_table")) {
            return false;
        }
        if (SqlLogNoiseFilter.isSystemRowset(qualifiedTable, systemSchemas)) {
            return false;
        }
        if (!SqlLogNoiseFilter.isValidIdentifierToken(tableName) || SqlLogNoiseFilter.isTruncatedToken(sql, tableName)) {
            return false;
        }
        String schema = schemaName(qualifiedTable);
        return schema == null || SqlLogNoiseFilter.isValidIdentifierToken(schema);
    }

    private List<String> usingColumns(String columns) {
        return java.util.Arrays.stream(columns.split(","))
                .map(RelationExtractionVisitor::clean)
                .filter(column -> !column.isBlank())
                .toList();
    }

    private boolean unusableColumnPair(ColumnRef left, ColumnRef right) {
        if (left == null || right == null) {
            return true;
        }
        return left.table().normalizedName().equals(right.table().normalizedName())
                && left.normalizedName().equals(right.normalizedName());
    }

    private void addRawEqualityCandidates(
            SqlStatementRecord statement,
            String sql,
            Map<String, TableId> aliases,
            SqlLineageResolver lineage,
            List<RelationshipCandidate> candidates
    ) {
        Set<String> existing = candidates.stream()
                .map(this::fingerprint)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Matcher matcher = RAW_EQUALITY.matcher(sql);
        while (matcher.find()) {
            String leftAlias = clean(matcher.group(1));
            String leftColumnName = clean(matcher.group(2));
            String rightAlias = clean(matcher.group(3));
            String rightColumnName = clean(matcher.group(4));
            ColumnRef left = resolveColumn(leftAlias, leftColumnName, aliases, lineage);
            ColumnRef right = resolveColumn(rightAlias, rightColumnName, aliases, lineage);
            if (unusableColumnPair(left, right)) {
                continue;
            }
            boolean leftLooksSource = looksLikeForeignKey(left, right);
            boolean rightLooksSource = looksLikeForeignKey(right, left);
            RelationshipCandidate candidate;
            if (!leftLooksSource && !rightLooksSource) {
                candidate = coOccurrence(statement, Endpoint.table(left.table()), Endpoint.table(right.table()),
                        "ANTLR raw equality co-occurrence: " + matcher.group(),
                        Map.of("joinKind", "WHERE_OR_UNKNOWN", "lineageResolved", false));
            } else {
                ColumnRef sourceColumn = leftLooksSource && !rightLooksSource ? left : right;
                ColumnRef targetColumn = leftLooksSource && !rightLooksSource ? right : left;
                candidate = new RelationshipCandidate(
                        Endpoint.column(sourceColumn), Endpoint.column(targetColumn),
                        RelationType.FK_LIKE, RelationSubType.INFERRED_JOIN_FK);
                candidate.evidence().add(new Evidence(joinEvidenceType(statement.sourceType()),
                        BigDecimal.valueOf(scoreForJoinSource(statement.sourceType())),
                        sourceType(statement.sourceType()),
                        statement.sourceName(),
                        "ANTLR raw equality: " + matcher.group(),
                        Map.of("joinKind", "WHERE_OR_UNKNOWN", "lineageResolved", false)));
            }
            if (existing.add(fingerprint(candidate))) {
                candidates.add(candidate);
            }
        }
    }

    private String fingerprint(RelationshipCandidate relation) {
        String evidenceTypes = relation.evidence().stream()
                .map(evidence -> evidence.type().name())
                .collect(java.util.stream.Collectors.joining(","));
        return relation.relationType() + ":"
                + relation.source().displayName() + "->" + relation.target().displayName()
                + ":" + evidenceTypes;
    }

    private List<RelationshipCandidate> extractScalarInSubqueries(
            SqlStatementRecord statement,
            String sql,
            Map<String, TableId> aliases,
            SqlLineageResolver lineage
    ) {
        List<RelationshipCandidate> candidates = new ArrayList<>();
        Matcher matcher = IN_SUBQUERY.matcher(sql);
        while (matcher.find()) {
            String innerAlias = clean(matcher.group(6));
            TableId innerTable = aliases.getOrDefault(innerAlias,
                    TableId.of(schemaName(matcher.group(5)), cleanTable(matcher.group(5))));
            ColumnRef source = resolveColumn(clean(matcher.group(1)), clean(matcher.group(2)), aliases, lineage);
            ColumnRef target = resolveColumn(innerAlias, clean(matcher.group(4)), aliases, lineage);
            if (target == null) {
                target = ColumnRef.of(innerTable, clean(matcher.group(4)));
            }
            if (unusableColumnPair(source, target)) {
                continue;
            }
            candidates.add(subqueryInCandidate(statement, source, target,
                    "ANTLR IN subquery relation: " + matcher.group()));
        }
        return candidates;
    }

    private List<RelationshipCandidate> extractTupleInSubqueries(
            SqlStatementRecord statement,
            String sql,
            Map<String, TableId> aliases,
            SqlLineageResolver lineage
    ) {
        List<RelationshipCandidate> candidates = new ArrayList<>();
        Matcher matcher = TUPLE_IN_SUBQUERY.matcher(sql);
        while (matcher.find()) {
            List<ColumnPair> sourceTokens = parseColumnList(matcher.group(1));
            List<ColumnPair> targetTokens = parseColumnList(matcher.group(2));
            if (sourceTokens.isEmpty() || sourceTokens.size() != targetTokens.size()) {
                continue;
            }
            String innerAlias = clean(matcher.group(4));
            TableId innerTable = aliases.getOrDefault(innerAlias,
                    TableId.of(schemaName(matcher.group(3)), cleanTable(matcher.group(3))));
            for (int i = 0; i < sourceTokens.size(); i++) {
                ColumnPair sourceToken = sourceTokens.get(i);
                ColumnPair targetToken = targetTokens.get(i);
                ColumnRef source = resolveColumn(sourceToken.alias(), sourceToken.column(), aliases, lineage);
                ColumnRef target = resolveColumn(targetToken.alias(), targetToken.column(), aliases, lineage);
                if (target == null) {
                    target = ColumnRef.of(innerTable, targetToken.column());
                }
                if (unusableColumnPair(source, target)) {
                    continue;
                }
                candidates.add(subqueryInCandidate(statement, source, target,
                        "ANTLR tuple IN subquery relation position " + (i + 1) + ": " + matcher.group()));
            }
        }
        return candidates;
    }

    private RelationshipCandidate subqueryInCandidate(
            SqlStatementRecord statement,
            ColumnRef source,
            ColumnRef target,
            String detail
    ) {
        RelationshipCandidate candidate = new RelationshipCandidate(
                Endpoint.column(source), Endpoint.column(target),
                RelationType.FK_LIKE, RelationSubType.SUBQUERY_INFERRED_FK);
        candidate.evidence().add(Evidence.of(EvidenceType.SQL_LOG_SUBQUERY_IN,
                DefaultEvidenceScores.SQL_LOG_SUBQUERY_IN,
                sourceType(statement.sourceType()), statement.sourceName(), detail));
        return candidate;
    }

    private List<ColumnPair> parseColumnList(String text) {
        List<ColumnPair> columns = new ArrayList<>();
        for (String part : text.split(",")) {
            Matcher matcher = COLUMN_TOKEN.matcher(part);
            if (!matcher.matches()) {
                return List.of();
            }
            columns.add(new ColumnPair(clean(matcher.group(1)), clean(matcher.group(2))));
        }
        return columns;
    }

    private record ColumnPair(String alias, String column) {
    }

    private boolean looksLikeForeignKey(ColumnRef sourceColumn, ColumnRef targetColumn) {
        if (sourceColumn.table().normalizedName().equals(targetColumn.table().normalizedName())
                && "id".equalsIgnoreCase(clean(targetColumn.columnName()))) {
            String source = clean(sourceColumn.columnName()).toLowerCase(Locale.ROOT);
            return source.endsWith("_id") && !source.equals("id");
        }
        return looksLikeForeignKey(sourceColumn.columnName(), targetColumn.table().tableName(), targetColumn.columnName());
    }

    private boolean looksLikeForeignKey(String sourceColumn, String targetTable, String targetColumn) {
        String source = clean(sourceColumn).toLowerCase(Locale.ROOT);
        String target = clean(targetTable).toLowerCase(Locale.ROOT);
        String targetSingular = target.endsWith("s") ? target.substring(0, target.length() - 1) : target;
        boolean targetLooksIdentifier = "id".equalsIgnoreCase(clean(targetColumn)) || clean(targetColumn).endsWith("_id");
        if (!targetLooksIdentifier) {
            return false;
        }
        return source.equals(targetSingular + "_id")
                || source.equals(target + "_id")
                || source.endsWith("_" + targetSingular + "_id")
                || source.endsWith("_" + target + "_id");
    }

    private EvidenceType joinEvidenceType(StatementSourceType sourceType) {
        return switch (sourceType) {
            case VIEW, MATERIALIZED_VIEW, RULE -> EvidenceType.VIEW_JOIN;
            case PROCEDURE, FUNCTION, EVENT, PACKAGE, PACKAGE_BODY -> EvidenceType.PROCEDURE_JOIN;
            case TRIGGER -> EvidenceType.TRIGGER_REFERENCE;
            default -> EvidenceType.SQL_LOG_JOIN;
        };
    }

    private double scoreForJoinSource(StatementSourceType sourceType) {
        return switch (sourceType) {
            case VIEW, MATERIALIZED_VIEW, RULE -> DefaultEvidenceScores.VIEW_JOIN;
            case PROCEDURE, FUNCTION, EVENT, PACKAGE, PACKAGE_BODY -> DefaultEvidenceScores.PROCEDURE_JOIN;
            case TRIGGER -> DefaultEvidenceScores.TRIGGER_REFERENCE;
            default -> DefaultEvidenceScores.SQL_LOG_JOIN;
        };
    }

    private EvidenceSourceType sourceType(StatementSourceType statementSourceType) {
        return switch (statementSourceType) {
            case DDL_FILE -> EvidenceSourceType.DDL_FILE;
            case PROCEDURE, FUNCTION, VIEW, MATERIALIZED_VIEW, TRIGGER, EVENT, RULE, PACKAGE, PACKAGE_BODY ->
                    EvidenceSourceType.DATABASE_OBJECT;
            case MIGRATION -> EvidenceSourceType.PLAIN_SQL;
            case NATIVE_LOG -> EvidenceSourceType.NATIVE_LOG;
            case PLAIN_SQL -> EvidenceSourceType.PLAIN_SQL;
        };
    }

    private Set<String> ignoredRowsets(String sql) {
        Set<String> names = new LinkedHashSet<>();
        Matcher cte = CTE_NAME.matcher(sql);
        while (cte.find()) {
            names.add(clean(cte.group(1)));
        }
        Matcher temp = TEMP_TABLE_NAME.matcher(sql);
        while (temp.find()) {
            names.add(cleanTable(temp.group(1)));
        }
        return names;
    }

    private boolean containsIgnoreCase(Set<String> values, String candidate) {
        return values.stream().anyMatch(value -> value.equalsIgnoreCase(candidate));
    }

    private boolean isKeyword(String value) {
        String lower = clean(value).toLowerCase(Locale.ROOT);
        return lower.equals("on") || lower.equals("where") || lower.equals("join") || lower.equals("left")
                || lower.equals("right") || lower.equals("inner") || lower.equals("outer") || lower.equals("full")
                || lower.equals("cross") || lower.equals("using") || lower.equals("group") || lower.equals("order")
                || lower.equals("having") || lower.equals("limit") || lower.equals("union");
    }

    private String text(StructuredSqlEvent event, String key) {
        Object value = event.attributes().get(key);
        return value == null ? "" : clean(String.valueOf(value));
    }

    private String textOrDefault(StructuredSqlEvent event, String key, String fallback) {
        String value = text(event, key);
        return value.isBlank() ? fallback : value;
    }

    private String stripComments(String sql) {
        return sql.replaceAll("(?m)--.*$", " ").replaceAll("(?s)/\\*.*?\\*/", " ");
    }

    private static String clean(String identifier) {
        if (identifier == null) {
            return "";
        }
        String value = identifier.trim();
        if ((value.startsWith("`") && value.endsWith("`")) || (value.startsWith("\"") && value.endsWith("\""))) {
            value = value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String cleanTable(String table) {
        List<String> parts = identifierParts(table);
        return parts.isEmpty() ? "" : parts.get(parts.size() - 1);
    }

    private static String schemaName(String table) {
        List<String> parts = identifierParts(table);
        return parts.size() > 1 ? parts.get(parts.size() - 2) : null;
    }

    private static List<String> identifierParts(String identifier) {
        List<String> parts = new ArrayList<>();
        if (identifier == null || identifier.isBlank()) {
            return parts;
        }
        StringBuilder current = new StringBuilder();
        char quote = 0;
        for (int i = 0; i < identifier.length(); i++) {
            char c = identifier.charAt(i);
            if ((c == '"' || c == '`') && quote == 0) {
                quote = c;
                current.append(c);
                continue;
            }
            if (c == quote) {
                quote = 0;
                current.append(c);
                continue;
            }
            if (c == '.' && quote == 0) {
                String part = clean(current.toString());
                if (!part.isBlank()) {
                    parts.add(part);
                }
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        String part = clean(current.toString());
        if (!part.isBlank()) {
            parts.add(part);
        }
        return parts;
    }
}
