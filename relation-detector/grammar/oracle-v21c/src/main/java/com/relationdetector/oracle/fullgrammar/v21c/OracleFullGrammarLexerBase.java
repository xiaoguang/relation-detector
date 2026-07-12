package com.relationdetector.oracle.fullgrammar.v21c;

import org.antlr.v4.runtime.*;

public abstract class OracleFullGrammarLexerBase extends Lexer
{
    public OracleFullGrammarLexerBase(CharStream input)
    {
        super(input);
    }

    protected boolean IsNewlineAtPos(int pos)
    {
        int la = _input.LA(pos);
        return la == -1 || la == '\n';
    }
}
