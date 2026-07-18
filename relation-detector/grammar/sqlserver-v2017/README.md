# SQL Server 2017 Full-Grammar Profile

Source base: `antlr/grammars-v4` commit
`994628b6d261f5313b72e76039818549352684ce`, path `sql/tsql`. Microsoft Learn
T-SQL reference and compatibility level 140
define the version boundary. Generated package:
`com.relationdetector.sqlserver.fullgrammar.v2017`.

This module adds the source-backed 2017 `STRING_AGG` surface. Later
`DATETRUNC`, `GENERATE_SERIES` and `VECTOR` probes remain grammar errors. The
adaptor contains only version binding and typed consumers.

The generated-context adapter remains version-local by design. Its repeated
accessors bind to the 2017 generated context classes and must not be shared with
another version through reflection or an untyped bridge.
