package com.relationdetector.core.script;

/**
 *
 * Structural token classes needed only for client-script framing.
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
