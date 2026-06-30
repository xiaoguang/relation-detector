# ERP sample data

This directory contains executable ERP sample databases for MySQL 8.0 and PostgreSQL 16/17/18. The primary goal is a runnable business sample, not just parser input.

## Scope

- `mysql/8.0`: MySQL 8.0 ERP sample with schema, triggers, stored procedures/functions, seed data, and analysis queries.
- `postgres/16`: PostgreSQL 16 translation of the same ERP business model, with PG18-only examples rewritten to PostgreSQL 16-compatible SQL.
- `postgres/17`: PostgreSQL 17 translation of the same ERP business model, with PG18-only examples rewritten to PostgreSQL 17-compatible SQL.
- `postgres/18`: PostgreSQL 18 translation of the same ERP business model, with PostgreSQL-native enum types, PL/pgSQL, `JSONB`, `IDENTITY`, and PostgreSQL 18-specific examples isolated in `04-queries/11-pg18-specific.sql`.

The two versions should converge on the same business model: organization, HR, RBAC, product/category/batch, supplier, customer, inventory, procurement, sales, returns, finance, approval, contract, tax, quality, project, service, production, and enterprise operations.

MySQL 8.0, PostgreSQL 16/17/18, and portable SQL now all include the newer deep ERP scenario extension for MRP, shop-floor execution, costing, AR/AP invoices, WMS, repair orders, master-data governance, and sensitive access audit. The PostgreSQL 16/17 directories are complete source-level translations of the PostgreSQL 18 directory, with version-specific syntax downgraded where needed.

All persistent ERP tables have representative seed rows or generator coverage. MySQL also contains four temporary generator tables used only inside the massive-data procedure; PostgreSQL 18 contains three isolated version-specific demo tables.

## Load order

Run files in lexical order inside each section.

### MySQL 8.0

1. `mysql/8.0/01-schema/*.sql`
2. `mysql/8.0/02-procedures/*.sql`
3. `mysql/8.0/03-data/*.sql`
4. `mysql/8.0/04-queries/*.sql` as smoke queries

The deep ERP scenario files extend the base model with MRP, shop-floor execution,
cost accounting, AR/AP invoices, WMS locations, repair orders, master-data
governance, and security audit coverage:

- `01-schema/07-erp-deep-scenario-tables.sql`
- `02-procedures/13-erp-deep-scenario-procedures.sql`
- `03-data/07-erp-deep-scenario-data.sql`
- `04-queries/11-erp-deep-scenario-queries.sql`

Example:

```bash
docker run --name erp-mysql80 -e MYSQL_ROOT_PASSWORD=root -p 33060:3306 -d mysql:8.0
for f in sample-data/mysql/8.0/01-schema/*.sql sample-data/mysql/8.0/02-procedures/*.sql sample-data/mysql/8.0/03-data/*.sql; do
  docker exec -i erp-mysql80 mysql -uroot -proot < "$f"
done
```

### PostgreSQL 16 / 17 / 18

1. Create a database manually, for example `erp_system`.
2. Pick the target directory: `postgres/16`, `postgres/17`, or `postgres/18`.
3. Run `<version>/01-schema/*.sql`
4. Run `<version>/02-procedures/*.sql`
5. Run `<version>/03-data/*.sql`
6. Run `<version>/04-queries/01-*.sql` to `10-*.sql` as business smoke queries
7. Run `<version>/04-queries/12-erp-deep-scenario-queries.sql` as the deep ERP smoke query set
8. Run the version demo query:
   - `postgres/16/04-queries/11-pg16-compatible.sql`
   - `postgres/17/04-queries/11-pg17-compatible.sql`
   - `postgres/18/04-queries/11-pg18-specific.sql`

Each PostgreSQL translation includes the same deep ERP business extension plus a
small seed-coverage file for return, damage, shipment, depreciation, review and
price-change tables:

- `01-schema/07-erp-deep-scenario-tables.sql`
- `02-procedures/13-erp-deep-scenario-procedures.sql`
- `03-data/04-erp-deep-scenario-data.sql`
- `03-data/05-erp-coverage-gap-data.sql`
- `04-queries/12-erp-deep-scenario-queries.sql`

Example:

```bash
docker run --name erp-postgres18 -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=erp_system -p 55432:5432 -d postgres:18
for f in sample-data/postgres/18/01-schema/*.sql sample-data/postgres/18/02-procedures/*.sql sample-data/postgres/18/03-data/*.sql; do
  docker exec -i erp-postgres18 psql -U postgres -d erp_system -v ON_ERROR_STOP=1 < "$f"
done
```

For PostgreSQL 16 or 17, replace the Docker image tag and directory:

```bash
docker run --name erp-postgres16 -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=erp_system -p 55416:5432 -d postgres:16
for f in sample-data/postgres/16/01-schema/*.sql sample-data/postgres/16/02-procedures/*.sql sample-data/postgres/16/03-data/*.sql; do
  docker exec -i erp-postgres16 psql -U postgres -d erp_system -v ON_ERROR_STOP=1 < "$f"
done
```

## Smoke workflow

After loading schema, procedures, and data, run at least one workflow per database:

- Procurement: requisition -> purchase order -> purchase receipt -> supplier payment
- Sales: sales order -> inventory reservation/outbound -> customer receipt
- Inventory: stocktake -> variance adjustment -> inventory transaction
- Finance: voucher -> accounting period close
- Approval: workflow -> approval nodes -> approval instance/records

The files under `04-queries` are designed as smoke and analytics queries. A query may return an empty result only when the seed data intentionally has no matching rows; otherwise empty results should be treated as a data coverage gap.

## Version boundary

- MySQL files must remain valid MySQL 8.0 SQL. Do not add PostgreSQL-only syntax such as `GROUPING SETS`, `RETURNING`, `JSONB`, or PL/pgSQL.
- PostgreSQL files must remain valid for their directory version. Do not leave MySQL-only syntax such as `AUTO_INCREMENT`, `UNSIGNED`, `ON DUPLICATE KEY`, `LAST_INSERT_ID()`, `DATE_FORMAT()`, or storage engine clauses.
- PostgreSQL 18-only syntax belongs in `postgres/18/04-queries/11-pg18-specific.sql` unless it is required by the shared business model.
- PostgreSQL 16/17 compatibility versions of the PG18 demo belong in `postgres/16/04-queries/11-pg16-compatible.sql` and `postgres/17/04-queries/11-pg17-compatible.sql`.

## Coverage report

See `object-coverage-report.md` for the current object count and MySQL/PostgreSQL alignment notes.
