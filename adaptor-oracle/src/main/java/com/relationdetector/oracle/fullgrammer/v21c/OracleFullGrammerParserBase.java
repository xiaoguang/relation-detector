package com.relationdetector.oracle.fullgrammer.v21c;

import org.antlr.v4.runtime.*;

public abstract class OracleFullGrammerParserBase extends Parser
{
    private boolean _isVersion12 = true;
    private boolean _isVersion11 = true;
    private boolean _isVersion10 = true;

    public OracleFullGrammerParserBase(TokenStream input) {
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
        if ((lt1.getType() == OracleFullGrammerParser.SUM ||
             lt1.getType() == OracleFullGrammerParser.COUNT ||
             lt1.getType() == OracleFullGrammerParser.AVG ||
             lt1.getType() == OracleFullGrammerParser.MIN ||
             lt1.getType() == OracleFullGrammerParser.MAX ||
             lt1.getType() == OracleFullGrammerParser.ROUND ||
             lt1.getType() == OracleFullGrammerParser.LEAST ||
             lt1.getType() == OracleFullGrammerParser.GREATEST) &&
             lt2.getType() == OracleFullGrammerParser.LEFT_PAREN)
            return false;
        return true;
    }

    public boolean isNotStartOfJoin() {
        Token lt1 = _input.LT(1);
        if (lt1.getType() == OracleFullGrammerParser.INNER ||
            lt1.getType() == OracleFullGrammerParser.CROSS ||
            lt1.getType() == OracleFullGrammerParser.NATURAL ||
            lt1.getType() == OracleFullGrammerParser.PARTITION ||
            lt1.getType() == OracleFullGrammerParser.FULL ||
            lt1.getType() == OracleFullGrammerParser.LEFT ||
            lt1.getType() == OracleFullGrammerParser.RIGHT ||
            lt1.getType() == OracleFullGrammerParser.OUTER)
            return false;
        return true;
    }
}
