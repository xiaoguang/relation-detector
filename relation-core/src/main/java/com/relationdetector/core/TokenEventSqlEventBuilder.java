package com.relationdetector.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.antlr.v4.runtime.Token;

import com.relationdetector.api.Enums.StructuredParseEventType;
import com.relationdetector.api.SqlStatementRecord;
import com.relationdetector.api.StructuredSqlEvent;
import com.relationdetector.core.antlr.RelationSqlLexer;

/**
 * Token/Event structural event builder.
 *
 * <p>This class is intentionally separate from {@link TokenEventSqlTokenSupport}.
 * Token/Event is the production SQL structure source for MySQL/PostgreSQL.
 * The separation gives dialect token/span rules a clear home before
 * relationship and data-lineage extraction:
 *
 * <p>New SQL-structure compatibility must be added to dialect-specific Token/Event
 * builders first. The common builder should stay limited to cross-dialect
 * event concepts such as table references and column equality predicates.
 */
public class TokenEventSqlEventBuilder extends TokenEventSqlTokenSupport {
    public TokenEventSqlEventBuilder() {
        this("TokenEventSqlEventBuilder",
                Set.of(RelationSqlLexer.IDENTIFIER, RelationSqlLexer.QUOTED_IDENTIFIER));
    }

    protected TokenEventSqlEventBuilder(String name, Set<Integer> identifierTokenTypes) {
        super(name, identifierTokenTypes);
    }

    @Override
    public List<StructuredSqlEvent> extractEvents(SqlStatementRecord statement, List<Token> tokens) {
        List<StructuredSqlEvent> events = new ArrayList<>(super.extractEvents(statement, tokens));
        events.addAll(copyPredicateEqualityEvents(events, tokens));
        events.addAll(extractPredicateEqualityEvents(statement, tokens, events));
        events.addAll(extractRowsetReferences(statement, tokens));
        events.addAll(extractScopeEvents(statement, tokens));
        events.addAll(extractLineageEvents(statement, tokens));
        events.addAll(extractJoinUsingEvents(statement, tokens));
        events.addAll(extractExistsEvents(statement, tokens));
        events.addAll(extractInSubqueryEvents(statement, tokens));
        return events;
    }

    private List<StructuredSqlEvent> copyPredicateEqualityEvents(
            List<StructuredSqlEvent> sourceEvents,
            List<Token> tokens
    ) {
        List<StructuredSqlEvent> events = new ArrayList<>();
        Set<String> assignmentKeys = assignmentEqualityKeys(tokens);
        Set<String> copied = new java.util.HashSet<>();
        for (StructuredSqlEvent event : sourceEvents) {
            if (event.type() != StructuredParseEventType.COLUMN_EQUALITY) {
                continue;
            }
            String key = equalityKey(event);
            if (assignmentKeys.contains(key) || !copied.add(key)) {
                continue;
            }
            events.add(copyAs(event, StructuredParseEventType.PREDICATE_EQUALITY));
        }
        return events;
    }

    private Set<String> assignmentEqualityKeys(List<Token> tokens) {
        Set<String> keys = new java.util.HashSet<>();
        for (int index = 0; index < tokens.size(); index++) {
            if (!tokens.get(index).getText().equals("=") || !isSetAssignmentEquality(tokens, index)) {
                continue;
            }
            ColumnRead left = readColumnBackwardsV2(tokens, index - 1);
            ColumnRead right = readColumnForwardV2(tokens, index + 1);
            if (left == null || right == null) {
                continue;
            }
            keys.add(equalityKey(left, right));
        }
        return keys;
    }

    private List<StructuredSqlEvent> extractPredicateEqualityEvents(
            SqlStatementRecord statement,
            List<Token> tokens,
            List<StructuredSqlEvent> existingEvents
    ) {
        List<StructuredSqlEvent> events = new ArrayList<>();
        Set<String> existing = new java.util.HashSet<>();
        for (StructuredSqlEvent event : existingEvents) {
            if (event.type() == StructuredParseEventType.PREDICATE_EQUALITY) {
                existing.add(equalityKey(event));
            }
        }
        for (int index = 0; index < tokens.size(); index++) {
            if (!tokens.get(index).getText().equals("=")) {
                continue;
            }
            boolean setAssignment = isSetAssignmentEquality(tokens, index);
            ColumnRead left = readColumnBackwardsV2(tokens, index - 1);
            ColumnRead right = readColumnForwardV2(tokens, index + 1);
            if (left == null && right != null) {
                left = readColumnBackwardsWithDefault(tokens, index - 1, defaultOuterRowsetBefore(tokens, index));
            }
            if (left == null || right == null) {
                continue;
            }
            if (setAssignment && !isLikelyForeignKeyAssignment(left, right)) {
                continue;
            }
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("leftAlias", left.qualifier);
            attributes.put("leftColumn", left.column);
            attributes.put("rightAlias", right.qualifier);
            attributes.put("rightColumn", right.column);
            attributes.put("joinKind", setAssignment ? "UPDATE_SET" : equalityJoinKind(tokens, index));
            attributes.put("tokenEventNative", true);
            StructuredSqlEvent event = new StructuredSqlEvent(StructuredParseEventType.PREDICATE_EQUALITY,
                    statement.sourceName(), line(statement, tokens.get(index)), attributes);
            if (existing.add(equalityKey(event))) {
                events.add(event);
            }
        }
        return events;
    }

    private boolean isLikelyForeignKeyAssignment(ColumnRead target, ColumnRead source) {
        String targetColumn = target.column.toLowerCase(Locale.ROOT);
        String sourceColumn = source.column.toLowerCase(Locale.ROOT);
        return sourceColumn.equals("id") && targetColumn.endsWith("_id");
    }

    private String equalityKey(StructuredSqlEvent event) {
        return String.join("|",
                text(event, "leftAlias"),
                text(event, "leftColumn"),
                text(event, "rightAlias"),
                text(event, "rightColumn"));
    }

    private String equalityKey(ColumnRead left, ColumnRead right) {
        return String.join("|", left.qualifier, left.column, right.qualifier, right.column);
    }

    private String text(StructuredSqlEvent event, String key) {
        Object value = event.attributes().get(key);
        return value == null ? "" : value.toString();
    }

    private boolean isSetAssignmentEquality(List<Token> tokens, int equalsIndex) {
        int depth = 0;
        for (int index = equalsIndex - 1; index >= 0; index--) {
            String text = tokens.get(index).getText();
            if (text.equals(")")) {
                depth++;
            } else if (text.equals("(")) {
                depth--;
            }
            if (depth != 0) {
                continue;
            }
            String lower = lower(tokens.get(index));
            if (Set.of("on", "where", "and", "or", "when", "having").contains(lower)) {
                return false;
            }
            if (lower.equals("set")) {
                return true;
            }
            if (Set.of("select", "from", "join", "using", "values").contains(lower)) {
                return false;
            }
        }
        return false;
    }

    private String equalityJoinKind(List<Token> tokens, int equalsIndex) {
        int depth = 0;
        for (int index = equalsIndex - 1; index >= 0; index--) {
            String text = tokens.get(index).getText();
            if (text.equals(")")) {
                depth++;
            } else if (text.equals("(")) {
                depth--;
            }
            if (depth != 0) {
                continue;
            }
            String lower = lower(tokens.get(index));
            if (lower.equals("on")) {
                return "JOIN_ON";
            }
            if (lower.equals("where") || lower.equals("and") || lower.equals("or") || lower.equals("having")) {
                return "WHERE_OR_UNKNOWN";
            }
        }
        return "WHERE_OR_UNKNOWN";
    }

    private StructuredSqlEvent copyAs(StructuredSqlEvent event, StructuredParseEventType type) {
        Map<String, Object> attributes = new LinkedHashMap<>(event.attributes());
        attributes.put("tokenEventNative", true);
        return new StructuredSqlEvent(type, event.sourceName(), event.line(), attributes);
    }

    private List<StructuredSqlEvent> extractRowsetReferences(SqlStatementRecord statement, List<Token> tokens) {
        List<StructuredSqlEvent> events = new ArrayList<>();
        for (int index = 0; index < tokens.size(); index++) {
            String token = lower(tokens.get(index));
            if (isRowsetKeyword(tokens, index, token)) {
                addRowsetReference(statement, tokens, events, index, token.toUpperCase(Locale.ROOT), index + 1);
            } else if (tokens.get(index).getText().equals(",") && isLikelyRowsetComma(tokens, index)) {
                addRowsetReference(statement, tokens, events, index, "COMMA", index + 1);
            }
        }
        return events;
    }

    private boolean isRowsetKeyword(List<Token> tokens, int index, String token) {
        if (token.equals("join") && isDialectNonRowsetJoin(tokens, index)) {
            return false;
        }
        if (token.equals("from") || token.equals("join") || token.equals("straight_join") || token.equals("update")
                || token.equals("into")) {
            return true;
        }
        return token.equals("using") && index > 0
                && (hasKeywordBefore(tokens, index, "merge", 32) || hasKeywordBefore(tokens, index, "delete", 32));
    }

    protected boolean isDialectNonRowsetJoin(List<Token> tokens, int index) {
        return false;
    }

    private void addRowsetReference(
            SqlStatementRecord statement,
            List<Token> tokens,
            List<StructuredSqlEvent> events,
            int keywordIndex,
            String keyword,
            int tableIndex
    ) {
        int actualTableIndex = skipRowsetModifiers(tokens, tableIndex);
        if (actualTableIndex < tokens.size() && tokens.get(actualTableIndex).getText().equals("(")) {
            if (hasTopLevelSelectInside(tokens, actualTableIndex)) {
                addDerivedRowsetIgnore(statement, tokens, events, keywordIndex, actualTableIndex);
            } else {
                addRowsetReference(statement, tokens, events, keywordIndex, keyword, actualTableIndex + 1);
            }
            return;
        }
        IdentifierRead table = readQualifiedIdentifierV2(tokens, actualTableIndex);
        if (table == null || isCommonNonTableKeyword(table.qualifiedName)) {
            return;
        }
        if (isRowsFromRowset(tokens, table)) {
            addIgnoredRowset(statement, events, tokens.get(keywordIndex), table.qualifiedName, "ROWS_FROM");
            return;
        }
        if (table.nextIndex < tokens.size() && tokens.get(table.nextIndex).getText().equals("(")) {
            addIgnoredRowset(statement, events, tokens.get(keywordIndex), table.qualifiedName, "FUNCTION_ROWSET");
            return;
        }
        int aliasIndex = skipDialectTableDecorators(tokens, table.nextIndex);
        if (aliasIndex < tokens.size() && lower(tokens.get(aliasIndex)).equals("as")) {
            aliasIndex++;
        }
        String alias = "";
        if (aliasIndex < tokens.size()) {
            String aliasCandidate = cleanIdentifier(tokens.get(aliasIndex).getText());
            if (isIdentifierText(aliasCandidate) && !isCommonNonTableKeyword(aliasCandidate)) {
                alias = aliasCandidate;
            }
        }
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("keyword", keyword);
        attributes.put("qualifiedTable", table.qualifiedName);
        attributes.put("table", baseName(table.qualifiedName));
        attributes.put("tokenEventNative", true);
        if (!alias.isBlank()) {
            attributes.put("alias", alias);
        }
        events.add(new StructuredSqlEvent(StructuredParseEventType.ROWSET_REFERENCE,
                statement.sourceName(), line(statement, tokens.get(keywordIndex)), attributes));
    }

    private List<StructuredSqlEvent> extractScopeEvents(SqlStatementRecord statement, List<Token> tokens) {
        List<StructuredSqlEvent> events = new ArrayList<>();
        events.addAll(extractCteDeclarations(statement, tokens));
        events.addAll(extractLocalTempTables(statement, tokens));
        events.addAll(extractTriggerScope(statement, tokens));
        return events;
    }

    protected int skipDialectTableDecorators(List<Token> tokens, int index) {
        return index;
    }

    private boolean isRowsFromRowset(List<Token> tokens, IdentifierRead table) {
        return table.qualifiedName.equalsIgnoreCase("rows")
                && table.nextIndex < tokens.size()
                && lower(tokens.get(table.nextIndex)).equals("from");
    }

    private List<StructuredSqlEvent> extractLineageEvents(SqlStatementRecord statement, List<Token> tokens) {
        List<StructuredSqlEvent> events = new ArrayList<>();
        events.addAll(extractProjectionEvents(statement, tokens));
        events.addAll(extractUpdateLineageEvents(statement, tokens));
        events.addAll(extractInsertSelectLineageEvents(statement, tokens));
        events.addAll(extractMergeLineageEvents(statement, tokens));
        return events;
    }

    private List<StructuredSqlEvent> extractProjectionEvents(SqlStatementRecord statement, List<Token> tokens) {
        List<StructuredSqlEvent> events = new ArrayList<>();
        addDerivedProjectionEvents(statement, tokens, events);
        addCteProjectionEvents(statement, tokens, events);
        return events;
    }

    private void addDerivedProjectionEvents(
            SqlStatementRecord statement,
            List<Token> tokens,
            List<StructuredSqlEvent> events
    ) {
        for (int index = 0; index < tokens.size(); index++) {
            if (!tokens.get(index).getText().equals("(")) {
                continue;
            }
            int close = matchingParen(tokens, index);
            if (close < 0 || close + 1 >= tokens.size()) {
                continue;
            }
            int contentStart = index + 1;
            int contentEnd = close;
            while (contentStart < contentEnd && tokens.get(contentStart).getText().equals("(")
                    && matchingParen(tokens, contentStart) == contentEnd - 1) {
                contentStart++;
                contentEnd--;
            }
            int select = firstTopLevelWord(tokens, "select", contentStart, contentEnd);
            int from = firstTopLevelWord(tokens, "from", select < 0 ? contentStart : select + 1, contentEnd);
            if (select < 0) {
                continue;
            }
            int aliasIndex = close + 1;
            if (lower(tokens.get(aliasIndex)).equals("as")) {
                aliasIndex++;
            }
            if (aliasIndex >= tokens.size()) {
                continue;
            }
            String alias = cleanIdentifier(tokens.get(aliasIndex).getText());
            if (!isIdentifierText(alias) || isCommonNonTableKeyword(alias)) {
                continue;
            }
            addProjectionEventsForSelect(statement, tokens, events, alias, select, from < 0 ? contentEnd : from);
        }
    }

    private void addCteProjectionEvents(
            SqlStatementRecord statement,
            List<Token> tokens,
            List<StructuredSqlEvent> events
    ) {
        for (int index = 0; index < tokens.size(); index++) {
            if (!lower(tokens.get(index)).equals("with")) {
                continue;
            }
            int cursor = index + 1;
            if (cursor < tokens.size() && lower(tokens.get(cursor)).equals("recursive")) {
                cursor++;
            }
            while (cursor < tokens.size()) {
                IdentifierRead name = readQualifiedIdentifierV2(tokens, cursor);
                if (name == null) {
                    break;
                }
                int afterName = name.nextIndex;
                if (afterName < tokens.size() && tokens.get(afterName).getText().equals("(")) {
                    int closeColumns = matchingParen(tokens, afterName);
                    afterName = closeColumns < 0 ? afterName : closeColumns + 1;
                }
                if (afterName >= tokens.size() || !lower(tokens.get(afterName)).equals("as")) {
                    break;
                }
                int open = afterName + 1;
                if (open < tokens.size()
                        && (lower(tokens.get(open)).equals("materialized") || lower(tokens.get(open)).equals("not"))) {
                    open += lower(tokens.get(open)).equals("not") ? 2 : 1;
                }
                if (open >= tokens.size() || !tokens.get(open).getText().equals("(")) {
                    break;
                }
                int close = matchingParen(tokens, open);
                if (close < 0) {
                    break;
                }
                int select = firstTopLevelWord(tokens, "select", open + 1, close);
                int from = firstTopLevelWord(tokens, "from", select < 0 ? open + 1 : select + 1, close);
                if (select >= 0 && from >= 0) {
                    addProjectionEventsForSelect(statement, tokens, events, name.qualifiedName, select, from);
                }
                cursor = close + 1;
                if (cursor < tokens.size() && tokens.get(cursor).getText().equals(",")) {
                    cursor++;
                    continue;
                }
                break;
            }
        }
    }

    private void addProjectionEventsForSelect(
            SqlStatementRecord statement,
            List<Token> tokens,
            List<StructuredSqlEvent> events,
            String outputAlias,
            int select,
            int from
    ) {
        String defaultSourceQualifier = defaultProjectionSourceQualifier(tokens, from);
        for (Span projection : splitTopLevelSpans(tokens, select + 1, from, ",")) {
            List<ColumnRead> sources = readProjectionSourceColumns(tokens, projection.start, projection.end,
                    defaultSourceQualifier);
            if (sources.isEmpty()) {
                continue;
            }
            String outputColumn = projectionOutputColumn(tokens, projection, sources.get(0).column);
            String expression = tokenText(tokens, projection.start, projection.end);
            addProjectionItem(statement, tokens, events, projection.start, outputAlias, outputColumn,
                    sources, transformType(expression));
        }
    }

    private List<StructuredSqlEvent> extractUpdateLineageEvents(SqlStatementRecord statement, List<Token> tokens) {
        List<StructuredSqlEvent> events = new ArrayList<>();
        int update = firstTopLevelWord(tokens, "update", 0);
        int set = firstTopLevelWord(tokens, "set", update < 0 ? 0 : update);
        if (update < 0 || set < 0) {
            return events;
        }
        IdentifierRead target = readQualifiedIdentifierV2(tokens, update + 1);
        if (target != null) {
            addWriteTarget(statement, tokens, events, update, target.qualifiedName, aliasAfter(tokens, target.nextIndex), "UPDATE");
        }
        int end = firstTopLevelWord(tokens, List.of("where", "returning", "order", "limit"), set + 1);
        if (end < 0) {
            end = tokens.size();
        }
        for (Span assignment : splitTopLevelSpans(tokens, set + 1, end, ",")) {
            int equals = topLevelToken(tokens, assignment.start, assignment.end, "=");
            if (equals < 0) {
                continue;
            }
            ColumnRead targetColumn = readAssignmentTarget(tokens, assignment.start, equals);
            if (targetColumn == null) {
                continue;
            }
            addWriteExpressionEvents(statement, tokens, events, StructuredParseEventType.UPDATE_ASSIGNMENT,
                    assignment.start, targetColumn, "", equals + 1, assignment.end, "UPDATE_SET");
        }
        return events;
    }

    private List<StructuredSqlEvent> extractInsertSelectLineageEvents(SqlStatementRecord statement, List<Token> tokens) {
        List<StructuredSqlEvent> events = new ArrayList<>();
        int insert = firstTopLevelWord(tokens, "insert", 0);
        int into = firstTopLevelWord(tokens, "into", insert < 0 ? 0 : insert);
        if (insert < 0 || into < 0) {
            return events;
        }
        IdentifierRead target = readQualifiedIdentifierV2(tokens, into + 1);
        if (target == null || target.nextIndex >= tokens.size() || !tokens.get(target.nextIndex).getText().equals("(")) {
            return events;
        }
        addWriteTarget(statement, tokens, events, into, target.qualifiedName, "", "INSERT");
        int targetClose = matchingParen(tokens, target.nextIndex);
        int select = firstTopLevelWord(tokens, "select", targetClose < 0 ? target.nextIndex : targetClose + 1);
        int from = firstTopLevelWord(tokens, "from", select < 0 ? target.nextIndex : select);
        if (targetClose < 0 || select < 0 || from < 0) {
            return events;
        }
        List<String> targetColumns = identifierList(tokens, target.nextIndex + 1, targetClose);
        List<Span> projections = splitTopLevelSpans(tokens, select + 1, from, ",");
        for (int index = 0; index < Math.min(targetColumns.size(), projections.size()); index++) {
            Span projection = projections.get(index);
            ColumnRead source = firstColumnInSpan(tokens, projection.start, projection.end);
            if (source == null) {
                continue;
            }
            String transform = transformType(tokenText(tokens, projection.start, projection.end));
            addProjectionItem(statement, tokens, events, projection.start, "", targetColumns.get(index),
                    List.of(source), transform);
            Map<String, Object> attributes = lineageAttributes(
                    ColumnRead.target("", targetColumns.get(index)),
                    projection.start,
                    projection.end,
                    List.of(source),
                    transform,
                    "VALUE");
            attributes.put("targetTable", target.qualifiedName);
            attributes.put("mappingKind", "INSERT_SELECT");
            events.add(new StructuredSqlEvent(StructuredParseEventType.INSERT_SELECT_MAPPING,
                    statement.sourceName(), line(statement, tokens.get(projection.start)), attributes));
        }
        return events;
    }

    private List<StructuredSqlEvent> extractMergeLineageEvents(SqlStatementRecord statement, List<Token> tokens) {
        List<StructuredSqlEvent> events = new ArrayList<>();
        int merge = firstTopLevelWord(tokens, "merge", 0);
        int into = firstTopLevelWord(tokens, "into", merge < 0 ? 0 : merge);
        if (merge < 0 || into < 0) {
            return events;
        }
        IdentifierRead target = readQualifiedIdentifierV2(tokens, into + 1);
        if (target == null) {
            return events;
        }
        addWriteTarget(statement, tokens, events, into, target.qualifiedName, aliasAfter(tokens, target.nextIndex), "MERGE");
        int set = firstTopLevelWord(tokens, "set", into);
        if (set >= 0) {
            int end = firstTopLevelWord(tokens, List.of("when"), set + 1);
            if (end < 0) {
                end = tokens.size();
            }
            for (Span assignment : splitTopLevelSpans(tokens, set + 1, end, ",")) {
                int equals = topLevelToken(tokens, assignment.start, assignment.end, "=");
                if (equals < 0) {
                    continue;
                }
                ColumnRead targetColumn = readAssignmentTarget(tokens, assignment.start, equals);
                if (targetColumn == null) {
                    continue;
                }
                addWriteExpressionEvents(statement, tokens, events, StructuredParseEventType.MERGE_WRITE_MAPPING,
                        assignment.start, targetColumn, target.qualifiedName, equals + 1, assignment.end, "MERGE_UPDATE");
            }
        }
        int insert = firstTopLevelWord(tokens, "insert", into);
        int values = firstTopLevelWord(tokens, "values", insert < 0 ? into : insert);
        if (insert >= 0 && values >= 0 && insert + 1 < tokens.size() && tokens.get(insert + 1).getText().equals("(")
                && values + 1 < tokens.size() && tokens.get(values + 1).getText().equals("(")) {
            int insertClose = matchingParen(tokens, insert + 1);
            int valuesClose = matchingParen(tokens, values + 1);
            if (insertClose > insert && valuesClose > values) {
                List<String> targetColumns = identifierList(tokens, insert + 2, insertClose);
                List<Span> expressions = splitTopLevelSpans(tokens, values + 2, valuesClose, ",");
                for (int index = 0; index < Math.min(targetColumns.size(), expressions.size()); index++) {
                    Span expression = expressions.get(index);
                    ColumnRead source = firstColumnInSpan(tokens, expression.start, expression.end);
                    if (source == null) {
                        continue;
                    }
                    Map<String, Object> attributes = lineageAttributes(
                            ColumnRead.target("", targetColumns.get(index)),
                            expression.start,
                            expression.end,
                            List.of(source),
                            "DIRECT",
                            "VALUE");
                    attributes.put("targetTable", target.qualifiedName);
                    attributes.put("mappingKind", "MERGE_INSERT");
                    events.add(new StructuredSqlEvent(StructuredParseEventType.MERGE_WRITE_MAPPING,
                            statement.sourceName(), line(statement, tokens.get(expression.start)), attributes));
                }
            }
        }
        return events;
    }

    private void addWriteTarget(
            SqlStatementRecord statement,
            List<Token> tokens,
            List<StructuredSqlEvent> events,
            int tokenIndex,
            String table,
            String alias,
            String writeKind
    ) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("qualifiedTable", table);
        attributes.put("table", baseName(table));
        attributes.put("writeKind", writeKind);
        attributes.put("tokenEventNative", true);
        if (!alias.isBlank()) {
            attributes.put("alias", alias);
        }
        events.add(new StructuredSqlEvent(StructuredParseEventType.WRITE_TARGET,
                statement.sourceName(), line(statement, tokens.get(tokenIndex)), attributes));
    }

    private void addWriteExpressionEvents(
            SqlStatementRecord statement,
            List<Token> tokens,
            List<StructuredSqlEvent> events,
            StructuredParseEventType type,
            int eventTokenIndex,
            ColumnRead target,
            String targetTable,
            int expressionStart,
            int expressionEnd,
            String mappingKind
    ) {
        List<ColumnRead> sources = readColumnList(tokens, expressionStart, expressionEnd);
        if (sources.isEmpty()) {
            return;
        }
        String expression = tokenText(tokens, expressionStart, expressionEnd);
        String transform = transformType(expression);
        String flowKind = expression.toLowerCase(Locale.ROOT).contains("case") ? "CONTROL" : "VALUE";
        Map<String, Object> attributes = lineageAttributes(target, expressionStart, expressionEnd, sources, transform, flowKind);
        if (!targetTable.isBlank()) {
            attributes.put("targetTable", targetTable);
        }
        attributes.put("mappingKind", mappingKind);
        events.add(new StructuredSqlEvent(type, statement.sourceName(), line(statement, tokens.get(eventTokenIndex)), attributes));
        for (ColumnRead source : sources) {
            Map<String, Object> sourceAttributes = new LinkedHashMap<>();
            sourceAttributes.put("sourceAlias", source.qualifier);
            sourceAttributes.put("sourceColumn", source.column);
            sourceAttributes.put("targetAlias", target.qualifier);
            sourceAttributes.put("targetColumn", target.column);
            sourceAttributes.put("transformType", transform);
            sourceAttributes.put("flowKind", flowKind);
            sourceAttributes.put("tokenEventNative", true);
            events.add(new StructuredSqlEvent(StructuredParseEventType.EXPRESSION_SOURCE,
                    statement.sourceName(), line(statement, tokens.get(eventTokenIndex)), sourceAttributes));
        }
    }

    private void addProjectionItem(
            SqlStatementRecord statement,
            List<Token> tokens,
            List<StructuredSqlEvent> events,
            int tokenIndex,
            String outputAlias,
            String outputColumn,
            List<ColumnRead> sources,
            String transform
    ) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("outputAlias", outputAlias);
        attributes.put("outputColumn", outputColumn);
        attributes.put("sourceAliases", sources.stream().map(ColumnRead::qualifier).toList());
        attributes.put("sourceColumns", sources.stream().map(ColumnRead::column).toList());
        attributes.put("transformType", transform);
        attributes.put("tokenEventNative", true);
        events.add(new StructuredSqlEvent(StructuredParseEventType.PROJECTION_ITEM,
                statement.sourceName(), line(statement, tokens.get(tokenIndex)), attributes));
    }

    private Map<String, Object> lineageAttributes(
            ColumnRead target,
            int expressionStart,
            int expressionEnd,
            List<ColumnRead> sources,
            String transform,
            String flowKind
    ) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("targetAlias", target.qualifier);
        attributes.put("targetColumn", target.column);
        attributes.put("sourceAliases", sources.stream().map(ColumnRead::qualifier).toList());
        attributes.put("sourceColumns", sources.stream().map(ColumnRead::column).toList());
        attributes.put("expressionStartToken", expressionStart);
        attributes.put("expressionEndToken", expressionEnd);
        attributes.put("transformType", transform);
        attributes.put("flowKind", flowKind);
        attributes.put("tokenEventNative", true);
        return attributes;
    }

    private List<StructuredSqlEvent> extractCteDeclarations(SqlStatementRecord statement, List<Token> tokens) {
        List<StructuredSqlEvent> events = new ArrayList<>();
        for (int index = 0; index < tokens.size(); index++) {
            if (!lower(tokens.get(index)).equals("with")) {
                continue;
            }
            int cursor = index + 1;
            if (cursor < tokens.size() && lower(tokens.get(cursor)).equals("recursive")) {
                cursor++;
            }
            while (cursor < tokens.size()) {
                IdentifierRead name = readQualifiedIdentifierV2(tokens, cursor);
                if (name == null || name.nextIndex >= tokens.size()) {
                    break;
                }
                int afterName = name.nextIndex;
                if (afterName < tokens.size() && tokens.get(afterName).getText().equals("(")) {
                    int closeColumns = matchingParen(tokens, afterName);
                    afterName = closeColumns < 0 ? afterName : closeColumns + 1;
                }
                if (afterName < tokens.size() && lower(tokens.get(afterName)).equals("as")) {
                    int maybeParen = afterName + 1;
                    if (maybeParen < tokens.size()
                            && (lower(tokens.get(maybeParen)).equals("materialized")
                            || lower(tokens.get(maybeParen)).equals("not"))) {
                        maybeParen += lower(tokens.get(maybeParen)).equals("not") ? 2 : 1;
                    }
                    if (maybeParen < tokens.size() && tokens.get(maybeParen).getText().equals("(")) {
                        Map<String, Object> attributes = new LinkedHashMap<>();
                        attributes.put("name", name.qualifiedName);
                        attributes.put("tokenEventNative", true);
                        events.add(new StructuredSqlEvent(StructuredParseEventType.CTE_DECLARATION,
                                statement.sourceName(), line(statement, tokens.get(cursor)), attributes));
                        addIgnoredRowset(statement, events, tokens.get(cursor), name.qualifiedName, "CTE");
                        int close = matchingParen(tokens, maybeParen);
                        cursor = close < 0 ? maybeParen + 1 : close + 1;
                        if (cursor < tokens.size() && tokens.get(cursor).getText().equals(",")) {
                            cursor++;
                            continue;
                        }
                    }
                }
                break;
            }
        }
        return events;
    }

    private List<StructuredSqlEvent> extractLocalTempTables(SqlStatementRecord statement, List<Token> tokens) {
        List<StructuredSqlEvent> events = new ArrayList<>();
        for (int index = 0; index + 2 < tokens.size(); index++) {
            if (!lower(tokens.get(index)).equals("create")) {
                continue;
            }
            int cursor = index + 1;
            boolean temporary = false;
            if (cursor < tokens.size()
                    && (lower(tokens.get(cursor)).equals("temporary") || lower(tokens.get(cursor)).equals("temp"))) {
                temporary = true;
                cursor++;
            }
            if (!temporary || cursor >= tokens.size() || !lower(tokens.get(cursor)).equals("table")) {
                continue;
            }
            IdentifierRead table = readQualifiedIdentifierV2(tokens, cursor + 1);
            if (table == null) {
                continue;
            }
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("qualifiedTable", table.qualifiedName);
            attributes.put("table", baseName(table.qualifiedName));
            attributes.put("tokenEventNative", true);
            events.add(new StructuredSqlEvent(StructuredParseEventType.LOCAL_TEMP_TABLE_DECLARATION,
                    statement.sourceName(), line(statement, tokens.get(index)), attributes));
            addIgnoredRowset(statement, events, tokens.get(index), table.qualifiedName, "LOCAL_TEMP_TABLE");
        }
        return events;
    }

    private List<StructuredSqlEvent> extractTriggerScope(SqlStatementRecord statement, List<Token> tokens) {
        List<StructuredSqlEvent> events = new ArrayList<>();
        for (int index = 0; index < tokens.size(); index++) {
            if (!lower(tokens.get(index)).equals("trigger")) {
                continue;
            }
            for (int cursor = index + 1; cursor < tokens.size(); cursor++) {
                if (!lower(tokens.get(cursor)).equals("on")) {
                    continue;
                }
                IdentifierRead target = readQualifiedIdentifierV2(tokens, cursor + 1);
                if (target == null) {
                    continue;
                }
                Map<String, Object> attributes = new LinkedHashMap<>();
                attributes.put("qualifiedTable", target.qualifiedName);
                attributes.put("table", baseName(target.qualifiedName));
                attributes.put("tokenEventNative", true);
                events.add(new StructuredSqlEvent(StructuredParseEventType.TRIGGER_TARGET_TABLE,
                        statement.sourceName(), line(statement, tokens.get(cursor)), attributes));
                addTriggerPseudoRowset(statement, events, tokens.get(cursor), "NEW", target.qualifiedName);
                addTriggerPseudoRowset(statement, events, tokens.get(cursor), "OLD", target.qualifiedName);
                break;
            }
        }
        return events;
    }

    private void addDerivedRowsetIgnore(
            SqlStatementRecord statement,
            List<Token> tokens,
            List<StructuredSqlEvent> events,
            int keywordIndex,
            int openParenIndex
    ) {
        int close = matchingParen(tokens, openParenIndex);
        if (close < 0 || close + 1 >= tokens.size()) {
            return;
        }
        int aliasIndex = close + 1;
        if (lower(tokens.get(aliasIndex)).equals("as")) {
            aliasIndex++;
        }
        if (aliasIndex >= tokens.size()) {
            return;
        }
        String alias = cleanIdentifier(tokens.get(aliasIndex).getText());
        if (isIdentifierText(alias) && !isCommonNonTableKeyword(alias)) {
            addIgnoredRowset(statement, events, tokens.get(keywordIndex), alias, "DERIVED_TABLE");
        }
    }

    private void addIgnoredRowset(
            SqlStatementRecord statement,
            List<StructuredSqlEvent> events,
            Token token,
            String name,
            String reason
    ) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("name", name);
        attributes.put("reason", reason);
        attributes.put("tokenEventNative", true);
        events.add(new StructuredSqlEvent(StructuredParseEventType.IGNORED_ROWSET,
                statement.sourceName(), line(statement, token), attributes));
    }

    private void addTriggerPseudoRowset(
            SqlStatementRecord statement,
            List<StructuredSqlEvent> events,
            Token token,
            String pseudoName,
            String targetTable
    ) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("name", pseudoName);
        attributes.put("targetTable", targetTable);
        attributes.put("tokenEventNative", true);
        events.add(new StructuredSqlEvent(StructuredParseEventType.TRIGGER_PSEUDO_ROWSET,
                statement.sourceName(), line(statement, token), attributes));
    }

    private List<StructuredSqlEvent> extractJoinUsingEvents(SqlStatementRecord statement, List<Token> tokens) {
        List<StructuredSqlEvent> events = new ArrayList<>();
        for (int index = 0; index < tokens.size(); index++) {
            if (!lower(tokens.get(index)).equals("using") || !hasJoinBefore(tokens, index)) {
                continue;
            }
            if (index + 1 >= tokens.size() || !tokens.get(index + 1).getText().equals("(")) {
                continue;
            }
            int closeParen = matchingParen(tokens, index + 1);
            if (closeParen < 0 || !shouldExtractJoinUsingEvent(tokens, index, closeParen)) {
                continue;
            }
            List<String> columns = new ArrayList<>();
            for (int cursor = index + 2; cursor < closeParen; cursor++) {
                String text = cleanIdentifier(tokens.get(cursor).getText());
                if (isIdentifierText(text)) {
                    columns.add(text);
                }
            }
            if (columns.isEmpty()) {
                continue;
            }
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("usingColumns", columns);
            String rightAlias = rowsetAliasAtKeyword(tokens, joinKeywordBeforeUsing(tokens, index));
            String leftAlias = previousRowsetAliasBefore(tokens, joinKeywordBeforeUsing(tokens, index));
            if (!leftAlias.isBlank() && !rightAlias.isBlank()) {
                attributes.put("leftAlias", leftAlias);
                attributes.put("rightAlias", rightAlias);
            }
            attributes.put("tokenEventNative", true);
            events.add(new StructuredSqlEvent(StructuredParseEventType.JOIN_USING_COLUMNS,
                    statement.sourceName(), line(statement, tokens.get(index)), attributes));
        }
        return events;
    }

    protected boolean shouldExtractJoinUsingEvent(List<Token> tokens, int usingIndex, int closeParenIndex) {
        return true;
    }

    private List<StructuredSqlEvent> extractExistsEvents(SqlStatementRecord statement, List<Token> tokens) {
        List<StructuredSqlEvent> events = new ArrayList<>();
        for (int index = 0; index < tokens.size(); index++) {
            if (!lower(tokens.get(index)).equals("exists")) {
                continue;
            }
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("tokenEventNative", true);
            int openParen = index + 1;
            int closeParen = matchingParen(tokens, openParen);
            if (closeParen > openParen) {
                ColumnEquality equality = firstColumnEquality(tokens, openParen + 1, closeParen);
                if (equality != null) {
                    attributes.put("leftAlias", equality.left.qualifier);
                    attributes.put("leftColumn", equality.left.column);
                    attributes.put("rightAlias", equality.right.qualifier);
                    attributes.put("rightColumn", equality.right.column);
                }
            }
            events.add(new StructuredSqlEvent(StructuredParseEventType.EXISTS_PREDICATE,
                    statement.sourceName(), line(statement, tokens.get(index)), attributes));
        }
        return events;
    }

    private List<StructuredSqlEvent> extractInSubqueryEvents(SqlStatementRecord statement, List<Token> tokens) {
        List<StructuredSqlEvent> events = new ArrayList<>();
        for (int index = 0; index < tokens.size(); index++) {
            if (!lower(tokens.get(index)).equals("in") || !hasSelectInsideFollowingParen(tokens, index)) {
                continue;
            }
            if (isNestedInsideInSubquery(tokens, index)) {
                continue;
            }
            int operandIndex = lower(tokens.get(index - 1)).equals("not") ? index - 2 : index - 1;
            StructuredParseEventType type = operandIndex >= 0 && tokens.get(operandIndex).getText().equals(")")
                    ? StructuredParseEventType.TUPLE_IN_SUBQUERY_PREDICATE
                    : StructuredParseEventType.IN_SUBQUERY_PREDICATE;
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("tokenEventNative", true);
            int openParen = index + 1;
            int closeParen = matchingParen(tokens, openParen);
            if (type == StructuredParseEventType.IN_SUBQUERY_PREDICATE && closeParen > openParen) {
                ColumnRead outer = readColumnBackwardsWithDefault(
                        tokens, operandIndex, defaultOuterRowsetBefore(tokens, index));
                SubqueryColumnRead inner = readSingleColumnSubquery(tokens, openParen + 1, closeParen);
                if (outer != null && inner != null) {
                    attributes.put("outerAlias", outer.qualifier);
                    attributes.put("outerColumn", outer.column);
                    String outerTable = outer.qualifier.isBlank() ? defaultOuterRowsetBefore(tokens, index) : "";
                    if (!outerTable.isBlank()) {
                        attributes.put("outerTable", outerTable);
                    }
                    attributes.put("innerAlias", inner.column.qualifier);
                    attributes.put("innerColumn", inner.column.column);
                    attributes.put("innerTable", inner.table);
                    attributes.put("innerTableAlias", inner.alias);
                }
            } else if (type == StructuredParseEventType.TUPLE_IN_SUBQUERY_PREDICATE && closeParen > openParen) {
                TupleRead outer = readTupleBeforeIn(tokens, index);
                TupleSubqueryRead inner = readTupleSubquery(tokens, openParen + 1, closeParen);
                if (outer != null && inner != null && outer.columns.size() == inner.columns.size()) {
                    attributes.put("outerAliases", outer.columns.stream().map(ColumnRead::qualifier).toList());
                    attributes.put("outerColumns", outer.columns.stream().map(ColumnRead::column).toList());
                    attributes.put("innerAliases", inner.columns.stream().map(ColumnRead::qualifier).toList());
                    attributes.put("innerColumns", inner.columns.stream().map(ColumnRead::column).toList());
                    attributes.put("innerTable", inner.table);
                    attributes.put("innerTableAlias", inner.alias);
                }
            }
            events.add(new StructuredSqlEvent(type, statement.sourceName(), line(statement, tokens.get(index)), attributes));
        }
        return events;
    }

    private boolean isNestedInsideInSubquery(List<Token> tokens, int inIndex) {
        for (int index = inIndex - 1; index >= 1; index--) {
            if (!tokens.get(index).getText().equals("(")) {
                continue;
            }
            int close = matchingParen(tokens, index);
            if (close <= inIndex || !lower(tokens.get(index - 1)).equals("in")) {
                continue;
            }
            return firstTopLevelWord(tokens, "select", index + 1, close) >= 0;
        }
        return false;
    }

    private ColumnEquality firstColumnEquality(List<Token> tokens, int startInclusive, int endExclusive) {
        for (int index = startInclusive; index < endExclusive; index++) {
            if (!tokens.get(index).getText().equals("=")) {
                continue;
            }
            ColumnRead left = readColumnBackwardsV2(tokens, index - 1);
            ColumnRead right = readColumnForwardV2(tokens, index + 1);
            if (left != null && right != null) {
                return new ColumnEquality(left, right);
            }
        }
        return null;
    }

    private SubqueryColumnRead readSingleColumnSubquery(List<Token> tokens, int startInclusive, int endExclusive) {
        int selectIndex = -1;
        int fromIndex = -1;
        int depth = 0;
        for (int index = startInclusive; index < endExclusive; index++) {
            String text = tokens.get(index).getText();
            if (text.equals("(")) {
                depth++;
            } else if (text.equals(")")) {
                depth--;
            } else if (depth == 0 && lower(tokens.get(index)).equals("select")) {
                selectIndex = index;
            } else if (depth == 0 && selectIndex >= 0 && lower(tokens.get(index)).equals("from")) {
                fromIndex = index;
                break;
            }
        }
        if (selectIndex < 0 || fromIndex < 0) {
            return null;
        }
        IdentifierRead table = readQualifiedIdentifierV2(tokens, fromIndex + 1);
        if (table == null) {
            return null;
        }
        String alias = "";
        int aliasIndex = table.nextIndex;
        if (aliasIndex < endExclusive && lower(tokens.get(aliasIndex)).equals("as")) {
            aliasIndex++;
        }
        if (aliasIndex < endExclusive && isIdentifierText(cleanIdentifier(tokens.get(aliasIndex).getText()))) {
            alias = cleanIdentifier(tokens.get(aliasIndex).getText());
        }
        String defaultQualifier = alias.isBlank() ? baseName(table.qualifiedName) : alias;
        ColumnRead selected = readSimpleSelectedColumn(tokens, selectIndex + 1, fromIndex, defaultQualifier);
        if (selected == null || table == null) {
            return null;
        }
        return new SubqueryColumnRead(selected, table.qualifiedName, alias);
    }

    private ColumnRead readSimpleSelectedColumn(
            List<Token> tokens,
            int startInclusive,
            int endExclusive,
            String defaultQualifier
    ) {
        int cursor = startInclusive;
        if (cursor < endExclusive && lower(tokens.get(cursor)).equals("distinct")) {
            cursor++;
        }
        while (cursor < endExclusive && tokens.get(cursor).getText().equals("(")) {
            int close = matchingParen(tokens, cursor);
            if (close != endExclusive - 1) {
                break;
            }
            cursor++;
            endExclusive--;
        }
        ColumnRead selected = readColumnForwardV2(tokens, cursor);
        int consumedUntil = selected == null ? cursor + 1 : cursor + 3;
        if (selected == null) {
            selected = readColumnForwardWithDefaultForProjection(tokens, cursor, defaultQualifier);
            consumedUntil = cursor + 1;
        }
        if (selected == null) {
            return null;
        }
        for (int index = consumedUntil; index < endExclusive; index++) {
            String token = lower(tokens.get(index));
            if (token.equals("as")) {
                break;
            }
            if (!tokens.get(index).getText().isBlank()) {
                return null;
            }
        }
        return selected;
    }

    private TupleRead readTupleBeforeIn(List<Token> tokens, int inIndex) {
        int closeParen = inIndex - 1;
        if (closeParen < 0 || !tokens.get(closeParen).getText().equals(")")) {
            return null;
        }
        int openParen = matchingOpenParen(tokens, closeParen);
        if (openParen < 0) {
            return null;
        }
        List<ColumnRead> columns = readColumnList(tokens, openParen + 1, closeParen);
        return columns.isEmpty() ? null : new TupleRead(columns);
    }

    private TupleSubqueryRead readTupleSubquery(List<Token> tokens, int startInclusive, int endExclusive) {
        int selectIndex = -1;
        int fromIndex = -1;
        int depth = 0;
        for (int index = startInclusive; index < endExclusive; index++) {
            String text = tokens.get(index).getText();
            if (text.equals("(")) {
                depth++;
            } else if (text.equals(")")) {
                depth--;
            } else if (depth == 0 && lower(tokens.get(index)).equals("select")) {
                selectIndex = index;
            } else if (depth == 0 && selectIndex >= 0 && lower(tokens.get(index)).equals("from")) {
                fromIndex = index;
                break;
            }
        }
        if (selectIndex < 0 || fromIndex < 0) {
            return null;
        }
        List<ColumnRead> columns = readColumnList(tokens, selectIndex + 1, fromIndex);
        IdentifierRead table = readQualifiedIdentifierV2(tokens, fromIndex + 1);
        if (columns.isEmpty() || table == null) {
            return null;
        }
        String alias = "";
        int aliasIndex = table.nextIndex;
        if (aliasIndex < endExclusive && lower(tokens.get(aliasIndex)).equals("as")) {
            aliasIndex++;
        }
        if (aliasIndex < endExclusive && isIdentifierText(cleanIdentifier(tokens.get(aliasIndex).getText()))) {
            alias = cleanIdentifier(tokens.get(aliasIndex).getText());
        }
        return new TupleSubqueryRead(columns, table.qualifiedName, alias);
    }

    private List<ColumnRead> readColumnList(List<Token> tokens, int startInclusive, int endExclusive) {
        List<ColumnRead> columns = new ArrayList<>();
        for (int index = startInclusive; index < endExclusive; index++) {
            ColumnRead column = readColumnForwardV2(tokens, index);
            if (column != null) {
                columns.add(column);
                index += 2;
            }
        }
        return columns;
    }

    private List<ColumnRead> readProjectionSourceColumns(
            List<Token> tokens,
            int startInclusive,
            int endExclusive,
            String defaultQualifier
    ) {
        List<ColumnRead> qualified = readColumnList(tokens, startInclusive, endExclusive);
        if (!qualified.isEmpty() || defaultQualifier.isBlank()) {
            return qualified;
        }
        List<ColumnRead> columns = new ArrayList<>();
        for (int index = startInclusive; index < endExclusive; index++) {
            String identifier = cleanIdentifier(tokens.get(index).getText());
            if (!isIdentifierText(identifier) || isCommonNonTableKeyword(identifier)) {
                continue;
            }
            String previous = index > startInclusive ? tokens.get(index - 1).getText() : "";
            String next = index + 1 < endExclusive ? tokens.get(index + 1).getText() : "";
            String previousWord = index > startInclusive ? lower(tokens.get(index - 1)) : "";
            if (previous.equals(".") || next.equals(".") || next.equals("(")) {
                continue;
            }
            if (previousWord.equals("as")) {
                continue;
            }
            columns.add(new ColumnRead(defaultQualifier, identifier));
        }
        return columns;
    }

    private ColumnRead readColumnForwardV2(List<Token> tokens, int index) {
        if (index + 2 >= tokens.size() || !tokens.get(index + 1).getText().equals(".")) {
            return null;
        }
        String qualifier = cleanIdentifier(tokens.get(index).getText());
        String column = cleanIdentifier(tokens.get(index + 2).getText());
        if (!isIdentifierText(qualifier) || !isIdentifierText(column)) {
            return null;
        }
        return new ColumnRead(qualifier, column);
    }

    private ColumnRead readColumnForwardWithDefaultForProjection(
            List<Token> tokens,
            int index,
            String defaultQualifier
    ) {
        if (defaultQualifier.isBlank() || index >= tokens.size()) {
            return null;
        }
        if (isQuotedStringLiteral(tokens.get(index).getText())) {
            return null;
        }
        String column = cleanIdentifier(tokens.get(index).getText());
        if (!isIdentifierText(column) || isCommonNonTableKeyword(column) || isLiteralLikeIdentifier(column)) {
            return null;
        }
        String previous = index - 1 >= 0 ? tokens.get(index - 1).getText() : "";
        String next = index + 1 < tokens.size() ? tokens.get(index + 1).getText() : "";
        if (previous.equals(".") || next.equals(".") || next.equals("(")) {
            return null;
        }
        return new ColumnRead(defaultQualifier, column);
    }

    private ColumnRead readColumnBackwardsV2(List<Token> tokens, int index) {
        if (index - 2 < 0 || !tokens.get(index - 1).getText().equals(".")) {
            return null;
        }
        String qualifier = cleanIdentifier(tokens.get(index - 2).getText());
        String column = cleanIdentifier(tokens.get(index).getText());
        if (!isIdentifierText(qualifier) || !isIdentifierText(column)) {
            return null;
        }
        return new ColumnRead(qualifier, column);
    }

    private ColumnRead readColumnBackwardsWithDefault(List<Token> tokens, int index, String defaultQualifier) {
        ColumnRead qualified = readColumnBackwardsV2(tokens, index);
        if (qualified != null || defaultQualifier.isBlank() || index < 0) {
            return qualified;
        }
        if (isQuotedStringLiteral(tokens.get(index).getText())) {
            return null;
        }
        String column = cleanIdentifier(tokens.get(index).getText());
        if (!isIdentifierText(column) || isCommonNonTableKeyword(column) || isLiteralLikeIdentifier(column)) {
            return null;
        }
        String previous = index - 1 >= 0 ? tokens.get(index - 1).getText() : "";
        if (previous.equals(".")) {
            return null;
        }
        return new ColumnRead(defaultQualifier, column);
    }

    private boolean isLiteralLikeIdentifier(String value) {
        if (value.isBlank()) {
            return true;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return Character.isDigit(value.charAt(0))
                || normalized.equals("true")
                || normalized.equals("false")
                || normalized.equals("null")
                || normalized.equals("current_date")
                || normalized.equals("current_timestamp");
    }

    private boolean isQuotedStringLiteral(String value) {
        return value.length() >= 2 && value.startsWith("'") && value.endsWith("'");
    }

    private IdentifierRead readQualifiedIdentifierV2(List<Token> tokens, int index) {
        if (index >= tokens.size()) {
            return null;
        }
        String first = cleanIdentifier(tokens.get(index).getText());
        if (!isIdentifierText(first)) {
            return null;
        }
        String qualified = first;
        int cursor = index + 1;
        while (cursor + 1 < tokens.size() && tokens.get(cursor).getText().equals(".")) {
            String next = cleanIdentifier(tokens.get(cursor + 1).getText());
            if (!isIdentifierText(next)) {
                break;
            }
            qualified = qualified + "." + next;
            cursor += 2;
        }
        return new IdentifierRead(qualified, cursor);
    }

    private int matchingParen(List<Token> tokens, int openParenIndex) {
        if (openParenIndex >= tokens.size() || !tokens.get(openParenIndex).getText().equals("(")) {
            return -1;
        }
        int depth = 0;
        for (int index = openParenIndex; index < tokens.size(); index++) {
            String text = tokens.get(index).getText();
            if (text.equals("(")) {
                depth++;
            } else if (text.equals(")")) {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return -1;
    }

    private int matchingOpenParen(List<Token> tokens, int closeParenIndex) {
        int depth = 0;
        for (int index = closeParenIndex; index >= 0; index--) {
            String text = tokens.get(index).getText();
            if (text.equals(")")) {
                depth++;
            } else if (text.equals("(")) {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return -1;
    }

    private boolean hasJoinBefore(List<Token> tokens, int index) {
        int start = Math.max(0, index - 12);
        for (int cursor = index - 1; cursor >= start; cursor--) {
            String token = lower(tokens.get(cursor));
            if (token.equals("join") || token.equals("straight_join")) {
                return true;
            }
            if (token.equals("where") || token.equals("on")) {
                return false;
            }
        }
        return false;
    }

    private int joinKeywordBeforeUsing(List<Token> tokens, int usingIndex) {
        for (int cursor = usingIndex - 1; cursor >= 0; cursor--) {
            String token = lower(tokens.get(cursor));
            if (token.equals("join") || token.equals("straight_join")) {
                return cursor;
            }
            if (token.equals("where") || token.equals("on") || token.equals("select")) {
                return -1;
            }
        }
        return -1;
    }

    private String previousRowsetAliasBefore(List<Token> tokens, int beforeIndex) {
        if (beforeIndex < 0) {
            return "";
        }
        for (int cursor = beforeIndex - 1; cursor >= 0; cursor--) {
            String token = lower(tokens.get(cursor));
            if (token.equals("from") || token.equals("join") || token.equals("straight_join")
                    || (tokens.get(cursor).getText().equals(",") && isLikelyRowsetComma(tokens, cursor))) {
                String alias = rowsetAliasAtKeyword(tokens, cursor);
                if (!alias.isBlank()) {
                    return alias;
                }
            }
            if (token.equals("where") || token.equals("select")) {
                return "";
            }
        }
        return "";
    }

    private String rowsetAliasAtKeyword(List<Token> tokens, int keywordIndex) {
        if (keywordIndex < 0) {
            return "";
        }
        int tableIndex = tokens.get(keywordIndex).getText().equals(",") ? keywordIndex + 1 : keywordIndex + 1;
        int actualTableIndex = skipRowsetModifiers(tokens, tableIndex);
        if (actualTableIndex >= tokens.size() || tokens.get(actualTableIndex).getText().equals("(")) {
            return "";
        }
        IdentifierRead table = readQualifiedIdentifierV2(tokens, actualTableIndex);
        if (table == null || isCommonNonTableKeyword(table.qualifiedName)) {
            return "";
        }
        int aliasIndex = skipDialectTableDecorators(tokens, table.nextIndex);
        String alias = aliasAfter(tokens, aliasIndex);
        return alias.isBlank() ? baseName(table.qualifiedName) : alias;
    }

    private String defaultOuterRowsetBefore(List<Token> tokens, int beforeIndex) {
        for (int cursor = beforeIndex - 1; cursor >= 0; cursor--) {
            String token = lower(tokens.get(cursor));
            if (token.equals("from") || token.equals("join") || token.equals("straight_join")
                    || (tokens.get(cursor).getText().equals(",") && isLikelyRowsetComma(tokens, cursor))) {
                String alias = rowsetAliasAtKeyword(tokens, cursor);
                if (!alias.isBlank()) {
                    return alias;
                }
            }
            if (token.equals("select")) {
                return "";
            }
        }
        return "";
    }

    private int skipRowsetModifiers(List<Token> tokens, int index) {
        int cursor = index;
        while (cursor < tokens.size()) {
            String token = lower(tokens.get(cursor));
            if (tokens.get(cursor).getText().equals("{")) {
                cursor++;
                if (cursor < tokens.size() && lower(tokens.get(cursor)).equals("oj")) {
                    cursor++;
                }
            } else if (token.equals("only") || token.equals("lateral")) {
                cursor++;
            } else {
                break;
            }
        }
        return cursor;
    }

    private boolean isLikelyRowsetComma(List<Token> tokens, int commaIndex) {
        int previousBoundary = -1;
        for (int cursor = commaIndex - 1; cursor >= 0; cursor--) {
            String token = lower(tokens.get(cursor));
            if (token.equals("from") || token.equals("update") || token.equals("using")) {
                previousBoundary = cursor;
                break;
            }
            if (token.equals("select") || token.equals("set") || token.equals("where") || token.equals("on")) {
                return false;
            }
        }
        if (previousBoundary < 0) {
            return false;
        }
        int depth = 0;
        for (int cursor = previousBoundary + 1; cursor < commaIndex; cursor++) {
            String text = tokens.get(cursor).getText();
            if (text.equals("(")) {
                depth++;
            } else if (text.equals(")")) {
                depth--;
            }
        }
        return depth == 0;
    }

    private String previousKeyword(List<Token> tokens, int index) {
        for (int cursor = index - 1; cursor >= 0; cursor--) {
            String token = lower(tokens.get(cursor));
            if (token.chars().allMatch(Character::isLetter)) {
                return token;
            }
        }
        return "";
    }

    private boolean hasKeywordBefore(List<Token> tokens, int index, String keyword, int maxDistance) {
        int start = Math.max(0, index - maxDistance);
        for (int cursor = index - 1; cursor >= start; cursor--) {
            if (lower(tokens.get(cursor)).equals(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean isCommonNonTableKeyword(String value) {
        String token = cleanIdentifier(value).toLowerCase(Locale.ROOT);
        return token.isBlank()
                || token.equals("select")
                || token.equals("set")
                || token.equals("where")
                || token.equals("on")
                || token.equals("join")
                || token.equals("inner")
                || token.equals("left")
                || token.equals("right")
                || token.equals("full")
                || token.equals("outer")
                || token.equals("cross")
                || token.equals("natural")
                || token.equals("straight_join")
                || token.equals("as")
                || token.equals("using")
                || token.equals("with")
                || token.equals("recursive")
                || token.equals("materialized")
                || token.equals("not")
                || token.equals("temporary")
                || token.equals("temp")
                || token.equals("table")
                || token.equals("values");
    }

    private String baseName(String qualifiedName) {
        String clean = cleanIdentifier(qualifiedName);
        int dot = clean.lastIndexOf('.');
        return dot < 0 ? clean : clean.substring(dot + 1);
    }

    private boolean hasSelectInsideFollowingParen(List<Token> tokens, int index) {
        if (index + 1 >= tokens.size() || !tokens.get(index + 1).getText().equals("(")) {
            return false;
        }
        int depth = 0;
        for (int cursor = index + 1; cursor < tokens.size(); cursor++) {
            String text = tokens.get(cursor).getText();
            if (text.equals("(")) {
                depth++;
            } else if (text.equals(")")) {
                depth--;
                if (depth == 0) {
                    return false;
                }
            } else if (depth > 0 && lower(tokens.get(cursor)).equals("select")) {
                return true;
            }
        }
        return false;
    }

    private boolean hasTopLevelSelectInside(List<Token> tokens, int openParenIndex) {
        int close = matchingParen(tokens, openParenIndex);
        return close > openParenIndex
                && firstTopLevelWord(tokens, "select", openParenIndex + 1, close) >= 0;
    }

    private int firstTopLevelWord(List<Token> tokens, String word, int startInclusive) {
        return firstTopLevelWord(tokens, List.of(word), startInclusive);
    }

    private int firstTopLevelWord(List<Token> tokens, String word, int startInclusive, int endExclusive) {
        int depth = 0;
        for (int index = Math.max(0, startInclusive); index < Math.min(tokens.size(), endExclusive); index++) {
            String text = tokens.get(index).getText();
            if (text.equals("(")) {
                depth++;
            } else if (text.equals(")")) {
                depth--;
            } else if (depth == 0 && lower(tokens.get(index)).equals(word)) {
                return index;
            }
        }
        return -1;
    }

    private int firstTopLevelWord(List<Token> tokens, List<String> words, int startInclusive) {
        int depth = 0;
        for (int index = Math.max(0, startInclusive); index < tokens.size(); index++) {
            String text = tokens.get(index).getText();
            if (text.equals("(")) {
                depth++;
            } else if (text.equals(")")) {
                depth--;
            } else if (depth == 0 && words.contains(lower(tokens.get(index)))) {
                return index;
            }
        }
        return -1;
    }

    private int topLevelToken(List<Token> tokens, int startInclusive, int endExclusive, String tokenText) {
        int depth = 0;
        for (int index = startInclusive; index < endExclusive; index++) {
            String text = tokens.get(index).getText();
            if (text.equals("(")) {
                depth++;
            } else if (text.equals(")")) {
                depth--;
            } else if (depth == 0 && text.equals(tokenText)) {
                return index;
            }
        }
        return -1;
    }

    private List<Span> splitTopLevelSpans(List<Token> tokens, int startInclusive, int endExclusive, String separator) {
        List<Span> spans = new ArrayList<>();
        int depth = 0;
        int start = startInclusive;
        for (int index = startInclusive; index < endExclusive; index++) {
            String text = tokens.get(index).getText();
            if (text.equals("(")) {
                depth++;
            } else if (text.equals(")")) {
                depth--;
            } else if (depth == 0 && text.equals(separator)) {
                if (start < index) {
                    spans.add(new Span(start, index));
                }
                start = index + 1;
            }
        }
        if (start < endExclusive) {
            spans.add(new Span(start, endExclusive));
        }
        return spans;
    }

    private List<String> identifierList(List<Token> tokens, int startInclusive, int endExclusive) {
        List<String> identifiers = new ArrayList<>();
        for (Span span : splitTopLevelSpans(tokens, startInclusive, endExclusive, ",")) {
            for (int index = span.start; index < span.end; index++) {
                String identifier = cleanIdentifier(tokens.get(index).getText());
                if (isIdentifierText(identifier)) {
                    identifiers.add(identifier);
                    break;
                }
            }
        }
        return identifiers;
    }

    private ColumnRead readAssignmentTarget(List<Token> tokens, int startInclusive, int endExclusive) {
        for (int index = startInclusive; index < endExclusive; index++) {
            ColumnRead qualified = readColumnForwardV2(tokens, index);
            if (qualified != null) {
                return qualified;
            }
            String column = cleanIdentifier(tokens.get(index).getText());
            if (isIdentifierText(column) && !isCommonNonTableKeyword(column)) {
                return ColumnRead.target("", column);
            }
        }
        return null;
    }

    private ColumnRead firstColumnInSpan(List<Token> tokens, int startInclusive, int endExclusive) {
        for (int index = startInclusive; index < endExclusive; index++) {
            ColumnRead column = readColumnForwardV2(tokens, index);
            if (column != null) {
                return column;
            }
        }
        return null;
    }

    private String projectionOutputColumn(List<Token> tokens, Span projection, String fallback) {
        for (int index = projection.start; index + 1 < projection.end; index++) {
            if (lower(tokens.get(index)).equals("as")) {
                String alias = cleanIdentifier(tokens.get(index + 1).getText());
                if (isIdentifierText(alias) && !isCommonNonTableKeyword(alias)) {
                    return alias;
                }
            }
        }
        return fallback;
    }

    private String defaultProjectionSourceQualifier(List<Token> tokens, int fromIndex) {
        IdentifierRead table = readQualifiedIdentifierV2(tokens, fromIndex + 1);
        if (table == null) {
            return "";
        }
        String alias = aliasAfter(tokens, table.nextIndex);
        return alias.isBlank() ? baseName(table.qualifiedName) : alias;
    }

    private String aliasAfter(List<Token> tokens, int index) {
        int aliasIndex = index;
        if (aliasIndex < tokens.size() && lower(tokens.get(aliasIndex)).equals("as")) {
            aliasIndex++;
        }
        if (aliasIndex >= tokens.size()) {
            return "";
        }
        String alias = cleanIdentifier(tokens.get(aliasIndex).getText());
        return isIdentifierText(alias) && !isCommonNonTableKeyword(alias) ? alias : "";
    }

    private String tokenText(List<Token> tokens, int startInclusive, int endExclusive) {
        StringBuilder result = new StringBuilder();
        for (int index = startInclusive; index < endExclusive; index++) {
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(tokens.get(index).getText());
        }
        return result.toString();
    }

    private String transformType(String expression) {
        String normalized = expression.toLowerCase(Locale.ROOT);
        if (normalized.contains("case")) {
            return "CASE_WHEN";
        }
        if (normalized.contains("sum") || normalized.contains("avg") || normalized.contains("count")
                || normalized.contains("min") || normalized.contains("max")) {
            return "AGGREGATE";
        }
        if (normalized.contains("coalesce")) {
            return "COALESCE";
        }
        if (normalized.contains("concat") || normalized.contains("format") || normalized.contains("||")) {
            return "CONCAT_FORMAT";
        }
        if (normalized.contains("+") || normalized.contains("-") || normalized.contains("*") || normalized.contains("/")) {
            return "ARITHMETIC";
        }
        if (normalized.contains("(")) {
            return "FUNCTION_CALL";
        }
        return "DIRECT";
    }

    private String previousNonWhitespaceText(List<Token> tokens, int index) {
        if (index <= 0) {
            return "";
        }
        return tokens.get(index - 1).getText();
    }

    private boolean isIdentifierText(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return text.chars().allMatch(ch -> Character.isLetterOrDigit(ch) || ch == '_' || ch == '$');
    }

    private String lower(Token token) {
        return token.getText().toLowerCase(Locale.ROOT);
    }

    private long line(SqlStatementRecord statement, Token token) {
        return Math.max(1, statement.startLine() + token.getLine() - 1);
    }

    private record ColumnRead(String qualifier, String column) {
        private static ColumnRead target(String qualifier, String column) {
            return new ColumnRead(qualifier, column);
        }
    }

    private record ColumnEquality(ColumnRead left, ColumnRead right) {
    }

    private record IdentifierRead(String qualifiedName, int nextIndex) {
    }

    private record SubqueryColumnRead(ColumnRead column, String table, String alias) {
    }

    private record TupleRead(List<ColumnRead> columns) {
    }

    private record TupleSubqueryRead(List<ColumnRead> columns, String table, String alias) {
    }

    private record Span(int start, int end) {
    }
}
