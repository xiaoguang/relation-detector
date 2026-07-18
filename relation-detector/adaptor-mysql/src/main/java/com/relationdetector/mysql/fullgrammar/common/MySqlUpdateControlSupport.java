package com.relationdetector.mysql.fullgrammar.common;

import java.util.List;
import java.util.function.Function;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.core.fullgrammar.FullGrammarEventFacade;
import com.relationdetector.mysql.fullgrammar.common.MySqlSqlEventVisitorCore.ColumnParts;

/**
 * CN: 将 visitor 已识别的 UPDATE WHERE context 绑定到同一 write scope 的目标列并发射 CONTROL/DIRECT；空条件或无法定位的目标跳过，不分析 raw SQL。
 * EN: Binds a typed UPDATE WHERE context to target columns in the same write scope and emits CONTROL/DIRECT dependencies. Missing conditions or unresolved targets are skipped without raw-SQL analysis.
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
