# SQL Server 2025 Full-Grammar Profile

Source base: the vendored `antlr/grammars-v4/sql/tsql` snapshot retained in the
grammar headers. Microsoft Learn T-SQL reference and compatibility level 170
define the version boundary. Generated package:
`com.relationdetector.sqlserver.fullgrammar.v2025`.

This module adds the source-backed `VECTOR` data type probe. Preview syntax is
not accepted merely because it is newer; every additional family requires an
official source and positive/negative version fixture.
