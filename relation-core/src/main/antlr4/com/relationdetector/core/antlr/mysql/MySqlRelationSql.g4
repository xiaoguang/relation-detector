grammar MySqlRelationSql;

/*
 * MySQL structural grammar for ANTLR shadow mode.
 *
 * This is intentionally still a tolerant grammar, not a full MySQL grammar.
 * The important Phase-1 boundary is architectural: MySQL now owns a separate
 * generated lexer/parser class, so later MySQL 8.x grammar rules, sql_mode
 * gates, and server-version switches can evolve without changing PostgreSQL.
 *
 * MySQL-specific choice in this first grammar:
 *   - backtick identifiers are quoted identifiers;
 *   - double quoted strings are not treated as quoted identifiers here because
 *     that depends on ANSI_QUOTES mode and should become an explicit capability
 *     flag later.
 */
script
    : sqlToken* EOF
    ;

sqlToken
    : IDENTIFIER
    | QUOTED_IDENTIFIER
    | STRING_LITERAL
    | NUMBER
    | DOT
    | COMMA
    | STAR
    | EQ
    | LPAREN
    | RPAREN
    | SEMI
    | OTHER
    ;

DOT: '.';
COMMA: ',';
STAR: '*';
EQ: '=';
LPAREN: '(';
RPAREN: ')';
SEMI: ';';

QUOTED_IDENTIFIER
    : '`' (~'`' | '``')* '`'
    ;

STRING_LITERAL
    : '\'' ('\\' . | '\'\'' | ~['\\])* '\''
    | '"' ('\\' . | '""' | ~["\\])* '"'
    ;

NUMBER
    : [0-9]+ ('.' [0-9]+)?
    ;

IDENTIFIER
    : [A-Za-z_] [A-Za-z0-9_$]*
    ;

LINE_COMMENT
    : '--' ~[\r\n]* -> channel(HIDDEN)
    ;

BLOCK_COMMENT
    : '/*' .*? '*/' -> channel(HIDDEN)
    ;

WS
    : [ \t\r\n]+ -> channel(HIDDEN)
    ;

OTHER
    : .
    ;
