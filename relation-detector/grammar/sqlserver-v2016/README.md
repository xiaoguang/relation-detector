# SQL Server 2016 Full-Grammar Profile

Source base: the vendored `antlr/grammars-v4/sql/tsql` snapshot retained in the
grammar headers. Microsoft Learn T-SQL reference and compatibility level 130
define the version boundary. Generated package:
`com.relationdetector.sqlserver.fullgrammar.v2016`.

This module owns the 2016 lexer/parser only. The adaptor owns the binding,
typed context adapter and collectors. Version probes reject later
`STRING_AGG`, `DATETRUNC`, `GENERATE_SERIES` and `VECTOR` syntax in grammar;
the natural sample-data remains a 2016-compatible baseline.
