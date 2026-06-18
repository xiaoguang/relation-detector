grammar PostgresRelationSql;

/*
 * PostgreSQL structural grammar for ANTLR shadow mode.
 *
 * This remains tolerant by design, but it is no longer the same generated
 * parser as MySQL. PostgreSQL-specific syntax can now grow here independently:
 * dollar-quoted strings, double-quoted identifiers, LATERAL/table-function
 * rowsets, and future PostgreSQL-version feature gates.
 *
 * PostgreSQL-specific choice in this first grammar:
 *   - double quoted identifiers are quoted identifiers;
 *   - backticks are ordinary OTHER tokens and therefore cannot become table
 *     references through the structured event visitor.
 */
script
    : sqlToken* EOF
    ;

sqlToken
    : IDENTIFIER
    | QUOTED_IDENTIFIER
    | STRING_LITERAL
    | DOLLAR_QUOTED_STRING
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
    : '"' (~'"' | '""')* '"'
    ;

STRING_LITERAL
    : '\'' ('\\' . | '\'\'' | ~['\\])* '\''
    ;

DOLLAR_QUOTED_STRING
    : '$$' .*? '$$'
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
