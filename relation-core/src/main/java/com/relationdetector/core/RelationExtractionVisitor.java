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
 *   -> SqlRelationParserRunner
 * }</pre>
 *
 * <p>This class consumes ANTLR structured events directly. It may reuse shared
 * semantic helpers such as {@link SqlLineageResolver}; the ANTLR path is the
 * only SQL parser correctness baseline.
 */
public class RelationExtractionVisitor {
    private static final Pattern CTE_NAME = Pattern.compile(
            "(?is)(?:\\bwith\\s+(?:recursive\\s+)?|,\\s*)([`\"\\w]+)(?:\\s*\\([^)]*\\))?\\s+as\\s*"
                    + "(?:(?:not\\s+)?materialized\\s+)?\\(");
    private static final Pattern TEMP_TABLE_NAME = Pattern.compile(
            "(?is)\\bcreate\\s+(?:temporary|temp)\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?([`\"\\w.]+)");
    private static final Pattern TRIGGER_TARGET_TABLE = Pattern.compile(
            "(?is)\\bcreate\\s+(?:or\\s+replace\\s+)?trigger\\b.*?\\bon\\s+([`\"\\w.]+)");
    private static final Pattern EXISTS_START = Pattern.compile("(?is)\\bexists\\s*\\(");
    private static final Pattern COMMON_JOIN_USING = Pattern.compile(
            "(?is)\\bjoin\\s+([`\"\\w.]+)(?:\\s+(?:as\\s+)?([`\"\\w]+))?\\s+using\\s*\\(([^)]*)\\)");
    private static final Pattern COMMON_ROWSET_TEXT_REFERENCE = Pattern.compile(
            "(?is)\\b(?:from|join)\\s+([`\"\\w.]+)"
                    + "(?=\\s*(?:,|\\b(?:join|left|right|inner|outer|full|cross|natural|where|on|group|order|having|limit|union|set|using)\\b|$))");
    private static final Pattern COMMON_ROWSET_TEXT_REFERENCE_WITH_ALIAS = Pattern.compile(
            "(?is)\\b(?:from|join)\\s+([`\"\\w.]+)(?:\\s+(?:as\\s+)?([`\"\\w]+))?"
                    + "(?=\\s*(?:,|\\b(?:join|left|right|inner|outer|full|cross|natural|where|on|group|order|having|limit|union|set|using)\\b|$))");
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
        String dialect = result.dialect();
        Set<String> ignoredRowsets = ignoredRowsets(sql);
        Set<String> systemSchemas = SqlLogNoiseFilter.systemSchemasFromStatementOrDialect(statement, dialect);
        SqlLineageResolver lineage = SqlLineageResolver.analyze(sql, ignoredRowsets,
                allowSingleTableUnqualifiedProjectionLineage(sql, dialect));
        Map<String, TableId> aliases = aliasesFromEvents(statement.sql(), result.events(), ignoredRowsets, systemSchemas);
        List<SqlSpan> existsSpans = existsSpansFor(statement.sourceType(), sql);

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

            boolean leftLooksSource = looksLikeForeignKey(left, right, dialect);
            boolean rightLooksSource = looksLikeForeignKey(right, left, dialect);
            String joinKind = textOrDefault(event, "joinKind", "WHERE_OR_UNKNOWN");
            boolean lineageResolved = lineage.hasLineage(leftAlias, leftColumnName)
                    || lineage.hasLineage(rightAlias, rightColumnName);
            boolean updateSetAssignment = booleanAttribute(event, "updateSetAssignment");
            String predicate = leftAlias + "." + leftColumnName + " = " + rightAlias + "." + rightColumnName;

            if (!leftLooksSource && !rightLooksSource) {
                if (updateSetAssignment && samePhysicalTable(left, right)) {
                    continue;
                }
                candidates.add(ambiguousEqualityCandidate(statement, left, right,
                        "ANTLR ambiguous equality: " + predicate,
                        Map.of("joinKind", joinKind, "lineageResolved", lineageResolved),
                        dialect));
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
        candidates.addAll(extractExistsSubqueries(statement, sql, aliases, lineage, existsSpans, dialect));
        candidates.addAll(extractTupleInSubqueries(statement, sql, aliases, lineage));
        candidates.addAll(extractScalarInSubqueries(statement, sql, aliases, lineage));
        addRawEqualityCandidates(statement, sql, aliases, lineage, existsSpans, candidates, dialect);
        addUsingJoinCoOccurrences(statement, sql, result.events(), ignoredRowsets, systemSchemas, candidates);
        addTableCoOccurrenceBaseline(statement, sql, result.events(), result.dialect(), ignoredRowsets, systemSchemas, candidates);
        removeJoinCandidatesCoveredByExists(candidates);
        return candidates;
    }

    /**
     * Dialect hook for one intentionally narrow lineage enhancement.
     *
     * <p>By default a projection such as {@code SELECT user_id FROM orders} is
     * not treated as precise lineage, even when the SELECT has one table. That
     * keeps the shared visitor conservative across dialects. MySQL overrides
     * this for UPDATE statements that join to a derived aggregate/subquery, for
     * example {@code UPDATE users u LEFT JOIN (SELECT user_id FROM orders) s
     * ON u.id = s.user_id ...}.
     */
    protected boolean allowSingleTableUnqualifiedProjectionLineage(String sql, String dialect) {
        return false;
    }

    /**
     * Dialect hook for column-level weak co-occurrence.
     *
     * <p>The enum is public, but emitting it changes golden output for many
     * existing fixtures. Dialects opt in after they have coverage proving that
     * column-level weak evidence is preferred over older table-level fallback.
     */
    protected boolean emitColumnCoOccurrenceForAmbiguousEquality(String dialect) {
        return false;
    }

    /**
     * Dialect hook for common {@code *_id = table.id} joins where the source
     * column does not repeat the target table name.
     */
    protected boolean allowGenericIdTargetFk(String dialect) {
        return false;
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
        addTriggerRowAliases(sql, aliases);
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
        Matcher matcher = rowsetTextReferenceWithAliasPattern().matcher(sql);
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

    /**
     * Maps trigger pseudo row aliases back to the physical trigger table.
     *
     * <p>Complete SQL example:
     *
     * <pre>{@code
     * CREATE TRIGGER orders_audit_after_insert
     * AFTER INSERT ON orders
     * FOR EACH ROW
     * BEGIN
     *   SELECT u.email FROM users u WHERE u.id = NEW.user_id;
     * END
     * }</pre>
     *
     * {@code NEW.user_id} is a column on {@code orders}, not an alias for a
     * physical table named NEW. The mapping is local to this trigger statement
     * and lets the normal equality logic emit
     * {@code orders.user_id -> users.id} with {@code TRIGGER_REFERENCE}
     * evidence.
     */
    private void addTriggerRowAliases(String sql, Map<String, TableId> aliases) {
        Matcher matcher = TRIGGER_TARGET_TABLE.matcher(sql);
        if (!matcher.find()) {
            return;
        }
        String qualifiedTable = matcher.group(1);
        TableId table = TableId.of(schemaName(qualifiedTable), cleanTable(qualifiedTable));
        aliases.putIfAbsent("new", table);
        aliases.putIfAbsent("old", table);
        aliases.putIfAbsent("NEW", table);
        aliases.putIfAbsent("OLD", table);
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
     * Emits column-level weak co-occurrence when an equality predicate gives us
     * exact physical columns but not enough direction evidence for FK-like.
     *
     * <p>Complete SQL example:
     *
     * <pre>{@code
     * UPDATE warehouse_inventory wi
     * JOIN order_items oi ON wi.product_id = oi.product_id
     * }</pre>
     *
     * The predicate is stronger than table-only co-occurrence because it names
     * both columns, but it remains weaker than {@code SQL_LOG_JOIN}: neither
     * side is a clear referenced key, so the parser must not invent FK
     * direction.
     */
    private RelationshipCandidate columnCoOccurrence(
            SqlStatementRecord record,
            ColumnRef source,
            ColumnRef target,
            String detail,
            Map<String, Object> attributes
    ) {
        RelationshipCandidate candidate = new RelationshipCandidate(
                Endpoint.column(source), Endpoint.column(target),
                RelationType.CO_OCCURRENCE, RelationSubType.COLUMN_CO_OCCURRENCE);
        candidate.evidence().add(new Evidence(EvidenceType.SQL_LOG_COLUMN_CO_OCCURRENCE,
                BigDecimal.valueOf(DefaultEvidenceScores.SQL_LOG_COLUMN_CO_OCCURRENCE),
                sourceType(record.sourceType()), record.sourceName(), detail, attributes));
        return candidate;
    }

    private RelationshipCandidate ambiguousEqualityCandidate(
            SqlStatementRecord record,
            ColumnRef left,
            ColumnRef right,
            String detail,
            Map<String, Object> attributes,
            String dialect
    ) {
        if (emitColumnCoOccurrenceForAmbiguousEquality(dialect)) {
            return columnCoOccurrence(record, left, right, detail, attributes);
        }
        return coOccurrence(record, Endpoint.table(left.table()), Endpoint.table(right.table()), detail, attributes);
    }

    /**
     * Emits conservative table-level co-occurrence for statements where table
     * participation itself is the available relationship signal.
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
     * {@code users.account_id -> accounts.id}. Once those stronger predicates
     * exist, this method deliberately does not add a generic
     * {@code orders -> accounts} table-level edge: that pair is only connected
     * through the already-extracted path, not by a direct predicate.
     *
     * <p>Generic breadth is only used when no stronger relationship has already
     * been extracted from the statement. Ordinary explicit JOIN, CTE,
     * derived-table, comma rowset SELECT, and multi-table DML queries should
     * not invent every remaining table pair once column-level evidence exists.
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
        if (!candidates.isEmpty()) {
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
        Matcher matcher = joinUsingPattern().matcher(statement.sql());
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
        Matcher matcher = rowsetTextReferenceWithAliasPattern().matcher(sql);
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
        if (tableName.isBlank() || isKeyword(tableName) || containsIgnoreCase(ignoredRowsets, tableName)) {
            return false;
        }
        if (isDialectRowsetModifier(tableName)) {
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

    private boolean samePhysicalTable(ColumnRef left, ColumnRef right) {
        return left.table().normalizedName().equals(right.table().normalizedName());
    }

    private void addRawEqualityCandidates(
            SqlStatementRecord statement,
            String sql,
            Map<String, TableId> aliases,
            SqlLineageResolver lineage,
            List<SqlSpan> existsSpans,
            List<RelationshipCandidate> candidates,
            String dialect
    ) {
        Set<String> existing = candidates.stream()
                .map(this::fingerprint)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Matcher matcher = RAW_EQUALITY.matcher(sql);
        while (matcher.find()) {
            if (insideAnySpan(matcher.start(), existsSpans)) {
                continue;
            }
            boolean updateSetAssignment = insideUpdateSetAssignment(sql, matcher.start());
            String leftAlias = clean(matcher.group(1));
            String leftColumnName = clean(matcher.group(2));
            String rightAlias = clean(matcher.group(3));
            String rightColumnName = clean(matcher.group(4));
            ColumnRef left = resolveColumn(leftAlias, leftColumnName, aliases, lineage);
            ColumnRef right = resolveColumn(rightAlias, rightColumnName, aliases, lineage);
            if (unusableColumnPair(left, right)) {
                continue;
            }
            boolean leftLooksSource = looksLikeForeignKey(left, right, dialect);
            boolean rightLooksSource = looksLikeForeignKey(right, left, dialect);
            RelationshipCandidate candidate;
            if (!leftLooksSource && !rightLooksSource) {
                if (updateSetAssignment && samePhysicalTable(left, right)) {
                    continue;
                }
                candidate = ambiguousEqualityCandidate(statement, left, right,
                        "ANTLR raw equality co-occurrence: " + matcher.group(),
                        Map.of("joinKind", "WHERE_OR_UNKNOWN", "lineageResolved", false),
                        dialect);
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

    private void removeJoinCandidatesCoveredByExists(List<RelationshipCandidate> candidates) {
        Set<String> existsPairs = candidates.stream()
                .filter(candidate -> candidate.evidence().stream()
                        .anyMatch(evidence -> evidence.type() == EvidenceType.SQL_LOG_EXISTS))
                .map(this::endpointPair)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (existsPairs.isEmpty()) {
            return;
        }
        candidates.removeIf(candidate -> existsPairs.contains(endpointPair(candidate))
                && candidate.evidence().stream().anyMatch(evidence -> evidence.type() == EvidenceType.SQL_LOG_JOIN)
                && candidate.evidence().stream().noneMatch(evidence -> evidence.type() == EvidenceType.SQL_LOG_EXISTS));
    }

    private String endpointPair(RelationshipCandidate relation) {
        return relation.source().displayName() + "->" + relation.target().displayName();
    }

    private List<RelationshipCandidate> extractExistsSubqueries(
            SqlStatementRecord statement,
            String sql,
            Map<String, TableId> aliases,
            SqlLineageResolver lineage,
            List<SqlSpan> existsSpans,
            String dialect
    ) {
        List<RelationshipCandidate> candidates = new ArrayList<>();
        for (SqlSpan span : existsSpans) {
            Matcher matcher = RAW_EQUALITY.matcher(sql.substring(span.start(), span.end()));
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

                boolean leftLooksSource = looksLikeForeignKey(left, right, dialect);
                boolean rightLooksSource = looksLikeForeignKey(right, left, dialect);
                if (!leftLooksSource && !rightLooksSource) {
                    candidates.add(ambiguousEqualityCandidate(statement, left, right,
                            "ANTLR ambiguous EXISTS equality: " + matcher.group(),
                            Map.of("joinKind", "EXISTS", "lineageResolved", false),
                            dialect));
                    continue;
                }

                ColumnRef sourceColumn = leftLooksSource && !rightLooksSource ? left : right;
                ColumnRef targetColumn = leftLooksSource && !rightLooksSource ? right : left;
                boolean lineageResolved = lineage.hasLineage(leftAlias, leftColumnName)
                        || lineage.hasLineage(rightAlias, rightColumnName);
                RelationshipCandidate candidate = new RelationshipCandidate(
                        Endpoint.column(sourceColumn), Endpoint.column(targetColumn),
                        RelationType.FK_LIKE, RelationSubType.SUBQUERY_INFERRED_FK);
                candidate.evidence().add(new Evidence(EvidenceType.SQL_LOG_EXISTS,
                        BigDecimal.valueOf(DefaultEvidenceScores.SQL_LOG_EXISTS),
                        sourceType(statement.sourceType()),
                        statement.sourceName(),
                        "ANTLR EXISTS equality: " + matcher.group(),
                        Map.of("joinKind", "EXISTS", "lineageResolved", lineageResolved)));
                candidates.add(candidate);
            }
        }
        return candidates;
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

    private boolean looksLikeForeignKey(ColumnRef sourceColumn, ColumnRef targetColumn, String dialect) {
        if (sourceColumn.table().normalizedName().equals(targetColumn.table().normalizedName())
                && "id".equalsIgnoreCase(clean(targetColumn.columnName()))) {
            String source = clean(sourceColumn.columnName()).toLowerCase(Locale.ROOT);
            return source.endsWith("_id") && !source.equals("id");
        }
        String source = clean(sourceColumn.columnName()).toLowerCase(Locale.ROOT);
        if (allowGenericIdTargetFk(dialect)
                && "id".equalsIgnoreCase(clean(targetColumn.columnName()))
                && source.endsWith("_id")
                && !source.equals("id")) {
            return true;
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
        collectDialectIgnoredRowsets(sql, names);
        return names;
    }

    private boolean containsIgnoreCase(Set<String> values, String candidate) {
        return values.stream().anyMatch(value -> value.equalsIgnoreCase(candidate));
    }

    protected boolean isKeyword(String value) {
        String lower = clean(value).toLowerCase(Locale.ROOT);
        return lower.equals("on") || lower.equals("where") || lower.equals("join") || lower.equals("left")
                || lower.equals("right") || lower.equals("inner") || lower.equals("outer") || lower.equals("full")
                || lower.equals("cross") || lower.equals("using") || lower.equals("group") || lower.equals("order")
                || lower.equals("having") || lower.equals("limit") || lower.equals("union")
                || lower.equals("select") || lower.equals("from") || lower.equals("update") || lower.equals("into")
                || lower.equals("delete") || lower.equals("set") || lower.equals("values");
    }

    /**
     * Regex hook for {@code JOIN ... USING (...)} syntax. The core default is
     * deliberately dialect-neutral; MySQL/PostgreSQL subclasses own any
     * database-specific join spellings.
     */
    protected Pattern joinUsingPattern() {
        return COMMON_JOIN_USING;
    }

    /**
     * Regex hook for text-level rowset fallback used when structured events are
     * incomplete. The core default recognizes only standard {@code FROM/JOIN}.
     */
    protected Pattern rowsetTextReferencePattern() {
        return COMMON_ROWSET_TEXT_REFERENCE;
    }

    /**
     * Alias-aware rowset fallback counterpart to
     * {@link #rowsetTextReferencePattern()}.
     */
    protected Pattern rowsetTextReferenceWithAliasPattern() {
        return COMMON_ROWSET_TEXT_REFERENCE_WITH_ALIAS;
    }

    /**
     * Dialect hook for rowset modifiers or function-like rowsets that must not
     * become physical tables in relationship output.
     */
    protected boolean isDialectRowsetModifier(String tableName) {
        return false;
    }

    /**
     * Dialect hook for statement-local rowsets whose declaration syntax is not
     * part of the cross-dialect CTE/local-temp patterns.
     */
    protected void collectDialectIgnoredRowsets(String sql, Set<String> names) {
    }

    private List<SqlSpan> existsSpansFor(StatementSourceType sourceType, String sql) {
        if (sourceType != StatementSourceType.NATIVE_LOG && sourceType != StatementSourceType.PLAIN_SQL) {
            return List.of();
        }
        List<SqlSpan> spans = new ArrayList<>();
        Matcher matcher = EXISTS_START.matcher(sql);
        while (matcher.find()) {
            int openParen = sql.indexOf('(', matcher.start());
            int closeParen = matchingParen(sql, openParen);
            if (openParen >= 0 && closeParen > openParen) {
                spans.add(new SqlSpan(openParen + 1, closeParen));
            }
        }
        return spans;
    }

    private boolean insideAnySpan(int position, List<SqlSpan> spans) {
        for (SqlSpan span : spans) {
            if (position >= span.start() && position < span.end()) {
                return true;
            }
        }
        return false;
    }

    private boolean insideUpdateSetAssignment(String sql, int position) {
        String prefix = sql.substring(0, Math.max(0, Math.min(position, sql.length()))).toLowerCase(Locale.ROOT);
        int lastUpdate = prefix.lastIndexOf("update");
        int lastSet = prefix.lastIndexOf("set");
        int lastWhere = prefix.lastIndexOf("where");
        return lastUpdate >= 0 && lastSet > lastUpdate && lastWhere < lastSet;
    }

    private int matchingParen(String sql, int openParen) {
        if (openParen < 0 || openParen >= sql.length() || sql.charAt(openParen) != '(') {
            return -1;
        }
        int depth = 0;
        char quote = 0;
        for (int i = openParen; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                quote = c;
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private record SqlSpan(int start, int end) {
    }

    private String text(StructuredSqlEvent event, String key) {
        Object value = event.attributes().get(key);
        return value == null ? "" : clean(String.valueOf(value));
    }

    private String textOrDefault(StructuredSqlEvent event, String key, String fallback) {
        String value = text(event, key);
        return value.isBlank() ? fallback : value;
    }

    private boolean booleanAttribute(StructuredSqlEvent event, String key) {
        Object value = event.attributes().get(key);
        return value instanceof Boolean booleanValue && booleanValue;
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
