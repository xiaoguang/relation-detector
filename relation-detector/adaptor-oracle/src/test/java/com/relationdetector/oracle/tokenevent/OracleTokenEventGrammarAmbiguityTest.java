package com.relationdetector.oracle.tokenevent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.atn.PredictionMode;
import org.antlr.v4.runtime.dfa.DFA;
import org.junit.jupiter.api.Test;

class OracleTokenEventGrammarAmbiguityTest {
    @Test
    void routineDeclarationDoesNotTreatParenthesisAsBothTypedAndFallbackToken() {
        String sql = """
                CREATE OR REPLACE PROCEDURE sp_apply_rate(p_rate IN NUMBER) AS
                    v_adjusted_rate NUMBER(10, 4) := p_rate;
                BEGIN
                    NULL;
                END;
                """;
        var parser = new OracleRelationSqlParser(new CommonTokenStream(
                new OracleRelationSqlLexer(CharStreams.fromString(sql))));
        var ambiguities = new RoutineDeclarationAmbiguities();
        parser.removeErrorListeners();
        parser.addErrorListener(ambiguities);
        parser.getInterpreter().setPredictionMode(PredictionMode.LL_EXACT_AMBIG_DETECTION);

        parser.script();

        assertEquals(List.of(), ambiguities.details,
                () -> "routineDeclarationToken ambiguities: " + ambiguities.details);
    }

    private static final class RoutineDeclarationAmbiguities extends BaseErrorListener {
        private final List<String> details = new ArrayList<>();

        @Override
        public void reportAmbiguity(
                Parser recognizer,
                DFA dfa,
                int startIndex,
                int stopIndex,
                boolean exact,
                BitSet ambiguousAlternatives,
                ATNConfigSet configs
        ) {
            String ruleName = recognizer.getRuleNames()[dfa.atnStartState.ruleIndex];
            if (ruleName.equals("routineDeclarationToken")) {
                details.add(startIndex + ".." + stopIndex + " alternatives=" + ambiguousAlternatives);
            }
        }
    }
}
