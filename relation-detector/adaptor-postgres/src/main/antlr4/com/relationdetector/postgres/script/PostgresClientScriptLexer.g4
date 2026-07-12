lexer grammar PostgresClientScriptLexer;

options { caseInsensitive = true; }

LINE_COMMENT: '--' ~[\r\n]*;
BLOCK_COMMENT: '/*' .*? '*/';
ESCAPE_QUOTED: [eE] '\'' ('\'\'' | '\\' . | ~['\\])* '\'';
SINGLE_QUOTED: '\'' ('\'\'' | ~'\'')* '\'';
DOUBLE_QUOTED: '"' ('""' | ~'"')* '"';
DOLLAR_TAG: '$$' | '$' [A-Za-z_] [A-Za-z0-9_]* '$';
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
WORD: [A-Z_$] [A-Z0-9_$#]*;
DOT: '.';
OTHER: .;
