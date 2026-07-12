package com.relationdetector.mysql.fullgrammar.v8_0;

/*
 * Copyright 2024, Oracle and/or its affiliates
 */

/* eslint-disable no-underscore-dangle */
/* cspell: ignore antlr, longlong, ULONGLONG, MAXDB */

import org.antlr.v4.runtime.*;
import java.util.*;


/** The base lexer class provides a number of functions needed in actions in the lexer (grammar). */
public abstract class MySqlLexerBase extends Lexer {

    public MySqlLexerBase(CharStream input) {
        super(input);
        this.serverVersion = 80200;
        this.sqlModes = MySqlGrammarSqlModes.sqlModeFromString("ANSI_QUOTES");
    }

    public int serverVersion = 0;
    public Set<MySqlGrammarSqlMode> sqlModes = new HashSet<>();

    /** Enable Multi Language Extension support. */
    public boolean supportMle = true;

    public Set<String> charSets = new HashSet<>(); // Used to check repertoires.
    protected boolean inVersionComment = false;

    private Queue<Token> pendingTokens = new LinkedList<>();

    static String longString = "2147483647";
    static int longLength = 10;
    static String signedLongString = "-2147483648";
    static String longLongString = "9223372036854775807";
    static int longLongLength = 19;
    static String signedLongLongString = "-9223372036854775808";
    static int signedLongLongLength = 19;
    static String unsignedLongLongString = "18446744073709551615";
    static int unsignedLongLongLength = 20;

    private boolean justEmittedDot = false;

    public boolean isSqlModeActive(MySqlGrammarSqlMode mode) { return this.sqlModes.contains(mode); }

    @Override
    public void reset() {
        this.inVersionComment = false;
        super.reset();
    }

    @Override
    public Token nextToken() {
        Token pending = pendingTokens.poll();
        if (pending != null) {
            return pending;
        }

        Token next = super.nextToken();

        pending = pendingTokens.poll();
        if (pending != null) {
            pendingTokens.add(next);
            return pending;
        }

        return next;
    }

    protected boolean checkMySQLVersion(String text) {
        if (text.length() < 8) { // Minimum is: /*!12345
            return false;
        }

        int version = Integer.parseInt(text.substring(3));
        if (version <= this.serverVersion) {
            this.inVersionComment = true;
            return true;
        }

        return false;
    }

    protected int determineFunction(int proposed) {
        char input = (char) this._input.LA(1);
        if (this.isSqlModeActive(MySqlGrammarSqlMode.IgnoreSpace)) {
            while (input == ' ' || input == '\t' || input == '\r' || input == '\n') {
                this.getInterpreter().consume(this._input);
                this._channel = HIDDEN;
                this._type = MySqlFullGrammarLexer.WHITESPACE;
                input = (char) this._input.LA(1);
            }
        }
        return input == '(' ? proposed : MySqlFullGrammarLexer.IDENTIFIER;
    }

    protected int determineNumericType(String text) {
        int length = text.length() - 1;
        if (length < MySqlLexerBase.longLength) {
            return MySqlFullGrammarLexer.INT_NUMBER;
        }

        boolean negative = false;
        int index = 0;
        if (text.charAt(index) == '+') {
            ++index;
            --length;
        } else if (text.charAt(index) == '-') {
            ++index;
            --length;
            negative = true;
        }

        while (text.charAt(index) == '0' && length > 0) {
            ++index;
            --length;
        }

        if (length < MySqlLexerBase.longLength) {
            return MySqlFullGrammarLexer.INT_NUMBER;
        }

        String cmp;
        int smaller;
        int bigger;
        if (negative) {
            if (length == MySqlLexerBase.longLength) {
                cmp = MySqlLexerBase.signedLongString.substring(1);
                smaller = MySqlFullGrammarLexer.INT_NUMBER;
                bigger = MySqlFullGrammarLexer.LONG_NUMBER;
            } else if (length < MySqlLexerBase.signedLongLongLength) {
                return MySqlFullGrammarLexer.LONG_NUMBER;
            } else if (length > MySqlLexerBase.signedLongLongLength) {
                return MySqlFullGrammarLexer.DECIMAL_NUMBER;
            } else {
                cmp = MySqlLexerBase.signedLongLongString.substring(1);
                smaller = MySqlFullGrammarLexer.LONG_NUMBER;
                bigger = MySqlFullGrammarLexer.DECIMAL_NUMBER;
            }
        } else {
            if (length == MySqlLexerBase.longLength) {
                cmp = MySqlLexerBase.longString;
                smaller = MySqlFullGrammarLexer.INT_NUMBER;
                bigger = MySqlFullGrammarLexer.LONG_NUMBER;
            } else if (length < MySqlLexerBase.longLongLength) {
                return MySqlFullGrammarLexer.LONG_NUMBER;
            } else if (length > MySqlLexerBase.longLongLength) {
                if (length > MySqlLexerBase.unsignedLongLongLength) {
                    return MySqlFullGrammarLexer.DECIMAL_NUMBER;
                }
                cmp = MySqlLexerBase.unsignedLongLongString;
                smaller = MySqlFullGrammarLexer.ULONGLONG_NUMBER;
                bigger = MySqlFullGrammarLexer.DECIMAL_NUMBER;
            } else {
                cmp = MySqlLexerBase.longLongString;
                smaller = MySqlFullGrammarLexer.LONG_NUMBER;
                bigger = MySqlFullGrammarLexer.ULONGLONG_NUMBER;
            }
        }

        int otherIndex = 0;
        while (index < text.length() && cmp.charAt(otherIndex++) == text.charAt(index++)) {}

        return text.charAt(index - 1) <= cmp.charAt(otherIndex - 1) ? smaller : bigger;
    }

    protected int checkCharset(String text) {
        return this.charSets.contains(text) ? MySqlFullGrammarLexer.UNDERSCORE_CHARSET : MySqlFullGrammarLexer.IDENTIFIER;
    }

    protected void emitDot() {
        var len = this.getText().length();
        pendingTokens.add(this._factory.create(this._tokenFactorySourcePair, MySqlFullGrammarLexer.DOT_SYMBOL,
                ".", this._channel, this._tokenStartCharIndex, this._tokenStartCharIndex, this.getLine(), this.getCharPositionInLine() - len));
        ++this._tokenStartCharPositionInLine;
        this.justEmittedDot = true;
    }

    @Override
    public Token emit() {
        var t = super.emit();
        if (this.justEmittedDot) {
            var p = (CommonToken)t;
            p.setText(p.getText().substring(1));
            p.setStartIndex(p.getStartIndex() + 1);
            this.justEmittedDot = false;
        }
        return t;
    }

    public boolean isMasterCompressionAlgorithm() { return serverVersion >= 80018 && isServerVersionLt80024(); }
    public boolean isServerVersionGe80011() { return serverVersion >= 80011; }
    public boolean isServerVersionGe80013() { return serverVersion >= 80013; }
    public boolean isServerVersionLt80014() { return serverVersion < 80014; }
    public boolean isServerVersionGe80014() { return serverVersion >= 80014; }
    public boolean isServerVersionGe80016() { return serverVersion >= 80016; }
    public boolean isServerVersionGe80017() { return serverVersion >= 80017; }
    public boolean isServerVersionGe80018() { return serverVersion >= 80018; }
    public boolean isServerVersionLt80021() { return serverVersion < 80021; }
    public boolean isServerVersionGe80021() { return serverVersion >= 80021; }
    public boolean isServerVersionLt80022() { return serverVersion < 80022; }
    public boolean isServerVersionGe80022() { return serverVersion >= 80022; }
    public boolean isServerVersionLt80023() { return serverVersion < 80023; }
    public boolean isServerVersionGe80023() { return serverVersion >= 80023; }
    public boolean isServerVersionLt80024() { return serverVersion < 80024; }
    public boolean isServerVersionGe80024() { return serverVersion >= 80024; }
    public boolean isServerVersionLt80031() { return serverVersion < 80031; }
    public void doLogicalOr() { this._type = isSqlModeActive(MySqlGrammarSqlMode.PipesAsConcat) ? MySqlFullGrammarLexer.CONCAT_PIPES_SYMBOL : MySqlFullGrammarLexer.LOGICAL_OR_OPERATOR; }
    public void doIntNumber() { this._type = determineNumericType(this.getText()); }
    public void doAdddate() { this._type = determineFunction(MySqlFullGrammarLexer.ADDDATE_SYMBOL); }
    public void doBitAnd() { this._type = determineFunction(MySqlFullGrammarLexer.BIT_AND_SYMBOL); }
    public void doBitOr() { this._type = determineFunction(MySqlFullGrammarLexer.BIT_OR_SYMBOL); }
    public void doBitXor() { this._type = determineFunction(MySqlFullGrammarLexer.BIT_XOR_SYMBOL); }
    public void doCast() { this._type = determineFunction(MySqlFullGrammarLexer.CAST_SYMBOL); }
    public void doCount() { this._type = determineFunction(MySqlFullGrammarLexer.COUNT_SYMBOL); }
    public void doCurdate() { this._type = determineFunction(MySqlFullGrammarLexer.CURDATE_SYMBOL); }
    public void doCurrentDate() { this._type = determineFunction(MySqlFullGrammarLexer.CURDATE_SYMBOL); }
    public void doCurrentTime() { this._type = determineFunction(MySqlFullGrammarLexer.CURTIME_SYMBOL); }
    public void doCurtime() { this._type = determineFunction(MySqlFullGrammarLexer.CURTIME_SYMBOL); }
    public void doDateAdd() { this._type = determineFunction(MySqlFullGrammarLexer.DATE_ADD_SYMBOL); }
    public void doDateSub() { this._type = determineFunction(MySqlFullGrammarLexer.DATE_SUB_SYMBOL); }
    public void doExtract() { this._type = determineFunction(MySqlFullGrammarLexer.EXTRACT_SYMBOL); }
    public void doGroupConcat() { this._type = determineFunction(MySqlFullGrammarLexer.GROUP_CONCAT_SYMBOL); }
    public void doMax() { this._type = determineFunction(MySqlFullGrammarLexer.MAX_SYMBOL); }
    public void doMid() { this._type = determineFunction(MySqlFullGrammarLexer.SUBSTRING_SYMBOL); }
    public void doMin() { this._type = determineFunction(MySqlFullGrammarLexer.MIN_SYMBOL); }
    public void doNot() { this._type = isSqlModeActive(MySqlGrammarSqlMode.HighNotPrecedence) ? MySqlFullGrammarLexer.NOT2_SYMBOL : MySqlFullGrammarLexer.NOT_SYMBOL; }
    public void doNow() { this._type = determineFunction(MySqlFullGrammarLexer.NOW_SYMBOL); }
    public void doPosition() { this._type = determineFunction(MySqlFullGrammarLexer.POSITION_SYMBOL); }
    public void doSessionUser() { this._type = determineFunction(MySqlFullGrammarLexer.USER_SYMBOL); }
    public void doStddevSamp() { this._type = determineFunction(MySqlFullGrammarLexer.STDDEV_SAMP_SYMBOL); }
    public void doStddev() { this._type = determineFunction(MySqlFullGrammarLexer.STD_SYMBOL); }
    public void doStddevPop() { this._type = determineFunction(MySqlFullGrammarLexer.STD_SYMBOL); }
    public void doStd() { this._type = determineFunction(MySqlFullGrammarLexer.STD_SYMBOL); }
    public void doSubdate() { this._type = determineFunction(MySqlFullGrammarLexer.SUBDATE_SYMBOL); }
    public void doSubstr() { this._type = determineFunction(MySqlFullGrammarLexer.SUBSTRING_SYMBOL); }
    public void doSubstring() { this._type = determineFunction(MySqlFullGrammarLexer.SUBSTRING_SYMBOL); }
    public void doSum() { this._type = determineFunction(MySqlFullGrammarLexer.SUM_SYMBOL); }
    public void doSysdate() { this._type = determineFunction(MySqlFullGrammarLexer.SYSDATE_SYMBOL); }
    public void doSystemUser() { this._type = determineFunction(MySqlFullGrammarLexer.USER_SYMBOL); }
    public void doTrim() { this._type = determineFunction(MySqlFullGrammarLexer.TRIM_SYMBOL); }
    public void doVariance() { this._type = determineFunction(MySqlFullGrammarLexer.VARIANCE_SYMBOL); }
    public void doVarPop() { this._type = determineFunction(MySqlFullGrammarLexer.VARIANCE_SYMBOL); }
    public void doVarSamp() { this._type = determineFunction(MySqlFullGrammarLexer.VAR_SAMP_SYMBOL); }
    public void doUnderscoreCharset() { this._type = checkCharset(this.getText()); }
    public boolean doDollarQuotedStringText() { return this.serverVersion >= 80034 && this.supportMle; }
    public boolean isVersionComment() { return checkMySQLVersion(this.getText()); }
    public boolean isBackTickQuotedId() { return !this.isSqlModeActive(MySqlGrammarSqlMode.NoBackslashEscapes); }
    public boolean isDoubleQuotedText() { return !this.isSqlModeActive(MySqlGrammarSqlMode.NoBackslashEscapes); }
    public boolean isSingleQuotedText() { return !this.isSqlModeActive(MySqlGrammarSqlMode.NoBackslashEscapes); }
    public void startInVersionComment() { inVersionComment = true; }
    public void endInVersionComment() { inVersionComment = false; }
    public boolean isInVersionComment() { return inVersionComment; }
}
