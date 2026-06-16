package com.relationdetector.core;

import java.nio.file.Files;
import java.nio.file.Path;
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
import com.relationdetector.api.TableId;
import com.relationdetector.api.Enums.EvidenceSourceType;
import com.relationdetector.api.Enums.EvidenceType;
import com.relationdetector.api.Enums.RelationSubType;
import com.relationdetector.api.Enums.RelationType;

/**
 * Conservative DDL parser for schema dump files.
 *
 * <p>Design mapping: Phase 6 DDL parsing. The parser separates strong
 * relationship evidence from auxiliary confidence evidence:
 *
 * <ul>
 *   <li>{@code FOREIGN KEY} and inline {@code REFERENCES} create FK-like
 *       candidates;</li>
 *   <li>{@code PRIMARY KEY}, full-table {@code UNIQUE}, and ordinary indexes
 *       only add evidence to an existing candidate;</li>
 *   <li>partial indexes, expression/functional indexes, and prefix indexes are
 *       intentionally skipped as global uniqueness/index evidence.</li>
 * </ul>
 *
 * <p>This is still a lightweight parser, not a full DDL grammar. It targets
 * common MySQL/PostgreSQL schema dumps while preserving conservative behavior
 * for ambiguous dialect-specific constructs.
 */
public final class SimpleDdlParser {
    /*
     * DDL object identifier fragment used by the regexes below.
     *
     * Complete SQL examples this fragment must match:
     *   CREATE TABLE public.orders (...);
     *   CREATE TABLE "sales"."orders" (...);
     *   CREATE TABLE `shop`.`orders` (...);
     *
     * The parser normalizes only the last two parts into schema/table. It does
     * not try to interpret database/catalog names beyond keeping the final
     * schema-qualified relation stable for comparison.
     */
    private static final String IDENTIFIER =
            "(?:`[^`]+`|\"[^\"]+\"|[\\w$]+)(?:\\s*\\.\\s*(?:`[^`]+`|\"[^\"]+\"|[\\w$]+))*";

    /*
     * Index names are not converted into model objects, but they still need to
     * be skipped correctly so the parser can reach the ON <table> clause.
     *
     * Complete SQL examples:
     *   CREATE INDEX idx_orders_user_id ON orders(user_id);
     *   CREATE INDEX "idx orders user" ON orders(user_id);
     *   CREATE INDEX `idx-orders-user` ON orders(user_id);
     */
    private static final String INDEX_NAME = "(?:`[^`]+`|\"[^\"]+\"|[\\w$.-]+)";

    /*
     * CREATE TABLE may contain quoted schema-qualified identifiers:
     *   CREATE TABLE orders (
     *     id BIGINT PRIMARY KEY
     *   )
     *
     *   CREATE TABLE public.orders (
     *     user_id BIGINT REFERENCES public.users(id)
     *   )
     *
     *   CREATE TABLE "sales"."orders" (
     *     "customer_id" BIGINT REFERENCES "sales"."customers"("id")
     *   )
     *
     *   CREATE TABLE `sales`.`orders` (
     *     `customer_id` BIGINT REFERENCES `sales`.`customers`(`id`)
     *   )
     */
    private static final Pattern CREATE_TABLE = Pattern.compile(
            "(?is)\\bcreate\\s+(?:temporary\\s+|unlogged\\s+)?table\\s+(?:if\\s+not\\s+exists\\s+)?("
                    + IDENTIFIER + ")\\s*\\(");

    /*
     * ALTER TABLE ... ADD ... FOREIGN KEY ... REFERENCES ...
     *
     * Complete SQL examples:
     *   ALTER TABLE orders
     *     ADD FOREIGN KEY (user_id) REFERENCES users(id)
     *
     *   ALTER TABLE orders
     *     ADD CONSTRAINT fk_orders_users
     *     FOREIGN KEY (user_id) REFERENCES users(id)
     *
     *   ALTER TABLE ONLY public.orders
     *     ADD CONSTRAINT fk_orders_users
     *     FOREIGN KEY (user_id) REFERENCES public.users(id) NOT VALID
     */
    private static final Pattern ALTER_TABLE = Pattern.compile(
            "(?is)\\balter\\s+table\\s+(?:if\\s+exists\\s+)?(?:only\\s+)?("
                    + IDENTIFIER + ")\\s+(.+)");

    /*
     * Table-level FK. The optional CONSTRAINT name is deliberately allowed
     * before FOREIGN KEY because schema dumps usually include it.
     *
     * Complete SQL example:
     *   CREATE TABLE orders (
     *     user_id BIGINT,
     *     CONSTRAINT fk_orders_users
     *       FOREIGN KEY (user_id) REFERENCES users(id)
     *   )
     */
    private static final Pattern TABLE_FK = Pattern.compile(
            "(?is)(?:constraint\\s+(?:`[^`]+`|\"[^\"]+\"|[\\w$.-]+)\\s+)?foreign\\s+key\\s*\\(([^)]+)\\)\\s+references\\s+("
                    + IDENTIFIER + ")\\s*\\(([^)]+)\\)");

    /*
     * Inline REFERENCES inside a column definition:
     *   CREATE TABLE orders (
     *     customer_id BIGINT REFERENCES customers(id)
     *   )
     */
    private static final Pattern INLINE_REFERENCES = Pattern.compile(
            "(?is)\\breferences\\s+(" + IDENTIFIER + ")\\s*\\(([^)]+)\\)");

    /*
     * PostgreSQL/MySQL CREATE INDEX forms. The WHERE tail is retained in group
     * 4 so partial indexes can be skipped as global uniqueness evidence.
     *
     * Complete SQL examples:
     *   CREATE INDEX idx_orders_user_id ON orders(user_id)
     *   CREATE UNIQUE INDEX users_email_uq ON users(email)
     *   CREATE UNIQUE INDEX users_email_active_uq ON users(email) WHERE deleted_at IS NULL
     *   CREATE UNIQUE INDEX IF NOT EXISTS users_email_uq ON public.users USING btree (email)
     *   CREATE UNIQUE INDEX users_email_cover_uq ON users(email) INCLUDE (id)
     */
    private static final Pattern CREATE_INDEX = Pattern.compile(
            "(?is)\\bcreate\\s+(unique\\s+)?index\\s+(?:concurrently\\s+)?(?:if\\s+not\\s+exists\\s+)?"
                    + INDEX_NAME + "\\s+on\\s+(" + IDENTIFIER
                    + ")(?:\\s+using\\s+\\w+)?\\s*\\((.*?)\\)\\s*(?:include\\s*\\([^)]*\\)\\s*)?(where\\b.*)?$");

    /**
     * File-based entry point used by DatabaseAdaptor.ddlParser().
     *
     * <p>Call chain:
     * ScanEngine.scan() -> adaptor.ddlParser().parseDdl(file, context) ->
     * SimpleDdlParser.parse(file) -> parseText(...).
     *
     * <p>Parsing errors return an empty list instead of failing the whole scan;
     * the CLI can still process SQL logs and metadata from other sources.
     */
    public List<RelationshipCandidate> parse(Path file) {
        try {
            return parseText(Files.readString(file), file.toString());
        } catch (Exception ex) {
            return List.of();
        }
    }

    /**
     * Parses a DDL text blob into relationship candidates.
     *
     * <p>This is the central orchestration method. It performs a two-pass-like
     * process through DdlState:
     * <ol>
     *   <li>split the file into statements;</li>
     *   <li>for each statement, collect FK candidates and auxiliary index/
     *       uniqueness facts;</li>
     *   <li>after all statements are scanned, attach SOURCE_INDEX and
     *       TARGET_UNIQUE evidence to the matching FK candidates.</li>
     * </ol>
     *
     * <p>The final enhancement step is important because the index may appear
     * before or after the FK in a dump file.
     */
    public List<RelationshipCandidate> parseText(String ddl, String source) {
        DdlState state = new DdlState(source);
        for (String statement : splitStatements(ddl)) {
            parseCreateTable(statement, state);
            parseAlterTable(statement, state);
            parseCreateIndex(statement, state);
        }
        state.enhanceCandidatesWithIndexes();
        return state.candidates();
    }

    /**
     * Parses one CREATE TABLE statement.
     *
     * <p>Called from parseText(). It extracts the table name and the body inside
     * the top-level parentheses, then splits the body into comma-separated
     * table items. The loop exists because one CREATE TABLE body can contain
     * column definitions, table-level FKs, primary keys, unique constraints, and
     * indexes side by side.
     */
    private void parseCreateTable(String statement, DdlState state) {
        Matcher tableMatcher = CREATE_TABLE.matcher(statement);
        if (!tableMatcher.find()) {
            return;
        }
        TableId table = table(tableMatcher.group(1));
        int bodyStart = statement.indexOf('(', tableMatcher.end() - 1);
        int bodyEnd = findMatchingParen(statement, bodyStart);
        if (bodyStart < 0 || bodyEnd < 0) {
            return;
        }
        for (String item : splitTopLevel(statement.substring(bodyStart + 1, bodyEnd), ',')) {
            parseCreateTableItem(table, item.trim(), state);
        }
    }

    /**
     * Dispatches one comma-separated item from a CREATE TABLE body.
     *
     * <p>Called by parseCreateTable(). The checks are ordered from most
     * structural to most local:
     * <ol>
     *   <li>table-level FK creates strong relationship evidence;</li>
     *   <li>primary/unique/index table constraints add auxiliary facts;</li>
     *   <li>ordinary column definitions may contain inline PK/UNIQUE/REFERENCES.</li>
     * </ol>
     */
    private void parseCreateTableItem(TableId table, String item, DdlState state) {
        if (item.isBlank()) {
            return;
        }
        String lower = item.toLowerCase(Locale.ROOT);
        Matcher tableFk = TABLE_FK.matcher(item);
        if (tableFk.find()) {
            addForeignKey(table, columns(tableFk.group(1)), table(tableFk.group(2)), columns(tableFk.group(3)), state);
            return;
        }
        if (lower.startsWith("primary key") || lower.startsWith("constraint") && lower.contains(" primary key")) {
            columnsInsideLastParens(item).forEach(column -> state.addTargetUnique(table, column, "PRIMARY_KEY"));
            return;
        }
        if (isTableUniqueDefinition(lower)) {
            columnsInsideLastParens(item).forEach(column -> state.addTargetUnique(table, column, "UNIQUE_CONSTRAINT"));
            return;
        }
        if (isTableIndexDefinition(lower)) {
            for (IndexPart part : indexPartsInsideLastParens(item)) {
                if (part.safeColumn()) {
                    state.addSourceIndex(table, part.column(), "CREATE_TABLE_INDEX");
                }
            }
            return;
        }

        parseColumnDefinition(table, item, state);
    }

    /**
     * Parses one ordinary column definition.
     *
     * <p>Called by parseCreateTableItem() after table-level constraints have
     * been ruled out. It extracts the first identifier as the column name and
     * looks for inline PRIMARY KEY, UNIQUE, or REFERENCES clauses.
     *
     * <pre>{@code
     * CREATE TABLE orders (
     *   customer_id BIGINT REFERENCES customers(id)
     * )
     * }</pre>
     */
    private void parseColumnDefinition(TableId table, String item, DdlState state) {
        String column = firstIdentifier(item);
        if (column.isBlank()) {
            return;
        }
        String lower = item.toLowerCase(Locale.ROOT);
        if (lower.contains(" primary key")) {
            state.addTargetUnique(table, column, "INLINE_PRIMARY_KEY");
        }
        if (lower.contains(" unique")) {
            state.addTargetUnique(table, column, "INLINE_UNIQUE");
        }
        Matcher inlineReferences = INLINE_REFERENCES.matcher(item);
        if (inlineReferences.find()) {
            addForeignKey(table, List.of(column), table(inlineReferences.group(1)), columns(inlineReferences.group(2)), state);
        }
    }

    /**
     * Parses ALTER TABLE statements that add foreign keys.
     *
     * <p>Called from parseText(). The while-loop is intentionally tolerant:
     * some dump formats pack more than one ADD CONSTRAINT fragment into a
     * single ALTER TABLE statement, so each TABLE_FK match becomes a candidate.
     */
    private void parseAlterTable(String statement, DdlState state) {
        Matcher alter = ALTER_TABLE.matcher(statement);
        if (!alter.find()) {
            return;
        }
        TableId table = table(alter.group(1));
        Matcher tableFk = TABLE_FK.matcher(alter.group(2));
        while (tableFk.find()) {
            addForeignKey(table, columns(tableFk.group(1)), table(tableFk.group(2)), columns(tableFk.group(3)), state);
        }
    }

    /**
     * Parses standalone CREATE INDEX / CREATE UNIQUE INDEX statements.
     *
     * <p>Called from parseText(). It does not create relationships by itself.
     * Instead it records auxiliary facts in DdlState:
     *
     * <pre>{@code
     * CREATE INDEX idx_orders_user_id ON orders(user_id)
     * CREATE UNIQUE INDEX users_email_uq ON users(email)
     * CREATE UNIQUE INDEX users_email_active_uq ON users(email) WHERE deleted_at IS NULL
     * }</pre>
     *
     * Partial unique indexes are skipped as global TARGET_UNIQUE evidence
     * because the uniqueness only applies when the WHERE predicate is true.
     */
    private void parseCreateIndex(String statement, DdlState state) {
        Matcher index = CREATE_INDEX.matcher(statement.trim());
        if (!index.find()) {
            return;
        }
        boolean unique = index.group(1) != null;
        TableId table = table(index.group(2));
        boolean partial = index.group(4) != null && !index.group(4).isBlank();
        List<IndexPart> parts = indexParts(index.group(3));
        for (IndexPart part : parts) {
            if (!part.safeColumn()) {
                continue;
            }
            if (unique && !partial) {
                state.addTargetUnique(table, part.column(), "CREATE_UNIQUE_INDEX");
            }
            if (!unique) {
                state.addSourceIndex(table, part.column(), "CREATE_INDEX");
            }
        }
    }

    /**
     * Adds FK candidates for a single FK clause.
     *
     * <p>Called by parseCreateTableItem(), parseColumnDefinition(), and
     * parseAlterTable(). Composite FKs are represented as aligned column pairs:
     *
     * <pre>{@code
     * FOREIGN KEY (tenant_id, customer_id)
     * REFERENCES customers(tenant_id, id)
     * }</pre>
     *
     * The loop emits:
     * <ul>
     *   <li>orders.tenant_id -> customers.tenant_id</li>
     *   <li>orders.customer_id -> customers.id</li>
     * </ul>
     *
     * compositePosition/compositeSize are kept as evidence attributes so a
     * later scoring layer can reconstruct that these pairs came from one
     * composite constraint.
     */
    private void addForeignKey(
            TableId sourceTable,
            List<String> sourceColumns,
            TableId targetTable,
            List<String> targetColumns,
            DdlState state
    ) {
        int count = Math.min(sourceColumns.size(), targetColumns.size());
        for (int i = 0; i < count; i++) {
            ColumnRef source = ColumnRef.of(sourceTable, sourceColumns.get(i));
            ColumnRef target = ColumnRef.of(targetTable, targetColumns.get(i));
            RelationshipCandidate candidate = new RelationshipCandidate(
                    Endpoint.column(source), Endpoint.column(target),
                    RelationType.FK_LIKE, RelationSubType.DDL_DECLARED_FK);
            candidate.evidence().add(new Evidence(EvidenceType.DDL_FOREIGN_KEY,
                    java.math.BigDecimal.valueOf(DefaultEvidenceScores.DDL_FOREIGN_KEY),
                    EvidenceSourceType.DDL_FILE,
                    state.source(),
                    "DDL foreign key",
                    Map.of("compositePosition", i + 1, "compositeSize", count)));
            state.candidates().add(candidate);
        }
    }

    private boolean isTableUniqueDefinition(String lower) {
        return lower.startsWith("unique")
                || lower.startsWith("unique key")
                || lower.startsWith("unique index")
                || lower.startsWith("constraint") && lower.contains(" unique");
    }

    private boolean isTableIndexDefinition(String lower) {
        return lower.startsWith("index ") || lower.startsWith("key ");
    }

    /**
     * Reads direct column names from the last parenthesized list in a constraint.
     *
     * <p>Called for PRIMARY KEY, UNIQUE, and inline table indexes. It delegates
     * to indexParts() so prefix/expression parts are rejected consistently.
     */
    private List<String> columnsInsideLastParens(String text) {
        int open = text.lastIndexOf('(');
        if (open < 0) {
            return List.of();
        }
        int close = findMatchingParen(text, open);
        if (close < 0) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (IndexPart part : indexParts(text.substring(open + 1, close))) {
            if (part.safeColumn()) {
                result.add(part.column());
            }
        }
        return result;
    }

    private List<IndexPart> indexPartsInsideLastParens(String text) {
        int open = text.lastIndexOf('(');
        if (open < 0) {
            return List.of();
        }
        int close = findMatchingParen(text, open);
        if (close < 0) {
            return List.of();
        }
        return indexParts(text.substring(open + 1, close));
    }

    /**
     * Parses a FK column list.
     *
     * <p>Called by FK parsing paths. The loop preserves order because composite
     * FKs depend on source and target lists being aligned by position.
     */
    private List<String> columns(String rawColumns) {
        List<String> result = new ArrayList<>();
        for (String item : splitTopLevel(rawColumns, ',')) {
            String column = firstIdentifier(item.trim());
            if (!column.isBlank()) {
                result.add(column);
            }
        }
        return result;
    }

    /**
     * Parses an index/constraint column list into safe or unsafe parts.
     *
     * <p>Called by CREATE INDEX and CREATE TABLE constraint paths. The loop does
     * not drop unsafe parts immediately so parseIndexPart() can centralize the
     * rules for prefix/expression indexes.
     */
    private List<IndexPart> indexParts(String rawColumns) {
        List<IndexPart> result = new ArrayList<>();
        for (String item : splitTopLevel(rawColumns, ',')) {
            result.add(parseIndexPart(item.trim()));
        }
        return result;
    }

    /**
     * Classifies one index column expression.
     *
     * <p>Called by indexParts(). It returns safeColumn=false for constructs that
     * are not full physical-column indexes:
     *
     * <pre>{@code
     * CREATE UNIQUE INDEX users_email_expr_uq ON users((lower(email)))
     * CREATE INDEX idx_orders_email_prefix ON orders(user_email(10))
     * CREATE INDEX `idx-orders-email-prefix` ON orders(`user_email`(10))
     * }</pre>
     */
    private IndexPart parseIndexPart(String rawPart) {
        String part = rawPart.trim();
        if (part.isBlank()) {
            return new IndexPart("", false);
        }

        /*
         * Expression/functional indexes:
         *   PostgreSQL: CREATE INDEX ... ON users ((lower(email)))
         *   MySQL:      CREATE INDEX ... ON users ((email + suffix))
         *
         * These are not direct column indexes for relation inference.
         */
        if (part.startsWith("(")) {
            return new IndexPart("", false);
        }

        int identifierEnd = firstIdentifierEnd(part);
        String column = identifierEnd > 0 ? clean(part.substring(0, identifierEnd)) : "";
        if (column.isBlank()) {
            return new IndexPart("", false);
        }
        String afterColumn = part.substring(identifierEnd).stripLeading().toLowerCase(Locale.ROOT);
        boolean prefixIndex = afterColumn.startsWith("(");
        return new IndexPart(column, !prefixIndex);
    }

    private String firstIdentifier(String text) {
        int end = firstIdentifierEnd(text);
        return end == 0 ? "" : clean(text.stripLeading().substring(0, end));
    }

    /**
     * Returns the raw character length of the first identifier in stripped text.
     *
     * <p>Called by firstIdentifier() and parseIndexPart(). parseIndexPart()
     * needs the raw end position, not only the cleaned column name, because a
     * quoted prefix index keeps the prefix length after the quote:
     *
     * <pre>{@code
     * KEY `idx-orders-email-prefix` (`user_email`(10))
     * }</pre>
     *
     * The raw identifier is {@code `user_email`}; the following {@code (10)}
     * marks a MySQL prefix index and must not become SOURCE_INDEX evidence for
     * the full column.
     */
    private int firstIdentifierEnd(String text) {
        String trimmed = text.stripLeading();
        if (trimmed.isBlank()) {
            return 0;
        }
        char first = trimmed.charAt(0);
        if (first == '`' || first == '"') {
            int end = trimmed.indexOf(first, 1);
            return end > 0 ? end + 1 : 0;
        }
        int end = 0;
        while (end < trimmed.length() && isIdentifierPart(trimmed.charAt(end))) {
            end++;
        }
        return end;
    }

    /**
     * Converts a raw table identifier into TableId.
     *
     * <p>Called by every DDL parser branch. It supports unquoted and quoted
     * schema-qualified identifiers, for example {@code public.orders},
     * {@code "sales"."orders"}, and {@code `sales`.`orders`}.
     */
    private TableId table(String raw) {
        List<String> parts = identifierParts(raw);
        String tableName = parts.isEmpty() ? clean(raw) : parts.get(parts.size() - 1);
        String schema = parts.size() > 1 ? parts.get(parts.size() - 2) : null;
        return TableId.of(schema, tableName);
    }

    /*
     * Splits identifiers on dots only outside quotes:
     *   public.orders      -> [public, orders]
     *   "sales"."orders"  -> [sales, orders]
     *   `sales`.`orders`  -> [sales, orders]
     */
    private List<String> identifierParts(String identifier) {
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
     * Splits a DDL file into statements.
     *
     * <p>Called by parseText(). Delegates to splitTopLevel() so semicolons
     * inside quoted text or parentheses do not break a statement.
     */
    private List<String> splitStatements(String ddl) {
        return splitTopLevel(ddl, ';');
    }

    /**
     * Splits text by delimiter only at top level.
     *
     * <p>Called for statements, CREATE TABLE body items, and column lists. The
     * loop keeps two pieces of state:
     * <ul>
     *   <li>parenthesis depth, so commas inside {@code user_email(10)} or
     *       {@code lower(email)} are ignored;</li>
     *   <li>quote character, so delimiters inside quoted identifiers or string
     *       literals are ignored.</li>
     * </ul>
     */
    private List<String> splitTopLevel(String text, char delimiter) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        char quote = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c == '\'' || c == '"' || c == '`') && quote == 0) {
                quote = c;
                current.append(c);
                continue;
            }
            if (c == quote) {
                quote = 0;
                current.append(c);
                continue;
            }
            if (quote == 0 && c == '(') {
                depth++;
            } else if (quote == 0 && c == ')' && depth > 0) {
                depth--;
            }
            if (c == delimiter && quote == 0 && depth == 0) {
                String value = current.toString().trim();
                if (!value.isBlank()) {
                    parts.add(value);
                }
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        String value = current.toString().trim();
        if (!value.isBlank()) {
            parts.add(value);
        }
        return parts;
    }

    /**
     * Finds the closing parenthesis that matches a known opening parenthesis.
     *
     * <p>Called when reading CREATE TABLE bodies and parenthesized column
     * lists. The loop ignores parentheses inside quoted strings/identifiers.
     */
    private int findMatchingParen(String text, int openPosition) {
        if (openPosition < 0) {
            return -1;
        }
        int depth = 0;
        char quote = 0;
        for (int i = openPosition; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c == '\'' || c == '"' || c == '`') && quote == 0) {
                quote = c;
                continue;
            }
            if (c == quote) {
                quote = 0;
                continue;
            }
            if (quote != 0) {
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

    private boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    private String clean(String identifier) {
        if (identifier == null) {
            return "";
        }
        String value = identifier.trim();
        if ((value.startsWith("`") && value.endsWith("`")) || (value.startsWith("\"") && value.endsWith("\""))) {
            value = value.substring(1, value.length() - 1);
        }
        return value;
    }

    private record IndexPart(String column, boolean safeColumn) {
    }

    private record ColumnKey(String table, String column) {
        static ColumnKey of(TableId table, String column) {
            return new ColumnKey(table.normalizedName(), column.toLowerCase(Locale.ROOT));
        }
    }

    /**
     * Mutable state for one DDL parse.
     *
     * <p>parseText() creates one DdlState and passes it through every statement
     * parser. FK candidates are collected immediately; source index and target
     * uniqueness facts are collected separately so they can be attached after
     * the whole file has been scanned.
     */
    private static final class DdlState {
        private final String source;
        private final List<RelationshipCandidate> candidates = new ArrayList<>();
        private final Set<ColumnKey> sourceIndexes = new LinkedHashSet<>();
        private final Set<ColumnKey> targetUnique = new LinkedHashSet<>();

        DdlState(String source) {
            this.source = source;
        }

        String source() {
            return source;
        }

        List<RelationshipCandidate> candidates() {
            return candidates;
        }

        void addSourceIndex(TableId table, String column, String kind) {
            sourceIndexes.add(ColumnKey.of(table, column));
        }

        void addTargetUnique(TableId table, String column, String kind) {
            targetUnique.add(ColumnKey.of(table, column));
        }

        /**
         * Adds SOURCE_INDEX and TARGET_UNIQUE evidence to matching FK candidates.
         *
         * <p>Called once at the end of parseText(). The loop walks every FK
         * candidate and checks whether its source column or target column was
         * seen in an eligible index/unique constraint somewhere in the DDL.
         */
        void enhanceCandidatesWithIndexes() {
            for (RelationshipCandidate candidate : candidates) {
                ColumnKey sourceKey = ColumnKey.of(candidate.source().table(), candidate.source().column().columnName());
                ColumnKey targetKey = ColumnKey.of(candidate.target().table(), candidate.target().column().columnName());
                if (sourceIndexes.contains(sourceKey)) {
                    candidate.evidence().add(Evidence.of(EvidenceType.SOURCE_INDEX, DefaultEvidenceScores.SOURCE_INDEX,
                            EvidenceSourceType.DDL_FILE, source, "DDL source-side index"));
                }
                if (targetUnique.contains(targetKey)) {
                    candidate.evidence().add(Evidence.of(EvidenceType.TARGET_UNIQUE, DefaultEvidenceScores.TARGET_UNIQUE,
                            EvidenceSourceType.DDL_FILE, source, "DDL target-side primary/unique key"));
                }
            }
        }
    }
}
