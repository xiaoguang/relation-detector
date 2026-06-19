grammar RelationSql;

/*
 * A deliberately tolerant ANTLR grammar for shared Token/Event parser support.
 *
 * This is not intended to be a complete MySQL/PostgreSQL grammar. It gives the
 * Java code a real ANTLR lexer/parser/token stream that can survive procedural
 * bodies, dialect-specific clauses, partial log statements, and DDL fragments.
 * Higher-level visitors then extract the small set of relationship-relevant
 * facts required by the Token/Event extractors. Future tasks can replace this grammar with richer
 * dialect grammars while keeping the StructuredSqlParser API stable.
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
    : '`' (~'`' | '``')* '`'
    | '"' (~'"' | '""')* '"'
    ;

STRING_LITERAL
    : '\'' ('\'\'' | ~'\'')* '\''
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
