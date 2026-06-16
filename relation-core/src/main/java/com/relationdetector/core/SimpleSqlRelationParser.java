package com.relationdetector.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.relationdetector.api.ColumnRef;
import com.relationdetector.api.Endpoint;
import com.relationdetector.api.Evidence;
import com.relationdetector.api.RelationshipCandidate;
import com.relationdetector.api.SqlStatementRecord;
import com.relationdetector.api.TableId;
import com.relationdetector.api.Enums.EvidenceSourceType;
import com.relationdetector.api.Enums.EvidenceType;
import com.relationdetector.api.Enums.RelationSubType;
import com.relationdetector.api.Enums.RelationType;
import com.relationdetector.api.Enums.StatementSourceType;

/**
 * Lightweight parser used for the first implementation.
 *
 * <p>Design mapping: Phase 6. This parser intentionally covers common,
 * maintainable cases: simple JOIN ... ON equality, WHERE equality joins, IN
 * subqueries, and table co-occurrence. The code is structured so a future
 * JSqlParser-based implementation can replace this class behind the same API.
 */
public final class SimpleSqlRelationParser {
    /*
     * Recognizes table references in ordinary FROM/JOIN clauses and records
     * their aliases.
     *
     * Complete SQL examples:
     *   SELECT * FROM orders o JOIN users u ON o.user_id = u.id
     *   SELECT * FROM shop.orders AS o JOIN shop.users AS u ON o.user_id = u.id
     *   SELECT * FROM `orders` o JOIN `users` u ON o.`user_id` = u.`id`
     *   SELECT * FROM "public"."orders" o JOIN "public"."accounts" a ON o.account_id = a.id
     *
     * Captures:
     *   group(1): table identifier, optionally schema-qualified
     *   group(2): optional alias
     *
     * This is intentionally lexical, not a full SQL grammar. It does not try to
     * directly resolve derived table aliases such as JOIN (SELECT ...) x. Those
     * are handled by SqlLineageResolver when the derived table projects simple
     * base columns; otherwise they remain out of scope for this lightweight
     * parser.
     */
    private static final String ALIAS_KEYWORD_GUARD =
            "(?!(?:on|where|join|left|right|inner|outer|full|cross|using|natural|group|order|having|limit|union)\\b)";

    private static final Pattern FROM_OR_JOIN = Pattern.compile(
            "(?i)\\b(?:from|join)\\s+([`\"\\w.]+)(?:\\s+(?:as\\s+)?" + ALIAS_KEYWORD_GUARD + "([`\"\\w]+))?");

    /*
     * Extracts the raw FROM list before WHERE for old-style comma joins.
     *
     * Complete SQL example:
     *   SELECT *
     *   FROM users u, audit_logs l, security_events se
     *   WHERE l.user_id = u.id AND se.user_id = u.id
     *
     * Purpose:
     *   The normal FROM_OR_JOIN pattern only sees the first table after FROM.
     *   This pattern lets AliasExtractor add the remaining comma-separated
     *   tables so parseCoOccurrence can produce table-level relationships.
     *
     * Limit:
     *   If the FROM block contains explicit JOIN, we leave it to FROM_OR_JOIN.
     */
    private static final Pattern FROM_BLOCK = Pattern.compile("(?is)\\bfrom\\s+(.+?)(?:\\bwhere\\b|$)");

    /*
     * Recognizes equality predicates between two aliased columns.
     *
     * Complete SQL examples:
     *   SELECT * FROM orders o JOIN users u ON o.user_id = u.id
     *   SELECT * FROM payments p JOIN orders o ON p.order_id=o.id
     *   SELECT * FROM `orders` `o` JOIN `users` `u` ON `o`.`user_id` = `u`.`id`
     *
     * Captures:
     *   group(1): left alias
     *   group(2): left column
     *   group(3): right alias
     *   group(4): right column
     *
     * This is the primary FK-like signal for JOIN ... ON, WHERE equi-joins,
     * and equality predicates inside procedure/function/trigger/view bodies.
     */
    private static final Pattern EQUALITY = Pattern.compile(
            "([`\"\\w]+)\\.([`\"\\w]+)\\s*=\\s*([`\"\\w]+)\\.([`\"\\w]+)");

    /*
     * Recognizes simple IN subqueries where an outer column is compared with a
     * single projected inner column.
     *
     * Complete SQL example:
     *   SELECT *
     *   FROM orders o
     *   WHERE o.customer_id IN (SELECT c.id FROM customers c)
     *
     * Captures:
     *   group(1): outer alias
     *   group(2): outer column
     *   group(3): inner projected alias
     *   group(4): inner projected column
     *   group(5): inner table
     *   group(6): inner table alias
     *
     * Limit:
     *   It only recognizes a single projected column directly in the IN
     *   subquery. If the outer or inner alias is a CTE/derived-table alias,
     *   parseInSubquery calls SqlLineageResolver to try a safe base-column
     *   rewrite. UNIONs, tuple IN, and expression projections remain out of
     *   scope for this regex.
     */
    private static final Pattern IN_SUBQUERY = Pattern.compile(
            "(?is)([`\"\\w]+)\\.([`\"\\w]+)\\s+in\\s*\\(\\s*select\\s+([`\"\\w]+)\\.([`\"\\w]+)\\s+from\\s+([`\"\\w.]+)\\s+([`\"\\w]+)");

    /*
     * Recognizes JOIN ... USING(column[, column...]) clauses.
     *
     * Complete SQL examples:
     *   SELECT * FROM orders o JOIN order_tags ot USING (order_id)
     *   SELECT * FROM tenant_users a LEFT JOIN tenant_roles b USING (tenant_id, user_id)
     *
     * Important semantic choice:
     *   USING tells us both tables have columns with the same names and that
     *   the query compares them. It does not tell us direction, uniqueness, or
     *   whether the same-named column is a foreign key. Therefore this parser
     *   emits table-level CO_OCCURRENCE evidence with the usingColumns attribute
     *   instead of inventing a column-level FK_LIKE relation.
     */
    private static final Pattern JOIN_USING = Pattern.compile(
            "(?is)\\b(?:left\\s+(?:outer\\s+)?|right\\s+(?:outer\\s+)?|full\\s+(?:outer\\s+)?|inner\\s+|cross\\s+)?join\\s+([`\"\\w.]+)(?:\\s+(?:as\\s+)?" + ALIAS_KEYWORD_GUARD + "([`\"\\w]+))?\\s+using\\s*\\(([^)]+)\\)");

    /*
     * Recognizes NATURAL JOIN clauses.
     *
     * Complete SQL examples:
     *   SELECT * FROM orders NATURAL JOIN order_audit
     *   SELECT * FROM tenant_users a NATURAL LEFT JOIN tenant_user_audit b
     *
     * Important semantic choice:
     *   NATURAL JOIN compares every same-named column between the two tables,
     *   which is too implicit for safe FK inference without metadata. We record
     *   a weak table-level relationship and preserve naturalJoin=true in
     *   evidence attributes for reviewers and future scoring improvements.
     */
    private static final Pattern NATURAL_JOIN = Pattern.compile(
            "(?is)\\bnatural\\s+(?:left\\s+(?:outer\\s+)?|right\\s+(?:outer\\s+)?|full\\s+(?:outer\\s+)?|inner\\s+)?join\\s+([`\"\\w.]+)(?:\\s+(?:as\\s+)?" + ALIAS_KEYWORD_GUARD + "([`\"\\w]+))?");

    /*
     * Recognizes CTE names from WITH / WITH RECURSIVE clauses.
     *
     * Complete SQL examples:
     *   WITH recent_orders AS (
     *     SELECT o.id, o.user_id FROM orders o
     *   )
     *   SELECT * FROM recent_orders ro JOIN users u ON ro.user_id = u.id
     *
     *   WITH RECURSIVE tree(id, parent_id) AS (
     *     SELECT c.id, c.parent_id FROM categories c
     *   )
     *   SELECT * FROM tree t JOIN categories c ON t.parent_id = c.id
     *
     *   WITH a AS (SELECT o.id FROM orders o),
     *        b AS (SELECT p.order_id FROM payments p)
     *   SELECT * FROM a JOIN b ON b.order_id = a.id
     *
     * Purpose:
     *   CTE names are derived rowsets, not physical tables. If the outer query
     *   joins recent_orders ro ON p.order_id = ro.id, outputting
     *   payments.order_id -> recent_orders.id would be misleading. The alias
     *   extractor skips CTE names as physical tables; SqlLineageResolver can
     *   then restore safe mappings such as ro.id -> orders.id when the CTE
     *   projection is a plain base column. Relationships inside the CTE body
     *   are still visible because the regex scanner sees the inner SELECT text.
     */
    private static final Pattern CTE_NAME = Pattern.compile(
            "(?is)(?:\\bwith\\s+(?:recursive\\s+)?|,\\s*)([`\"\\w]+)(?:\\s*\\([^)]*\\))?\\s+as\\s*\\(");

    /*
     * Recognizes local temporary tables created inside stored procedures,
     * functions, or SQL batches.
     *
     * Complete SQL examples:
     *   CREATE TEMPORARY TABLE selected_user_ids(user_id BIGINT);
     *   CREATE TEMP TABLE selected_status_codes AS SELECT unnest(p_statuses);
     *   CREATE TEMPORARY TABLE IF NOT EXISTS "selected_users" ("user_id" BIGINT);
     *
     * Purpose:
     *   Such tables often materialize procedure inputs or intermediate filters.
     *   When the same body later contains
     *
     *     FROM orders o, users u, selected_user_ids sui
     *     WHERE o.user_id = u.id AND sui.user_id = u.id
     *
     *   only orders.user_id -> users.id is a domain relationship. The
     *   selected_user_ids predicate is a caller-input filter and should not
     *   become relation evidence.
     */
    private static final Pattern TEMP_TABLE_NAME = Pattern.compile(
            "(?is)\\bcreate\\s+(?:temporary|temp)\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?([`\"\\w.]+)");

    /**
     * Main entry point called by each database adaptor.
     *
     * <p>Call chain:
     * ScanEngine -> DatabaseAdaptor.sqlRelationParser() -> this method.
     *
     * <p>The order matters:
     * <ol>
     *   <li>strip comments so regexes do not match disabled SQL;</li>
     *   <li>extract CTE names so they are not treated as physical tables;</li>
     *   <li>build SqlLineageResolver before relation parsing so CTE/derived
     *       aliases can be safely rewritten to base-table columns;</li>
     *   <li>parse stronger column-level evidence first: IN, equality JOIN;</li>
     *   <li>parse weaker implicit evidence: USING/NATURAL;</li>
     *   <li>fall back to table co-occurrence only when no stronger candidate
     *       was found.</li>
     * </ol>
     */
    public List<RelationshipCandidate> parse(SqlStatementRecord record) {
        String sql = stripComments(record.sql());
        Set<String> cteNames = CteNames.extract(sql);
        Set<String> ignoredRowsets = new LinkedHashSet<>(cteNames);
        ignoredRowsets.addAll(LocalTempTableNames.extract(sql));
        SqlLineageResolver lineage = SqlLineageResolver.analyze(sql, ignoredRowsets);
        Map<String, TableId> aliases = AliasExtractor.extract(sql, ignoredRowsets);
        List<RelationshipCandidate> candidates = new ArrayList<>();
        candidates.addAll(parseInSubquery(record, sql, aliases, lineage));
        candidates.addAll(parseEqualityJoins(record, sql, aliases, lineage));
        candidates.addAll(parseUsingAndNaturalJoins(record, sql, aliases, ignoredRowsets));
        if (candidates.isEmpty()) {
            candidates.addAll(parseCoOccurrence(record, aliases));
        }
        return candidates;
    }

    /**
     * Parses aliased equality predicates and turns safe ones into column-level
     * FK-like candidates.
     *
     * <p>Called only from parse(). It consumes alias information from
     * AliasExtractor and optional rewrites from SqlLineageResolver.
     *
     * <p>Loop meaning: each EQUALITY regex match is one predicate such as
     * {@code o.user_id = u.id}. A single SQL statement may contain many such
     * predicates across JOIN, WHERE, EXISTS, procedure bodies, and view bodies,
     * so the loop emits zero or more RelationshipCandidate objects.
     *
     * <p>If neither side looks like a foreign-key column by naming convention,
     * this method deliberately downgrades to table-level CO_OCCURRENCE instead
     * of inventing a direction.
     */
    private List<RelationshipCandidate> parseEqualityJoins(
            SqlStatementRecord record,
            String sql,
            Map<String, TableId> aliases,
            SqlLineageResolver lineage
    ) {
        List<RelationshipCandidate> candidates = new ArrayList<>();
        Matcher matcher = EQUALITY.matcher(sql);
        while (matcher.find()) {
            String leftAlias = clean(matcher.group(1));
            String leftColumnName = clean(matcher.group(2));
            String rightAlias = clean(matcher.group(3));
            String rightColumnName = clean(matcher.group(4));
            ColumnRef left = resolveColumn(leftAlias, leftColumnName, aliases, lineage);
            ColumnRef right = resolveColumn(rightAlias, rightColumnName, aliases, lineage);
            if (left == null || right == null || left.table().normalizedName().equals(right.table().normalizedName())) {
                continue;
            }
            boolean leftLooksSource = looksLikeForeignKey(left.columnName(), right.table().tableName(), right.columnName());
            boolean rightLooksSource = looksLikeForeignKey(right.columnName(), left.table().tableName(), left.columnName());
            Endpoint source = leftLooksSource && !rightLooksSource ? Endpoint.column(left) : Endpoint.column(right);
            Endpoint target = leftLooksSource && !rightLooksSource ? Endpoint.column(right) : Endpoint.column(left);
            if (!leftLooksSource && !rightLooksSource) {
                source = Endpoint.table(left.table());
                target = Endpoint.table(right.table());
                candidates.add(coOccurrence(record, source, target, "ambiguous equality join: " + matcher.group()));
                continue;
            }
            RelationshipCandidate candidate = new RelationshipCandidate(
                    source, target, RelationType.FK_LIKE, RelationSubType.INFERRED_JOIN_FK);
            String joinKind = joinKindNear(sql, matcher.start());
            boolean lineageResolved = lineage.hasLineage(leftAlias, leftColumnName) || lineage.hasLineage(rightAlias, rightColumnName);
            candidate.evidence().add(new Evidence(joinEvidenceType(record.sourceType()),
                    java.math.BigDecimal.valueOf(scoreForJoinSource(record.sourceType())),
                    sourceType(record.sourceType()),
                    record.sourceName(),
                    joinKind + " equality: " + matcher.group(),
                    java.util.Map.of("joinKind", joinKind, "lineageResolved", lineageResolved)));
            candidates.add(candidate);
        }
        return candidates;
    }

    /**
     * Parses simple {@code outer.col IN (SELECT inner.col FROM table alias)}
     * forms.
     *
     * <p>Called from parse() before equality parsing because IN-subquery
     * evidence has a distinct subtype and evidence type. The method still uses
     * SqlLineageResolver, so an outer column such as {@code cte_alias.user_id}
     * can be rewritten to {@code orders.user_id} when the CTE output is a plain
     * projection.
     *
     * <p>Loop meaning: each regex match is one IN predicate. Statements may have
     * several IN predicates, so the loop appends a candidate for each safe match.
     */
    private List<RelationshipCandidate> parseInSubquery(
            SqlStatementRecord record,
            String sql,
            Map<String, TableId> aliases,
            SqlLineageResolver lineage
    ) {
        List<RelationshipCandidate> candidates = new ArrayList<>();
        Matcher matcher = IN_SUBQUERY.matcher(sql);
        while (matcher.find()) {
            String innerAlias = clean(matcher.group(6));
            TableId innerTable = TableId.of(null, cleanTable(matcher.group(5)));
            if (aliases.containsKey(innerAlias)) {
                innerTable = aliases.get(innerAlias);
            }
            ColumnRef source = resolveColumn(clean(matcher.group(1)), clean(matcher.group(2)), aliases, lineage);
            ColumnRef target = resolveColumn(innerAlias, clean(matcher.group(4)), aliases, lineage);
            if (target == null) {
                target = ColumnRef.of(innerTable, clean(matcher.group(4)));
            }
            if (source == null) {
                continue;
            }
            RelationshipCandidate candidate = new RelationshipCandidate(
                    Endpoint.column(source), Endpoint.column(target),
                    RelationType.FK_LIKE, RelationSubType.SUBQUERY_INFERRED_FK);
            candidate.evidence().add(Evidence.of(EvidenceType.SQL_LOG_SUBQUERY_IN, 0.58d,
                    sourceType(record.sourceType()), record.sourceName(), "IN subquery relation"));
            candidates.add(candidate);
        }
        return candidates;
    }

    /**
     * Resolves an SQL alias+column pair to a physical ColumnRef.
     *
     * <p>Called by parseEqualityJoins() and parseInSubquery(). It first asks
     * SqlLineageResolver because CTE/derived aliases are not physical tables.
     * If no lineage exists, it falls back to the ordinary alias map produced by
     * AliasExtractor.
     */
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

    /**
     * Parses implicit-column JOIN syntax that is meaningful but too ambiguous
     * for direct FK inference.
     *
     * <p>Called from parse() after equality and IN parsing. USING and NATURAL
     * both mean "the database compares columns", but they do not expose enough
     * information to decide FK direction or uniqueness. Therefore this method
     * emits only table-level CO_OCCURRENCE candidates with attributes such as
     * usingColumns or naturalJoin.
     *
     * <p>The ordered TableRef list lets the loop pair the current JOIN target
     * with the nearest preceding physical table, which is the left side of that
     * JOIN in normal SQL reading order.
     */
    private List<RelationshipCandidate> parseUsingAndNaturalJoins(
            SqlStatementRecord record,
            String sql,
            Map<String, TableId> aliases,
            Set<String> cteNames
    ) {
        List<RelationshipCandidate> candidates = new ArrayList<>();
        List<AliasExtractor.TableRef> refs = AliasExtractor.extractOrdered(sql, cteNames);

        Matcher usingMatcher = JOIN_USING.matcher(sql);
        while (usingMatcher.find()) {
            TableId right = tableFromJoinMatch(usingMatcher.group(1), usingMatcher.group(2), aliases);
            TableId left = previousPhysicalTable(refs, usingMatcher.start());
            if (left != null && right != null && !left.normalizedName().equals(right.normalizedName())) {
                candidates.add(coOccurrence(record, Endpoint.table(left), Endpoint.table(right),
                        "JOIN USING columns: " + usingMatcher.group(3),
                        java.util.Map.of("joinKind", joinKindNear(sql, usingMatcher.start()), "usingColumns", usingMatcher.group(3).trim())));
            }
        }

        Matcher naturalMatcher = NATURAL_JOIN.matcher(sql);
        while (naturalMatcher.find()) {
            TableId right = tableFromJoinMatch(naturalMatcher.group(1), naturalMatcher.group(2), aliases);
            TableId left = previousPhysicalTable(refs, naturalMatcher.start());
            if (left != null && right != null && !left.normalizedName().equals(right.normalizedName())) {
                candidates.add(coOccurrence(record, Endpoint.table(left), Endpoint.table(right),
                        "NATURAL JOIN uses implicit same-named columns",
                        java.util.Map.of("joinKind", joinKindNear(sql, naturalMatcher.start()), "naturalJoin", true)));
            }
        }
        return candidates;
    }

    /**
     * Last-resort fallback for statements that mention multiple tables without
     * any parseable column relationship.
     *
     * <p>Called only when parse() has found no stronger evidence. The nested
     * loops create every unordered table pair, capped by the 2..8 table guard so
     * one broad report query does not flood the output with weak relationships.
     */
    private List<RelationshipCandidate> parseCoOccurrence(SqlStatementRecord record, Map<String, TableId> aliases) {
        List<TableId> tables = aliases.values().stream().distinct().toList();
        List<RelationshipCandidate> candidates = new ArrayList<>();
        if (tables.size() < 2 || tables.size() > 8) {
            return candidates;
        }
        for (int i = 0; i < tables.size(); i++) {
            for (int j = i + 1; j < tables.size(); j++) {
                candidates.add(coOccurrence(record, Endpoint.table(tables.get(i)), Endpoint.table(tables.get(j)), "tables co-occur in statement"));
            }
        }
        return candidates;
    }

    private RelationshipCandidate coOccurrence(SqlStatementRecord record, Endpoint source, Endpoint target, String detail) {
        return coOccurrence(record, source, target, detail, java.util.Map.of());
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
        candidate.evidence().add(new Evidence(EvidenceType.SQL_LOG_TABLE_CO_OCCURRENCE, java.math.BigDecimal.valueOf(0.25d),
                sourceType(record.sourceType()), record.sourceName(), detail, attributes));
        return candidate;
    }

    /**
     * Maps where the SQL came from to the evidence type used for scoring.
     *
     * <p>Called by parseEqualityJoins(). The same predicate is more explainable
     * if it came from a view/procedure/trigger definition than from a generic
     * SQL log, so source category is preserved in the evidence type.
     */
    private EvidenceType joinEvidenceType(StatementSourceType sourceType) {
        return switch (sourceType) {
            case VIEW -> EvidenceType.VIEW_JOIN;
            case PROCEDURE, FUNCTION -> EvidenceType.PROCEDURE_JOIN;
            case TRIGGER -> EvidenceType.TRIGGER_REFERENCE;
            default -> EvidenceType.SQL_LOG_JOIN;
        };
    }

    private double scoreForJoinSource(StatementSourceType sourceType) {
        return switch (sourceType) {
            case VIEW -> 0.72d;
            case PROCEDURE, FUNCTION -> 0.70d;
            case TRIGGER -> 0.65d;
            default -> 0.55d;
        };
    }

    private EvidenceSourceType sourceType(StatementSourceType statementSourceType) {
        return switch (statementSourceType) {
            case DDL_FILE -> EvidenceSourceType.DDL_FILE;
            case PROCEDURE, FUNCTION, VIEW, TRIGGER -> EvidenceSourceType.DATABASE_OBJECT;
            case NATIVE_LOG -> EvidenceSourceType.NATIVE_LOG;
            case PLAIN_SQL -> EvidenceSourceType.PLAIN_SQL;
        };
    }

    private boolean looksLikeForeignKey(String sourceColumn, String targetTable, String targetColumn) {
        String source = clean(sourceColumn).toLowerCase(Locale.ROOT);
        String target = clean(targetTable).toLowerCase(Locale.ROOT);
        String targetSingular = target.endsWith("s") ? target.substring(0, target.length() - 1) : target;
        boolean targetLooksIdentifier = "id".equalsIgnoreCase(clean(targetColumn)) || clean(targetColumn).endsWith("_id");
        if (!targetLooksIdentifier) {
            return false;
        }

        /*
         * Direct FK names:
         *   SELECT * FROM orders o JOIN users u ON o.user_id = u.id
         *
         * Multi-role FK names:
         *   SELECT * FROM orders o JOIN users u ON o.created_user_id = u.id
         *   SELECT * FROM orders o JOIN users u ON o.updated_user_id = u.id
         *
         * The suffix checks intentionally require "_user_id" / "_users_id" so
         * unrelated names such as "super_id" do not match table "users".
         */
        return source.equals(targetSingular + "_id")
                || source.equals(target + "_id")
                || source.endsWith("_" + targetSingular + "_id")
                || source.endsWith("_" + target + "_id");
    }

    /**
     * Finds the closest JOIN-kind phrase immediately before an equality match.
     *
     * <p>Called by parseEqualityJoins() and parseUsingAndNaturalJoins(). It
     * scans a short prefix window instead of the whole SQL so an earlier JOIN in
     * the statement does not accidentally label a later predicate.
     */
    private String joinKindNear(String sql, int position) {
        String segment = sql.substring(Math.max(0, position - 220), position).toLowerCase(Locale.ROOT);
        String best = "WHERE_OR_UNKNOWN";
        int bestIndex = -1;
        for (JoinKindToken token : JOIN_KIND_TOKENS) {
            JoinKind candidate = nearestJoinKind(segment, best, bestIndex, token.token(), token.kind());
            best = candidate.kind();
            bestIndex = candidate.index();
        }
        return bestIndex >= 0 ? best : nearestJoinKind(segment, best, bestIndex, "join", "INNER_JOIN").kind();
    }

    private JoinKind nearestJoinKind(String segment, String currentKind, int currentIndex, String token, String kind) {
        int index = segment.lastIndexOf(token);
        if (index > currentIndex) {
            return new JoinKind(kind, index);
        }
        return new JoinKind(currentKind, currentIndex);
    }

    private record JoinKind(String kind, int index) {
    }

    private record JoinKindToken(String token, String kind) {
    }

    /*
     * Ordered from most specific phrases to less specific phrases. The
     * algorithm still chooses the nearest token before the equality predicate,
     * but keeping specific forms first makes duplicate phrases such as
     * "full outer join" and "full join" map to the same normalized value.
     */
    private static final List<JoinKindToken> JOIN_KIND_TOKENS = List.of(
            new JoinKindToken("natural full outer join", "NATURAL_FULL_JOIN"),
            new JoinKindToken("natural full join", "NATURAL_FULL_JOIN"),
            new JoinKindToken("natural left outer join", "NATURAL_LEFT_JOIN"),
            new JoinKindToken("natural left join", "NATURAL_LEFT_JOIN"),
            new JoinKindToken("natural right outer join", "NATURAL_RIGHT_JOIN"),
            new JoinKindToken("natural right join", "NATURAL_RIGHT_JOIN"),
            new JoinKindToken("natural join", "NATURAL_JOIN"),
            new JoinKindToken("full outer join", "FULL_JOIN"),
            new JoinKindToken("full join", "FULL_JOIN"),
            new JoinKindToken("left outer join", "LEFT_JOIN"),
            new JoinKindToken("left join", "LEFT_JOIN"),
            new JoinKindToken("right outer join", "RIGHT_JOIN"),
            new JoinKindToken("right join", "RIGHT_JOIN"),
            new JoinKindToken("cross join", "CROSS_JOIN")
    );

    private TableId tableFromJoinMatch(String rawTable, String alias, Map<String, TableId> aliases) {
        String cleanAlias = clean(alias);
        if (!cleanAlias.isBlank() && aliases.containsKey(cleanAlias)) {
            return aliases.get(cleanAlias);
        }
        String tableName = cleanTable(rawTable);
        return aliases.getOrDefault(tableName, TableId.of(null, tableName));
    }

    /**
     * Returns the physical table that appears immediately before a JOIN token.
     *
     * <p>Called by parseUsingAndNaturalJoins(). USING/NATURAL regexes capture
     * the right table, but not the left table. The ordered TableRef stream gives
     * the nearest previous physical table without treating CTE names as tables.
     */
    private TableId previousPhysicalTable(List<AliasExtractor.TableRef> refs, int position) {
        TableId previous = null;
        for (AliasExtractor.TableRef ref : refs) {
            if (ref.position() >= position) {
                break;
            }
            previous = ref.table();
        }
        return previous;
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

    /**
     * Detects rowsets commonly created from stored-procedure parameters or
     * application filter lists.
     *
     * <p>Called by AliasExtractor before a table enters the physical alias map.
     * These rowsets are often used like:
     *
     * <pre>{@code
     * SELECT *
     * FROM orders o,
     *      users u,
     *      tmp_input_user_ids input_users
     * WHERE o.user_id = u.id
     *   AND input_users.user_id = u.id
     * }</pre>
     *
     * The second equality filters the query to a caller-provided set of IDs; it
     * should not become evidence that tmp_input_user_ids is part of the domain
     * relationship graph. This heuristic is intentionally name-based and
     * conservative; database-specific adaptors can later replace it with real
     * temporary-table metadata.
     */
    private static boolean isLikelyInputFilterTable(String tableName, String alias) {
        String table = clean(tableName).toLowerCase(Locale.ROOT);
        String tableAlias = clean(alias).toLowerCase(Locale.ROOT);
        return startsLikeInputFilter(table) || startsLikeInputFilter(tableAlias);
    }

    private static boolean startsLikeInputFilter(String value) {
        return value.startsWith("tmp_")
                || value.startsWith("temp_")
                || value.startsWith("input_")
                || value.startsWith("param_")
                || value.startsWith("filter_")
                || value.startsWith("tmpinput_")
                || value.startsWith("tempinput_");
    }

    /*
     * Splits a possibly qualified table identifier without treating dots inside
     * quoted identifier parts as qualifiers.
     *
     * Supported examples:
     *   public.orders       -> [public, orders]
     *   "public"."orders"  -> [public, orders]
     *   `shop`.`orders`    -> [shop, orders]
     *
     * The parser still intentionally targets normal identifier parts. It does
     * not try to preserve embedded quote escapes inside unusual object names;
     * those should be handled by a parser-backed implementation later.
     */
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

    /**
     * Extracts physical table references and aliases from SQL text.
     *
     * <p>Called by parse() for the main alias map and by
     * parseUsingAndNaturalJoins() for ordered table references. CTE names are
     * passed in so they can be skipped as physical tables; SqlLineageResolver is
     * responsible for recovering safe CTE column mappings separately.
     */
    static final class AliasExtractor {
        private AliasExtractor() {
        }

        /**
         * Builds the alias lookup consumed by relation parsing.
         *
         * <p>Each physical table is added twice when possible: by table name and
         * by explicit alias. This lets SQL like {@code orders.user_id = u.id}
         * work even when only one side uses an alias.
         */
        static Map<String, TableId> extract(String sql, Set<String> cteNames) {
            java.util.LinkedHashMap<String, TableId> aliases = new java.util.LinkedHashMap<>();
            for (TableRef ref : extractOrdered(sql, cteNames)) {
                aliases.put(ref.table().tableName(), ref.table());
                if (!ref.alias().isBlank()) {
                    aliases.put(ref.alias(), ref.table());
                }
            }
            return aliases;
        }

        /**
         * Returns table references in source-text order.
         *
         * <p>The order is needed only for syntax such as JOIN USING and NATURAL
         * JOIN where the regex captures the right table but not the left table.
         * The loop first collects FROM/JOIN matches, then adds comma-separated
         * FROM-list items, and finally sorts by character position.
         */
        static List<TableRef> extractOrdered(String sql, Set<String> cteNames) {
            List<TableRef> refs = new ArrayList<>();
            Matcher matcher = FROM_OR_JOIN.matcher(sql);
            while (matcher.find()) {
                String rawTable = matcher.group(1);
                String schema = schemaName(rawTable);
                String tableName = cleanTable(rawTable);
                if (tableName.equalsIgnoreCase("select") || tableName.isBlank() || isCteName(tableName, cteNames)) {
                    continue;
                }
                TableId table = TableId.of(schema, tableName);
                String alias = clean(matcher.group(2));
                if (isLikelyInputFilterTable(tableName, alias)) {
                    continue;
                }
                refs.add(new TableRef(matcher.start(), table, !alias.isBlank() && !isKeyword(alias) ? alias : ""));
            }
            extractCommaSeparatedFrom(sql, cteNames, refs);
            refs.sort(java.util.Comparator.comparingInt(TableRef::position));
            return refs;
        }

        /**
         * Adds old-style comma joins from a FROM block.
         *
         * <p>Called by extractOrdered(). It intentionally exits when the block
         * contains explicit JOIN syntax because FROM_OR_JOIN already handles
         * that path and mixing both strategies would duplicate table refs.
         */
        private static void extractCommaSeparatedFrom(String sql, Set<String> cteNames, List<TableRef> refs) {
            Matcher matcher = FROM_BLOCK.matcher(sql);
            if (!matcher.find()) {
                return;
            }
            String block = matcher.group(1);
            if (!block.contains(",") || block.toLowerCase(Locale.ROOT).contains(" join ")) {
                return;
            }
            for (String part : block.split(",")) {
                String[] tokens = part.trim().split("\\s+");
                if (tokens.length == 0 || tokens[0].isBlank()) {
                    continue;
                }
                String rawTable = tokens[0];
                String schema = schemaName(rawTable);
                String tableName = cleanTable(rawTable);
                if (isCteName(tableName, cteNames)) {
                    continue;
                }
                TableId table = TableId.of(schema, tableName);
                String alias = tokens.length > 1 && !isKeyword(tokens[1]) ? clean(tokens[1]) : "";
                if (isLikelyInputFilterTable(tableName, alias)) {
                    continue;
                }
                refs.add(new TableRef(matcher.start(), table, alias));
            }
        }

        private static boolean isCteName(String tableName, Set<String> cteNames) {
            return cteNames.contains(clean(tableName).toLowerCase(Locale.ROOT));
        }

        private static boolean isKeyword(String value) {
            String lower = value.toLowerCase(Locale.ROOT);
            return lower.equals("on") || lower.equals("where") || lower.equals("join") || lower.equals("left")
                    || lower.equals("right") || lower.equals("inner") || lower.equals("outer");
        }

        record TableRef(int position, TableId table, String alias) {
        }
    }

    static final class CteNames {
        private CteNames() {
        }

        /**
         * Extracts names declared by WITH clauses.
         *
         * <p>Called before AliasExtractor. The loop scans every CTE declaration
         * matched by CTE_NAME so multi-CTE statements such as
         * {@code WITH a AS (...), b AS (...) SELECT ...} skip both pseudo-tables.
         */
        static Set<String> extract(String sql) {
            Set<String> names = new LinkedHashSet<>();
            Matcher matcher = CTE_NAME.matcher(sql);
            while (matcher.find()) {
                names.add(clean(matcher.group(1)).toLowerCase(Locale.ROOT));
            }
            return names;
        }
    }

    static final class LocalTempTableNames {
        private LocalTempTableNames() {
        }

        /**
         * Extracts temporary table names declared in the same SQL body.
         *
         * <p>Called by parse() before AliasExtractor. The loop handles multiple
         * CREATE TEMP TABLE statements inside one procedure/function body. Only
         * the table name is stored; later aliases such as {@code selected_users
         * su} are skipped because AliasExtractor sees the physical table name in
         * this ignored-rowset set.
         */
        static Set<String> extract(String sql) {
            Set<String> names = new LinkedHashSet<>();
            Matcher matcher = TEMP_TABLE_NAME.matcher(sql);
            while (matcher.find()) {
                names.add(cleanTable(matcher.group(1)).toLowerCase(Locale.ROOT));
            }
            return names;
        }
    }
}
