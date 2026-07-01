lexer grammar OracleFullGrammerLexer;

/*
 * Oracle scoped full-grammer lexer.
 *
 * CN: 该 lexer 只提供 Oracle version full-grammer 当前结构化规则需要的 token。
 * EN: This lexer provides the token set required by the current Oracle version full-grammer structural rules.
 */

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
IS: I S;
LIKE: L I K E;
ESCAPE: E S C A P E;
GROUP: G R O U P;
BY: B Y;
HAVING: H A V I N G;
ORDER: O R D E R;
FETCH: F E T C H;
FIRST: F I R S T;
NEXT: N E X T;
ROW: R O W;
LIMIT: L I M I T;
INSERT: I N S E R T;
INTO: I N T O;
UPDATE: U P D A T E;
SET: S E T;
DELETE: D E L E T E;
MERGE: M E R G E;
MATCHED: M A T C H E D;
VALUES: V A L U E S;
RETURNING: R E T U R N I N G;
RETURN: R E T U R N;
BETWEEN: B E T W E E N;
DO: D O;
NOTHING: N O T H I N G;
CREATE: C R E A T E;
ALTER: A L T E R;
TABLE: T A B L E;
TEMPORARY: T E M P O R A R Y;
UNLOGGED: U N L O G G E D;
BEGIN: B E G I N;
IF: I F;
ELSEIF: E L S E I F;
ELSIF: E L S I F;
WHILE: W H I L E;
LOOP: L O O P;
REPEAT: R E P E A T;
DECLARE: D E C L A R E;
PROCEDURE: P R O C E D U R E;
FUNCTION: F U N C T I O N;
TRIGGER: T R I G G E R;
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
MEMOPTIMIZE: M E M O P T I M I Z E;
READ: R E A D;
SQL_MACRO: S Q L '_' M A C R O;
VECTOR: V E C T O R;
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
TRUE: T R U E;
FALSE: F A L S E;
NULL: N U L L;

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
ASSIGN: ':=';
DOUBLE_COLON: '::';
JSON_ARROW_TEXT: '->>';
JSON_ARROW: '->';
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
