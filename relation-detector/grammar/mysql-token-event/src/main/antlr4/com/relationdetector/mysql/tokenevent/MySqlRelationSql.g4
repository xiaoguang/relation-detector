grammar MySqlRelationSql;

/*
 * MySQL token-event structural grammar.
 *
 * This grammar covers the portable token-event subset plus MySQL-specific lexical and structural extensions:
 * SELECT, CTE, derived tables, JOIN ... ON, comma rowsets, EXISTS, scalar and
 * tuple IN subqueries, INSERT ... SELECT, simple UPDATE SET ... WHERE, and
 * DELETE ... WHERE. Full MySQL 8.0 syntax belongs in full-grammar; this grammar is the token-event fallback subset.
 */

script
    : statement* EOF
    ;

statement
    : triggerStartStatement
    | routineStartStatement
    | blockStartStatement SEMI?
    | blockEndStatement SEMI?
    | cursorDeclarationStatement SEMI?
    | declarationStatement SEMI?
    | controlStartStatement
    | selectStatement SEMI?
    | insertSelectStatement SEMI?
    | insertValuesStatement SEMI?
    | updateStatement SEMI?
    | deleteStatement SEMI?
    | createTableStatement SEMI?
    | alterTableStatement SEMI?
    | createIndexStatement SEMI?
    | createViewStatement SEMI?
    | unknownStatement SEMI?
    | SEMI
    ;

createViewStatement
    : CREATE (OR REPLACE)? VIEW qualifiedName AS selectStatement
    ;

unknownStatement
    : ~SEMI+
    ;

routineStartStatement
    : CREATE (OR REPLACE)? (PROCEDURE | FUNCTION) qualifiedName routineParameterList routineHeaderToken* BEGIN
    ;

routineParameterList
    : LPAREN (routineParameter (COMMA routineParameter)*)? RPAREN
    ;

routineParameter
    : (IN | OUT | INOUT)? identifier routineParameterTypeToken+
    ;

routineParameterTypeToken
    : identifier
    | LPAREN routineParameterTypeParenToken* RPAREN
    ;

routineParameterTypeParenToken
    : identifier
    | literal
    | COMMA
    ;

triggerStartStatement
    : CREATE (OR REPLACE)? TRIGGER identifier (BEFORE | AFTER)
      (INSERT | UPDATE | DELETE) ON qualifiedName FOR EACH ROW BEGIN
    ;

routineHeaderToken
    : ~BEGIN
    ;

blockStartStatement
    : BEGIN
    ;

blockEndStatement
    : END (IF | LOOP | WHILE | REPEAT)?
    ;

declarationStatement
    : DECLARE identifier declarationToken*
    ;

declarationToken
    : ~SEMI
    ;

cursorDeclarationStatement
    : DECLARE identifier CURSOR FOR selectStatement
    ;

controlStartStatement
    : IF ~THEN* THEN
    | ELSEIF ~THEN* THEN
    | ELSE
    | WHILE ~DO* DO
    | FOR ~LOOP* LOOP
    | identifier OTHER LOOP
    | LOOP
    | REPEAT
    ;

selectStatement
    : withClause? querySpecification unionSelect*
    ;

unionSelect
    : UNION selectModifier* querySpecification
    ;

withClause
    : WITH RECURSIVE? commonTableExpression (COMMA commonTableExpression)*
    ;

commonTableExpression
    : identifier (LPAREN identifierList RPAREN)? AS LPAREN selectStatement RPAREN
    ;

querySpecification
    : SELECT selectModifier* selectList selectIntoClause? fromClause? whereClause? groupByClause? havingClause? orderByClause? limitClause?
    ;

selectModifier
    : STRAIGHT_JOIN
    | DISTINCT
    | ALL
    ;

selectList
    : selectItem (COMMA selectItem)*
    ;

selectIntoClause
    : INTO identifier (COMMA identifier)*
    ;

selectItem
    : STAR
    | booleanSelectExpression (AS? identifier)?
    | expression (AS? identifier)?
    | selectItemFallback (AS? identifier)?
    ;

selectItemFallback
    : selectItemFallbackToken+
    ;

selectItemFallbackToken
    : LPAREN selectItemNestedFallbackToken* RPAREN
    | ~(COMMA | FROM | RPAREN | SEMI)
    ;

selectItemNestedFallbackToken
    : LPAREN selectStatement RPAREN
    | LPAREN selectItemNestedFallbackToken* RPAREN
    | ~(RPAREN | SELECT)
    ;

fromClause
    : FROM tableReference (COMMA tableReference)*
    ;

tableReference
    : tablePrimary joinClause*
    ;

tablePrimary
    : qualifiedName partitionClause? tableAlias? indexHint* # namedTablePrimary
    | LPAREN selectStatement RPAREN tableAlias?         # derivedTablePrimary
    | JSON_TABLE LPAREN jsonTableContent* RPAREN tableAlias? # jsonTablePrimary
    | LBRACE OJ tableReference RBRACE                   # odbcTablePrimary
    ;

partitionClause
    : PARTITION LPAREN identifierList RPAREN
    ;

indexHint
    : (USE | IGNORE | FORCE) (INDEX | KEY) (FOR JOIN)? LPAREN indexHintNameList RPAREN
    ;

indexHintNameList
    : indexHintName (COMMA indexHintName)*
    ;

indexHintName
    : identifier
    | PRIMARY
    ;

tableAlias
    : AS? identifier
    ;

joinClause
    : CROSS JOIN tablePrimary
    | joinType? joinOperator tablePrimary (ON predicate | USING LPAREN identifierList RPAREN usingAlias?)
    ;

joinType
    : INNER
    | LEFT OUTER?
    | RIGHT OUTER?
    | FULL OUTER?
    ;

joinOperator
    : JOIN
    | STRAIGHT_JOIN
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
    : expression identifier?
    ;

limitClause
    : LIMIT NUMBER
    ;

insertSelectStatement
    : INSERT INTO qualifiedName LPAREN identifierList RPAREN selectStatement onDuplicateKeyUpdateClause?
    ;

insertValuesStatement
    : INSERT INTO qualifiedName LPAREN identifierList RPAREN VALUES valueRow (COMMA valueRow)*
      onDuplicateKeyUpdateClause?
    ;

valueRow
    : LPAREN expressionList? RPAREN
    ;

onDuplicateKeyUpdateClause
    : ON DUPLICATE KEY UPDATE onDuplicateKeyUpdateToken+
    ;

onDuplicateKeyUpdateToken
    : LPAREN onDuplicateKeyUpdateToken* RPAREN
    | ~SEMI
    ;

updateStatement
    : withClause? UPDATE tableReference (COMMA tableReference)* SET assignmentList whereClause?
    ;

assignmentList
    : assignment (COMMA assignment)*
    ;

assignment
    : qualifiedName EQ expression
    ;

deleteStatement
    : DELETE FROM deleteTarget USING tableReference whereClause?
    | DELETE deleteTarget? FROM tableReference whereClause?
    ;

deleteTarget
    : identifier
    ;

jsonTableContent
    : identifier
    | literal
    | qualifiedName
    | LPAREN jsonTableContent* RPAREN
    | COMMA
    | DOT
    | STAR
    | EQ
    | OTHER
    ;

createTableStatement
    : CREATE tableModifier* TABLE ifNotExists? qualifiedName LPAREN tableElement (COMMA tableElement)* RPAREN createTableTail*
    ;

createTableTail
    : identifier
    | literal
    | EQ
    | COMMA
    | DOT
    | LPAREN
    | RPAREN
    | OTHER
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
    | generatedColumnClause
    | columnDefinitionToken
    ;

generatedColumnClause
    : GENERATED ALWAYS? AS LPAREN generatedColumnBooleanExpression RPAREN (STORED | VIRTUAL)?
    ;

generatedColumnBooleanExpression
    : generatedColumnComparison ((AND | OR) generatedColumnComparison)*
    ;

generatedColumnComparison
    : generatedColumnOperand comparisonOperator generatedColumnOperand
    | LPAREN generatedColumnBooleanExpression RPAREN
    ;

generatedColumnOperand
    : qualifiedName
    | literal
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
    | ON
    | UPDATE
    | GENERATED
    | ALWAYS
    | STORED
    | VIRTUAL
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
    | expression IS NOT? NULL                                             # isNullPredicate
    | expression likeOperator expression (ESCAPE expression)?             # likePredicate
    | expression NOT? BETWEEN expression AND expression                    # betweenPredicate
    | expression comparisonOperator expression                            # comparisonPredicate
    | LPAREN predicate RPAREN                                             # parenPredicate
    | expression                                                          # expressionPredicate
    ;

booleanSelectExpression
    : booleanSelectExpression AND booleanSelectExpression                 # selectAndBoolean
    | booleanSelectExpression OR booleanSelectExpression                  # selectOrBoolean
    | NOT booleanSelectExpression                                         # selectNotBoolean
    | expression IS NOT? NULL                                             # selectIsNullBoolean
    | expression likeOperator expression (ESCAPE expression)?             # selectLikeBoolean
    | expression NOT? BETWEEN expression AND expression                    # selectBetweenBoolean
    | expression comparisonOperator expression                            # selectComparisonBoolean
    | LPAREN booleanSelectExpression RPAREN                               # selectParenBoolean
    ;

likeOperator
    : LIKE
    | NOT LIKE
    ;

comparisonOperator
    : EQ
    | NULL_SAFE_EQ
    | LT
    | GT
    | LE
    | GE
    | NEQ
    ;

expression
    : expression arithmeticOperator expression                            # binaryExpression
    | (PLUS | MINUS) expression                                           # unaryExpression
    | CASE selector=expression? caseWhenClause+ (ELSE elseBranch=expression)? END # caseExpression
    | INTERVAL expression identifier                                      # intervalExpression
    | IF LPAREN predicate COMMA expression COMMA expression RPAREN        # ifExpression
    | CAST LPAREN expression AS typeName RPAREN                           # castExpression
    | functionCall windowSpecification?                                  # functionExpression
    | LPAREN selectStatement RPAREN                                       # scalarSubqueryExpression
    | qualifiedName                                                       # columnExpression
    | literal                                                             # literalExpression
    | LPAREN expression RPAREN                                            # parenExpression
    ;

caseWhenClause
    : WHEN predicate THEN expression
    ;

functionCall
    : qualifiedName LPAREN (DISTINCT? expressionList | STAR)? functionOrderBy? functionSeparator? RPAREN
    ;

typeName
    : qualifiedName (LPAREN NUMBER (COMMA NUMBER)? RPAREN)?
    ;

functionOrderBy
    : ORDER BY orderByItem (COMMA orderByItem)*
    ;

functionSeparator
    : SEPARATOR expression
    ;

windowSpecification
    : OVER LPAREN windowSpecificationToken* RPAREN
    ;

windowSpecificationToken
    : LPAREN windowSpecificationToken* RPAREN
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
    | REPLACE
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
    : SELECT | WITH | RECURSIVE | AS | FROM | JOIN | STRAIGHT_JOIN | ON | INNER | LEFT | RIGHT | FULL
    | OUTER | CROSS | WHERE | AND | OR | NOT | EXISTS | IN | LIKE | ESCAPE | BETWEEN
    | USING | GROUP | BY | HAVING | ORDER | LIMIT | UNION | INSERT | INTO | UPDATE
    | SET | DELETE | CASE | WHEN | THEN | ELSE | END | DISTINCT | ALL | TRUE | FALSE
    | NULL | CREATE | ALTER | TABLE | TEMPORARY | UNLOGGED | BEGIN | IF | ELSEIF | WHILE | DO
    | LOOP | REPEAT | DECLARE | CURSOR | PROCEDURE | FUNCTION | TRIGGER | OR | REPLACE
    | BEFORE | AFTER | EACH | ROW | VALUES | SEPARATOR
    | OUT | INOUT
    | ADD | CONSTRAINT
    | FOREIGN | KEY | REFERENCES | PRIMARY | UNIQUE | INDEX | CONCURRENTLY | ONLY
    | INCLUDE | TABLESPACE | PARTITION | USE | IGNORE | FORCE | FOR | OJ | JSON_TABLE | INTERVAL | OVER
    | IS | CAST | IDENTIFIER | QUOTED_IDENTIFIER | STRING_LITERAL | NUMBER
    | PARAMETER | DOT | COMMA | STAR | EQ | NULL_SAFE_EQ | LPAREN | RPAREN | PLUS
    | MINUS | SLASH | PERCENT | CONCAT | LT | GT | LE | GE | NEQ | LBRACE | RBRACE | OTHER
    ;

SELECT: S E L E C T;
WITH: W I T H;
RECURSIVE: R E C U R S I V E;
AS: A S;
FROM: F R O M;
JOIN: J O I N;
STRAIGHT_JOIN: S T R A I G H T '_' J O I N;
JSON_TABLE: J S O N '_' T A B L E;
INTERVAL: I N T E R V A L;
OVER: O V E R;
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
GENERATED: G E N E R A T E D;
ALWAYS: A L W A Y S;
STORED: S T O R E D;
VIRTUAL: V I R T U A L;
NOT: N O T;
EXISTS: E X I S T S;
IN: I N;
OUT: O U T;
INOUT: I N O U T;
IS: I S;
LIKE: L I K E;
ESCAPE: E S C A P E;
BETWEEN: B E T W E E N;
GROUP: G R O U P;
BY: B Y;
HAVING: H A V I N G;
ORDER: O R D E R;
LIMIT: L I M I T;
UNION: U N I O N;
ALL: A L L;
INSERT: I N S E R T;
INTO: I N T O;
UPDATE: U P D A T E;
SET: S E T;
DUPLICATE: D U P L I C A T E;
DELETE: D E L E T E;
CREATE: C R E A T E;
ALTER: A L T E R;
TABLE: T A B L E;
TEMPORARY: T E M P O R A R Y;
UNLOGGED: U N L O G G E D;
BEGIN: B E G I N;
IF: I F;
ELSEIF: E L S E I F;
WHILE: W H I L E;
DO: D O;
LOOP: L O O P;
REPEAT: R E P E A T;
DECLARE: D E C L A R E;
CURSOR: C U R S O R;
PROCEDURE: P R O C E D U R E;
FUNCTION: F U N C T I O N;
TRIGGER: T R I G G E R;
BEFORE: B E F O R E;
AFTER: A F T E R;
EACH: E A C H;
ROW: R O W;
VALUES: V A L U E S;
SEPARATOR: S E P A R A T O R;
REPLACE: R E P L A C E;
CAST: C A S T;
VIEW: V I E W;
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
PARTITION: P A R T I T I O N;
USE: U S E;
IGNORE: I G N O R E;
FORCE: F O R C E;
FOR: F O R;
OJ: O J;
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
LBRACE: '{';
RBRACE: '}';
SEMI: ';';
PLUS: '+';
MINUS: '-';
SLASH: '/';
PERCENT: '%';
CONCAT: '||';
NULL_SAFE_EQ: '<=>';
LE: '<=';
GE: '>=';
NEQ: '<>' | '!=';
LT: '<';
GT: '>';

QUOTED_IDENTIFIER
    : '`' (~'`' | '``')* '`'
    ;

STRING_LITERAL
    : '\'' ('\'\'' | ~'\'')* '\''
    | '"' ('\\' . | '""' | ~["\\])* '"'
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
