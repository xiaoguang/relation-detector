package com.relationdetector.oracle.fullgrammar.v12c;

import org.antlr.v4.runtime.*;

/** CN: 为 Oracle 12c generated lexer 提供换行位置谓词，不解释 SQL 语义。 EN: Supplies lexer predicates only. */
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
