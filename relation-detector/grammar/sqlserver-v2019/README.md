# SQL Server 2019 Full-Grammar Profile

Source base: the vendored `antlr/grammars-v4/sql/tsql` snapshot retained in the
grammar headers. Microsoft Learn T-SQL reference and compatibility level 150
define the version boundary. Generated package:
`com.relationdetector.sqlserver.fullgrammar.v2019`.

No separate 2019-only syntax family is currently claimed. The module preserves
an independently generated version artifact and rejects the implemented 2022
and 2025 probes in grammar.
