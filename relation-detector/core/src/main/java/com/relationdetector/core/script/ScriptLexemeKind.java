package com.relationdetector.core.script;

/**
 * CN: 列举 client-script framing 使用的结构 token 类别，这些类别不生成 SQL 事实。
 * EN: Enumerates structural token classes used only for client-script framing; they do not produce SQL facts.
 */
public enum ScriptLexemeKind {
    WORD,
    QUOTED,
    COMMENT,
    DOLLAR_TAG,
    SEMICOLON,
    NEWLINE,
    WHITESPACE,
    DOT,
    DELIMITER,
    CUSTOM_TERMINATOR,
    GO,
    CREATE,
    OR,
    REPLACE,
    ALTER,
    PROCEDURE,
    FUNCTION,
    TRIGGER,
    PACKAGE,
    BODY,
    EVENT,
    VIEW,
    MATERIALIZED,
    RETURNS,
    EDITIONABLE,
    NONEDITIONABLE,
    TEMPORARY,
    TEMP,
    TABLE,
    IF,
    NOT,
    EXISTS,
    BEGIN,
    CASE,
    END,
    SYMBOL
}
