package com.relationdetector.mysql.fullgrammar.common;

import java.util.List;
import java.util.function.Function;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.core.fullgrammar.FullGrammarEventFacade;
import com.relationdetector.mysql.fullgrammar.common.MySqlSqlEventVisitorCore.ColumnParts;

/**
 *
 * Emits typed UPDATE locator controls without depending on a versioned parser.
 */
public final class MySqlUpdateControlSupport {
    private MySqlUpdateControlSupport() {
    }

    public static <T> void emit(
            ParserRuleContext whereClause,
            ParseTree condition,
            String defaultTarget,
            List<T> updates,
            Function<T, String> columnText,
            FullGrammarEventFacade sink,
            MySqlSqlEventVisitorCore core
    ) {
        if (whereClause == null || condition == null) {
            return;
        }
        for (T update : updates) {
            String rawColumn = columnText.apply(update);
            if (rawColumn == null || rawColumn.isBlank()) {
                continue;
            }
            ColumnParts targetColumn = core.columnParts(rawColumn);
            String targetTable = targetColumn.qualifier().isBlank()
                    ? defaultTarget : sink.tableForAlias(targetColumn.qualifier());
            sink.updateControl(whereClause, targetColumn.qualifier(), targetTable,
                    targetColumn.column(), condition);
        }
    }
}
