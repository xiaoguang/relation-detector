# SQL Server 2016 Full-Grammar Profile

Source base: `antlr/grammars-v4` commit
`994628b6d261f5313b72e76039818549352684ce`, path `sql/tsql`. Microsoft Learn
T-SQL reference and compatibility level 130
define the version boundary. Generated package:
`com.relationdetector.sqlserver.fullgrammar.v2016`.

This module owns the 2016 lexer/parser only. The adaptor owns the binding,
typed context adapter and collectors. Version probes reject later
`STRING_AGG`, `DATETRUNC`, `GENERATE_SERIES` and `VECTOR` syntax in grammar;
the natural sample-data remains a 2016-compatible baseline.

The generated-context adapter remains version-local by design. Its apparently
repeated accessors are compiled against this module's generated parser types;
merging them across versions would erase compile-time grammar boundaries.
