# Portable ERP Sample Data

This directory is the common SQL / portable SQL counterpart of `sample-data/mysql/8.0`.
It keeps the same business object inventory while lowering syntax to the portable subset used by the common token-event grammar.

## Object Counts

| Object type | MySQL 8.0 source | Portable |
|---|---:|---:|
| Tables | 135 | 135 |
| Views | 6 | 6 |
| Procedures | 109 | 109 |
| Functions | 20 | 20 |
| Triggers | 12 | 12 |
| Business table seed targets | 135 | 135 |

MySQL 8.0 also contains four temporary insert targets (`tmp_cat1`, `tmp_cat2`,
`tmp_city_coords`, `tmp_prod_names`) inside data-generation routines. Portable
sample data excludes those local temporary targets because they should not enter
relation or lineage golden.

## Files

- `01-schema/01-tables.sql`: portable DDL for all ERP tables, including FK/index/unique evidence.
- `01-schema/02-views.sql`: six portable view definitions documenting the reporting layer.
- `01-schema/03-erp-deep-scenario-tables.sql`: portable DDL for MRP, shop-floor execution, costing, AR/AP, WMS, repair, master-data governance, and sensitive access audit.
- `02-processes/01-procedures.sql`: SQL/PSM-style procedure declarations.
- `02-processes/02-functions.sql`: SQL/PSM-style function declarations.
- `02-processes/03-triggers.sql`: SQL/PSM-style trigger declarations.
- `02-processes/04-process-bodies-for-golden.sql`: parser-ready object blocks used by common correctness golden.
- `02-processes/05-erp-deep-scenario-procedures.sql`: SQL/PSM-style declarations for the deep ERP procedures.
- `02-processes/06-erp-deep-scenario-process-bodies-for-golden.sql`: parser-ready object blocks for the deep ERP procedures.
- `03-data/01-master-data.sql`: one portable `INSERT ... SELECT` seed target per table.
- `03-data/02-erp-deep-scenario-data.sql`: representative deep ERP seed rows translated from the MySQL 8.0 sample.
- `04-queries/01-business-queries.sql`: portable business queries and DML slices.
- `04-queries/02-erp-deep-scenario-queries.sql`: portable deep ERP analysis queries.

## Boundary

The portable process declarations are intended as relation-detector grammar/golden material, not as a promise that every SQL/PSM block is directly executable in MySQL or PostgreSQL without dialect adaptation.
