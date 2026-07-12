grammar SqlServerRelationSql;

/*
 * SQL Server token-event structural grammar.
 *
 * This is intentionally smaller than the versioned full-grammar grammar. It
 * only supports a high-value typed structural subset used by token-event
 * fallback.
 */

options {
    caseInsensitive = true;
}

tsql_file
    : statement* EOF
    ;

statement
    : with_expression? select_statement SEMI?
    | insert_statement SEMI?
    | update_statement SEMI?
    | merge_statement SEMI?
    | delete_statement SEMI?
    | set_identity_insert SEMI?
    | create_or_alter_procedure SEMI?
    | create_or_alter_function SEMI?
    | create_or_alter_trigger SEMI?
    | create_table SEMI?
    | alter_table SEMI?
    | create_index SEMI?
    | create_view SEMI?
    | GO
    | SEMI
    | unknown_statement SEMI?
    ;

create_view
    : CREATE (OR ALTER)? VIEW full_table_name AS select_statement
    ;

unknown_statement
    : ~(SEMI | GO)+
    ;

with_expression
    : WITH common_table_expression (COMMA common_table_expression)*
    ;

common_table_expression
    : id_ (LPAREN column_name_list RPAREN)? AS LPAREN select_statement RPAREN
    ;

select_statement
    : query_expression
    ;

query_expression
    : query_specification (UNION query_specification)*
    ;

query_specification
    : SELECT top_clause? select_list table_sources? search_condition_clause? group_by_clause? having_clause? order_by_clause? fetch_clause?
    ;

top_clause
    : TOP LPAREN? expression RPAREN?
    ;

select_list
    : select_list_elem (COMMA select_list_elem)*
    ;

select_list_elem
    : STAR
    | expression as_column_alias?
    ;

as_column_alias
    : AS? id_
    ;

table_sources
    : FROM? table_source (COMMA table_source)*
    ;

table_source
    : table_source_item table_source_suffix*
    ;

table_source_suffix
    : join_on
    | cross_join
    | apply_
    ;

join_on
    : join_type? JOIN table_source ON search_condition
    ;

cross_join
    : CROSS JOIN table_source
    ;

apply_
    : (CROSS | OUTER) APPLY table_source
    ;

join_type
    : INNER
    | LEFT OUTER?
    | RIGHT OUTER?
    | FULL OUTER?
    ;

table_source_item
    : full_table_name as_table_alias?
    | derived_table as_table_alias?
    | function_call as_table_alias?
    | LOCAL_ID as_table_alias?
    ;

derived_table
    : LPAREN select_statement RPAREN
    ;

as_table_alias
    : AS? table_alias
    ;

table_alias
    : id_
    ;

search_condition_clause
    : WHERE search_condition
    ;

search_condition
    : predicate ((AND | OR) predicate)*
    ;

predicate
    : NOT? EXISTS LPAREN subquery RPAREN
    | LPAREN search_condition RPAREN
    | expression IN LPAREN subquery RPAREN
    | expression IN LPAREN expression_list_ RPAREN
    | expression BETWEEN expression AND expression
    | expression NOT? LIKE expression
    | expression comparison_operator expression
    | expression IS NOT? NULL
    | expression
    ;

subquery
    : select_statement
    ;

comparison_operator
    : EQ
    | LT
    | GT
    | LE
    | GE
    | NEQ
    ;

group_by_clause
    : GROUP BY expression_list_
    ;

having_clause
    : HAVING search_condition
    ;

order_by_clause
    : ORDER BY order_by_expression (COMMA order_by_expression)*
    ;

order_by_expression
    : expression (ASC | DESC)?
    ;

fetch_clause
    : FETCH (FIRST | NEXT) expression ROWS ONLY
    ;

insert_statement
    : INSERT INTO ddl_object insert_column_name_list insert_statement_value
    ;

insert_column_name_list
    : column_name_list
    ;

insert_statement_value
    : with_expression? select_statement
    | VALUES LPAREN expression_list_ RPAREN
    ;

update_statement
    : UPDATE ddl_object SET update_elem (COMMA update_elem)* (FROM table_sources)? search_condition_clause?
    ;

update_elem
    : full_column_name EQ expression
    | id_ EQ expression
    ;

delete_statement
    : DELETE FROM table_source search_condition_clause?
    ;

set_identity_insert
    : SET IDENTITY_INSERT full_table_name (ON | OFF)
    ;

create_or_alter_procedure
    : CREATE OR ALTER PROCEDURE full_table_name procedure_parameter_list? AS BEGIN statement* END
    ;

procedure_parameter_list
    : procedure_parameter (COMMA procedure_parameter)*
    ;

procedure_parameter
    : LOCAL_ID data_type (EQ expression)? id_*
    ;

create_or_alter_function
    : CREATE OR ALTER FUNCTION full_table_name LPAREN RPAREN RETURNS TABLE AS RETURN LPAREN select_statement RPAREN
    ;

create_or_alter_trigger
    : CREATE OR ALTER TRIGGER full_table_name ON full_table_name AFTER (INSERT | UPDATE | DELETE) AS BEGIN statement* END
    ;

merge_statement
    : MERGE INTO ddl_object as_table_alias? USING table_sources ON search_condition merge_when_clause+
    ;

merge_when_clause
    : WHEN MATCHED THEN UPDATE SET update_elem_merge (COMMA update_elem_merge)*
    | merge_not_matched
    ;

update_elem_merge
    : full_column_name EQ expression
    | id_ EQ expression
    ;

merge_not_matched
    : WHEN NOT MATCHED THEN INSERT column_name_list values_clause
    ;

values_clause
    : VALUES LPAREN expression_list_ RPAREN
    ;

create_table
    : CREATE TABLE table_name LPAREN table_element (COMMA table_element)* RPAREN
    ;

alter_table
    : ALTER TABLE table_name ADD table_constraint
    ;

table_element
    : column_definition
    | table_constraint
    ;

column_definition
    : id_ data_type column_attribute*
    ;

data_type
    : id_ (LPAREN loose_token* RPAREN)?
    ;

column_attribute
    : NOT? NULL
    | IDENTITY LPAREN DECIMAL_LITERAL COMMA DECIMAL_LITERAL RPAREN
    | DEFAULT expression
    | PRIMARY KEY
    | UNIQUE
    | foreign_key_options
    | CONSTRAINT id_
    ;

table_constraint
    : CONSTRAINT? id_? PRIMARY KEY column_name_list_with_order
    | CONSTRAINT? id_? UNIQUE column_name_list_with_order
    | CONSTRAINT? id_? FOREIGN KEY column_name_list foreign_key_options
    ;

foreign_key_options
    : REFERENCES table_name column_name_list
    ;

create_index
    : CREATE UNIQUE? (CLUSTERED | NONCLUSTERED)? INDEX id_ ON table_name column_name_list_with_order
    ;

ddl_object
    : full_table_name
    ;

table_name
    : full_table_name
    ;

full_table_name
    : id_ (DOT id_)*
    ;

full_column_name
    : id_ (DOT id_)*
    ;

column_name_list
    : LPAREN id_ (COMMA id_)* RPAREN
    ;

column_name_list_with_order
    : LPAREN id_ (ASC | DESC)? (COMMA id_ (ASC | DESC)?)* RPAREN
    ;

expression_list_
    : expression (COMMA expression)*
    ;

expression
    : expression_atom (binary_operator expression_atom)*
    ;

expression_atom
    : (PLUS | MINUS) expression_atom
    | function_call
    | full_column_name
    | case_expression
    | CAST LPAREN expression AS data_type RPAREN
    | DECIMAL_LITERAL
    | STRING
    | NULL
    | LOCAL_ID
    | STAR
    | LPAREN select_statement RPAREN
    | LPAREN expression RPAREN
    ;

function_call
    : function_name LPAREN DISTINCT? (STAR | expression_list_)? RPAREN over_clause?
    ;

function_name
    : id_
    | COUNT
    | SUM
    | MAX
    | MIN
    | AVG
    ;

over_clause
    : OVER LPAREN (PARTITION BY expression_list_)? order_by_clause? RPAREN
    ;

case_expression
    : CASE searched_case_when+ case_else? END
    | CASE expression simple_case_when+ case_else? END
    ;

searched_case_when
    : WHEN search_condition THEN expression
    ;

simple_case_when
    : WHEN expression THEN expression
    ;

case_else
    : ELSE expression
    ;

binary_operator
    : PLUS
    | MINUS
    | STAR
    | DIVIDE
    | PERCENT
    ;

loose_token
    : SELECT | DISTINCT | WITH | AS | FROM | WHERE | JOIN | INNER | LEFT | RIGHT | FULL | OUTER | CROSS | APPLY
    | ON | AND | OR | NOT | EXISTS | IN | BETWEEN | GROUP | BY | HAVING | ORDER | ASC | DESC | TOP | FETCH
    | FIRST | NEXT | ROWS | ONLY | UNION | INSERT | INTO | VALUES | UPDATE | SET | DELETE | MERGE | USING | IDENTITY_INSERT
    | WHEN | MATCHED | THEN | CREATE | ALTER | PROCEDURE | FUNCTION | RETURNS | RETURN | TRIGGER | AFTER | BEGIN | TABLE | ADD | INDEX | UNIQUE | CLUSTERED | NONCLUSTERED
    | CONSTRAINT | FOREIGN | KEY | REFERENCES | PRIMARY | NULL | IDENTITY | DEFAULT | CASE | WHEN
    | ELSE | END | IS | LIKE | CAST | COUNT | SUM | MAX | MIN | AVG | OVER | PARTITION | OFF | ID | BRACKET_ID | DOUBLE_QUOTE_ID
    | LOCAL_ID | TEMP_ID | DECIMAL_LITERAL | STRING | COMMA | DOT | STAR | EQ | PLUS | MINUS
    | DIVIDE | PERCENT | LT | GT | LE | GE | NEQ | OTHER
    ;

id_
    : ID
    | BRACKET_ID
    | DOUBLE_QUOTE_ID
    | TEMP_ID
    ;

SELECT: 'SELECT';
DISTINCT: 'DISTINCT';
WITH: 'WITH';
AS: 'AS';
FROM: 'FROM';
WHERE: 'WHERE';
JOIN: 'JOIN';
INNER: 'INNER';
LEFT: 'LEFT';
RIGHT: 'RIGHT';
FULL: 'FULL';
OUTER: 'OUTER';
CROSS: 'CROSS';
APPLY: 'APPLY';
ON: 'ON';
AND: 'AND';
OR: 'OR';
NOT: 'NOT';
EXISTS: 'EXISTS';
IN: 'IN';
BETWEEN: 'BETWEEN';
GROUP: 'GROUP';
BY: 'BY';
HAVING: 'HAVING';
ORDER: 'ORDER';
ASC: 'ASC';
DESC: 'DESC';
TOP: 'TOP';
FETCH: 'FETCH';
FIRST: 'FIRST';
NEXT: 'NEXT';
ROWS: 'ROWS';
ONLY: 'ONLY';
UNION: 'UNION';
INSERT: 'INSERT';
INTO: 'INTO';
VALUES: 'VALUES';
UPDATE: 'UPDATE';
SET: 'SET';
DELETE: 'DELETE';
MERGE: 'MERGE';
USING: 'USING';
WHEN: 'WHEN';
MATCHED: 'MATCHED';
THEN: 'THEN';
CREATE: 'CREATE';
ALTER: 'ALTER';
VIEW: 'VIEW';
PROCEDURE: 'PROCEDURE';
FUNCTION: 'FUNCTION';
RETURNS: 'RETURNS';
RETURN: 'RETURN';
TRIGGER: 'TRIGGER';
AFTER: 'AFTER';
BEGIN: 'BEGIN';
TABLE: 'TABLE';
ADD: 'ADD';
INDEX: 'INDEX';
UNIQUE: 'UNIQUE';
CLUSTERED: 'CLUSTERED';
NONCLUSTERED: 'NONCLUSTERED';
CONSTRAINT: 'CONSTRAINT';
FOREIGN: 'FOREIGN';
KEY: 'KEY';
REFERENCES: 'REFERENCES';
PRIMARY: 'PRIMARY';
NULL: 'NULL';
IDENTITY: 'IDENTITY';
IDENTITY_INSERT: 'IDENTITY_INSERT';
DEFAULT: 'DEFAULT';
GO: 'GO';
OFF: 'OFF';
CASE: 'CASE';
ELSE: 'ELSE';
END: 'END';
IS: 'IS';
LIKE: 'LIKE';
CAST: 'CAST';
COUNT: 'COUNT';
SUM: 'SUM';
MAX: 'MAX';
MIN: 'MIN';
AVG: 'AVG';
OVER: 'OVER';
PARTITION: 'PARTITION';

BRACKET_ID: '[' (~']' | ']]')+ ']';
DOUBLE_QUOTE_ID: '"' (~'"' | '""')+ '"';
LOCAL_ID: '@' [A-Z_#] [A-Z_0-9#$@]*;
TEMP_ID: '#' [A-Z_#] [A-Z_0-9#$@]*;
ID: [A-Z_] [A-Z_0-9@$#]*;
DECIMAL_LITERAL: [0-9]+ ('.' [0-9]+)?;
STRING: 'N'? '\'' ( '\'\'' | ~'\'' )* '\'';

SEMI: ';';
COMMA: ',';
DOT: '.';
LPAREN: '(';
RPAREN: ')';
STAR: '*';
EQ: '=';
PLUS: '+';
MINUS: '-';
DIVIDE: '/';
PERCENT: '%';
LT: '<';
GT: '>';
LE: '<=';
GE: '>=';
NEQ: '<>' | '!=';

LINE_COMMENT: '--' ~[\r\n]* -> channel(HIDDEN);
BLOCK_COMMENT: '/*' .*? '*/' -> channel(HIDDEN);
SPACE: [ \t\r\n]+ -> channel(HIDDEN);

OTHER: .;
