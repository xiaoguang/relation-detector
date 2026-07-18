package com.relationdetector.mysql.fullgrammar.common;

import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;

import com.relationdetector.core.fullgrammar.FullGrammarEventFacade;

/**
 * CN: 将 version visitor 提供的 typed INSERT target columns 与 VALUES expressions 按 ordinal 发射 write mappings；arity 不足时只处理可证明位置，不访问 generated parser。
 * EN: Emits write mappings by ordinal from typed INSERT target columns and VALUES expressions supplied by version visitors. Only provable positions are used, with no generated-parser dependency.
 */
public final class MySqlInsertValuesSupport {
    private MySqlInsertValuesSupport() {
    }

    public static void emit(
            FullGrammarEventFacade sink,
            String target,
            List<String> targetColumns,
            List<? extends List<? extends ParserRuleContext>> rows
    ) {
        for (List<? extends ParserRuleContext> row : rows) {
            int count = Math.min(targetColumns.size(), row.size());
            for (int index = 0; index < count; index++) {
                ParserRuleContext expression = row.get(index);
                sink.insertValues(expression, "", target, targetColumns.get(index), expression);
            }
        }
    }
}
