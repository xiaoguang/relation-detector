package com.relationdetector.mysql;

import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.relationdetector.api.RelationshipCandidate;
import com.relationdetector.api.SqlStatementRecord;
import com.relationdetector.api.StructuredParseResult;
import com.relationdetector.core.RelationExtractionVisitor;

/**
 * MySQL relation extraction visitor for the ANTLR SQL path.
 *
 * <p>The shared {@link RelationExtractionVisitor} owns cross-dialect relation
 * semantics such as FK-like direction, evidence construction, and lineage. This
 * subclass owns MySQL-only text fallbacks and filters so syntax such as
 * {@code STRAIGHT_JOIN}, ODBC {@code { OJ ... }}, optimizer index hints,
 * {@code JSON_TABLE}, and MySQL legacy multi-table update breadth do not leak
 * into PostgreSQL behavior.
 */
public final class MySqlRelationExtractionVisitor extends RelationExtractionVisitor {
    private static final Pattern MYSQL_JOIN_USING = Pattern.compile(
            "(?is)\\b(?:join|straight_join)\\s+([`\"\\w.]+)(?:\\s+(?:as\\s+)?([`\"\\w]+))?\\s+using\\s*\\(([^)]*)\\)(?!\\s+as\\b)");
    /*
     * MySQL-only rowset fallback.
     *
     * Complete examples covered by this pattern:
     *   SELECT * FROM orders PARTITION (p202501) o JOIN users u ON o.user_id = u.id
     *   SELECT * FROM orders o FORCE INDEX FOR JOIN (idx_user) JOIN users u USE INDEX (PRIMARY) ON o.user_id = u.id
     *   UPDATE orders o JOIN users u ON o.user_id = u.id SET o.status = 'PAID'
     *   DELETE FROM o USING orders o JOIN users u ON o.user_id = u.id
     *
     * These spellings are intentionally not in the shared RelationExtractionVisitor.
     * PostgreSQL has different DML rowset grammar and should not inherit MySQL
     * optimizer hints, partition clauses, or legacy multi-table DML fallbacks.
     */
    private static final Pattern MYSQL_ROWSET_TEXT_REFERENCE = Pattern.compile(
            "(?is)(?:\\b(?:from|join|straight_join|using|update)\\s+|,\\s*)"
                    + "(?:\\{\\s*oj\\s+)?\\(*\\s*([`\"\\w.]+)"
                    + "(?:\\s+partition\\s*\\([^)]*\\))?"
                    + "(?:\\s+(?:force|use|ignore)\\s+(?:index|key)(?:\\s+for\\s+\\w+)?\\s*\\([^)]*\\))*"
                    + "(?=\\s*(?:,|\\b(?:join|straight_join|left|right|inner|outer|full|cross|natural|where|on|group|order|having|limit|union|set|using)\\b|$))");
    private static final Pattern MYSQL_ROWSET_TEXT_REFERENCE_WITH_ALIAS = Pattern.compile(
            "(?is)(?:\\b(?:from|join|straight_join|using|update)\\s+|,\\s*)"
                    + "(?:\\{\\s*oj\\s+)?\\(*\\s*([`\"\\w.]+)"
                    + "(?:\\s+partition\\s*\\([^)]*\\))?"
                    + "(?:\\s+(?:as\\s+)?([`\"\\w]+))?"
                    + "(?:\\s+(?:force|use|ignore)\\s+(?:index|key)(?:\\s+for\\s+\\w+)?\\s*\\([^)]*\\))*"
                    + "(?=\\s*(?:,|\\b(?:join|straight_join|left|right|inner|outer|full|cross|natural|where|on|group|order|having|limit|union|set|using)\\b|$))");
    private static final Pattern POSTGRES_JOIN_USING_ALIAS = Pattern.compile(
            "(?is)\\bjoin\\s+[`\"\\w.]+(?:\\s+(?:as\\s+)?[`\"\\w]+)?\\s+using\\s*\\([^)]*\\)\\s+as\\s+[`\"\\w]+");

    @Override
    public List<RelationshipCandidate> extract(SqlStatementRecord statement, StructuredParseResult result) {
        if (POSTGRES_JOIN_USING_ALIAS.matcher(statement.sql()).find()) {
            return List.of();
        }
        return super.extract(statement, result);
    }

    @Override
    protected Pattern joinUsingPattern() {
        return MYSQL_JOIN_USING;
    }

    @Override
    protected Pattern rowsetTextReferencePattern() {
        return MYSQL_ROWSET_TEXT_REFERENCE;
    }

    @Override
    protected Pattern rowsetTextReferenceWithAliasPattern() {
        return MYSQL_ROWSET_TEXT_REFERENCE_WITH_ALIAS;
    }

    @Override
    protected boolean isKeyword(String value) {
        String lower = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return super.isKeyword(value)
                || lower.equals("straight_join")
                || lower.equals("force")
                || lower.equals("use")
                || lower.equals("ignore")
                || lower.equals("index")
                || lower.equals("key")
                || lower.equals("partition")
                || lower.equals("only")
                || lower.equals("rows")
                || lower.equals("tablesample")
                || lower.equals("system")
                || lower.equals("bernoulli")
                || lower.equals("ordinality");
    }

    @Override
    protected boolean isDialectRowsetModifier(String tableName) {
        String lower = tableName == null ? "" : tableName.trim().toLowerCase(Locale.ROOT);
        return lower.equals("lateral") || lower.equals("json_table")
                || lower.equals("only") || lower.equals("rows") || lower.equals("tablesample")
                || lower.equals("unnest");
    }

    @Override
    protected void collectDialectIgnoredRowsets(String sql, Set<String> names) {
        MatcherSupport.POSTGRES_MATERIALIZED_CTE.matcher(sql).results()
                .map(match -> match.group(1))
                .map(MySqlRelationExtractionVisitor::cleanDialectIdentifier)
                .forEach(names::add);
    }

    @Override
    protected boolean keepsTableBreadthBaseline(String sql, String dialect) {
        return "MYSQL".equalsIgnoreCase(dialect)
                && sql.stripLeading().toLowerCase(Locale.ROOT).startsWith("update ");
    }

    private static String cleanDialectIdentifier(String identifier) {
        if (identifier == null) {
            return "";
        }
        String value = identifier.trim();
        if ((value.startsWith("`") && value.endsWith("`")) || (value.startsWith("\"") && value.endsWith("\""))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static final class MatcherSupport {
        private static final Pattern POSTGRES_MATERIALIZED_CTE = Pattern.compile(
                "(?is)(?:\\bwith\\s+(?:recursive\\s+)?|,\\s*)([`\"\\w]+)(?:\\s*\\([^)]*\\))?\\s+as\\s+(?:materialized|not\\s+materialized)\\s*\\(");
    }
}
