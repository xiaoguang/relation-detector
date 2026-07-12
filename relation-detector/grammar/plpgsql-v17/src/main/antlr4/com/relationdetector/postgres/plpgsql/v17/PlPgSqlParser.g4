parser grammar PlPgSqlParser;

options { tokenVocab = PlPgSqlLexer; }

script
    : statement* EOF
    ;

statement
    : block
    | declareSection
    | declarationItem
    | ifStatement
    | caseStatement
    | basicLoopStatement
    | whileStatement
    | forQueryStatement
    | forControlStatement
    | foreachStatement
    | returnQueryStatement
    | dynamicExecuteStatement
    | staticSqlStatement
    | proceduralStatement
    | unsupportedStatement
    | SEMI
    ;

block
    : blockLabel? declareSection? BEGIN statement* exceptionSection? END identifier? SEMI?
    ;

blockLabel
    : LT LT identifier GT GT
    ;

declareSection
    : DECLARE declarationItem*
    ;

declarationItem
    : identifier declarationToken* SEMI
    ;

declarationToken
    : LPAREN declarationToken* RPAREN
    | LBRACKET declarationToken* RBRACKET
    | ~SEMI
    ;

ifStatement
    : IF conditionToken* THEN statement*
      ((ELSIF | ELSEIF) conditionToken* THEN statement*)*
      (ELSE statement*)?
      END IF SEMI?
    ;

caseStatement
    : CASE caseHeadToken* caseWhenClause+ (ELSE statement*)? END CASE? SEMI?
    ;

caseWhenClause
    : WHEN conditionToken* THEN statement*
    ;

basicLoopStatement
    : blockLabel? LOOP statement* END LOOP identifier? SEMI?
    ;

whileStatement
    : blockLabel? WHILE loopConditionToken* LOOP statement* END LOOP identifier? SEMI?
    ;

forQueryStatement
    : blockLabel? FOR identifier IN embeddedQuery LOOP statement* END LOOP identifier? SEMI?
    ;

forControlStatement
    : blockLabel? FOR identifier IN REVERSE? loopConditionToken* LOOP statement* END LOOP identifier? SEMI?
    ;

foreachStatement
    : blockLabel? FOREACH identifier (SLICE NUMBER)? IN ARRAY loopConditionToken*
      LOOP statement* END LOOP identifier? SEMI?
    ;

exceptionSection
    : EXCEPTION exceptionHandler+
    ;

exceptionHandler
    : WHEN conditionToken* THEN statement*
    ;

returnQueryStatement
    : RETURN QUERY embeddedSql SEMI
    ;

dynamicExecuteStatement
    : EXECUTE dynamicToken* SEMI
    ;

staticSqlStatement
    : embeddedSql SEMI
    ;

embeddedQuery
    : (SELECT | WITH) staticSqlToken*
    ;

embeddedSql
    : selectSql
    | dmlSql
    | genericSql
    ;

selectSql
    : SELECT selectToken* plPgSqlIntoClause staticSqlToken*
    | SELECT staticSqlToken*
    ;

dmlSql
    : (INSERT | UPDATE | DELETE | MERGE) dmlToken* plPgSqlReturningIntoClause
    | (INSERT | UPDATE | DELETE | MERGE) staticSqlToken*
    ;

genericSql
    : (WITH | CREATE | ALTER | DROP) staticSqlToken*
    ;

plPgSqlIntoClause
    : INTO STRICT? identifierList
    ;

plPgSqlReturningIntoClause
    : RETURNING returningToken* INTO STRICT? identifierList
    ;

proceduralStatement
    : qualifiedIdentifier ASSIGN proceduralToken* SEMI
    | RETURN NEXT? proceduralToken* SEMI
    | RAISE proceduralToken* SEMI
    | PERFORM proceduralToken* SEMI
    | CALL proceduralToken* SEMI
    | GET proceduralToken* SEMI
    | OPEN proceduralToken* SEMI
    | FETCH proceduralToken* SEMI
    | CLOSE proceduralToken* SEMI
    | MOVE proceduralToken* SEMI
    | EXIT proceduralToken* SEMI
    | CONTINUE proceduralToken* SEMI
    | COMMIT SEMI
    | ROLLBACK SEMI
    | NULL SEMI
    ;

unsupportedStatement
    : OTHER unsupportedToken* SEMI?
    ;

identifierList
    : identifier (COMMA identifier)*
    ;

qualifiedIdentifier
    : identifier (DOT identifier)*
    ;

identifier
    : IDENTIFIER
    | QUOTED_IDENTIFIER
    ;

conditionToken
    : ~THEN
    ;

caseHeadToken
    : ~(WHEN | END)
    ;

loopConditionToken
    : ~LOOP
    ;

selectToken
    : ~(SEMI | INTO)
    ;

returningToken
    : ~(SEMI | INTO)
    ;

dmlToken
    : ~(SEMI | RETURNING)
    ;

staticSqlToken
    : ~SEMI
    ;

dynamicToken
    : ~SEMI
    ;

proceduralToken
    : ~SEMI
    ;

unsupportedToken
    : ~SEMI
    ;
