parser grammar OracleFullGrammerParser;
options { tokenVocab = OracleFullGrammerLexer; }

/*
 * Oracle scoped full-grammer.
 *
 * This scoped grammar covers the currently implemented generated-parser subset for Oracle:
 * SELECT, CTE, derived tables, JOIN ... ON, comma rowsets, EXISTS, scalar and
 * tuple IN subqueries, INSERT ... SELECT, simple UPDATE SET ... WHERE, and
 * DELETE ... WHERE. Oracle version-specific syntax is added here incrementally as official grammar support is implemented.
 */

script
    : statement* EOF
    ;

statement
    : routineStartStatement
    | blockStartStatement SEMI?
    | blockEndStatement SEMI?
    | declarationStatement SEMI?
    | controlStartStatement
    | selectStatement SEMI?
    | insertSelectStatement SEMI?
    | insertValuesStatement SEMI?
    | updateStatement SEMI?
    | mergeStatement SEMI?
    | deleteStatement SEMI?
    | returnStatement SEMI?
    | plsqlAssignmentStatement SEMI?
    | createTableStatement SEMI?
    | alterTableStatement SEMI?
    | createIndexStatement SEMI?
    | SLASH
    | SEMI
    ;

routineStartStatement
    : CREATE (OR REPLACE)? (PROCEDURE | FUNCTION | TRIGGER) routineHeaderToken* BEGIN
    ;

routineHeaderToken
    : ~(BEGIN | MEMOPTIMIZE | SQL_MACRO | VECTOR)
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
    | ELSIF ~THEN* THEN
    | ELSE
    | WHILE ~LOOP* LOOP
    | FOR ~LOOP* LOOP
    | LOOP
    | REPEAT
    ;

selectStatement
    : withClause? querySpecification
    ;

withClause
    : WITH commonTableExpression (COMMA commonTableExpression)*
    ;

commonTableExpression
    : identifier (LPAREN identifierList RPAREN)? AS cteMaterialization? LPAREN selectStatement RPAREN
    ;

cteMaterialization
    : MATERIALIZED
    | NOT MATERIALIZED
    ;

querySpecification
    : SELECT selectList fromClause? whereClause? groupByClause? havingClause? orderByClause? rowLimitingClause?
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
    | ~(COMMA | FROM | RPAREN | SLASH
    | SEMI)
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
    | OUTER | CROSS | WHERE | AND | OR | NOT | EXISTS | IN | LIKE | ESCAPE | IS
    | USING | GROUP | BY | HAVING | ORDER | FETCH | FIRST | NEXT | ROW | LIMIT | RETURN | BETWEEN | INSERT | INTO | UPDATE
    | SET | DELETE | CASE | WHEN | THEN | ELSE | END | DISTINCT | TRUE | FALSE
    | NULL | CREATE | ALTER | TABLE | TEMPORARY | UNLOGGED | IF | ADD | CONSTRAINT
    | FOREIGN | KEY | REFERENCES | PRIMARY | UNIQUE | INDEX | CONCURRENTLY | ONLY
    | INCLUDE | TABLESPACE | MATERIALIZED | ROWS | TABLESAMPLE | LATERAL | ORDINALITY | OVER
    | IDENTIFIER | QUOTED_IDENTIFIER | STRING_LITERAL | DOLLAR_QUOTED_STRING | NUMBER
    | PARAMETER | DOT | COMMA | STAR | EQ | LBRACKET | RBRACKET | PLUS
    | MINUS | SLASH | PERCENT | CONCAT | ASSIGN | DOUBLE_COLON | JSON_ARROW_TEXT | JSON_ARROW | LT | GT | LE | GE | NEQ
    ;

joinClause
    : joinType? JOIN tablePrimary (ON predicate | USING LPAREN identifierList RPAREN usingAlias?)
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
    : ORDER BY expression (COMMA expression)*
    ;

rowLimitingClause
    : FETCH (FIRST | NEXT) NUMBER? (ROW | ROWS) ONLY
    ;

insertSelectStatement
    : INSERT INTO qualifiedName LPAREN identifierList RPAREN selectStatement
    ;

insertValuesStatement
    : INSERT INTO qualifiedName LPAREN identifierList RPAREN VALUES LPAREN expressionList RPAREN
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
    ;

mergeAction
    : UPDATE SET assignmentList                    # mergeUpdateAction
    | INSERT LPAREN identifierList RPAREN VALUES LPAREN expressionList RPAREN # mergeInsertAction
    | DELETE                                       # mergeDeleteAction
    | DO NOTHING                                   # mergeDoNothingAction
    ;

returningClause
    : RETURNING expressionList (INTO identifierList)?
    ;

returnStatement
    : RETURN expression
    ;

plsqlAssignmentStatement
    : (bindVariable | qualifiedName) ASSIGN expression
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
    | AND
    | BETWEEN
    | DOT
    | ASSIGN
    | DOUBLE_COLON
    | JSON_ARROW_TEXT
    | JSON_ARROW
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
    | AND
    | BETWEEN
    | ON
    | UPDATE
    | SET
    | ASSIGN
    | DOUBLE_COLON
    | JSON_ARROW_TEXT
    | JSON_ARROW
    | LPAREN columnDefinitionParenToken* RPAREN
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
    | expression BETWEEN expression AND expression                         # betweenPredicate
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
    | CASE expression? caseWhenClause+ (ELSE expression)? END             # caseExpression
    | functionCall windowClause?                                          # functionExpression
    | bindVariable                                                        # bindExpression
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

bindVariable
    : PARAMETER (DOT identifier)?
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
    | OUTER | CROSS | WHERE | AND | OR | NOT | EXISTS | IN | LIKE | ESCAPE | IS
    | USING | GROUP | BY | HAVING | ORDER | FETCH | FIRST | NEXT | ROW | LIMIT | RETURN | BETWEEN | INSERT | INTO | UPDATE
    | SET | DELETE | MERGE | MATCHED | VALUES | RETURNING | RETURN | DO | NOTHING
    | CASE | WHEN | THEN | ELSE | END | DISTINCT | TRUE | FALSE
    | NULL | CREATE | ALTER | TABLE | TEMPORARY | UNLOGGED | BEGIN | IF | ELSEIF | ELSIF | WHILE
    | LOOP | REPEAT | DECLARE | PROCEDURE | FUNCTION | TRIGGER | OR | REPLACE | FOR
    | ADD | CONSTRAINT
    | FOREIGN | KEY | REFERENCES | PRIMARY | UNIQUE | INDEX | CONCURRENTLY | ONLY
    | INCLUDE | TABLESPACE | MATERIALIZED | ROWS | TABLESAMPLE | LATERAL | ORDINALITY | OVER
    | IDENTIFIER | QUOTED_IDENTIFIER | STRING_LITERAL | DOLLAR_QUOTED_STRING | NUMBER
    | PARAMETER | DOT | COMMA | STAR | EQ | LPAREN | RPAREN | LBRACKET | RBRACKET | PLUS
    | MINUS | SLASH | PERCENT | CONCAT | ASSIGN | DOUBLE_COLON | JSON_ARROW_TEXT | JSON_ARROW | LT | GT | LE | GE | NEQ
    ;
