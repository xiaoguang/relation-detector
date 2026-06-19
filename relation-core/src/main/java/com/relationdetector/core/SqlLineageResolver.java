package com.relationdetector.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.relationdetector.api.ColumnRef;
import com.relationdetector.api.TableId;

/**
 * Conservative SQL column lineage helper for ANTLR relation extraction.
 *
 * <p>Design mapping: Phase 6 parser enhancement / SqlLineageResolver. This is
 * not a full SQL AST engine. Its job is deliberately narrower: when a CTE or
 * derived table projects a plain base column, remember that mapping so later
 * predicates can be rewritten from derived aliases back to real table columns.
 *
 * <p>Supported safe examples:
 *
 * <pre>{@code
 * WITH x AS (
 *   SELECT o.id AS order_id, c.region_id
 *   FROM orders o
 *   JOIN customers c ON o.customer_id = c.id
 * )
 * SELECT *
 * FROM x
 * JOIN regions r ON x.region_id = r.id
 *
 * -- resolver records:
 * --   x.order_id  -> orders.id
 * --   x.region_id -> customers.region_id
 * }</pre>
 *
 * <pre>{@code
 * SELECT *
 * FROM (
 *   SELECT o.id AS order_id, o.customer_id
 *   FROM orders o
 * ) AS dt
 * JOIN customers c ON dt.customer_id = c.id
 *
 * -- resolver records:
 * --   dt.order_id    -> orders.id
 * --   dt.customer_id -> orders.customer_id
 * }</pre>
 *
 * <p>Unsafe examples are intentionally skipped:
 *
 * <pre>{@code
 * WITH x AS (
 *   SELECT COALESCE(a.user_id, b.user_id) AS user_id
 *   FROM account_events a
 *   JOIN backup_account_events b ON b.account_event_id = a.id
 * )
 * SELECT * FROM x JOIN users u ON x.user_id = u.id
 *
 * SELECT SUM(p.amount) AS total_amount FROM payments p
 * SELECT ROW_NUMBER() OVER (ORDER BY o.id) AS rn FROM orders o
 * }</pre>
 *
 * In those cases the output column is expression-derived. We may still parse
 * explicit joins inside the subquery, but we do not create precise column-level
 * FK-like evidence from the expression output.
 */
final class SqlLineageResolver {
    /*
     * Projection item with explicit AS alias.
     *
     * Complete SQL examples:
     *   SELECT o.id AS order_id FROM orders o
     *   SELECT "c"."region_id" AS region_id FROM "customers" "c"
     *
     * Captures:
     *   group(1): expression before AS
     *   group(2): output column name
     */
    private static final Pattern AS_ALIAS = Pattern.compile("(?is)^(.+?)\\s+as\\s+([`\"\\w]+)$");

    /*
     * A plain column reference is the only expression shape that this resolver
     * treats as precise lineage.
     *
     * Accepted complete SQL examples:
     *   SELECT o.customer_id FROM orders o
     *   SELECT `o`.`customer_id` FROM `orders` `o`
     *   SELECT "o"."customer_id" FROM "orders" "o"
     *
     * Rejected complete SQL examples:
     *   SELECT COALESCE(o.customer_id, fallback.customer_id) AS customer_id FROM orders o
     *   SELECT o.customer_id + 1 AS customer_id FROM orders o
     *   SELECT lower(o.email) AS email FROM orders o
     */
    private static final Pattern PLAIN_COLUMN = Pattern.compile("^[`\"\\w]+\\.[`\"\\w]+$");
    private static final Pattern PLAIN_UNQUALIFIED_COLUMN = Pattern.compile("^[`\"\\w]+$");

    /*
     * Finds references to already-known derived rowsets so aliases can inherit
     * the rowset's output lineage.
     *
     * Complete SQL example:
     *   WITH recent_orders AS (
     *     SELECT o.id AS order_id FROM orders o
     *   )
     *   SELECT * FROM recent_orders ro
     *
     * If recent_orders.order_id -> orders.id is known, this copies:
     *   ro.order_id -> orders.id
     */
    private static final Pattern FROM_OR_JOIN_NAME = Pattern.compile(
            "(?i)\\b(?:from|join)\\s+([`\"\\w.]+)(?:\\s+(?:as\\s+([`\"\\w]+)|"
                    + "(?!(?:join|left|right|inner|outer|full|cross|natural|where|on|group|order|having|limit|union|set|using|select)\\b)"
                    + "([`\"\\w]+)))?");

    private final Set<String> cteNames;
    private final boolean allowSingleTableUnqualifiedProjection;
    private final Map<ColumnKey, ColumnRef> columns = new LinkedHashMap<>();
    private final Map<String, Map<String, ColumnRef>> rowsets = new LinkedHashMap<>();

    private SqlLineageResolver(String sql, Set<String> cteNames, boolean allowSingleTableUnqualifiedProjection) {
        this.cteNames = new LinkedHashSet<>();
        for (String cteName : cteNames) {
            this.cteNames.add(normalize(cteName));
        }
        this.allowSingleTableUnqualifiedProjection = allowSingleTableUnqualifiedProjection;
    }

    /**
     * Entry point called by relation visitors before relationship extraction.
     *
     * <p>Call chain:
     * RelationExtractionVisitor.extract() -> analyze() -> analyzeCtes(),
     * applyRowsetReferenceAliases(), analyzeDerivedTables(),
     * applyRowsetReferenceAliases().
     *
     * <p>The order is important. CTEs first define rowsets, then aliases such
     * as {@code FROM recent_orders ro} are copied before derived/LATERAL tables
     * are analyzed. That lets a later body such as
     * {@code JOIN LATERAL (SELECT ro.user_id AS buyer_id) x} resolve
     * {@code ro.user_id} back to the CTE's base table column. A final alias pass
     * then copies newly discovered derived rowsets to their outer aliases.
     */
    static SqlLineageResolver analyze(String sql, Set<String> cteNames) {
        return analyze(sql, cteNames, false);
    }

    /**
     * Entry point used by dialect ANTLR visitors that can safely enable extra
     * lineage rules for a narrow SQL shape.
     *
     * <p>{@code allowSingleTableUnqualifiedProjection} means a derived table
     * body such as {@code SELECT user_id FROM orders} may expose
     * {@code user_id -> orders.user_id}. The default is false because broad
     * cross-dialect use can change legacy fixture baselines; MySQL enables it
     * only for UPDATE-derived-table cases where this projection shape is common
     * and unambiguous.
     */
    static SqlLineageResolver analyze(
            String sql,
            Set<String> cteNames,
            boolean allowSingleTableUnqualifiedProjection
    ) {
        SqlLineageResolver resolver = new SqlLineageResolver(sql, cteNames, allowSingleTableUnqualifiedProjection);
        resolver.analyzeCtes(sql);
        resolver.applyRowsetReferenceAliases(sql);
        resolver.analyzeDerivedTables(sql);
        resolver.applyRowsetReferenceAliases(sql);
        return resolver;
    }

    /**
     * Resolves a derived alias column to the physical base column, if known.
     *
     * <p>Called from relation visitor column resolution. Empty Optional is
     * a normal outcome and means the caller should fall back to physical table
     * aliases or skip the relation.
     */
    Optional<ColumnRef> resolve(String alias, String column) {
        return Optional.ofNullable(columns.get(new ColumnKey(normalize(alias), normalize(column))));
    }

    boolean hasLineage(String alias, String column) {
        return columns.containsKey(new ColumnKey(normalize(alias), normalize(column)));
    }

    /**
     * Reads WITH/CTE declarations and records their output column lineage.
     *
     * <p>Called by analyze() for the full SQL and recursively by
     * analyzeSelectOutput() for nested SELECT bodies. The loop advances through
     * comma-separated CTE declarations:
     *
     * <pre>{@code
     * WITH a AS (SELECT o.id FROM orders o),
     *      b AS (SELECT a.id FROM a)
     * SELECT * FROM b
     * }</pre>
     *
     * Each iteration extracts one CTE name, optional explicit output column
     * list, and parenthesized body, then delegates body parsing to
     * analyzeSelectOutput().
     */
    private void analyzeCtes(String text) {
        int with = indexOfWord(text, "with", 0);
        if (with < 0) {
            return;
        }
        int position = skipWhitespace(text, with + "with".length());
        if (startsWithWord(text, position, "recursive")) {
            position = skipWhitespace(text, position + "recursive".length());
        }

        while (position < text.length()) {
            position = skipWhitespaceAndCommas(text, position);
            Identifier cteName = readIdentifier(text, position);
            if (cteName.value().isBlank()) {
                return;
            }
            position = skipWhitespace(text, cteName.end());

            List<String> explicitColumns = List.of();
            if (position < text.length() && text.charAt(position) == '(') {
                int endColumns = findMatchingParen(text, position);
                if (endColumns < 0) {
                    return;
                }
                explicitColumns = splitIdentifierList(text.substring(position + 1, endColumns));
                position = skipWhitespace(text, endColumns + 1);
            }

            if (!startsWithWord(text, position, "as")) {
                return;
            }
            position = skipWhitespace(text, position + "as".length());
            if (startsWithWord(text, position, "not")) {
                position = skipWhitespace(text, position + "not".length());
            }
            if (startsWithWord(text, position, "materialized")) {
                position = skipWhitespace(text, position + "materialized".length());
            }
            if (position >= text.length() || text.charAt(position) != '(') {
                return;
            }
            int bodyEnd = findMatchingParen(text, position);
            if (bodyEnd < 0) {
                return;
            }
            String body = text.substring(position + 1, bodyEnd);
            Map<String, ColumnRef> output = analyzeSelectOutput(body, explicitColumns);
            addRowset(cteName.value(), output);
            position = bodyEnd + 1;
            position = skipWhitespace(text, position);
            if (position >= text.length() || text.charAt(position) != ',') {
                return;
            }
            position++;
        }
    }

    /**
     * Finds parenthesized SELECT/ WITH bodies used as derived tables.
     *
     * <p>Called by analyze() and analyzeSelectOutput(). The loop walks every
     * parenthesized block in source order. Only blocks whose trimmed body starts
     * with SELECT or WITH are treated as derived rowsets; ordinary function
     * calls and predicate parentheses are ignored.
     *
     * <pre>{@code
     * SELECT *
     * FROM (
     *   SELECT o.id AS order_id FROM orders o
     * ) AS projected_orders
     * }</pre>
     */
    private void analyzeDerivedTables(String text) {
        int position = 0;
        while (position < text.length()) {
            int open = text.indexOf('(', position);
            if (open < 0) {
                return;
            }
            int close = findMatchingParen(text, open);
            if (close < 0) {
                return;
            }
            String body = text.substring(open + 1, close).trim();
            if (startsWithQuery(body)) {
                AliasAfterParen alias = readAliasAfterParen(text, close + 1);
                if (!alias.alias().isBlank()) {
                    Map<String, TableId> outerAliases = outerAliasesBefore(text, open);
                    Map<String, ColumnRef> output = analyzeSelectOutput(body, alias.columnNames(), outerAliases);
                    addRowset(alias.alias(), output);
                }
            }
            position = close + 1;
        }
    }

    /**
     * Builds output-column lineage for one SELECT-like query body.
     *
     * <p>Called by analyzeCtes() and analyzeDerivedTables(). It first analyzes
     * nested rowsets inside the body, then identifies the top-level SELECT list
     * before the top-level FROM. Each projection is inspected independently.
     *
     * <p>Loop meaning: projection i may be renamed by an explicit CTE/derived
     * column list. For example:
     *
     * <pre>{@code
     * WITH x(order_id) AS (
     *   SELECT o.id FROM orders o
     * )
     * SELECT * FROM x
     * }</pre>
     *
     * Here projection {@code o.id} becomes output column {@code order_id}.
     */
    private Map<String, ColumnRef> analyzeSelectOutput(String query, List<String> explicitOutputColumns) {
        return analyzeSelectOutput(query, explicitOutputColumns, Map.of());
    }

    /**
     * Builds output-column lineage for one SELECT-like query body with optional
     * outer-scope aliases.
     *
     * <p>The three-argument overload is used for LATERAL/correlated derived
     * tables whose SELECT list can legally reference tables from the surrounding
     * FROM clause without declaring its own FROM:
     *
     * <pre>{@code
     * SELECT *
     * FROM orders o
     * JOIN LATERAL (
     *   SELECT o.user_id AS user_id
     * ) x ON true
     * JOIN users u ON x.user_id = u.id
     *
     * -- resolver records:
     * --   x.user_id -> orders.user_id
     * }</pre>
     *
     * For ordinary CTEs and non-correlated derived tables the caller passes an
     * empty map, so local aliases continue to be the only source of lineage.
     */
    private Map<String, ColumnRef> analyzeSelectOutput(
            String query,
            List<String> explicitOutputColumns,
            Map<String, TableId> outerAliases
    ) {
        analyzeCtes(query);
        applyRowsetReferenceAliases(query);
        analyzeDerivedTables(query);
        applyRowsetReferenceAliases(query);

        int select = findTopLevelKeyword(query, "select", 0);
        if (select < 0) {
            return Map.of();
        }
        int from = findTopLevelKeyword(query, "from", select + "select".length());
        if (from >= 0 && from <= select) {
            return Map.of();
        }

        Set<String> ignoredRowsets = new LinkedHashSet<>(cteNames);
        ignoredRowsets.addAll(rowsets.keySet());
        Map<String, TableId> aliases = new LinkedHashMap<>(outerAliases);
        Map<String, TableId> localAliases = extractPhysicalAliases(query, ignoredRowsets);
        Map<String, Map<String, ColumnRef>> localRowsetAliases = extractKnownRowsetAliases(query);
        aliases.putAll(localAliases);
        int projectionEnd = from < 0 ? query.length() : from;
        List<String> projections = splitTopLevel(query.substring(select + "select".length(), projectionEnd), ',');
        Map<String, ColumnRef> output = new LinkedHashMap<>();
        for (int i = 0; i < projections.size(); i++) {
            Projection projection = parseProjection(projections.get(i));
            String outputColumn = i < explicitOutputColumns.size()
                    ? explicitOutputColumns.get(i)
                    : projection.outputColumn();
            if (outputColumn.isBlank()) {
                continue;
            }
            directColumnSource(projection.expression(), aliases, localAliases, localRowsetAliases)
                    .ifPresent(source -> output.put(normalize(outputColumn), source));
        }
        return output;
    }

    /**
     * Reads physical table aliases that appear before a derived SELECT.
     *
     * <p>Called only by analyzeDerivedTables() for correlated/LATERAL rowsets.
     * Passing only the prefix before the derived table avoids leaking aliases
     * introduced later in the statement into the derived body.
     */
    private Map<String, TableId> outerAliasesBefore(String text, int openParen) {
        Set<String> ignoredRowsets = new LinkedHashSet<>(cteNames);
        ignoredRowsets.addAll(rowsets.keySet());
        return extractPhysicalAliases(text.substring(0, openParen), ignoredRowsets);
    }

    private Map<String, TableId> extractPhysicalAliases(String sql, Set<String> ignoredRowsets) {
        Map<String, TableId> aliases = new LinkedHashMap<>();
        Matcher matcher = FROM_OR_JOIN_NAME.matcher(sql);
        while (matcher.find()) {
            String rawTable = matcher.group(1);
            String tableName = cleanTable(rawTable);
            if (tableName.isBlank()
                    || isKeyword(tableName)
                    || ignoredRowsets.contains(normalize(tableName))
                    || !SqlLogNoiseFilter.isValidIdentifierToken(tableName)) {
                continue;
            }
            TableId table = TableId.of(schemaName(rawTable), tableName);
            aliases.put(table.tableName(), table);
            String alias = aliasFromRowsetMatcher(matcher);
            if (!alias.isBlank() && !isKeyword(alias)) {
                aliases.put(alias, table);
            }
        }
        return aliases;
    }

    /**
     * Reads CTE/derived rowset aliases visible inside one SELECT body.
     *
     * <p>This complements {@link #extractPhysicalAliases}. Physical aliases
     * answer {@code o.id -> orders.id}; rowset aliases answer
     * {@code recent_orders.id -> orders.id}. Keeping the two maps separate is
     * what lets unqualified projection handling stay conservative: a bare
     * {@code SELECT order_id FROM x} is accepted only when exactly one visible
     * rowset exposes {@code order_id}.
     */
    private Map<String, Map<String, ColumnRef>> extractKnownRowsetAliases(String sql) {
        Map<String, Map<String, ColumnRef>> aliases = new LinkedHashMap<>();
        Matcher matcher = FROM_OR_JOIN_NAME.matcher(sql);
        while (matcher.find()) {
            String rowsetName = normalize(cleanTable(matcher.group(1)));
            Map<String, ColumnRef> output = rowsets.get(rowsetName);
            if (output == null) {
                continue;
            }
            aliases.put(rowsetName, output);
            String alias = aliasFromRowsetMatcher(matcher);
            if (!alias.isBlank() && !isKeyword(alias)) {
                aliases.put(normalize(alias), output);
            }
        }
        return aliases;
    }

    /**
     * Converts a projection expression to a base column only when it is safe.
     *
     * <p>Called by analyzeSelectOutput(). The function first checks whether the
     * expression is exactly {@code alias.column}. If that alias is itself a CTE
     * or derived rowset, resolve() recursively collapses it to the original base
     * column. A bare column such as {@code user_id} is accepted only when the
     * SELECT body has exactly one physical source table, for example
     * {@code SELECT user_id FROM orders}; multi-table SELECT bodies remain
     * ambiguous and are skipped. Expressions return Optional.empty().
     */
    private Optional<ColumnRef> directColumnSource(
            String expression,
            Map<String, TableId> aliases,
            Map<String, TableId> localAliases,
            Map<String, Map<String, ColumnRef>> localRowsetAliases
    ) {
        String cleaned = expression.trim();
        if (PLAIN_UNQUALIFIED_COLUMN.matcher(cleaned).matches()) {
            Optional<ColumnRef> rowsetColumn = singleRowsetColumn(localRowsetAliases, cleaned);
            if (rowsetColumn.isPresent()) {
                return rowsetColumn;
            }
        }
        if (allowSingleTableUnqualifiedProjection && PLAIN_UNQUALIFIED_COLUMN.matcher(cleaned).matches()) {
            return singlePhysicalTable(localAliases).map(table -> ColumnRef.of(table, clean(cleaned)));
        }
        if (PLAIN_COLUMN.matcher(cleaned).matches()) {
            String[] parts = cleaned.split("\\.", 2);
            String alias = clean(parts[0]);
            String column = clean(parts[1]);
            Optional<ColumnRef> rowsetColumn = resolve(alias, column);
            if (rowsetColumn.isPresent()) {
                return rowsetColumn;
            }
            TableId table = aliases.get(alias);
            if (table == null) {
                return Optional.empty();
            }
            return Optional.of(ColumnRef.of(table, column));
        }
        return Optional.empty();
    }

    private Optional<ColumnRef> singleRowsetColumn(
            Map<String, Map<String, ColumnRef>> rowsetAliases,
            String column
    ) {
        String normalizedColumn = normalize(column);
        Map<String, ColumnRef> unique = new LinkedHashMap<>();
        for (Map<String, ColumnRef> output : rowsetAliases.values()) {
            ColumnRef source = output.get(normalizedColumn);
            if (source != null) {
                unique.put(source.displayName(), source);
            }
        }
        if (unique.size() != 1) {
            return Optional.empty();
        }
        return Optional.of(unique.values().iterator().next());
    }

    private Optional<TableId> singlePhysicalTable(Map<String, TableId> aliases) {
        Map<String, TableId> uniqueTables = new LinkedHashMap<>();
        for (TableId table : aliases.values()) {
            uniqueTables.put(table.normalizedName(), table);
        }
        if (uniqueTables.size() != 1) {
            return Optional.empty();
        }
        return Optional.of(uniqueTables.values().iterator().next());
    }

    /**
     * Normalizes one SELECT-list item into expression + output column name.
     *
     * <p>Called by analyzeSelectOutput(). Supported projection shapes are:
     *
     * <pre>{@code
     * SELECT o.id AS order_id FROM orders o
     * SELECT o.id order_id FROM orders o
     * SELECT o.id FROM orders o
     * }</pre>
     *
     * Complex expressions return an empty output column unless an explicit
     * caller-provided column list supplies the output name; even then
     * directColumnSource() will reject the expression for precise lineage.
     */
    private Projection parseProjection(String rawProjection) {
        String projection = rawProjection.trim();
        Matcher asMatcher = AS_ALIAS.matcher(projection);
        if (asMatcher.matches()) {
            return new Projection(asMatcher.group(1).trim(), clean(asMatcher.group(2)));
        }

        List<String> tokens = splitWhitespaceAtTopLevel(projection);
        if (tokens.size() == 2 && PLAIN_COLUMN.matcher(tokens.get(0)).matches() && !isKeyword(tokens.get(1))) {
            return new Projection(tokens.get(0), clean(tokens.get(1)));
        }
        if (tokens.size() == 2 && PLAIN_UNQUALIFIED_COLUMN.matcher(tokens.get(0)).matches() && !isKeyword(tokens.get(1))) {
            return new Projection(tokens.get(0), clean(tokens.get(1)));
        }
        if (PLAIN_COLUMN.matcher(projection).matches()) {
            String[] pieces = projection.split("\\.", 2);
            return new Projection(projection, clean(pieces[1]));
        }
        if (PLAIN_UNQUALIFIED_COLUMN.matcher(projection).matches()) {
            return new Projection(projection, clean(projection));
        }
        return new Projection(projection, "");
    }

    /**
     * Registers a CTE/derived table name and all of its output columns.
     *
     * <p>Called after analyzing a CTE body, a derived table body, or an alias to
     * an already-known rowset. It writes both rowsets[rowsetName] and the flat
     * columns map used by resolve().
     */
    private void addRowset(String rowsetName, Map<String, ColumnRef> output) {
        if (output.isEmpty()) {
            return;
        }
        String normalizedRowset = normalize(rowsetName);
        rowsets.put(normalizedRowset, output);
        for (Map.Entry<String, ColumnRef> entry : output.entrySet()) {
            columns.put(new ColumnKey(normalizedRowset, entry.getKey()), entry.getValue());
        }
    }

    /**
     * Copies lineage from a rowset name to aliases used in FROM/JOIN clauses.
     *
     * <p>Called after rowsets are discovered. Example:
     *
     * <pre>{@code
     * WITH recent_orders AS (
     *   SELECT o.id AS order_id FROM orders o
     * )
     * SELECT * FROM recent_orders ro
     * }</pre>
     *
     * If {@code recent_orders.order_id -> orders.id} is known, this method adds
     * {@code ro.order_id -> orders.id}. The loop scans every FROM/JOIN reference
     * and copies only when the referenced table name is a known rowset.
     */
    private void applyRowsetReferenceAliases(String text) {
        if (rowsets.isEmpty()) {
            return;
        }
        Matcher matcher = FROM_OR_JOIN_NAME.matcher(text);
        while (matcher.find()) {
            String rowsetName = normalize(cleanTable(matcher.group(1)));
            Map<String, ColumnRef> output = rowsets.get(rowsetName);
            if (output == null) {
                continue;
            }
            String alias = aliasFromRowsetMatcher(matcher);
            if (alias.isBlank() || isKeyword(alias)) {
                continue;
            }
            addRowset(alias, output);
        }
    }

    private static String aliasFromRowsetMatcher(Matcher matcher) {
        String explicitAsAlias = matcher.group(2);
        if (explicitAsAlias != null) {
            return clean(explicitAsAlias);
        }
        String plainAlias = matcher.group(3);
        return plainAlias == null ? "" : clean(plainAlias);
    }

    /**
     * Reads the alias that follows a derived-table closing parenthesis.
     *
     * <p>Called by analyzeDerivedTables(). It supports both ordinary aliases and
     * explicit output column lists:
     *
     * <pre>{@code
     * FROM (SELECT o.id FROM orders o) AS x(order_id)
     * FROM (SELECT o.id FROM orders o) x
     * }</pre>
     */
    private AliasAfterParen readAliasAfterParen(String text, int start) {
        int position = skipWhitespace(text, start);
        if (startsWithWord(text, position, "as")) {
            position = skipWhitespace(text, position + "as".length());
        }
        Identifier alias = readIdentifier(text, position);
        if (alias.value().isBlank() || isKeyword(alias.value())) {
            return new AliasAfterParen("", List.of());
        }
        position = skipWhitespace(text, alias.end());
        List<String> columnNames = List.of();
        if (position < text.length() && text.charAt(position) == '(') {
            int end = findMatchingParen(text, position);
            if (end >= 0) {
                columnNames = splitIdentifierList(text.substring(position + 1, end));
            }
        }
        return new AliasAfterParen(alias.value(), columnNames);
    }

    private static boolean startsWithQuery(String text) {
        String trimmed = text.stripLeading().toLowerCase(Locale.ROOT);
        return startsWithWord(trimmed, 0, "select") || startsWithWord(trimmed, 0, "with");
    }

    private static List<String> splitIdentifierList(String text) {
        List<String> values = new ArrayList<>();
        for (String item : splitTopLevel(text, ',')) {
            String value = clean(item);
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return values;
    }

    private static List<String> splitWhitespaceAtTopLevel(String text) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')' && depth > 0) {
                depth--;
            }
            if (Character.isWhitespace(c) && depth == 0) {
                if (!current.isEmpty()) {
                    parts.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            parts.add(current.toString());
        }
        return parts;
    }

    /**
     * Splits text by a delimiter only at parenthesis depth zero.
     *
     * <p>Called for SELECT lists and explicit column lists. The depth counter is
     * why {@code SELECT COALESCE(a.id, b.id) AS id, o.user_id ...} stays as two
     * projections rather than splitting inside COALESCE().
     */
    private static List<String> splitTopLevel(String text, char delimiter) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')' && depth > 0) {
                depth--;
            }
            if (c == delimiter && depth == 0) {
                parts.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            parts.add(current.toString().trim());
        }
        return parts;
    }

    /**
     * Finds a keyword outside parentheses.
     *
     * <p>Called by analyzeSelectOutput() to find the SELECT and FROM that belong
     * to the current query body, not a nested subquery inside the SELECT list.
     */
    private static int findTopLevelKeyword(String text, String keyword, int start) {
        int depth = 0;
        for (int i = Math.max(0, start); i <= text.length() - keyword.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') {
                depth++;
                continue;
            }
            if (c == ')' && depth > 0) {
                depth--;
                continue;
            }
            if (depth == 0 && startsWithWord(text, i, keyword)) {
                return i;
            }
        }
        return -1;
    }

    private static int findMatchingParen(String text, int openPosition) {
        int depth = 0;
        for (int i = openPosition; i < text.length(); i++) {
            char c = text.charAt(i);
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

    private static int indexOfWord(String text, String word, int start) {
        for (int i = Math.max(0, start); i <= text.length() - word.length(); i++) {
            if (startsWithWord(text, i, word)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean startsWithWord(String text, int position, String word) {
        if (position < 0 || position + word.length() > text.length()) {
            return false;
        }
        if (!text.regionMatches(true, position, word, 0, word.length())) {
            return false;
        }
        boolean beforeOk = position == 0 || !isIdentifierPart(text.charAt(position - 1));
        int after = position + word.length();
        boolean afterOk = after >= text.length() || !isIdentifierPart(text.charAt(after));
        return beforeOk && afterOk;
    }

    private static Identifier readIdentifier(String text, int start) {
        int position = skipWhitespace(text, start);
        if (position >= text.length()) {
            return new Identifier("", position);
        }
        char first = text.charAt(position);
        if (first == '`' || first == '"') {
            int end = text.indexOf(first, position + 1);
            if (end < 0) {
                return new Identifier("", position);
            }
            return new Identifier(text.substring(position + 1, end), end + 1);
        }
        int end = position;
        while (end < text.length() && isIdentifierPart(text.charAt(end))) {
            end++;
        }
        if (end == position) {
            return new Identifier("", position);
        }
        return new Identifier(text.substring(position, end), end);
    }

    private static int skipWhitespaceAndCommas(String text, int start) {
        int position = start;
        while (position < text.length()
                && (Character.isWhitespace(text.charAt(position)) || text.charAt(position) == ',')) {
            position++;
        }
        return position;
    }

    private static int skipWhitespace(String text, int start) {
        int position = start;
        while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
            position++;
        }
        return position;
    }

    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    private static boolean isKeyword(String value) {
        String lower = normalize(value);
        return lower.equals("on") || lower.equals("where") || lower.equals("join") || lower.equals("left")
                || lower.equals("right") || lower.equals("inner") || lower.equals("outer") || lower.equals("full")
                || lower.equals("cross") || lower.equals("using") || lower.equals("natural") || lower.equals("group")
                || lower.equals("order") || lower.equals("having") || lower.equals("limit") || lower.equals("union");
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

    private static String normalize(String value) {
        return clean(value).toLowerCase(Locale.ROOT);
    }

    private record ColumnKey(String alias, String column) {
    }

    private record Identifier(String value, int end) {
    }

    private record Projection(String expression, String outputColumn) {
    }

    private record AliasAfterParen(String alias, List<String> columnNames) {
    }
}
