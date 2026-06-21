package com.relationdetector.core.ddl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.relationdetector.api.StructuredSqlEvent;
import com.relationdetector.api.Enums.StructuredParseEventType;

/**
 * Extracts relationship-relevant DDL facts into structured events.
 *
 * <p>Call flow:
 *
 * <pre>{@code
 * TokenEventStructuredDdlParser.parseDdl(...)
 *   -> DdlStructuredEventVisitor.extractEvents(...)
 *   -> DdlRelationExtractionVisitor.extract(...)
 * }</pre>
 *
 * <p>The parser layer still runs ANTLR first to validate/tokenize the input and
 * report syntax diagnostics. This visitor is the semantic bridge for DDL
 * relation extraction: it emits explicit FK and index facts as structured
 * events.
 */
public class DdlStructuredEventVisitor {
    protected static final String IDENTIFIER =
            "(?:`[^`]+`|\"[^\"]+\"|[\\w$]+)(?:\\s*\\.\\s*(?:`[^`]+`|\"[^\"]+\"|[\\w$]+))*";
    protected static final String INDEX_NAME = "(?:`[^`]+`|\"[^\"]+\"|[\\w$.-]+)";

    private static final Pattern CREATE_TABLE = Pattern.compile(
            "(?is)\\bcreate\\s+(?:temporary\\s+|unlogged\\s+)?table\\s+(?:if\\s+not\\s+exists\\s+)?("
                    + IDENTIFIER + ")\\s*\\(");
    private static final Pattern ALTER_TABLE = Pattern.compile(
            "(?is)\\balter\\s+table\\s+(?:if\\s+exists\\s+)?("
                    + IDENTIFIER + ")\\s+(.+)");
    private static final Pattern TABLE_FK = Pattern.compile(
            "(?is)(?:constraint\\s+(?:`[^`]+`|\"[^\"]+\"|[\\w$.-]+)\\s+)?foreign\\s+key\\s*\\(([^)]+)\\)\\s+references\\s+("
                    + IDENTIFIER + ")\\s*\\(([^)]+)\\)");
    private static final Pattern INLINE_REFERENCES = Pattern.compile(
            "(?is)\\breferences\\s+(" + IDENTIFIER + ")\\s*\\(([^)]+)\\)");
    private static final Pattern CREATE_INDEX = Pattern.compile(
            "(?is)\\bcreate\\s+(unique\\s+)?index\\s+"
                    + "(?:if\\s+not\\s+exists\\s+)?"
                    + INDEX_NAME
                    + "\\s+on\\s+("
                    + IDENTIFIER + ")(?:\\s+using\\s+\\w+)?\\s*\\((.*?)\\)\\s*"
                    + "(where\\b.*)?$");

    public List<StructuredSqlEvent> extractEvents(String ddl, String sourceName) {
        List<StructuredSqlEvent> events = new ArrayList<>();
        int statementIndex = 0;
        for (String statement : DdlTokenCursor.splitTopLevel(ddl, ';')) {
            statementIndex++;
            DdlStatementView view = DdlStatementView.of(statement, statementIndex);
            switch (view.kind()) {
                case CREATE_TABLE -> extractCreateTable(view.text(), sourceName, view.statementIndex(), events);
                case ALTER_TABLE -> extractAlterTable(view.text(), sourceName, view.statementIndex(), events);
                case CREATE_INDEX -> extractCreateIndex(view.text(), sourceName, view.statementIndex(), events);
                case OTHER -> {
                    // Other DDL statements do not currently emit relationship evidence.
                }
            }
        }
        return events;
    }

    /**
     * Handles CREATE TABLE body items.
     *
     * <p>Complete examples:
     *
     * <pre>{@code
     * CREATE TABLE orders(user_id BIGINT REFERENCES users(id));
     * CREATE TABLE orders(
     *   account_id BIGINT,
     *   CONSTRAINT fk_orders_accounts FOREIGN KEY(account_id) REFERENCES accounts(id)
     * );
     * CREATE TABLE users(id BIGINT PRIMARY KEY);
     * }</pre>
     */
    private void extractCreateTable(
            String statement,
            String sourceName,
            long line,
            List<StructuredSqlEvent> events
    ) {
        Matcher tableMatcher = createTablePattern().matcher(statement);
        if (!tableMatcher.find()) {
            return;
        }
        String table = tableMatcher.group(1);
        int bodyStart = statement.indexOf('(', tableMatcher.end() - 1);
        int bodyEnd = DdlTokenCursor.findMatchingParen(statement, bodyStart);
        if (bodyStart < 0 || bodyEnd < 0) {
            return;
        }
        for (String item : DdlTokenCursor.splitTopLevel(statement.substring(bodyStart + 1, bodyEnd), ',')) {
            String trimmed = item.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            String lower = trimmed.toLowerCase(Locale.ROOT);
            Matcher tableFk = TABLE_FK.matcher(trimmed);
            if (tableFk.find()) {
                addForeignKeyEvents(table, columns(tableFk.group(1)), tableFk.group(2), columns(tableFk.group(3)),
                        sourceName, line, events);
                continue;
            }
            if (lower.startsWith("primary key") || lower.startsWith("constraint") && lower.contains(" primary key")) {
                columnsInsideLastParens(trimmed).forEach(column ->
                        events.add(indexEvent(sourceName, line, table, column, "TARGET_UNIQUE", "PRIMARY_KEY")));
                continue;
            }
            if (isTableUniqueDefinition(lower)) {
                columnsInsideLastParens(trimmed).forEach(column ->
                        events.add(indexEvent(sourceName, line, table, column, "TARGET_UNIQUE", "UNIQUE_CONSTRAINT")));
                continue;
            }
            if (isTableIndexDefinition(lower)) {
                indexPartsInsideLastParens(trimmed).stream()
                        .filter(IndexPart::safeColumn)
                        .forEach(part -> events.add(indexEvent(sourceName, line, table, part.column(), "SOURCE_INDEX", "CREATE_TABLE_INDEX")));
                continue;
            }
            extractColumnDefinition(table, trimmed, sourceName, line, events);
        }
    }

    private void extractColumnDefinition(
            String table,
            String item,
            String sourceName,
            long line,
            List<StructuredSqlEvent> events
    ) {
        String column = DdlTokenCursor.firstIdentifier(item);
        if (column.isBlank()) {
            return;
        }
        String lower = item.toLowerCase(Locale.ROOT);
        if (lower.contains(" primary key")) {
            events.add(indexEvent(sourceName, line, table, column, "TARGET_UNIQUE", "INLINE_PRIMARY_KEY"));
        }
        if (lower.contains(" unique")) {
            events.add(indexEvent(sourceName, line, table, column, "TARGET_UNIQUE", "INLINE_UNIQUE"));
        }
        Matcher inline = INLINE_REFERENCES.matcher(item);
        if (inline.find()) {
            addForeignKeyEvents(table, List.of(column), inline.group(1), columns(inline.group(2)), sourceName, line, events);
        }
    }

    private void extractAlterTable(String statement, String sourceName, long line, List<StructuredSqlEvent> events) {
        Matcher alter = alterTablePattern().matcher(statement);
        if (!alter.find()) {
            return;
        }
        Matcher tableFk = TABLE_FK.matcher(alter.group(2));
        while (tableFk.find()) {
            addForeignKeyEvents(alter.group(1), columns(tableFk.group(1)), tableFk.group(2), columns(tableFk.group(3)),
                    sourceName, line, events);
        }
    }

    /**
     * Handles standalone CREATE INDEX variants.
     *
     * <p>Examples are based on MySQL and PostgreSQL documented syntax:
     *
     * <pre>{@code
     * CREATE INDEX idx_orders_user_id ON orders(user_id);
     * CREATE UNIQUE INDEX users_email_uq ON users(email) INCLUDE (id);
     * CREATE UNIQUE INDEX CONCURRENTLY users_email_active_uq ON ONLY users(email) WHERE deleted_at IS NULL;
     * CREATE UNIQUE INDEX idx_users_email USING BTREE ON users(email) INVISIBLE;
     * CREATE INDEX idx_orders_email_prefix ON orders(user_email(10));
     * CREATE INDEX idx_users_lower_email ON users((lower(email)));
     * }</pre>
     *
     * Partial, prefix, and functional indexes are not global uniqueness/index
     * evidence for relation scoring and are therefore skipped.
     */
    private void extractCreateIndex(String statement, String sourceName, long line, List<StructuredSqlEvent> events) {
        if (!acceptCreateIndexStatement(statement)) {
            return;
        }
        Matcher index = createIndexPattern().matcher(statement.trim());
        if (!index.find()) {
            return;
        }
        boolean unique = index.group(1) != null;
        boolean partial = index.group(4) != null && !index.group(4).isBlank();
        for (IndexPart part : indexParts(index.group(3))) {
            if (!part.safeColumn()) {
                continue;
            }
            if (unique && !partial) {
                events.add(indexEvent(sourceName, line, index.group(2), part.column(), "TARGET_UNIQUE", "CREATE_UNIQUE_INDEX"));
            } else if (!unique) {
                events.add(indexEvent(sourceName, line, index.group(2), part.column(), "SOURCE_INDEX", "CREATE_INDEX"));
            }
        }
    }

    private void addForeignKeyEvents(
            String sourceTable,
            List<String> sourceColumns,
            String targetTable,
            List<String> targetColumns,
            String sourceName,
            long line,
            List<StructuredSqlEvent> events
    ) {
        int count = Math.min(sourceColumns.size(), targetColumns.size());
        for (int i = 0; i < count; i++) {
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("sourceTable", sourceTable);
            attributes.put("sourceColumn", sourceColumns.get(i));
            attributes.put("targetTable", targetTable);
            attributes.put("targetColumn", targetColumns.get(i));
            attributes.put("compositePosition", i + 1);
            attributes.put("compositeSize", count);
            events.add(new StructuredSqlEvent(StructuredParseEventType.DDL_FOREIGN_KEY, sourceName, line, attributes));
        }
    }

    private StructuredSqlEvent indexEvent(String sourceName, long line, String table, String column, String role, String kind) {
        return new StructuredSqlEvent(StructuredParseEventType.DDL_INDEX, sourceName, line,
                Map.of("table", table, "column", column, "role", role, "kind", kind));
    }

    protected Pattern createTablePattern() {
        return CREATE_TABLE;
    }

    protected Pattern alterTablePattern() {
        return ALTER_TABLE;
    }

    protected Pattern createIndexPattern() {
        return CREATE_INDEX;
    }

    protected boolean acceptCreateIndexStatement(String statement) {
        return true;
    }

    protected boolean isTableUniqueDefinition(String lower) {
        return lower.startsWith("unique")
                || lower.startsWith("unique key")
                || lower.startsWith("unique index")
                || lower.startsWith("constraint") && lower.contains(" unique");
    }

    protected boolean isTableIndexDefinition(String lower) {
        return false;
    }

    private List<String> columnsInsideLastParens(String text) {
        int open = text.lastIndexOf('(');
        if (open < 0) {
            return List.of();
        }
        int close = DdlTokenCursor.findMatchingParen(text, open);
        if (close < 0) {
            return List.of();
        }
        return indexParts(text.substring(open + 1, close)).stream()
                .filter(IndexPart::safeColumn)
                .map(IndexPart::column)
                .toList();
    }

    private List<IndexPart> indexPartsInsideLastParens(String text) {
        int open = text.lastIndexOf('(');
        if (open < 0) {
            return List.of();
        }
        int close = DdlTokenCursor.findMatchingParen(text, open);
        if (close < 0) {
            return List.of();
        }
        return indexParts(text.substring(open + 1, close));
    }

    private List<String> columns(String rawColumns) {
        List<String> result = new ArrayList<>();
        for (String item : DdlTokenCursor.splitTopLevel(rawColumns, ',')) {
            String column = DdlTokenCursor.firstIdentifier(item.trim());
            if (!column.isBlank()) {
                result.add(column);
            }
        }
        return result;
    }

    private List<IndexPart> indexParts(String rawColumns) {
        List<IndexPart> result = new ArrayList<>();
        for (String item : DdlTokenCursor.splitTopLevel(rawColumns, ',')) {
            result.add(parseIndexPart(item.trim()));
        }
        return result;
    }

    private IndexPart parseIndexPart(String rawPart) {
        DdlIndexPartParser.IndexPart part = DdlIndexPartParser.parse(rawPart);
        return new IndexPart(part.column(), part.safeColumn());
    }

    private String clean(String identifier) {
        return DdlTokenCursor.cleanIdentifier(identifier);
    }

    private record IndexPart(String column, boolean safeColumn) {
    }
}
