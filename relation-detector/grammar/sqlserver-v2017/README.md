# SQL Server 2017 Full-Grammar Profile

Source base: the vendored `antlr/grammars-v4/sql/tsql` snapshot retained in the
grammar headers. Microsoft Learn T-SQL reference and compatibility level 140
define the version boundary. Generated package:
`com.relationdetector.sqlserver.fullgrammar.v2017`.

This module adds the source-backed 2017 `STRING_AGG` surface. Later
`DATETRUNC`, `GENERATE_SERIES` and `VECTOR` probes remain grammar errors. The
adaptor contains only version binding and typed consumers.
