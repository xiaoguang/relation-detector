# SQL Server 2019 Full-Grammar Profile

Source base: `antlr/grammars-v4` commit
`994628b6d261f5313b72e76039818549352684ce`, path `sql/tsql`. Microsoft Learn
T-SQL reference and compatibility level 150
define the version boundary. Generated package:
`com.relationdetector.sqlserver.fullgrammar.v2019`.

No separate 2019-only syntax family is currently claimed. The module preserves
an independently generated version artifact and rejects the implemented 2022
and 2025 probes in grammar.

The generated-context adapter remains version-local even when productions are
currently identical. This preserves compile-time ownership of the 2019 parser
types and makes a future grammar difference a compiler-visible change.
