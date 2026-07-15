package com.relationdetector.oracle.fullgrammar.common;

import org.antlr.v4.runtime.ParserRuleContext;

import com.relationdetector.oracle.fullgrammar.common.OracleFullGrammarParseTreeAdapter.Role;
import com.relationdetector.oracle.routine.OracleRoutineScope;

/** Registers typed Oracle routine parameters and local variables. */
final class OracleRoutineSymbolCollector extends OracleFullGrammarParseTreeSupport {
    OracleRoutineSymbolCollector(
            OracleSqlEventVisitorCore core,
            OracleFullGrammarParseTreeAdapter adapter
    ) {
        super(core, adapter);
    }

    boolean declareIfPresent(ParserRuleContext context, OracleRoutineScope scope) {
        ParserRuleContext identifier;
        if (hasRole(context, Role.ROUTINE_PARAMETER)) {
            identifier = first(context, Role.ROUTINE_PARAMETER_NAME);
        } else if (hasRole(context, Role.VARIABLE_DECLARATION)) {
            identifier = first(context, Role.IDENTIFIER);
        } else {
            return false;
        }
        if (identifier != null) {
            scope.declare(name(identifier));
        }
        return true;
    }
}
