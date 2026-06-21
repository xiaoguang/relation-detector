package com.relationdetector.core.tokenevent;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.antlr.v4.runtime.Token;

import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.contracts.Enums.StructuredParseEventType;

/**
 * token-event 的低层 token 结构事件提取器。
 *
 * <p>CN: ANTLR 提供底层 lexer/parser support；本类从 visible token 中抽取
 * dialect-neutral 的 TABLE_REFERENCE / COLUMN_EQUALITY bootstrap 事件。更复杂的
 * rowset、predicate、lineage 事件由 TokenEventSqlEventBuilder 及方言子类补充。
 *
 * <p>EN: Low-level token-event structural extractor. ANTLR provides lexer/parser
 * support; this class extracts dialect-neutral TABLE_REFERENCE / COLUMN_EQUALITY
 * bootstrap events from visible tokens. More complex rowset, predicate, and
 * lineage events are added by TokenEventSqlEventBuilder and dialect subclasses.
 *
 * <pre>{@code
 * StructuredSqlParser
 *   -> generated dialect lexer/parser
 *   -> TokenEventSqlTokenSupport
 *   -> TABLE_REFERENCE / COLUMN_EQUALITY events
 * }</pre>
 */
public class TokenEventSqlTokenSupport {
    private final String name;
    private final Set<Integer> identifierTokenTypes;

    public TokenEventSqlTokenSupport(String name, Set<Integer> identifierTokenTypes) {
        this.name = name;
        this.identifierTokenTypes = Set.copyOf(identifierTokenTypes);
    }

    public String name() {
        return name;
    }

    public List<StructuredSqlEvent> extractEvents(SqlStatementRecord statement, List<Token> tokens) {
        List<StructuredSqlEvent> events = new ArrayList<>();
        events.addAll(extractTableReferences(statement, tokens));
        events.addAll(extractColumnEqualities(statement, tokens));
        return events;
    }

    /**
     * Extracts table references from tokens following rowset-introducing words.
     *
     * <p>Complete SQL examples handled here:
     *
     * <pre>{@code
     * SELECT * FROM orders o JOIN users u ON o.user_id = u.id
     * SELECT * FROM "public"."orders" AS o JOIN "public"."users" u ON o.user_id = u.id
     * UPDATE orders o SET status = 'X' FROM users u WHERE o.user_id = u.id
     * DELETE FROM orders o USING users u WHERE o.user_id = u.id
     * MERGE INTO target_orders t USING source_orders s ON t.source_order_id = s.id
     * INSERT INTO order_archive SELECT * FROM orders o JOIN users u ON o.user_id = u.id
     * }</pre>
     */
    private List<StructuredSqlEvent> extractTableReferences(SqlStatementRecord statement, List<Token> tokens) {
        List<StructuredSqlEvent> events = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            String lower = lower(tokens.get(i));
            if (lower.equals("from")
                    || lower.equals("join")
                    || lower.equals("straight_join")
                    || lower.equals("update")
                    || lower.equals("into")
                    || (lower.equals("using") && isDmlRowsetUsing(tokens, i))) {
                if (lower.equals("from") && isDeleteTargetAliasBeforeUsing(tokens, i)) {
                    continue;
                }
                if (lower.equals("update") && i + 1 < tokens.size() && lower(tokens.get(i + 1)).equals("set")) {
                    continue;
                }
                addTableReferenceEvent(statement, tokens, events, i, lower, i + 1);
            } else if (tokens.get(i).getText().equals(",") && isRowsetComma(tokens, i)) {
                /*
                 * MySQL and PostgreSQL both allow comma-separated rowsets:
                 *
                 *   SELECT * FROM orders o, users u WHERE o.user_id = u.id
                 *   UPDATE orders o, users u SET ... WHERE o.user_id = u.id
                 *
                 * There is no new FROM/JOIN keyword before users, so the event
                 * visitor must treat the comma itself as a rowset introducer.
                 * isRowsetComma() protects SELECT lists, SET assignments, and
                 * function argument commas from being mistaken for tables.
                 */
                addTableReferenceEvent(statement, tokens, events, i, "COMMA", i + 1);
            }
        }
        return events;
    }

    private void addTableReferenceEvent(
            SqlStatementRecord statement,
            List<Token> tokens,
            List<StructuredSqlEvent> events,
            int keywordIndex,
            String keyword,
            int tableIndex
    ) {
        IdentifierRead table = readQualifiedIdentifier(tokens, tableIndex);
        table = table == null ? readQualifiedIdentifier(tokens, skipRowsetWrappers(tokens, tableIndex)) : table;
        if (table == null) {
            return;
        }
        if (isKeyword(table.qualifiedName.toLowerCase(Locale.ROOT))) {
            return;
        }
        if (isFunctionLikeRowset(tokens, table.nextIndex)) {
            /*
             * Complete SQL examples:
             *
             *   SELECT * FROM json_to_recordset(payload) AS decoded(id bigint)
             *   SELECT * FROM ROWS FROM (generate_series(1, 3)) AS g(n)
             *
             * These names introduce table-function rowsets scoped to the current
             * statement, not physical database tables. If they become
             * TABLE_REFERENCE events, the relation visitor can later create fake
             * table co-occurrence edges such as orders -> json_to_recordset.
             */
            return;
        }
        if (isRowsetModifier(table.qualifiedName)) {
            /*
             * Complete SQL example:
             *
             *   SELECT *
             *   FROM orders o
             *   JOIN LATERAL (SELECT o.user_id AS user_id) projected ON true
             *
             * LATERAL is a rowset modifier, not a physical table. The derived
             * alias is handled by SqlLineageResolver from raw SQL text; emitting
             * a TABLE_REFERENCE for LATERAL would create a fake table-level
             * co-occurrence edge.
             */
            return;
        }
        int aliasIndex = table.nextIndex;
        if (aliasIndex < tokens.size() && lower(tokens.get(aliasIndex)).equals("as")) {
            aliasIndex++;
        }
        String alias = null;
        if (aliasIndex < tokens.size() && isIdentifier(tokens.get(aliasIndex)) && !isKeyword(lower(tokens.get(aliasIndex)))) {
            alias = cleanIdentifier(tokens.get(aliasIndex).getText());
        }
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("keyword", keyword.toUpperCase(Locale.ROOT));
        attributes.put("qualifiedTable", table.qualifiedName);
        attributes.put("table", baseName(table.qualifiedName));
        if (alias != null) {
            attributes.put("alias", alias);
        }
        events.add(new StructuredSqlEvent(StructuredParseEventType.TABLE_REFERENCE,
                statement.sourceName(), line(statement, tokens.get(keywordIndex)), attributes));
    }

    /**
     * Extracts simple equality predicates between two qualified column names.
     *
     * <p>This intentionally mirrors the existing lightweight parser's first
     * useful predicate shape:
     *
     * <pre>{@code
     * SELECT * FROM orders o JOIN users u ON o.user_id = u.id
     * SELECT * FROM `orders` o JOIN `users` u ON o.`user_id` = u.`id`
     * }</pre>
     */
    private List<StructuredSqlEvent> extractColumnEqualities(SqlStatementRecord statement, List<Token> tokens) {
        List<StructuredSqlEvent> events = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            if (!tokens.get(i).getText().equals("=")) {
                continue;
            }
            ColumnRead left = readColumnBackwards(tokens, i - 1);
            ColumnRead right = readColumnForward(tokens, i + 1);
            if (left == null || right == null) {
                continue;
            }
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("leftAlias", left.qualifier);
            attributes.put("leftColumn", left.column);
            attributes.put("rightAlias", right.qualifier);
            attributes.put("rightColumn", right.column);
            attributes.put("joinKind", joinKindNear(tokens, i));
            attributes.put("updateSetAssignment", insideUpdateSetAssignment(tokens, i));
            events.add(new StructuredSqlEvent(StructuredParseEventType.COLUMN_EQUALITY,
                    statement.sourceName(), line(statement, tokens.get(i)), attributes));
        }
        return events;
    }

    private boolean insideUpdateSetAssignment(List<Token> tokens, int equalityIndex) {
        int lastUpdate = -1;
        int lastSet = -1;
        int lastWhere = -1;
        for (int i = 0; i < equalityIndex; i++) {
            String token = lower(tokens.get(i));
            if ("update".equals(token)) {
                lastUpdate = i;
            } else if ("set".equals(token)) {
                lastSet = i;
            } else if ("where".equals(token)) {
                lastWhere = i;
            }
        }
        return lastUpdate >= 0 && lastSet > lastUpdate && lastWhere < lastSet;
    }

    /**
     * Finds the nearest JOIN flavor before a predicate token.
     *
     * <p>Complete SQL examples:
     *
     * <pre>{@code
     * SELECT * FROM orders o LEFT JOIN users u ON o.user_id = u.id
     * SELECT * FROM orders o, users u WHERE o.user_id = u.id
     * MERGE INTO target_orders t USING source_orders s ON t.source_order_id = s.id
     * }</pre>
     *
     * The output uses stable normalized strings so evidence
     * attributes remain comparable across parser diagnostics and tests.
     */
    private String joinKindNear(List<Token> tokens, int equalityIndex) {
        int start = Math.max(0, equalityIndex - 30);
        StringBuilder segment = new StringBuilder();
        for (int i = start; i < equalityIndex; i++) {
            segment.append(lower(tokens.get(i))).append(' ');
        }
        String text = segment.toString();
        String best = "WHERE_OR_UNKNOWN";
        int bestIndex = -1;
        for (JoinKindToken token : JOIN_KIND_TOKENS) {
            int index = text.lastIndexOf(token.token());
            if (index > bestIndex) {
                best = token.kind();
                bestIndex = index;
            }
        }
        int genericJoin = text.lastIndexOf("join");
        if (bestIndex < 0 && genericJoin >= 0) {
            best = "INNER_JOIN";
            bestIndex = genericJoin;
        }
        int mergeUsing = text.lastIndexOf("using");
        if (mergeUsing > bestIndex) {
            best = "MERGE_OR_USING";
        }
        return best;
    }

    private IdentifierRead readQualifiedIdentifier(List<Token> tokens, int index) {
        if (index >= tokens.size() || !isIdentifier(tokens.get(index))) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        int cursor = index;
        parts.add(cleanIdentifier(tokens.get(cursor++).getText()));
        while (cursor + 1 < tokens.size() && tokens.get(cursor).getText().equals(".") && isIdentifier(tokens.get(cursor + 1))) {
            cursor++;
            parts.add(cleanIdentifier(tokens.get(cursor++).getText()));
        }
        return new IdentifierRead(String.join(".", parts), cursor);
    }

    private ColumnRead readColumnForward(List<Token> tokens, int index) {
        if (index + 2 >= tokens.size() || !isIdentifier(tokens.get(index)) || !tokens.get(index + 1).getText().equals(".")
                || !isIdentifier(tokens.get(index + 2))) {
            return null;
        }
        return new ColumnRead(cleanIdentifier(tokens.get(index).getText()), cleanIdentifier(tokens.get(index + 2).getText()));
    }

    private ColumnRead readColumnBackwards(List<Token> tokens, int index) {
        if (index - 2 < 0 || !isIdentifier(tokens.get(index)) || !tokens.get(index - 1).getText().equals(".")
                || !isIdentifier(tokens.get(index - 2))) {
            return null;
        }
        return new ColumnRead(cleanIdentifier(tokens.get(index - 2).getText()), cleanIdentifier(tokens.get(index).getText()));
    }

    protected boolean isIdentifier(Token token) {
        return identifierTokenTypes.contains(token.getType());
    }

    protected String cleanIdentifier(String value) {
        if ((value.startsWith("`") && value.endsWith("`")) || (value.startsWith("\"") && value.endsWith("\""))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private boolean isKeyword(String lower) {
        return switch (lower) {
            case "on", "where", "join", "left", "right", "inner", "outer", "full", "cross", "using", "natural",
                    "straight_join", "group", "order", "having", "limit", "union", "set", "values", "select", "from",
                    "force", "use", "ignore", "index", "key", "partition" -> true;
            default -> false;
        };
    }

    /**
     * Skips lexical wrappers that can appear between a rowset keyword and the
     * physical table name in MySQL edge syntax.
     *
     * <p>Complete SQL examples:
     *
     * <pre>{@code
     * FROM { OJ orders AS o LEFT OUTER JOIN users AS u ON o.user_id = u.id }
     * JOIN (order_items AS oi JOIN products AS p ON oi.product_id = p.id)
     * }</pre>
     */
    private int skipRowsetWrappers(List<Token> tokens, int index) {
        int cursor = index;
        if (cursor < tokens.size() && tokens.get(cursor).getText().equals("{")) {
            cursor++;
            if (cursor < tokens.size() && lower(tokens.get(cursor)).equals("oj")) {
                cursor++;
            }
        }
        while (cursor < tokens.size() && tokens.get(cursor).getText().equals("(")) {
            cursor++;
        }
        return cursor;
    }

    private boolean isRowsetModifier(String qualifiedName) {
        String normalized = cleanIdentifier(qualifiedName).toLowerCase(Locale.ROOT);
        return normalized.equals("lateral")
                || normalized.equals("unnest")
                || normalized.equals("json_table");
    }

    private boolean isFunctionLikeRowset(List<Token> tokens, int nextIndex) {
        return nextIndex < tokens.size() && tokens.get(nextIndex).getText().equals("(");
    }

    private boolean isRowsetComma(List<Token> tokens, int commaIndex) {
        int commaDepth = depthBefore(tokens, commaIndex);
        for (int i = commaIndex - 1; i >= 0; i--) {
            if (depthBefore(tokens, i) != commaDepth) {
                continue;
            }
            String lower = lower(tokens.get(i));
            if (lower.equals("where") || lower.equals("on") || lower.equals("set")
                    || lower.equals("group") || lower.equals("order") || lower.equals("having")
                    || lower.equals("limit") || lower.equals("when") || lower.equals("then")
                    || lower.equals("values")) {
                return false;
            }
            if (lower.equals("using")) {
                return isDmlRowsetUsing(tokens, i);
            }
            if (lower.equals("from") || lower.equals("update") || lower.equals("join")) {
                return true;
            }
            if (lower.equals("select") || lower.equals("with")) {
                return false;
            }
        }
        return false;
    }

    private int depthBefore(List<Token> tokens, int tokenIndex) {
        int depth = 0;
        for (int i = 0; i < tokenIndex; i++) {
            String text = tokens.get(i).getText();
            if (text.equals("(")) {
                depth++;
            } else if (text.equals(")") && depth > 0) {
                depth--;
            }
        }
        return depth;
    }

    /**
     * Distinguishes DML {@code USING table} from {@code JOIN USING (columns)}.
     *
     * <p>Complete SQL examples:
     *
     * <pre>{@code
     * DELETE FROM o USING orders o JOIN users u ON o.user_id = u.id
     * MERGE INTO target_orders t USING source_orders s ON t.source_order_id = s.id
     * SELECT * FROM orders o JOIN order_tags ot USING (order_id)
     * }</pre>
     *
     * The first two forms introduce a rowset after {@code USING}. The last form
     * introduces a column list; those names must never become table references.
     */
    private boolean isDmlRowsetUsing(List<Token> tokens, int usingIndex) {
        if (usingIndex + 1 >= tokens.size() || tokens.get(usingIndex + 1).getText().equals("(")) {
            return false;
        }
        for (int i = usingIndex - 1; i >= 0; i--) {
            String lower = lower(tokens.get(i));
            if (lower.equals("join")) {
                return false;
            }
            if (lower.equals("delete") || lower.equals("merge")) {
                return true;
            }
            if (lower.equals("select") || lower.equals("with") || lower.equals(";")) {
                return false;
            }
        }
        return false;
    }

    /**
     * Skips MySQL's delete-target alias in multi-table DELETE.
     *
     * <p>Complete SQL example:
     *
     * <pre>{@code
     * DELETE FROM o
     * USING orders AS o
     * JOIN users u ON o.user_id = u.id
     * }</pre>
     *
     * The {@code o} after {@code DELETE FROM} is the deletion target alias, not a
     * physical rowset. The real table appears in the later {@code USING orders
     * AS o} clause and should be the only TABLE_REFERENCE event for that alias.
     */
    private boolean isDeleteTargetAliasBeforeUsing(List<Token> tokens, int fromIndex) {
        if (fromIndex == 0 || !lower(tokens.get(fromIndex - 1)).equals("delete")) {
            return false;
        }
        IdentifierRead deleteTarget = readQualifiedIdentifier(tokens, fromIndex + 1);
        if (deleteTarget == null) {
            return false;
        }
        for (int i = deleteTarget.nextIndex; i < tokens.size(); i++) {
            String lower = lower(tokens.get(i));
            if (lower.equals("where") || lower.equals(";")) {
                return false;
            }
            if (!lower.equals("using") || !isDmlRowsetUsing(tokens, i)) {
                continue;
            }
            IdentifierRead usingTable = readQualifiedIdentifier(tokens, i + 1);
            if (usingTable == null) {
                return false;
            }
            int aliasIndex = usingTable.nextIndex;
            if (aliasIndex < tokens.size() && lower(tokens.get(aliasIndex)).equals("as")) {
                aliasIndex++;
            }
            return aliasIndex < tokens.size()
                    && isIdentifier(tokens.get(aliasIndex))
                    && cleanIdentifier(tokens.get(aliasIndex).getText()).equalsIgnoreCase(deleteTarget.qualifiedName);
        }
        return false;
    }

    private String baseName(String qualifiedName) {
        int dot = qualifiedName.lastIndexOf('.');
        return dot >= 0 ? qualifiedName.substring(dot + 1) : qualifiedName;
    }

    private String lower(Token token) {
        return cleanIdentifier(token.getText()).toLowerCase(Locale.ROOT);
    }

    private long line(SqlStatementRecord statement, Token token) {
        return statement.startLine() + Math.max(0, token.getLine() - 1);
    }

    private record IdentifierRead(String qualifiedName, int nextIndex) {
    }

    private record ColumnRead(String qualifier, String column) {
    }

    private record JoinKindToken(String token, String kind) {
    }

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
}
