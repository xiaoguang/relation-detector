grammar CommonRelationSql;

/*
 * Portable SQL subset grammar for the common token-event path.
 *
 * This grammar is intentionally smaller than the dialect grammars. It covers
 * cross-dialect SQL structures that MySQL and PostgreSQL both commonly support:
 * SELECT, CTE, derived tables, JOIN ... ON, comma rowsets, EXISTS, scalar and
 * tuple IN subqueries, INSERT ... SELECT, simple UPDATE SET ... WHERE, and
 * DELETE ... WHERE. Dialect-only syntax belongs in adaptor grammars.
 */

script
    : statement* EOF
    ;

statement
    : selectStatement SEMI?
    | insertSelectStatement SEMI?
    | updateStatement SEMI?
    | deleteStatement SEMI?
    | createTableStatement SEMI?
    | alterTableStatement SEMI?
    | createIndexStatement SEMI?
    | unknownStatement SEMI?
    | SEMI
    ;

unknownStatement
    : sqlToken+
    ;

selectStatement
    : withClause? querySpecification
    ;

withClause
    : WITH commonTableExpression (COMMA commonTableExpression)*
    ;

commonTableExpression
    : identifier (LPAREN identifierList RPAREN)? AS LPAREN selectStatement RPAREN
    ;

querySpecification
    : SELECT selectList fromClause? whereClause? groupByClause? havingClause? orderByClause? limitClause?
    ;

selectList
    : selectItem (COMMA selectItem)*
    ;

selectItem
    : STAR
    | expression (AS? identifier)?
    | selectItemFallback (AS? identifier)?
    ;

selectItemFallback
    : selectItemFallbackToken+
    ;

selectItemFallbackToken
    : LPAREN selectItemFallbackToken* RPAREN
    | ~(COMMA | FROM | RPAREN | SEMI)
    ;

fromClause
    : FROM tableReference (COMMA tableReference)*
    ;

tableReference
    : tablePrimary joinClause*
    ;

tablePrimary
    : qualifiedName tableAlias?                         # namedTablePrimary
    | LPAREN selectStatement RPAREN tableAlias?         # derivedTablePrimary
    ;

tableAlias
    : AS? identifier
    ;

joinClause
    : joinType? JOIN tablePrimary (ON predicate | USING LPAREN identifierList RPAREN)
    ;

joinType
    : INNER
    | LEFT OUTER?
    | RIGHT OUTER?
    | FULL OUTER?
    | CROSS
    ;

whereClause
    : WHERE predicate
    ;

groupByClause
    : GROUP BY expressionList
    ;

havingClause
    : HAVING predicate
    ;

orderByClause
    : ORDER BY expression (COMMA expression)*
    ;

limitClause
    : LIMIT NUMBER
    ;

insertSelectStatement
    : INSERT INTO qualifiedName LPAREN identifierList RPAREN selectStatement
    ;

updateStatement
    : UPDATE tablePrimary SET assignmentList whereClause?
    ;

assignmentList
    : assignment (COMMA assignment)*
    ;

assignment
    : qualifiedName EQ expression
    ;

deleteStatement
    : DELETE FROM tablePrimary whereClause?
    ;

createTableStatement
    : CREATE tableModifier* TABLE ifNotExists? qualifiedName LPAREN tableElement (COMMA tableElement)* RPAREN
    ;

tableModifier
    : TEMPORARY
    | UNLOGGED
    ;

ifNotExists
    : IF NOT EXISTS
    ;

tableElement
    : tableForeignKey
    | primaryKeyConstraint
    | uniqueConstraint
    | tableIndexConstraint
    | columnDefinition
    ;

tableForeignKey
    : constraintName? FOREIGN KEY LPAREN identifierList RPAREN REFERENCES qualifiedName LPAREN identifierList RPAREN referentialAction*
    ;

primaryKeyConstraint
    : constraintName? PRIMARY KEY LPAREN identifierList RPAREN (USING identifier)?
    ;

uniqueConstraint
    : constraintName? UNIQUE (KEY | INDEX)? identifier? LPAREN identifierList RPAREN
    ;

tableIndexConstraint
    : (KEY | INDEX) identifier? LPAREN indexPartList RPAREN (USING identifier)?
    ;

columnDefinition
    : identifier columnDefinitionPart*
    ;

columnDefinitionPart
    : inlineColumnConstraint
    | columnDefinitionToken
    ;

columnDefinitionToken
    : identifier
    | NOT
    | literal
    | LPAREN columnDefinitionParenToken* RPAREN
    | STAR
    | PLUS
    | MINUS
    | SLASH
    | PERCENT
    | LT
    | GT
    | LE
    | GE
    | NEQ
    | AS
    | BY
    | DOT
    | OTHER
    ;

columnDefinitionParenToken
    : identifier
    | NOT
    | literal
    | COMMA
    | DOT
    | STAR
    | PLUS
    | MINUS
    | SLASH
    | PERCENT
    | LT
    | GT
    | LE
    | GE
    | NEQ
    | EQ
    | AS
    | BY
    | ON
    | UPDATE
    | SET
    | LPAREN columnDefinitionParenToken* RPAREN
    | OTHER
    ;

inlineColumnConstraint
    : PRIMARY KEY
    | UNIQUE
    | REFERENCES qualifiedName LPAREN identifierList RPAREN
    ;

alterTableStatement
    : ALTER TABLE (IF EXISTS)? ONLY? qualifiedName ADD? tableForeignKey
    ;

createIndexStatement
    : CREATE UNIQUE? INDEX CONCURRENTLY? ifNotExists? identifier (USING identifier)? ON ONLY? qualifiedName (USING identifier)? LPAREN indexPartList RPAREN createIndexTail*
    ;

createIndexTail
    : INCLUDE LPAREN indexPartList RPAREN
    | WHERE predicate
    | WITH LPAREN sqlToken* RPAREN
    | TABLESPACE identifier
    | identifier
    | literal
    | EQ
    | COMMA
    ;

constraintName
    : CONSTRAINT identifier
    ;

referentialAction
    : ON (DELETE | UPDATE) referentialActionToken+
    ;

referentialActionToken
    : identifier
    | SET
    | NULL
    ;

indexPartList
    : indexPart (COMMA indexPart)*
    ;

indexPart
    : identifier (LPAREN NUMBER RPAREN)?
    | LPAREN functionCall RPAREN
    ;

predicate
    : predicate AND predicate                                             # andPredicate
    | predicate OR predicate                                              # orPredicate
    | NOT predicate                                                       # notPredicate
    | EXISTS LPAREN selectStatement RPAREN                                # existsPredicate
    | expression IN LPAREN selectStatement RPAREN                         # inSubqueryPredicate
    | LPAREN expressionList RPAREN IN LPAREN selectStatement RPAREN       # tupleInSubqueryPredicate
    | expression IN LPAREN expressionList RPAREN                          # literalInPredicate
    | expression likeOperator expression (ESCAPE expression)?             # likePredicate
    | expression comparisonOperator expression                            # comparisonPredicate
    | LPAREN predicate RPAREN                                             # parenPredicate
    | expression                                                          # expressionPredicate
    ;

likeOperator
    : LIKE
    | NOT LIKE
    ;

comparisonOperator
    : EQ
    | LT
    | GT
    | LE
    | GE
    | NEQ
    ;

expression
    : expression arithmeticOperator expression                            # binaryExpression
    | CASE expression? caseWhenClause+ (ELSE expression)? END             # caseExpression
    | functionCall                                                        # functionExpression
    | LPAREN selectStatement RPAREN                                       # scalarSubqueryExpression
    | qualifiedName                                                       # columnExpression
    | literal                                                             # literalExpression
    | LPAREN expression RPAREN                                            # parenExpression
    ;

caseWhenClause
    : WHEN predicate THEN expression
    ;

functionCall
    : qualifiedName LPAREN (DISTINCT? expressionList | STAR)? functionCallOption* RPAREN
    ;

functionCallOption
    : ORDER BY functionCallOptionToken+
    | identifier functionCallOptionToken*
    ;

functionCallOptionToken
    : LPAREN functionCallOptionToken* RPAREN
    | ~RPAREN
    ;

arithmeticOperator
    : PLUS
    | MINUS
    | STAR
    | SLASH
    | PERCENT
    | CONCAT
    ;

expressionList
    : expression (COMMA expression)*
    ;

identifierList
    : identifier (COMMA identifier)*
    ;

qualifiedName
    : identifier (DOT identifier)*
    ;

identifier
    : IDENTIFIER
    | QUOTED_IDENTIFIER
    ;

literal
    : STRING_LITERAL
    | NUMBER
    | TRUE
    | FALSE
    | NULL
    | PARAMETER
    ;

sqlToken
    : SELECT | WITH | AS | FROM | JOIN | ON | INNER | LEFT | RIGHT | FULL
    | OUTER | CROSS | WHERE | AND | OR | NOT | EXISTS | IN | LIKE | ESCAPE
    | USING | GROUP | BY | HAVING | ORDER | LIMIT | INSERT | INTO | UPDATE
    | SET | DELETE | CASE | WHEN | THEN | ELSE | END | DISTINCT | TRUE | FALSE
    | NULL | CREATE | ALTER | TABLE | TEMPORARY | UNLOGGED | IF | ADD | CONSTRAINT
    | FOREIGN | KEY | REFERENCES | PRIMARY | UNIQUE | INDEX | CONCURRENTLY | ONLY
    | INCLUDE | TABLESPACE | IDENTIFIER | QUOTED_IDENTIFIER | STRING_LITERAL | NUMBER
    | PARAMETER | DOT | COMMA | STAR | EQ | LPAREN | RPAREN | PLUS
    | MINUS | SLASH | PERCENT | CONCAT | LT | GT | LE | GE | NEQ | OTHER
    ;

SELECT: S E L E C T;
WITH: W I T H;
AS: A S;
FROM: F R O M;
JOIN: J O I N;
ON: O N;
USING: U S I N G;
INNER: I N N E R;
LEFT: L E F T;
RIGHT: R I G H T;
FULL: F U L L;
OUTER: O U T E R;
CROSS: C R O S S;
WHERE: W H E R E;
AND: A N D;
OR: O R;
NOT: N O T;
EXISTS: E X I S T S;
IN: I N;
LIKE: L I K E;
ESCAPE: E S C A P E;
GROUP: G R O U P;
BY: B Y;
HAVING: H A V I N G;
ORDER: O R D E R;
LIMIT: L I M I T;
INSERT: I N S E R T;
INTO: I N T O;
UPDATE: U P D A T E;
SET: S E T;
DELETE: D E L E T E;
CREATE: C R E A T E;
ALTER: A L T E R;
TABLE: T A B L E;
TEMPORARY: T E M P O R A R Y;
UNLOGGED: U N L O G G E D;
IF: I F;
ADD: A D D;
CONSTRAINT: C O N S T R A I N T;
FOREIGN: F O R E I G N;
KEY: K E Y;
REFERENCES: R E F E R E N C E S;
PRIMARY: P R I M A R Y;
UNIQUE: U N I Q U E;
INDEX: I N D E X;
CONCURRENTLY: C O N C U R R E N T L Y;
ONLY: O N L Y;
INCLUDE: I N C L U D E;
TABLESPACE: T A B L E S P A C E;
CASE: C A S E;
WHEN: W H E N;
THEN: T H E N;
ELSE: E L S E;
END: E N D;
DISTINCT: D I S T I N C T;
TRUE: T R U E;
FALSE: F A L S E;
NULL: N U L L;

DOT: '.';
COMMA: ',';
STAR: '*';
EQ: '=';
LPAREN: '(';
RPAREN: ')';
SEMI: ';';
PLUS: '+';
MINUS: '-';
SLASH: '/';
PERCENT: '%';
CONCAT: '||';
LE: '<=';
GE: '>=';
NEQ: '<>' | '!=';
LT: '<';
GT: '>';

QUOTED_IDENTIFIER
    : '`' (~'`' | '``')* '`'
    | '"' (~'"' | '""')* '"'
    ;

STRING_LITERAL
    : '\'' ('\'\'' | ~'\'')* '\''
    ;

NUMBER
    : [0-9]+ ('.' [0-9]+)?
    ;

PARAMETER
    : '?' | ':' [A-Za-z_] [A-Za-z0-9_]*
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

fragment A: [aA];
fragment B: [bB];
fragment C: [cC];
fragment D: [dD];
fragment E: [eE];
fragment F: [fF];
fragment G: [gG];
fragment H: [hH];
fragment I: [iI];
fragment J: [jJ];
fragment K: [kK];
fragment L: [lL];
fragment M: [mM];
fragment N: [nN];
fragment O: [oO];
fragment P: [pP];
fragment Q: [qQ];
fragment R: [rR];
fragment S: [sS];
fragment T: [tT];
fragment U: [uU];
fragment V: [vV];
fragment W: [wW];
fragment X: [xX];
fragment Y: [yY];
fragment Z: [zZ];
