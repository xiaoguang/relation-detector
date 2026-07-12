package com.relationdetector.oracle.fullgrammar.v12c;

import org.antlr.v4.runtime.*;

public abstract class OracleFullGrammarParserBase extends Parser
{
    private boolean _isVersion12 = true;
    private boolean _isVersion11 = true;
    private boolean _isVersion10 = true;

    public OracleFullGrammarParserBase(TokenStream input) {
        super(input);
    }

    public boolean isVersion12() {
        return _isVersion12;
    }

    public void setVersion12(boolean value) {
        _isVersion12 = value;
    }

    public boolean isVersion11() {
        return _isVersion11;
    }

    public void setVersion11(boolean value) {
        _isVersion11 = value;
    }

    public boolean isVersion10() {
        return _isVersion10;
    }

    public void setVersion10(boolean value) {
        _isVersion10 = value;
    }

    public boolean IsNotNumericFunction() {
        Token lt1 = _input.LT(1);
        Token lt2 = _input.LT(2);
        if ((lt1.getType() == OracleFullGrammarParser.SUM ||
             lt1.getType() == OracleFullGrammarParser.COUNT ||
             lt1.getType() == OracleFullGrammarParser.AVG ||
             lt1.getType() == OracleFullGrammarParser.MIN ||
             lt1.getType() == OracleFullGrammarParser.MAX ||
             lt1.getType() == OracleFullGrammarParser.ROUND ||
             lt1.getType() == OracleFullGrammarParser.LEAST ||
             lt1.getType() == OracleFullGrammarParser.GREATEST) &&
             lt2.getType() == OracleFullGrammarParser.LEFT_PAREN)
            return false;
        return true;
    }

    public boolean isNotStartOfJoin() {
        Token lt1 = _input.LT(1);
        if (lt1.getType() == OracleFullGrammarParser.INNER ||
            lt1.getType() == OracleFullGrammarParser.CROSS ||
            lt1.getType() == OracleFullGrammarParser.NATURAL ||
            lt1.getType() == OracleFullGrammarParser.PARTITION ||
            lt1.getType() == OracleFullGrammarParser.FULL ||
            lt1.getType() == OracleFullGrammarParser.LEFT ||
            lt1.getType() == OracleFullGrammarParser.RIGHT ||
            lt1.getType() == OracleFullGrammarParser.OUTER)
            return false;
        return true;
    }
}
