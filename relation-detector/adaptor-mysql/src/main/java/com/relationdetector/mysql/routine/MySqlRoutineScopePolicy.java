package com.relationdetector.mysql.routine;

import com.relationdetector.core.fullgrammar.FullGrammarEventFacade;
import java.util.Locale;
import java.util.Set;

/**
 * CN: 接收 visitor 已 typed 识别的参数、局部变量、临时表和 pseudo rowset 声明，维护一次 routine 的非物理 symbol scope；输出供 column-read 过滤，不发现 SQL 结构。
 * EN: Maintains one routine's non-physical symbol scope from parameters, variables, temporary tables, and pseudo-rowsets already identified by typed visitors. It filters column reads but does not discover SQL structure.
 *
 * <p>This class does not discover SQL structure. Visitors call it only after a
 * typed grammar context has identified a parameter, local variable, temporary
 * table declaration, or pseudo rowset.
 */
public final class MySqlRoutineScopePolicy {
    private static final Set<String> PSEUDO_ROWSETS = Set.of("NEW", "OLD");

    private MySqlRoutineScopePolicy() {
    }

    public static void markNonColumnIdentifier(FullGrammarEventFacade sink, String identifier) {
        if (sink != null && identifier != null && !identifier.isBlank()) {
            sink.nonColumnIdentifier(identifier);
        }
    }

    public static boolean isPseudoRowset(String identifier) {
        return identifier != null && PSEUDO_ROWSETS.contains(identifier.toUpperCase(Locale.ROOT));
    }
}
