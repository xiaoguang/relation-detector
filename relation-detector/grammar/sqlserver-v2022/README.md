# SQL Server 2022 Full-Grammar Profile

Source base: `antlr/grammars-v4` commit
`994628b6d261f5313b72e76039818549352684ce`, path `sql/tsql`. Microsoft Learn
T-SQL reference and compatibility level 160
define the version boundary. Generated package:
`com.relationdetector.sqlserver.fullgrammar.v2022`.

This module adds source-backed `DATETRUNC` and `GENERATE_SERIES` syntax. The
2025 `VECTOR` probe remains a grammar error. The adaptor contains only binding,
typed context adapter and collectors.

The generated-context adapter remains version-local by design. It consumes the
2022 generated context classes directly, so duplicated accessor code is an
intentional version boundary rather than reusable semantic logic.
