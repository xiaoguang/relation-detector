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
import com.relationdetector.api.DefaultEvidenceScores;
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
     * Recognizes tuple equality comparisons where both sides are aligned lists
     * of simple column references.
     *
     * Complete SQL example:
     *   SELECT *
     *   FROM orders o
     *   JOIN users u
     *     ON (o.tenant_id, o.user_id) = (u.tenant_id, u.id)
     *
     * The parser only accepts pure alias.column items. Expressions such as
     * COALESCE(o.user_id, 0) deliberately do not match the ColumnToken parser.
     */
    private static final Pattern TUPLE_EQUALITY = Pattern.compile(
            "(?is)\\(([^()]+)\\)\\s*=\\s*\\(([^()]+)\\)");

    /*
     * Recognizes tuple IN subqueries with simple aligned outer and inner column
     * lists.
     *
     * Complete SQL example:
     *   SELECT *
     *   FROM orders o
     *   WHERE (o.tenant_id, o.user_id) IN (
     *     SELECT u.tenant_id, u.id
     *     FROM users u
     *   )
     */
    private static final Pattern TUPLE_IN_SUBQUERY = Pattern.compile(
            "(?is)\\(([^()]+)\\)\\s+in\\s*\\(\\s*select\\s+(.+?)\\s+from\\s+([`\"\\w.]+)\\s+([`\"\\w]+)");

    /*
     * Finds the beginning of EXISTS subqueries. The body itself is read with a
     * small balanced-parenthesis scanner instead of one large regex so nested
     * predicates do not stop at the first inner ")".
     *
     * Complete SQL example:
     *   SELECT o.id
     *   FROM orders o
     *   WHERE EXISTS (
     *     SELECT 1
     *     FROM users u
     *     WHERE u.id = o.user_id
     *   )
     *
     * In native/plain SQL logs, equality predicates inside this span produce
     * SQL_LOG_EXISTS evidence rather than ordinary SQL_LOG_JOIN evidence.
     */
    private static final Pattern EXISTS_START = Pattern.compile("(?is)\\bexists\\s*\\(");

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

    /*
     * Captures the table owned by a trigger so NEW.column and OLD.column can be
     * resolved back to that physical table.
     *
     * Complete SQL examples:
     *   CREATE TRIGGER orders_ai AFTER INSERT ON orders FOR EACH ROW ...
     *   CREATE TRIGGER audit_orders AFTER UPDATE ON public.orders EXECUTE FUNCTION ...
     */
    private static final Pattern TRIGGER_ON_TABLE = Pattern.compile(
            "(?is)\\bcreate\\s+(?:or\\s+replace\\s+)?trigger\\s+[`\"\\w.]+\\s+.+?\\bon\\s+([`\"\\w.]+)");

    /*
     * Mutation statements can carry relationship predicates even though the
     * mutated table is not introduced by an ordinary SELECT FROM clause.
     *
     * Complete SQL examples:
     *   UPDATE orders o SET status = 'PAID' FROM users u WHERE o.user_id = u.id
     *   DELETE FROM orders o USING users u WHERE o.user_id = u.id
     *   MERGE INTO target_orders t USING source_orders s ON t.source_order_id = s.id
     */
    private static final Pattern UPDATE_TABLE = Pattern.compile(
            "(?is)\\bupdate\\s+(?!set\\b)([`\"\\w.]+)(?:\\s+(?:as\\s+)?" + ALIAS_KEYWORD_GUARD + "([`\"\\w]+))?");

    private static final Pattern DELETE_FROM_TABLE = Pattern.compile(
            "(?is)\\bdelete\\s+from\\s+([`\"\\w.]+)(?:\\s+(?:as\\s+)?" + ALIAS_KEYWORD_GUARD + "([`\"\\w]+))?");

    private static final Pattern DELETE_USING_TABLE = Pattern.compile(
            "(?is)\\busing\\s+(?!\\()([`\"\\w.]+)(?:\\s+(?:as\\s+)?" + ALIAS_KEYWORD_GUARD + "([`\"\\w]+))?");

    private static final Pattern MERGE_INTO_TABLE = Pattern.compile(
            "(?is)\\bmerge\\s+into\\s+([`\"\\w.]+)(?:\\s+(?:as\\s+)?" + ALIAS_KEYWORD_GUARD + "([`\"\\w]+))?");

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
        aliases = TriggerPseudoRows.addIfTrigger(record.sourceType(), sql, aliases);
        List<SqlSpan> existsSpans = existsSpansFor(record.sourceType(), sql);
        List<RelationshipCandidate> candidates = new ArrayList<>();
        candidates.addAll(parseTupleInSubquery(record, sql, aliases, lineage));
        candidates.addAll(parseInSubquery(record, sql, aliases, lineage));
        candidates.addAll(parseExistsSubquery(record, sql, aliases, lineage, existsSpans));
        candidates.addAll(parseTupleEqualityJoins(record, sql, aliases, lineage));
        candidates.addAll(parseEqualityJoins(record, sql, aliases, lineage, existsSpans));
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
            SqlLineageResolver lineage,
            List<SqlSpan> existsSpans
    ) {
        List<RelationshipCandidate> candidates = new ArrayList<>();
        Matcher matcher = EQUALITY.matcher(sql);
        while (matcher.find()) {
            if (insideAnySpan(matcher.start(), existsSpans)) {
                continue;
            }
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
     * Parses aligned tuple equality comparisons.
     *
     * <p>Called from parse() before scalar equality parsing. Tuple equality has a
     * useful extra signal: if one aligned pair makes direction clear, the whole
     * tuple normally follows that direction. For example:
     *
     * <pre>{@code
     * SELECT *
     * FROM orders o
     * JOIN users u
     *   ON (o.tenant_id, o.user_id) = (u.tenant_id, u.id)
     * }</pre>
     *
     * The {@code o.user_id -> u.id} pair establishes the tuple direction, so the
     * parser can emit both aligned column relations:
     *
     * <pre>{@code
     * orders.tenant_id -> users.tenant_id
     * orders.user_id   -> users.id
     * }</pre>
     *
     * If direction is conflicting or cannot be established by any pair, the
     * method skips the tuple instead of inventing a column-level FK-like relation.
     */
    private List<RelationshipCandidate> parseTupleEqualityJoins(
            SqlStatementRecord record,
            String sql,
            Map<String, TableId> aliases,
            SqlLineageResolver lineage
    ) {
        List<RelationshipCandidate> candidates = new ArrayList<>();
        Matcher matcher = TUPLE_EQUALITY.matcher(sql);
        while (matcher.find()) {
            List<ColumnToken> leftTokens = ColumnToken.parseList(matcher.group(1));
            List<ColumnToken> rightTokens = ColumnToken.parseList(matcher.group(2));
            if (leftTokens.isEmpty() || leftTokens.size() != rightTokens.size()) {
                continue;
            }
            TupleDirection direction = tupleDirection(leftTokens, rightTokens, aliases, lineage);
            if (direction == TupleDirection.AMBIGUOUS) {
                continue;
            }
            String joinKind = joinKindNear(sql, matcher.start());
            for (int i = 0; i < leftTokens.size(); i++) {
                ColumnRef left = resolveColumn(leftTokens.get(i).alias(), leftTokens.get(i).column(), aliases, lineage);
                ColumnRef right = resolveColumn(rightTokens.get(i).alias(), rightTokens.get(i).column(), aliases, lineage);
                if (unusableColumnPair(left, right)) {
                    continue;
                }
                ColumnToken sourceToken = direction == TupleDirection.LEFT_TO_RIGHT ? leftTokens.get(i) : rightTokens.get(i);
                ColumnToken targetToken = direction == TupleDirection.LEFT_TO_RIGHT ? rightTokens.get(i) : leftTokens.get(i);
                ColumnRef sourceColumn = direction == TupleDirection.LEFT_TO_RIGHT ? left : right;
                ColumnRef targetColumn = direction == TupleDirection.LEFT_TO_RIGHT ? right : left;
                RelationshipCandidate candidate = new RelationshipCandidate(
                        Endpoint.column(sourceColumn), Endpoint.column(targetColumn),
                        RelationType.FK_LIKE, RelationSubType.INFERRED_JOIN_FK);
                boolean lineageResolved = lineage.hasLineage(sourceToken.alias(), sourceToken.column())
                        || lineage.hasLineage(targetToken.alias(), targetToken.column());
                candidate.evidence().add(new Evidence(joinEvidenceType(record.sourceType()),
                        java.math.BigDecimal.valueOf(scoreForJoinSource(record.sourceType())),
                        sourceType(record.sourceType()),
                        record.sourceName(),
                        joinKind + " tuple equality: " + matcher.group(),
                        java.util.Map.of("joinKind", joinKind, "lineageResolved", lineageResolved, "tuplePosition", i + 1)));
                candidates.add(candidate);
            }
        }
        return candidates;
    }

    /**
     * Parses equality predicates inside EXISTS subqueries for SQL log inputs.
     *
     * <p>Called from parse() before ordinary equality parsing. The ordinary
     * equality parser receives the same EXISTS spans and skips them, so a
     * predicate such as {@code u.id = o.user_id} produces exactly one evidence
     * item:
     *
     * <pre>{@code
     * SELECT o.id
     * FROM orders o
     * WHERE EXISTS (
     *   SELECT 1
     *   FROM users u
     *   WHERE u.id = o.user_id
     * )
     * }</pre>
     *
     * The result is {@code orders.user_id -> users.id} with
     * {@code SQL_LOG_EXISTS = 0.58}. This method is currently enabled only for
     * native/plain SQL log statements; view/procedure/trigger bodies keep their
     * object-specific evidence types to preserve source semantics.
     */
    private List<RelationshipCandidate> parseExistsSubquery(
            SqlStatementRecord record,
            String sql,
            Map<String, TableId> aliases,
            SqlLineageResolver lineage,
            List<SqlSpan> existsSpans
    ) {
        List<RelationshipCandidate> candidates = new ArrayList<>();
        for (SqlSpan span : existsSpans) {
            Matcher matcher = EQUALITY.matcher(sql.substring(span.start(), span.end()));
            while (matcher.find()) {
                int absoluteStart = span.start() + matcher.start();
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
                if (!leftLooksSource && !rightLooksSource) {
                    candidates.add(coOccurrence(record, Endpoint.table(left.table()), Endpoint.table(right.table()),
                            "ambiguous EXISTS equality: " + matcher.group()));
                    continue;
                }
                Endpoint source = leftLooksSource && !rightLooksSource ? Endpoint.column(left) : Endpoint.column(right);
                Endpoint target = leftLooksSource && !rightLooksSource ? Endpoint.column(right) : Endpoint.column(left);
                RelationshipCandidate candidate = new RelationshipCandidate(
                        source, target, RelationType.FK_LIKE, RelationSubType.SUBQUERY_INFERRED_FK);
                boolean lineageResolved = lineage.hasLineage(leftAlias, leftColumnName) || lineage.hasLineage(rightAlias, rightColumnName);
                candidate.evidence().add(new Evidence(EvidenceType.SQL_LOG_EXISTS,
                        java.math.BigDecimal.valueOf(DefaultEvidenceScores.SQL_LOG_EXISTS),
                        sourceType(record.sourceType()),
                        record.sourceName(),
                        "EXISTS equality: " + matcher.group(),
                        java.util.Map.of("joinKind", joinKindNear(sql, absoluteStart), "lineageResolved", lineageResolved)));
                candidates.add(candidate);
            }
        }
        return candidates;
    }

    /**
     * Parses tuple IN subqueries with aligned pure column lists.
     *
     * <p>The outer tuple is the source side and the SELECT tuple is the target
     * side, matching the scalar {@code outer.col IN (SELECT inner.col ...)}
     * semantics. Expressions, tuple-size mismatches, and complex projections are
     * skipped.
     */
    private List<RelationshipCandidate> parseTupleInSubquery(
            SqlStatementRecord record,
            String sql,
            Map<String, TableId> aliases,
            SqlLineageResolver lineage
    ) {
        List<RelationshipCandidate> candidates = new ArrayList<>();
        Matcher matcher = TUPLE_IN_SUBQUERY.matcher(sql);
        while (matcher.find()) {
            List<ColumnToken> sourceTokens = ColumnToken.parseList(matcher.group(1));
            List<ColumnToken> targetTokens = ColumnToken.parseList(matcher.group(2));
            if (sourceTokens.isEmpty() || sourceTokens.size() != targetTokens.size()) {
                continue;
            }
            for (int i = 0; i < sourceTokens.size(); i++) {
                ColumnRef source = resolveColumn(sourceTokens.get(i).alias(), sourceTokens.get(i).column(), aliases, lineage);
                ColumnRef target = resolveColumn(targetTokens.get(i).alias(), targetTokens.get(i).column(), aliases, lineage);
                if (target == null) {
                    TableId innerTable = aliases.get(clean(matcher.group(4)));
                    if (innerTable == null) {
                        innerTable = TableId.of(schemaName(matcher.group(3)), cleanTable(matcher.group(3)));
                    }
                    target = ColumnRef.of(innerTable, targetTokens.get(i).column());
                }
                if (unusableColumnPair(source, target)) {
                    continue;
                }
                RelationshipCandidate candidate = new RelationshipCandidate(
                        Endpoint.column(source), Endpoint.column(target),
                        RelationType.FK_LIKE, RelationSubType.SUBQUERY_INFERRED_FK);
                candidate.evidence().add(new Evidence(EvidenceType.SQL_LOG_SUBQUERY_IN,
                        java.math.BigDecimal.valueOf(DefaultEvidenceScores.SQL_LOG_SUBQUERY_IN),
                        sourceType(record.sourceType()),
                        record.sourceName(),
                        "tuple IN subquery relation: " + matcher.group(),
                        java.util.Map.of("tuplePosition", i + 1)));
                candidates.add(candidate);
            }
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
            candidate.evidence().add(Evidence.of(EvidenceType.SQL_LOG_SUBQUERY_IN, DefaultEvidenceScores.SQL_LOG_SUBQUERY_IN,
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
        candidate.evidence().add(new Evidence(EvidenceType.SQL_LOG_TABLE_CO_OCCURRENCE, java.math.BigDecimal.valueOf(DefaultEvidenceScores.SQL_LOG_TABLE_CO_OCCURRENCE),
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

    /**
     * Returns EXISTS bodies that should be treated as SQL-log subquery evidence.
     *
     * <p>Called once from parse(). We deliberately limit this to NATIVE_LOG and
     * PLAIN_SQL because database objects already carry stronger source-specific
     * evidence such as VIEW_JOIN, PROCEDURE_JOIN, or TRIGGER_REFERENCE. The spans
     * contain only the text inside the parentheses, not the word EXISTS itself.
     */
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

    /**
     * Rejects predicates that cannot produce a useful column relation.
     *
     * <p>Called by every equality-style parser before direction inference.
     * Earlier versions skipped all same-table predicates, which accidentally
     * lost legitimate recursive/self-reference evidence:
     *
     * <pre>{@code
     * WITH RECURSIVE employee_paths AS (...)
     * SELECT *
     * FROM employee_paths ep
     * JOIN employees e ON ep.manager_id = e.id
     *
     * -- after lineage: employees.manager_id -> employees.id
     * }</pre>
     *
     * The useful guard is therefore narrower: skip missing columns and exact
     * same table+same column comparisons, but allow different columns on the
     * same table so self-FK-like relationships can be represented.
     */
    private boolean unusableColumnPair(ColumnRef left, ColumnRef right) {
        if (left == null || right == null) {
            return true;
        }
        return left.table().normalizedName().equals(right.table().normalizedName())
                && left.normalizedName().equals(right.normalizedName());
    }

    private TupleDirection tupleDirection(
            List<ColumnToken> leftTokens,
            List<ColumnToken> rightTokens,
            Map<String, TableId> aliases,
            SqlLineageResolver lineage
    ) {
        TupleDirection direction = TupleDirection.AMBIGUOUS;
        for (int i = 0; i < leftTokens.size(); i++) {
            ColumnRef left = resolveColumn(leftTokens.get(i).alias(), leftTokens.get(i).column(), aliases, lineage);
            ColumnRef right = resolveColumn(rightTokens.get(i).alias(), rightTokens.get(i).column(), aliases, lineage);
            if (unusableColumnPair(left, right)) {
                continue;
            }
            boolean leftLooksSource = looksLikeForeignKey(left, right);
            boolean rightLooksSource = looksLikeForeignKey(right, left);
            TupleDirection pairDirection = TupleDirection.AMBIGUOUS;
            if (leftLooksSource && !rightLooksSource) {
                pairDirection = TupleDirection.LEFT_TO_RIGHT;
            } else if (rightLooksSource && !leftLooksSource) {
                pairDirection = TupleDirection.RIGHT_TO_LEFT;
            }
            if (pairDirection == TupleDirection.AMBIGUOUS) {
                continue;
            }
            if (direction != TupleDirection.AMBIGUOUS && direction != pairDirection) {
                return TupleDirection.AMBIGUOUS;
            }
            direction = pairDirection;
        }
        return direction;
    }

    private enum TupleDirection {
        LEFT_TO_RIGHT,
        RIGHT_TO_LEFT,
        AMBIGUOUS
    }

    private record ColumnToken(String alias, String column) {
        private static final Pattern COLUMN = Pattern.compile("\\s*([`\"\\w]+)\\.([`\"\\w]+)\\s*");

        static List<ColumnToken> parseList(String text) {
            List<ColumnToken> tokens = new ArrayList<>();
            for (String part : text.split(",")) {
                Matcher matcher = COLUMN.matcher(part);
                if (!matcher.matches()) {
                    return List.of();
                }
                tokens.add(new ColumnToken(clean(matcher.group(1)), clean(matcher.group(2))));
            }
            return tokens;
        }
    }

    /**
     * Applies conservative naming heuristics to decide FK-like direction.
     *
     * <p>Called from scalar and tuple equality parsing after aliases/lineage
     * have already resolved both sides to physical columns.
     *
     * <p>Cross-table examples still require the source column to mention the
     * target table name:
     *
     * <pre>{@code
     * orders.user_id         -> users.id
     * orders.created_user_id -> users.id
     * }</pre>
     *
     * <p>Self-references need a narrower special case because the FK column
     * often names a role rather than the table:
     *
     * <pre>{@code
     * employees.manager_id -> employees.id
     * categories.parent_id -> categories.id
     * }</pre>
     *
     * The self-reference branch only accepts {@code *_id -> id}; it does not
     * treat arbitrary same-table equality as FK-like.
     */
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
                if (tableName.equalsIgnoreCase("select")
                        || isIgnoredRowsetKeyword(tableName)
                        || isFunctionRowset(sql, matcher.end(1))
                        || !SqlLogNoiseFilter.isValidIdentifierToken(tableName)
                        || tableName.isBlank()
                        || isCteName(tableName, cteNames)) {
                    continue;
                }
                TableId table = TableId.of(schema, tableName);
                String alias = clean(matcher.group(2));
                refs.add(new TableRef(matcher.start(), table, !alias.isBlank() && !isKeyword(alias) ? alias : ""));
            }
            extractMutationTables(sql, cteNames, refs);
            extractCommaSeparatedFrom(sql, cteNames, refs);
            refs.sort(java.util.Comparator.comparingInt(TableRef::position));
            return refs;
        }

        /**
         * Adds aliases introduced by UPDATE/DELETE syntaxes.
         *
         * <p>Called by extractOrdered() before comma-FROM handling. Mutation SQL
         * often carries the same relationship predicates as SELECT, but the
         * mutated table may be introduced by UPDATE or DELETE FROM rather than an
         * ordinary SELECT FROM:
         *
         * <pre>{@code
         * UPDATE orders o
         * SET status = 'PAID'
         * FROM users u
         * WHERE o.user_id = u.id;
         *
         * DELETE FROM orders o
         * USING users u
         * WHERE o.user_id = u.id;
         * }</pre>
         *
         * The loop is conservative: it only registers simple physical table
         * identifiers and lets the existing equality parser decide whether any
         * predicate is strong enough to become FK-like evidence.
         */
        private static void extractMutationTables(String sql, Set<String> cteNames, List<TableRef> refs) {
            addTableRefs(sql, cteNames, refs, UPDATE_TABLE);
            addTableRefs(sql, cteNames, refs, DELETE_FROM_TABLE);
            addTableRefs(sql, cteNames, refs, DELETE_USING_TABLE);
            addTableRefs(sql, cteNames, refs, MERGE_INTO_TABLE);
        }

        private static void addTableRefs(String sql, Set<String> cteNames, List<TableRef> refs, Pattern pattern) {
            Matcher matcher = pattern.matcher(sql);
            while (matcher.find()) {
                String rawTable = matcher.group(1);
                String schema = schemaName(rawTable);
                String tableName = cleanTable(rawTable);
                if (tableName.isBlank()
                        || isIgnoredRowsetKeyword(tableName)
                        || isFunctionRowset(sql, matcher.end(1))
                        || !SqlLogNoiseFilter.isValidIdentifierToken(tableName)
                        || isCteName(tableName, cteNames)) {
                    continue;
                }
                String alias = clean(matcher.group(2));
                refs.add(new TableRef(matcher.start(), TableId.of(schema, tableName),
                        !alias.isBlank() && !isKeyword(alias) ? alias : ""));
            }
        }

        /**
         * Adds old-style comma joins from a FROM block.
         *
         * <p>Called by extractOrdered(). It intentionally exits when the block
         * contains explicit JOIN syntax because FROM_OR_JOIN already handles
         * that path and mixing both strategies would duplicate table refs.
         */
        private static void extractCommaSeparatedFrom(String sql, Set<String> cteNames, List<TableRef> refs) {
            for (FromBlock fromBlock : fromBlocks(sql)) {
                String block = fromBlock.text();
                if (!block.contains(",") || Pattern.compile("(?i)\\bjoin\\b").matcher(block).find()) {
                    continue;
                }
                for (String part : splitTopLevelComma(block)) {
                    String[] tokens = part.trim().split("\\s+");
                    if (tokens.length == 0 || tokens[0].isBlank()) {
                        continue;
                    }
                    String rawTable = tokens[0];
                    String schema = schemaName(rawTable);
                    String tableName = cleanTable(rawTable);
                    if (isCteName(tableName, cteNames)
                            || isIgnoredRowsetKeyword(tableName)
                            || !SqlLogNoiseFilter.isValidIdentifierToken(tableName)) {
                        continue;
                    }
                    TableId table = TableId.of(schema, tableName);
                    String alias = tokens.length > 1 && !isKeyword(tokens[1]) ? clean(tokens[1]) : "";
                    refs.add(new TableRef(fromBlock.position(), table, alias));
                }
            }
        }

        private static List<FromBlock> fromBlocks(String sql) {
            List<FromBlock> blocks = new ArrayList<>();
            String lower = sql.toLowerCase(Locale.ROOT);
            int start = 0;
            while (start < lower.length()) {
                int fromIndex = keywordAtAnyDepth(sql, "from", start);
                if (fromIndex < 0) {
                    break;
                }
                int depth = depthBefore(sql, fromIndex);
                int blockStart = fromIndex + "from".length();
                int blockEnd = sql.length();
                int closeParen = closeParenLeavingDepth(sql, blockStart, depth);
                if (closeParen >= 0) {
                    blockEnd = closeParen;
                }
                for (String terminator : List.of("where", "group", "order", "having", "limit", "union")) {
                    int index = keywordAtDepth(sql, terminator, blockStart, depth);
                    if (index >= 0 && index < blockEnd) {
                        blockEnd = index;
                    }
                }
                blocks.add(new FromBlock(fromIndex, sql.substring(blockStart, blockEnd)));
                start = blockStart;
            }
            return blocks;
        }

        private static int closeParenLeavingDepth(String sql, int start, int targetDepth) {
            int depth = depthBefore(sql, start);
            char quote = 0;
            for (int i = start; i < sql.length(); i++) {
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
                    continue;
                }
                if (c == ')' && depth > 0) {
                    depth--;
                    if (depth < targetDepth) {
                        return i;
                    }
                }
            }
            return -1;
        }

        private static int keywordAtAnyDepth(String sql, String keyword, int start) {
            String lower = sql.toLowerCase(Locale.ROOT);
            char quote = 0;
            for (int i = start; i < lower.length(); i++) {
                char c = lower.charAt(i);
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
                if (isKeywordAt(lower, keyword, i)) {
                    return i;
                }
            }
            return -1;
        }

        private static int keywordAtDepth(String sql, String keyword, int start, int targetDepth) {
            String lower = sql.toLowerCase(Locale.ROOT);
            int depth = depthBefore(sql, start);
            char quote = 0;
            for (int i = start; i < lower.length(); i++) {
                char c = lower.charAt(i);
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
                    continue;
                }
                if (c == ')' && depth > 0) {
                    depth--;
                    continue;
                }
                if (depth == targetDepth && isKeywordAt(lower, keyword, i)) {
                    return i;
                }
            }
            return -1;
        }

        private static int depthBefore(String sql, int index) {
            int depth = 0;
            char quote = 0;
            for (int i = 0; i < index; i++) {
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
                } else if (c == ')' && depth > 0) {
                    depth--;
                }
            }
            return depth;
        }

        private static boolean isKeywordAt(String lower, String keyword, int index) {
            return lower.startsWith(keyword, index)
                    && (index == 0 || !Character.isLetterOrDigit(lower.charAt(index - 1)))
                    && (index + keyword.length() >= lower.length()
                    || !Character.isLetterOrDigit(lower.charAt(index + keyword.length())));
        }

        private static List<String> splitTopLevelComma(String block) {
            List<String> parts = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            int depth = 0;
            char quote = 0;
            for (int i = 0; i < block.length(); i++) {
                char c = block.charAt(i);
                if (quote != 0) {
                    current.append(c);
                    if (c == quote) {
                        quote = 0;
                    }
                    continue;
                }
                if (c == '\'' || c == '"' || c == '`') {
                    quote = c;
                    current.append(c);
                    continue;
                }
                if (c == '(') {
                    depth++;
                    current.append(c);
                    continue;
                }
                if (c == ')' && depth > 0) {
                    depth--;
                    current.append(c);
                    continue;
                }
                if (c == ',' && depth == 0) {
                    parts.add(current.toString());
                    current.setLength(0);
                    continue;
                }
                current.append(c);
            }
            parts.add(current.toString());
            return parts;
        }

        private static boolean isCteName(String tableName, Set<String> cteNames) {
            return cteNames.contains(clean(tableName).toLowerCase(Locale.ROOT));
        }

        /**
         * Filters rowset introducer keywords and table-valued functions that the
         * regex scanner can mistake for physical tables.
         *
         * <p>Complete SQL examples:
         *
         * <pre>{@code
         * SELECT *
         * FROM orders o
         * JOIN LATERAL (SELECT o.user_id AS user_id) x ON true
         *
         * SELECT *
         * FROM users u
         * JOIN unnest(ARRAY[1, 2, 3]) AS input_ids(user_id) ON input_ids.user_id = u.id
         * }</pre>
         *
         * {@code LATERAL} is a modifier and {@code unnest(...)} is a rowset
         * function, not a durable table. SqlLineageResolver may still recover
         * safe lineage from the derived body; this alias extractor must avoid
         * recording those names as physical table identities.
         */
        private static boolean isIgnoredRowsetKeyword(String tableName) {
            String lower = tableName.toLowerCase(Locale.ROOT);
            return lower.equals("lateral") || lower.equals("unnest") || lower.equals("json_table");
        }

        private static boolean isFunctionRowset(String sql, int afterIdentifier) {
            int position = afterIdentifier;
            while (position < sql.length() && Character.isWhitespace(sql.charAt(position))) {
                position++;
            }
            return position < sql.length() && sql.charAt(position) == '(';
        }

        private static boolean isKeyword(String value) {
            String lower = value.toLowerCase(Locale.ROOT);
            return lower.equals("on") || lower.equals("where") || lower.equals("join") || lower.equals("left")
                    || lower.equals("right") || lower.equals("inner") || lower.equals("outer");
        }

        record TableRef(int position, TableId table, String alias) {
        }

        record FromBlock(int position, String text) {
        }
    }

    /**
     * Adds trigger pseudo-row aliases to the alias map for trigger bodies.
     *
     * <p>Trigger SQL can refer to the row being inserted/updated/deleted through
     * pseudo aliases instead of ordinary FROM aliases:
     *
     * <pre>{@code
     * CREATE TRIGGER orders_audit_after_insert
     * AFTER INSERT ON orders
     * FOR EACH ROW
     * BEGIN
     *   INSERT INTO order_audit(order_id, user_email)
     *   SELECT NEW.id, u.email
     *   FROM users u
     *   WHERE u.id = NEW.user_id;
     * END;
     * }</pre>
     *
     * Without this mapping, {@code NEW.user_id} is not resolvable because there
     * is no {@code FROM orders NEW}. The helper maps NEW/new and OLD/old to the
     * trigger table captured from {@code CREATE TRIGGER ... ON orders}.
     */
    static final class TriggerPseudoRows {
        private TriggerPseudoRows() {
        }

        static Map<String, TableId> addIfTrigger(StatementSourceType sourceType, String sql, Map<String, TableId> aliases) {
            if (sourceType != StatementSourceType.TRIGGER) {
                return aliases;
            }
            TableId triggerTable = triggerTable(sql);
            if (triggerTable == null) {
                return aliases;
            }
            java.util.LinkedHashMap<String, TableId> expanded = new java.util.LinkedHashMap<>(aliases);
            expanded.put("NEW", triggerTable);
            expanded.put("new", triggerTable);
            expanded.put("OLD", triggerTable);
            expanded.put("old", triggerTable);
            return expanded;
        }

        private static TableId triggerTable(String sql) {
            Matcher matcher = TRIGGER_ON_TABLE.matcher(sql);
            if (!matcher.find()) {
                return null;
            }
            String rawTable = matcher.group(1);
            return TableId.of(schemaName(rawTable), cleanTable(rawTable));
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
