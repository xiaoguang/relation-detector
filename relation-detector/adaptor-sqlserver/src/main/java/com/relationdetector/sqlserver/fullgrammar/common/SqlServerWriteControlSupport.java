package com.relationdetector.sqlserver.fullgrammar.common;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;

import com.relationdetector.core.fullgrammar.FullGrammarEventFacade;
import com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter;
import com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter.Role;

/** Emits SQL Server full-grammar write controls from typed contexts. */
final class SqlServerWriteControlSupport extends SqlServerParseTreeSupport {
    private final FullGrammarEventFacade sink;
    private final SqlServerExpressionAnalyzer expressions;

    SqlServerWriteControlSupport(
            FullGrammarParseTreeAdapter adapter,
            FullGrammarEventFacade sink,
            SqlServerExpressionAnalyzer expressions
    ) {
        super(adapter);
        this.sink = sink;
        this.expressions = expressions;
    }

    void emitInsertSelect(
            ParserRuleContext item,
            String targetTable,
            String targetColumn,
            ParserRuleContext expression,
            ParserRuleContext query
    ) {
        sink.insertSelect(item, "", targetTable, targetColumn, expression);
        if (query != null) {
            List<ParserRuleContext> locators = locatorContexts(query, null);
            if (!locators.isEmpty()) {
                sink.insertSelectControl(item, "", targetTable, targetColumn, locators, "DIRECT");
            }
        }
        if (!"AGGREGATE".equals(expressions.analyze(expression).transformType()) || query == null) {
            return;
        }
        List<ParserRuleContext> groupings = descendants(query, Role.GROUPING_SCOPE);
        if (!groupings.isEmpty()) {
            sink.insertSelectControl(item, "", targetTable, targetColumn, groupings, "AGGREGATE");
        }
    }

    List<ParserRuleContext> updateLocators(
            ParserRuleContext tableSources,
            ParserRuleContext condition
    ) {
        return locatorContexts(tableSources, condition);
    }

    void emitUpdate(
            ParserRuleContext element,
            String targetTable,
            List<ParserRuleContext> conditions,
            boolean merge
    ) {
        List<ParserRuleContext> columns = directChildren(element, Role.COLUMN_REFERENCE);
        if (columns.isEmpty() || conditions.isEmpty()) {
            return;
        }
        String targetColumn = lastIdentifier(columns.get(0).getText());
        if (merge) {
            for (ParserRuleContext condition : conditions) {
                sink.mergeControl(element, "", qualifiedTable(targetTable), targetColumn, condition);
            }
        } else {
            sink.updateControl(element, "", qualifiedTable(targetTable), targetColumn, conditions);
        }
    }

    void emitMergeInsert(
            ParserRuleContext value,
            String targetAlias,
            String targetTable,
            String targetColumn,
            ParserRuleContext condition
    ) {
        if (condition != null) {
            sink.mergeControl(value, targetAlias, qualifiedTable(targetTable), targetColumn, condition);
        }
    }

    private List<ParserRuleContext> locatorContexts(
            ParserRuleContext container,
            ParserRuleContext condition
    ) {
        List<ParserRuleContext> result = new ArrayList<>();
        if (container != null) {
            for (ParserRuleContext join : descendants(container, Role.JOIN_ON)) {
                firstDirect(join, Role.SEARCH_CONDITION).ifPresent(result::add);
            }
            if (condition == null) {
                result.addAll(directChildren(container, Role.SEARCH_CONDITION));
                for (ParserRuleContext query : descendants(container, Role.QUERY_SPECIFICATION)) {
                    result.addAll(directChildren(query, Role.SEARCH_CONDITION));
                }
            }
        }
        if (condition != null) {
            result.add(condition);
        }
        return List.copyOf(result);
    }
}
