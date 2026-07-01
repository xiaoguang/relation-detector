package com.relationdetector.oracle.fullgrammer.v19c;

import org.antlr.v4.runtime.*;

public abstract class OracleFullGrammerLexerBase extends Lexer
{
    public OracleFullGrammerLexerBase(CharStream input)
    {
        super(input);
    }

    protected boolean IsNewlineAtPos(int pos)
    {
        int la = _input.LA(pos);
        return la == -1 || la == '\n';
    }
}
