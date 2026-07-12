grammar OracleRelationSql;

/*
 * Oracle token-event structural grammar.
 *
 * This grammar covers the portable token-event subset plus Oracle-specific lexical and structural extensions:
 * SELECT, CTE, derived tables, JOIN ... ON, comma rowsets, EXISTS, scalar and
 * tuple IN subqueries, INSERT ... SELECT, simple UPDATE SET ... WHERE, and
 * DELETE ... WHERE. Full Oracle version syntax belongs in full-grammar; this grammar is the token-event fallback subset.
 */

script
    : statement* EOF
    ;

statement
    : createTriggerStatement
    | routineStartStatement
    | blockStartStatement SEMI?
    | blockEndStatement SEMI?
    | declarationStatement SEMI?
    | controlStartStatement
    | openCursorSelectStatement SEMI?
    | selectStatement SEMI?
    | insertSelectStatement SEMI?
    | updateStatement SEMI?
    | mergeStatement SEMI?
    | deleteStatement SEMI?
    | createTableStatement SEMI?
    | alterTableStatement SEMI?
    | createIndexStatement SEMI?
    | createViewStatement SEMI?
    | commentStatement SEMI?
    | unknownStatement SEMI?
    | SLASH
    | SEMI
    ;

createViewStatement
    : CREATE (OR REPLACE)? MATERIALIZED? VIEW qualifiedName AS selectStatement
    ;

unknownStatement
    : ~(SEMI | SLASH)+
    ;

routineStartStatement
    : CREATE (OR REPLACE)? (PROCEDURE | FUNCTION) routineHeaderToken* BEGIN
    ;

createTriggerStatement
    : CREATE (OR REPLACE)? TRIGGER qualifiedName triggerBeforeOnToken* ON qualifiedName triggerAfterOnToken* BEGIN
    ;

triggerBeforeOnToken
    : ~(ON | BEGIN)
    ;

triggerAfterOnToken
    : ~BEGIN
    ;

routineHeaderToken
    : cursorDeclaration
    | ~(BEGIN | CURSOR)
    ;

cursorDeclaration
    : CURSOR identifier IS selectStatement SEMI
    ;

blockStartStatement
    : BEGIN
    ;

blockEndStatement
    : END (IF | LOOP | WHILE | REPEAT)?
    ;

declarationStatement
    : DECLARE ~SEMI+
    ;

controlStartStatement
    : IF ~THEN* THEN
    | ELSEIF ~THEN* THEN
    | ELSE
    | WHILE ~LOOP* LOOP
    | FOR ~LOOP* LOOP
    | identifier OTHER LOOP
    | LOOP
    | REPEAT
    ;

openCursorSelectStatement
    : OPEN identifier FOR selectStatement
    ;

selectStatement
    : withClause? querySpecification setOperation*
    ;

withClause
    : WITH commonTableExpression (COMMA commonTableExpression)*
    ;

setOperation
    : (UNION ALL? | INTERSECT | EXCEPT) querySpecification
    ;

commonTableExpression
    : identifier (LPAREN identifierList RPAREN)? AS cteMaterialization? LPAREN selectStatement RPAREN
    ;

cteMaterialization
    : MATERIALIZED
    | NOT MATERIALIZED
    ;

querySpecification
    : SELECT selectModifier? selectList intoClause? fromClause? whereClause? groupByClause? havingClause? orderByClause? limitClause?
    ;

selectModifier
    : DISTINCT (ON LPAREN expressionList RPAREN)?
    ;

intoClause
    : INTO qualifiedName (COMMA qualifiedName)*
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
    : ONLY? qualifiedName tableSampleClause? tableAlias? tableSampleClause?  # namedTablePrimary
    | LPAREN selectStatement RPAREN tableAlias?                              # derivedTablePrimary
    | ROWS FROM LPAREN rowsFromItem (COMMA rowsFromItem)* RPAREN tableAlias? # rowsFromTablePrimary
    | LATERAL? qualifiedName LPAREN looseToken* RPAREN withOrdinality? tableAlias? # functionRowsetPrimary
    ;

tableAlias
    : AS? identifier (LPAREN identifierList RPAREN)?
    ;

tableSampleClause
    : TABLESAMPLE identifier LPAREN looseToken* RPAREN
    ;

rowsFromItem
    : qualifiedName LPAREN looseToken* RPAREN (AS LPAREN looseToken* RPAREN)?
    ;

withOrdinality
    : WITH ORDINALITY
    ;

looseToken
    : SELECT | WITH | AS | FROM | JOIN | ON | INNER | LEFT | RIGHT | FULL
    | OUTER | CROSS | WHERE | AND | OR | NOT | EXISTS | IN | BETWEEN | LIKE | ESCAPE | IS
    | USING | GROUP | BY | HAVING | ORDER | LIMIT | ASC | DESC | NULLS | FIRST | LAST | INSERT | INTO | UPDATE
    | SET | DELETE | CASE | WHEN | THEN | ELSE | END | DISTINCT | EXTRACT | TRUE | FALSE
    | NULL | CREATE | ALTER | TABLE | TEMPORARY | UNLOGGED | IF | ADD | CONSTRAINT
    | FOREIGN | KEY | REFERENCES | PRIMARY | UNIQUE | INDEX | CONCURRENTLY | ONLY
    | INCLUDE | TABLESPACE | MATERIALIZED | ROWS | TABLESAMPLE | LATERAL | ORDINALITY | OVER
    | DATE | CURRENT_DATE | CURRENT_TIMESTAMP | SYSTIMESTAMP
    | UNION | ALL | INTERSECT | EXCEPT | FETCH | FIRST | ASC | DESC | NULLS | LAST
    | IDENTIFIER | QUOTED_IDENTIFIER | STRING_LITERAL | DOLLAR_QUOTED_STRING | NUMBER
    | PARAMETER | DOT | COMMA | STAR | EQ | LBRACKET | RBRACKET | PLUS
    | MINUS | SLASH | PERCENT | CONCAT | LT | GT | LE | GE | NEQ | OTHER
    ;

joinClause
    : joinType? JOIN tablePrimary (ON predicate | USING LPAREN identifierList RPAREN usingAlias?)?
    ;

joinType
    : INNER
    | LEFT OUTER?
    | RIGHT OUTER?
    | FULL OUTER?
    | CROSS
    ;

usingAlias
    : AS? identifier
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
    : ORDER BY orderByItem (COMMA orderByItem)*
    ;

orderByItem
    : expression (ASC | DESC)? (NULLS (FIRST | LAST))?
    ;

limitClause
    : LIMIT NUMBER
    | FETCH FIRST NUMBER ROWS ONLY
    ;

insertSelectStatement
    : INSERT INTO qualifiedName LPAREN identifierList RPAREN selectStatement returningClause?
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

mergeStatement
    : MERGE INTO tablePrimary USING tablePrimary ON predicate mergeWhenClause+ returningClause?
    ;

mergeWhenClause
    : WHEN mergeWhenConditionToken* THEN mergeAction
    ;

mergeWhenConditionToken
    : MATCHED
    | NOT
    | BY
    | AND
    | identifier
    | literal
    | comparisonOperator
    | DOT
    | COMMA
    | LPAREN mergeWhenConditionToken* RPAREN
    | OTHER
    ;

mergeAction
    : UPDATE SET assignmentList                    # mergeUpdateAction
    | INSERT LPAREN identifierList RPAREN VALUES LPAREN expressionList RPAREN # mergeInsertAction
    | DELETE                                       # mergeDeleteAction
    | DO NOTHING                                   # mergeDoNothingAction
    ;

returningClause
    : RETURNING sqlToken+
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
    | DATE
    | CURRENT_DATE
    | CURRENT_TIMESTAMP
    | SYSTIMESTAMP
    | INTERVAL
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
    | AND
    | OR
    | BETWEEN
    | IS
    | literal
    | DATE
    | CURRENT_DATE
    | CURRENT_TIMESTAMP
    | SYSTIMESTAMP
    | INTERVAL
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
    | CASE
    | WHEN
    | THEN
    | ELSE
    | END
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

commentStatement
    : COMMENT ON TABLE qualifiedName IS literal
    | COMMENT ON COLUMN commentColumnTarget IS literal
    ;

commentColumnTarget
    : identifier DOT identifier (DOT identifier)?
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
    | expression NOT? IN LPAREN selectStatement RPAREN                    # inSubqueryPredicate
    | LPAREN expressionList RPAREN NOT? IN LPAREN selectStatement RPAREN  # tupleInSubqueryPredicate
    | expression NOT? IN LPAREN expressionList RPAREN                     # literalInPredicate
    | expression NOT? BETWEEN expression AND expression                    # betweenPredicate
    | expression likeOperator expression (ESCAPE expression)?             # likePredicate
    | expression IS NOT? NULL                                           # isNullPredicate
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
    | MINUS expression                                                    # unaryExpression
    | CASE expression? caseWhenClause+ (ELSE expression)? END             # caseExpression
    | EXTRACT LPAREN identifier FROM expression RPAREN                    # extractExpression
    | CAST LPAREN expression AS identifier RPAREN                         # castExpression
    | functionCall withinGroupClause? windowClause?                       # functionExpression
    | LPAREN selectStatement RPAREN                                       # scalarSubqueryExpression
    | triggerPseudoColumn                                                 # triggerPseudoColumnExpression
    | qualifiedName                                                       # columnExpression
    | literal                                                             # literalExpression
    | LPAREN expression RPAREN                                            # parenExpression
    ;

triggerPseudoColumn
    : PARAMETER DOT identifier
    ;

caseWhenClause
    : WHEN predicate THEN expression
    ;

functionCall
    : qualifiedName LPAREN (DISTINCT? expressionList | STAR)? functionCallOption* RPAREN
    ;

withinGroupClause
    : WITHIN GROUP LPAREN ORDER BY functionCallOptionToken+ RPAREN
    ;

windowClause
    : OVER LPAREN functionCallOptionToken* RPAREN
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
    | COMMENT
    | COLUMN
    | REPLACE
    ;

literal
    : STRING_LITERAL
    | NUMBER
    | TRUE
    | FALSE
    | NULL
    | PARAMETER
    | DATE STRING_LITERAL
    | INTERVAL STRING_LITERAL identifier
    | CURRENT_DATE
    | CURRENT_TIMESTAMP
    | SYSTIMESTAMP
    ;

sqlToken
    : SELECT | WITH | AS | FROM | JOIN | ON | INNER | LEFT | RIGHT | FULL
    | OUTER | CROSS | WHERE | AND | OR | NOT | EXISTS | IN | BETWEEN | LIKE | ESCAPE | IS
    | USING | GROUP | WITHIN | BY | HAVING | ORDER | LIMIT | ASC | DESC | NULLS | FIRST | LAST | INSERT | INTO | UPDATE
    | SET | DELETE | MERGE | MATCHED | VALUES | RETURNING | DO | NOTHING | CURSOR
    | CASE | WHEN | THEN | ELSE | END | DISTINCT | EXTRACT | CAST | TRUE | FALSE
    | NULL | CREATE | ALTER | TABLE | TEMPORARY | UNLOGGED | BEGIN | IF | ELSEIF | WHILE
    | LOOP | REPEAT | DECLARE | PROCEDURE | FUNCTION | TRIGGER | OR | REPLACE | FOR
    | ADD | CONSTRAINT
    | FOREIGN | KEY | REFERENCES | PRIMARY | UNIQUE | INDEX | CONCURRENTLY | ONLY
    | INCLUDE | TABLESPACE | MATERIALIZED | ROWS | TABLESAMPLE | LATERAL | ORDINALITY | OVER
    | DATE | CURRENT_DATE | CURRENT_TIMESTAMP | SYSTIMESTAMP | INTERVAL
    | UNION | ALL | INTERSECT | EXCEPT | FETCH | FIRST | ASC | DESC | NULLS | LAST
    | IDENTIFIER | QUOTED_IDENTIFIER | STRING_LITERAL | DOLLAR_QUOTED_STRING | NUMBER
    | PARAMETER | DOT | COMMA | STAR | EQ | LPAREN | RPAREN | LBRACKET | RBRACKET | PLUS
    | MINUS | SLASH | PERCENT | CONCAT | LT | GT | LE | GE | NEQ | OTHER
    ;

SELECT: S E L E C T;
WITH: W I T H;
UNION: U N I O N;
ALL: A L L;
INTERSECT: I N T E R S E C T;
EXCEPT: E X C E P T;
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
BETWEEN: B E T W E E N;
IS: I S;
LIKE: L I K E;
ESCAPE: E S C A P E;
GROUP: G R O U P;
WITHIN: W I T H I N;
BY: B Y;
HAVING: H A V I N G;
ORDER: O R D E R;
LIMIT: L I M I T;
FETCH: F E T C H;
FIRST: F I R S T;
ASC: A S C;
DESC: D E S C;
NULLS: N U L L S;
LAST: L A S T;
INSERT: I N S E R T;
INTO: I N T O;
UPDATE: U P D A T E;
SET: S E T;
DELETE: D E L E T E;
MERGE: M E R G E;
MATCHED: M A T C H E D;
VALUES: V A L U E S;
RETURNING: R E T U R N I N G;
DO: D O;
NOTHING: N O T H I N G;
CREATE: C R E A T E;
OPEN: O P E N;
ALTER: A L T E R;
TABLE: T A B L E;
COMMENT: C O M M E N T;
COLUMN: C O L U M N;
TEMPORARY: T E M P O R A R Y;
UNLOGGED: U N L O G G E D;
BEGIN: B E G I N;
IF: I F;
ELSEIF: E L S E I F;
WHILE: W H I L E;
LOOP: L O O P;
REPEAT: R E P E A T;
DECLARE: D E C L A R E;
PROCEDURE: P R O C E D U R E;
FUNCTION: F U N C T I O N;
TRIGGER: T R I G G E R;
CURSOR: C U R S O R;
REPLACE: R E P L A C E;
FOR: F O R;
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
MATERIALIZED: M A T E R I A L I Z E D;
VIEW: V I E W;
ROWS: R O W S;
TABLESAMPLE: T A B L E S A M P L E;
LATERAL: L A T E R A L;
ORDINALITY: O R D I N A L I T Y;
OVER: O V E R;
CASE: C A S E;
WHEN: W H E N;
THEN: T H E N;
ELSE: E L S E;
END: E N D;
DISTINCT: D I S T I N C T;
EXTRACT: E X T R A C T;
CAST: C A S T;
TRUE: T R U E;
FALSE: F A L S E;
NULL: N U L L;
DATE: D A T E;
CURRENT_DATE: C U R R E N T '_' D A T E;
CURRENT_TIMESTAMP: C U R R E N T '_' T I M E S T A M P;
SYSTIMESTAMP: S Y S T I M E S T A M P;
INTERVAL: I N T E R V A L;

DOT: '.';
COMMA: ',';
STAR: '*';
EQ: '=';
LPAREN: '(';
RPAREN: ')';
LBRACKET: '[';
RBRACKET: ']';
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
    : '"' (~'"' | '""')* '"'
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
