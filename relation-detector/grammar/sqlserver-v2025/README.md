# SQL Server 2025 Full-Grammar Profile

Source base: `antlr/grammars-v4` commit
`994628b6d261f5313b72e76039818549352684ce`, path `sql/tsql`. Microsoft Learn
T-SQL reference and compatibility level 170
define the version boundary. Generated package:
`com.relationdetector.sqlserver.fullgrammar.v2025`.

This module adds the source-backed `VECTOR` data type probe. Preview syntax is
not accepted merely because it is newer; every additional family requires an
official source and positive/negative version fixture.

The generated-context adapter remains version-local by design. It binds to the
2025 generated parser types and keeps preview/version changes compile-time
visible; only context-independent semantic helpers may be shared.
