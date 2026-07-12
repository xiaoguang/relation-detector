# SQL Server 2022 Full-Grammar Profile

Source base: the vendored `antlr/grammars-v4/sql/tsql` snapshot retained in the
grammar headers. Microsoft Learn T-SQL reference and compatibility level 160
define the version boundary. Generated package:
`com.relationdetector.sqlserver.fullgrammar.v2022`.

This module adds source-backed `DATETRUNC` and `GENERATE_SERIES` syntax. The
2025 `VECTOR` probe remains a grammar error. The adaptor contains only binding,
typed context adapter and collectors.
