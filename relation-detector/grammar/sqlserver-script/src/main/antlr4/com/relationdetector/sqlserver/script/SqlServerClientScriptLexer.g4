lexer grammar SqlServerClientScriptLexer;

options { caseInsensitive = true; }

LINE_COMMENT: '--' ~[\r\n]*;
BLOCK_COMMENT: '/*' .*? '*/';
SINGLE_QUOTED: ([nN])? '\'' ('\'\'' | ~'\'')* '\'';
DOUBLE_QUOTED: '"' ('""' | ~'"')* '"';
BRACKET_QUOTED: '[' (']]' | ~']')* ']';
GO: 'GO';
CREATE: 'CREATE';
OR: 'OR';
REPLACE: 'REPLACE';
ALTER: 'ALTER';
PROCEDURE: 'PROCEDURE';
FUNCTION: 'FUNCTION';
TRIGGER: 'TRIGGER';
PACKAGE: 'PACKAGE';
BODY: 'BODY';
EVENT: 'EVENT';
VIEW: 'VIEW';
MATERIALIZED: 'MATERIALIZED';
RETURNS: 'RETURNS';
TEMPORARY: 'TEMPORARY';
TEMP: 'TEMP';
TABLE: 'TABLE';
IF: 'IF';
NOT: 'NOT';
EXISTS: 'EXISTS';
SEMI: ';';
NEWLINE: '\r\n' | '\r' | '\n';
WS: [ \t\f]+;
WORD: [A-Z_@$#] [A-Z0-9_@$#]*;
DOT: '.';
OTHER: .;
