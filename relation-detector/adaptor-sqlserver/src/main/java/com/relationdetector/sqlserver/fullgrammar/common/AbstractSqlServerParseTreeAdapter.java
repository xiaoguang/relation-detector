package com.relationdetector.sqlserver.fullgrammar.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.core.fullgrammar.AbstractFullGrammarParseTreeAdapter;
import com.relationdetector.core.fullgrammar.FullGrammarColumnReference;
import com.relationdetector.core.fullgrammar.FullGrammarIdentifiers;

/** Shared typed identifier/function/rowset semantics for versioned T-SQL contexts. */
public abstract class AbstractSqlServerParseTreeAdapter extends AbstractFullGrammarParseTreeAdapter {
    protected AbstractSqlServerParseTreeAdapter(RoleBinding... bindings) {
        super(bindings);
    }

    /** Returns the normalized semantic kind of a typed version-specific JOIN context. */
    public abstract String joinKind(ParseTree tree);

    @Override
    public final Optional<FullGrammarColumnReference> directColumn(ParseTree tree) {
        return hasRole(tree, Role.COLUMN_REFERENCE)
                ? FullGrammarIdentifiers.columnReference(tree.getText())
                : Optional.empty();
    }

    @Override
    public final List<String> identifiers(ParseTree tree) {
        if (hasRole(tree, Role.IDENTIFIER)
                || hasRole(tree, Role.FULL_TABLE_NAME)
                || hasRole(tree, Role.TABLE_NAME)
                || hasRole(tree, Role.COLUMN_REFERENCE)
                || hasRole(tree, Role.DDL_OBJECT)) {
            return FullGrammarIdentifiers.qualifiedParts(tree.getText());
        }
        return List.of();
    }

    @Override
    public final Optional<String> functionName(ParseTree tree) {
        if (!hasRole(tree, Role.FUNCTION_CALL) && !hasRole(tree, Role.AGGREGATE_FUNCTION)) {
            return Optional.empty();
        }
        Token start = tree instanceof ParserRuleContext context ? context.getStart() : null;
        return start == null
                ? Optional.empty()
                : Optional.of(FullGrammarIdentifiers.clean(start.getText())).filter(value -> !value.isBlank());
    }

    @Override
    public final Optional<RowsetBinding> rowsetBinding(ParseTree tree) {
        if (!hasRole(tree, Role.TABLE_SOURCE_ITEM)) {
            return Optional.empty();
        }
        ParseTree table = firstDirectChild(tree, Role.FULL_TABLE_NAME);
        if (table == null) {
            return Optional.empty();
        }
        String physical = String.join(".", FullGrammarIdentifiers.qualifiedParts(table.getText()));
        ParseTree aliasContext = firstDirectChild(tree, Role.TABLE_ALIAS);
        String qualifier = aliasContext == null
                ? lastPart(physical)
                : firstIdentifier(aliasContext).orElse(lastPart(physical));
        return physical.isBlank() ? Optional.empty() : Optional.of(new RowsetBinding(physical, qualifier));
    }

    @Override
    public final CaseParts caseParts(ParseTree tree) {
        if (!hasRole(tree, Role.CASE_EXPRESSION)) {
            return CaseParts.NONE;
        }
        List<ParseTree> values = new ArrayList<>();
        List<ParseTree> controls = new ArrayList<>();
        List<ParseTree> switchSections = directChildren(tree, Role.CASE_SWITCH_SECTION);
        List<ParseTree> searchSections = directChildren(tree, Role.CASE_SEARCH_SECTION);
        List<ParseTree> directExpressions = directChildren(tree, Role.EXPRESSION);
        if (!switchSections.isEmpty() && !directExpressions.isEmpty()) {
            controls.add(directExpressions.get(0));
        }
        for (ParseTree section : switchSections) {
            List<ParseTree> expressions = directChildren(section, Role.EXPRESSION);
            if (expressions.size() == 2) {
                controls.add(expressions.get(0));
                values.add(expressions.get(1));
            }
        }
        for (ParseTree section : searchSections) {
            ParseTree condition = firstDirectChild(section, Role.CONTROL_SCOPE);
            ParseTree value = firstDirectChild(section, Role.EXPRESSION);
            if (condition != null) controls.add(condition);
            if (value != null) values.add(value);
        }
        int expectedDirect = switchSections.isEmpty() ? 0 : 1;
        if (directExpressions.size() > expectedDirect) {
            values.add(directExpressions.get(directExpressions.size() - 1));
        }
        return new CaseParts(true, values, controls);
    }

    private Optional<String> firstIdentifier(ParseTree tree) {
        List<String> direct = identifiers(tree);
        if (!direct.isEmpty()) {
            return Optional.of(direct.get(0));
        }
        for (ParseTree child : typedChildren(tree)) {
            Optional<String> found = firstIdentifier(child);
            if (found.isPresent()) return found;
        }
        return Optional.empty();
    }

    private String lastPart(String qualified) {
        List<String> parts = FullGrammarIdentifiers.qualifiedParts(qualified);
        return parts.isEmpty() ? "" : parts.get(parts.size() - 1);
    }
}
