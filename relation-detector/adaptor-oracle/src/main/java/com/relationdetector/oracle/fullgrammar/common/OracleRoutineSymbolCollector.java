package com.relationdetector.oracle.fullgrammar.common;

import org.antlr.v4.runtime.ParserRuleContext;

import com.relationdetector.oracle.fullgrammar.common.OracleFullGrammarParseTreeAdapter.Role;
import com.relationdetector.oracle.routine.OracleRoutineScope;

/**
 * CN: 从 Oracle generated parameter/variable declaration contexts 登记当前 block symbols 到 OracleRoutineScope；只消费 typed declaration，不发现 column reads。
 * EN: Registers symbols from typed Oracle parameter and variable-declaration contexts into the current OracleRoutineScope block. It consumes declarations only and does not discover column reads.
 */
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
