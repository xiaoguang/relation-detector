# MySQL Client Script Grammar

Owns `MySqlClientScriptLexer.g4`. It recognizes client framing constructs such as `DELIMITER`, comments, strings, and routine boundaries. It only frames server SQL and never produces relationship, lineage, naming, or DDL facts.
