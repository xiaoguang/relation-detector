package com.relationdetector.postgres;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.relationdetector.api.RelationshipCandidate;
import com.relationdetector.api.SqlStatementRecord;
import com.relationdetector.api.StructuredParseResult;
import com.relationdetector.core.RelationExtractionVisitor;

/**
 * PostgreSQL relation extraction visitor for the ANTLR SQL path.
 *
 * <p>The shared {@link RelationExtractionVisitor} owns cross-dialect relation
 * semantics such as FK-like direction, evidence construction, and lineage. This
 * subclass owns PostgreSQL-only table-expression fallbacks and filters so
 * syntax such as {@code ONLY}, {@code LATERAL}, {@code UNNEST}, set-returning
 * functions, and PostgreSQL DML rowsets do not leak into MySQL behavior.
 */
public final class PostgresRelationExtractionVisitor extends RelationExtractionVisitor {
    private static final Pattern POSTGRES_JOIN_USING = Pattern.compile(
            "(?is)\\bjoin\\s+([`\"\\w.]+)(?:\\s+(?:as\\s+)?([`\"\\w]+))?\\s+using\\s*\\(([^)]*)\\)(?:\\s+as\\s+[`\"\\w]+)?");
    private static final Pattern POSTGRES_ROWSET_TEXT_REFERENCE = Pattern.compile(
            "(?is)\\b(?:from|join)\\s+(?:only\\s+)?\\(*\\s*([`\"\\w.]+)(?:\\s*\\*)?"
                    + "(?:\\s+tablesample\\s+\\w+\\s*\\([^)]*\\)(?:\\s+repeatable\\s*\\([^)]*\\))?)?"
                    + "(?=\\s*(?:,|\\b(?:join|left|right|inner|outer|full|cross|natural|where|on|group|order|having|limit|union|set|using)\\b|$))");
    private static final Pattern POSTGRES_ROWSET_TEXT_REFERENCE_WITH_ALIAS = Pattern.compile(
            "(?is)\\b(?:from|join)\\s+(?:only\\s+)?\\(*\\s*([`\"\\w.]+)(?:\\s*\\*)?"
                    + "(?:\\s+(?:as\\s+)?([`\"\\w]+))?"
                    + "(?:\\s+tablesample\\s+\\w+\\s*\\([^)]*\\)(?:\\s+repeatable\\s*\\([^)]*\\))?)?"
                    + "(?=\\s*(?:,|\\b(?:join|left|right|inner|outer|full|cross|natural|where|on|group|order|having|limit|union|set|using)\\b|$))");
    private static final Pattern POSTGRES_MATERIALIZED_CTE = Pattern.compile(
            "(?is)(?:\\bwith\\s+(?:recursive\\s+)?|,\\s*)([`\"\\w]+)(?:\\s*\\([^)]*\\))?\\s+as\\s+(?:materialized|not\\s+materialized)\\s*\\(");
    /*
     * Guardrail for SQL that is syntactically MySQL-specific.
     *
     * PostgreSQL should continue to parse normal shared SELECT/JOIN syntax and
     * its own UPDATE FROM / DELETE USING / MERGE USING forms, but it must not
     * infer relationships from MySQL-only wrappers such as:
     *   SELECT STRAIGHT_JOIN * FROM orders o JOIN users u ON o.user_id = u.id
     *   SELECT * FROM { OJ orders o LEFT JOIN users u ON o.user_id = u.id }
     *   SELECT * FROM orders PARTITION (p202501) o JOIN users u ON o.user_id = u.id
     *   DELETE o FROM orders o JOIN users u ON o.user_id = u.id
     */
    private static final Pattern MYSQL_ONLY_TOKEN = Pattern.compile(
            "(?is)(^\\s*select\\s+straight_join\\b|\\{\\s*oj\\b|\\bpartition\\s*\\(|\\b(?:force|use|ignore)\\s+(?:index|key)\\b|\\bjson_table\\s*\\()");
    private static final Pattern MYSQL_DELETE_TARGET_FROM = Pattern.compile(
            "(?is)^\\s*delete\\s+[`\"\\w]+\\s+from\\b");
    private static final Pattern MYSQL_UPDATE_JOIN_OR_COMMA_TARGETS = Pattern.compile(
            "(?is)^\\s*update\\s+.+?(?:\\bjoin\\b|,)\\s+.+?\\bset\\b");
    private static final Pattern DELETE_FROM_USING_TARGET = Pattern.compile(
            "(?is)^\\s*delete\\s+from\\s+([`\"\\w]+)\\s+using\\b(.*)$");

    @Override
    public List<RelationshipCandidate> extract(SqlStatementRecord statement, StructuredParseResult result) {
        if (containsMysqlOnlySyntax(statement.sql())) {
            return List.of();
        }
        return super.extract(statement, result);
    }

    @Override
    protected Pattern joinUsingPattern() {
        return POSTGRES_JOIN_USING;
    }

    @Override
    protected Pattern rowsetTextReferencePattern() {
        return POSTGRES_ROWSET_TEXT_REFERENCE;
    }

    @Override
    protected Pattern rowsetTextReferenceWithAliasPattern() {
        return POSTGRES_ROWSET_TEXT_REFERENCE_WITH_ALIAS;
    }

    @Override
    protected boolean isKeyword(String value) {
        String lower = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return super.isKeyword(value)
                || lower.equals("only")
                || lower.equals("lateral")
                || lower.equals("ordinality")
                || lower.equals("tablesample")
                || lower.equals("system")
                || lower.equals("bernoulli")
                || lower.equals("repeatable")
                || lower.equals("rows")
                || lower.equals("materialized");
    }

    @Override
    protected boolean isDialectRowsetModifier(String tableName) {
        String lower = tableName == null ? "" : tableName.trim().toLowerCase(Locale.ROOT);
        return lower.equals("lateral") || lower.equals("unnest") || lower.equals("rows");
    }

    @Override
    protected void collectDialectIgnoredRowsets(String sql, Set<String> names) {
        POSTGRES_MATERIALIZED_CTE.matcher(sql).results()
                .map(match -> match.group(1))
                .map(PostgresRelationExtractionVisitor::cleanDialectIdentifier)
                .forEach(names::add);
    }

    private boolean containsMysqlOnlySyntax(String sql) {
        if (MYSQL_ONLY_TOKEN.matcher(sql).find()
                || MYSQL_DELETE_TARGET_FROM.matcher(sql).find()
                || MYSQL_UPDATE_JOIN_OR_COMMA_TARGETS.matcher(sql).find()) {
            return true;
        }
        Matcher deleteFromUsing = DELETE_FROM_USING_TARGET.matcher(sql);
        if (!deleteFromUsing.find()) {
            return false;
        }
        String target = Pattern.quote(cleanDialectIdentifier(deleteFromUsing.group(1)));
        String usingTail = deleteFromUsing.group(2);
        return Pattern.compile("(?is)\\b(?:using|join)\\s+[`\"\\w.]+\\s+(?:as\\s+)?" + target + "\\b")
                .matcher(usingTail)
                .find();
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
}
