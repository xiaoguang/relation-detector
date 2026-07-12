package com.relationdetector.mysql.routine;

import com.relationdetector.core.fullgrammar.FullGrammarEventFacade;
import java.util.Locale;
import java.util.Set;

/**
 * MySQL routine scope policy shared by parser families.
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
