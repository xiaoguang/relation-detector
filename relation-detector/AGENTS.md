# Relation Detector Agent Instructions

These instructions apply to the entire `relation-detector/` subtree and extend the
repository root instructions.

## System Role

Relation Detector is the physical-fact producer. It parses metadata and SQL into
auditable relationship, lineage, naming, diagnostic, and derived outputs. It does
not perform business ontology construction, LLM enrichment, semantic search, or KG
question answering.

## Parser Architecture

- All tracked `.g4` files belong under `relation-detector/grammar/`. Core and
  adaptor modules consume generated grammar artifacts and must not run ANTLR code
  generation themselves.
- A dialect script framer recognizes client-side boundaries such as MySQL
  `DELIMITER`, PostgreSQL dollar quotes, Oracle `/`, and SQL Server `GO`. It does
  not infer relationships, lineage, DDL semantics, or routine behavior.
- Token-event and full-grammar are independent parser implementations. They may
  share typed contracts, immutable models, provenance mapping, and stateless
  semantic helpers, but they must not delegate parsing to one another.
- Versioned full-grammar packages retain generated parser bindings, version policy,
  and typed context adapters. Do not merge version grammars or enforce version
  boundaries with Java keyword blacklists.
- PostgreSQL routine parsing has two paths:
  - token-event outer SQL -> compact PL/pgSQL shell -> token-event embedded SQL;
  - versioned full-grammar outer SQL -> same-version PL/pgSQL shell -> same-version
    full-grammar embedded SQL.
- PL/pgSQL grammar owns the procedural shell and typed static-SQL boundaries. It
  must not duplicate PostgreSQL SELECT, DML, predicate, or expression grammar.
- Static SQL inside routines is always returned to the parser mode that discovered
  the routine. Dynamic SQL is unresolved evidence/diagnostic unless a future
  explicit design safely models it.

## SQL Structure Boundary

Production fact extraction must not use:

- raw SQL regex or scanner passes;
- token-span guesses for SQL structure;
- generated grammar rule names as strings;
- reflection over generated context method names;
- terminal keyword blacklists;
- table or column name special cases.

Allowed string/configuration matching is limited to:

- dialect and version selection;
- user and system rules executed by `NamingRuleEngine`;
- configured non-fact filters;
- CLI paths, tests, asset hygiene, reports, and diagnostics.

## Endpoint And Namespace Semantics

- Preserve identifiers exactly as written by SQL/DDL after normal quoting and case
  normalization.
- Explicit `catalog.schema.table` or `schema.table` remains qualified.
- A bare table remains bare unless an explicit scan namespace uniquely resolves it.
- Never auto-merge `dbo.table` with `table`, `public.table` with `table`, or two
  schemas containing the same table name.
- Alias resolution is local to the typed query scope. CTEs, derived tables, temp
  tables, and trigger pseudo-rowsets have distinct symbol kinds.
- Derived graph keys use canonical endpoints only; they must not fall back from a
  qualified endpoint to a bare endpoint.

## Relationship Semantics

- Direct physical-column equality may produce structural relationship evidence.
  Functions, casts, conversions, arithmetic, aggregates, and formatted-date
  comparisons are not direct column equality.
- `CO_OCCURRENCE` represents structural co-use/equality without sufficient
  direction evidence. Filter same-endpoint CO relationships from final facts.
- A declared FK remains `DDL_DECLARED_FK`. Inferred FK-like direction requires
  structural evidence and valid direction support.
- A standalone index or unique declaration can enhance an existing relationship;
  it cannot create a relationship by itself.
- Composite PK/UNIQUE/index declarations are group evidence. A member of
  `UNIQUE(a,b)` is not independently target-unique, and a member of a composite
  index is not independently indexed for direction inference.
- Relationship evidence side must be recomputed after any endpoint direction flip.

## Lineage Semantics

- `VALUE` means data contributes to the written value. `CONTROL` means it selects,
  filters, locates, or controls the write without becoming the value.
- CASE `THEN`/`ELSE` expressions are VALUE; CASE selector and `WHEN` predicates are
  CONTROL. CASE control transform is `CASE_WHEN`.
- Scalar-subquery projection, aggregate arguments, function inputs, and arithmetic
  inputs are VALUE. JOIN, WHERE, HAVING, GROUP BY, and correlated locator columns
  are CONTROL.
- Transform precedence must reflect the effective write expression rather than an
  arbitrary nested function. Preserve all arithmetic operands.
- Keep non-trivial self-updates such as `quantity = quantity - delta`; ignore pure
  identity/no-op self edges when building derived paths.
- Derived lineage must reject non-adjacent endpoint re-entry and self-loop output.
- UNION/UNION ALL projection lineage must include every physical branch source,
  not only the first branch.

## Naming Evidence

- `NamingEvidenceExtractor` and `NamingRuleEngine` are the only direct naming rule
  execution path.
- System and customer rules use the same configured engine. Relationship merger,
  parser runner, profile code, and derived inference must not recompute naming
  rules.
- Top-level `namingEvidence` is the single complete evidence pool. Relationship
  `NAMING_MATCH` entries contain an `evidenceRef` into that pool.
- Different source files, objects, statements, blocks, and lines are separate raw
  observations. Matching an endpoint pair once must not discard additional valid
  observations for that pair.
- `TRANSITIVE_NAMING_PATH` is generated only by derived inference and cannot be a
  user-configured direct rule.

## Derived Inference

- Direct relationship direction remains dependent/child -> referenced/parent.
  Relationship traversal may walk referenced-by internally but must output the
  established forward FK-like direction.
- Use strict canonical table identity bridges only within the same qualified table.
- Pure naming-only paths cannot create relationships. CONTROL lineage cannot create
  VALUE lineage.
- Direct facts are never duplicated as derived facts. Prevent cycles and self-loop
  output; obey configured path length and deterministic ordering.
- Conditional/polymorphic relationships are direct facts with discriminator
  evidence. They must not participate in derived relationship or derived naming
  inference until the derived model can preserve the discriminator condition.

## DDL, Views, Triggers, And Routines

- Dispatch framed statements by typed kind. DDL declarations go to the DDL parser;
  CREATE VIEW/MATERIALIZED VIEW also sends its typed query body through the SQL
  parser.
- View predicates may produce `VIEW_JOIN` relationship evidence. Do not invent view
  output-column lineage without a typed projection mapping.
- Trigger `NEW`/`OLD` or `inserted`/`deleted` pseudo-rowsets must resolve through
  typed trigger target metadata.
- Object provenance must use the real declaration: file, statement, line, block,
  object type, and object name.
- Unsupported routine statements must produce precise diagnostics while preserving
  events parsed before and after a recoverable statement.
- PostgreSQL `RETURN QUERY` belongs to a set-returning function, not a procedure.
  Query routines should be functions with an explicit return contract; mutating
  procedures should use valid OUT/INOUT semantics or no returned result set.

## Evidence And Provenance

- `StructuredSqlEvent.line()` is an absolute source-file line. Consumers must not
  add the statement start line a second time.
- Every SQL/database-object observation should carry source file, statement, line,
  block, object type, and object name when applicable.
- File paths in output are repository-relative. Do not emit local absolute paths.
- Merged fact attributes contain only values common to all observations. Conflicting
  provenance remains in raw evidence.
- A semantic observation identity includes fact, semantic evidence type, source
  file/object, statement/block, line, and relevant mapping/join attributes.
- Implementation markers such as token/full parser class names are not semantic
  parity keys.

## SQL Asset Quality

- Natural sample-data must be executable for its declared dialect/version. Parser
  acceptance with `Diag=0` is not proof of database validity.
- Keep natural business assets separate from synthetic correctness/parser coverage
  probes.
- Do not add FK-copy queries, synthetic one-relation routines, or comments claiming
  literal seed rows create physical source lineage.
- Version-neutral natural samples use the oldest supported baseline. Version-only
  syntax belongs in explicit positive/negative boundary fixtures.
- When changing SQL assets, state the expected relationship/lineage changes before
  updating golden output.

## Golden And Audit Protocol

For every unexpected difference:

1. Identify parser category, SQL file/object, statement, and line.
2. Compare old and new source/target, kind, flow, transform, evidence, and raw
   observation.
3. Classify it as parser correction, false-positive removal, SQL asset correction,
   provenance-only, observation recovery, expected version/asset delta, or review
   needed.
4. Update golden only after the SQL supports the new result.
5. Re-run without update immediately.

Do not infer correctness from counts. Fact equality can hide lost observations, and
observation equality can hide invalid SQL assets.

## Verification

Use the narrowest useful test first, then expand. Release-level verification must
cover:

- all correctness fixtures;
- all parser categories;
- direct and derived sample-data JSON;
- token/full semantic observation parity;
- diagnostics;
- evidenceRef resolution, relative paths, source-line ranges, and derived cycles;
- semantic-layer tests affected by the external JSON contract.

Current verification tools:

```bash
bash relation-detector/scripts/verify-release.sh
```

Inspect the generated `verification-manifest.json`. Do not claim success from the
last console line alone.

## Implemented Guardrails

The following behavior is implemented and must remain covered by focused,
correctness, sample-data, parity, and audit tests:

1. Polymorphic relationships such as `contracts.party_id -> customers.id` and
   `contracts.party_id -> suppliers.id` remain direct conditional relationships.
   Evidence must carry `conditional`, `polymorphic`, discriminator endpoint, and
   discriminator value. They are excluded from derived inference for now.
2. Naming evidence must aggregate all distinct SQL-location observations for a
   matched endpoint pair.
3. PostgreSQL UNION projection tracing must retain all physical branch sources.
4. PostgreSQL natural assets containing `PROCEDURE` plus `RETURN QUERY` must be
   corrected and protected by an asset-validity test.
5. Grammar-migration A-to-B audit classification must separate strict provenance
   normalization from routine observation recovery and naming observation changes.

Do not weaken these behaviors to restore historical counts. Any future change must
re-read the supporting SQL, compare semantic observations, and update the audit
classification rather than hiding the difference in a golden refresh.

## Reference Documents

- `docs/design/relation-detector/grammar-parser-architecture-migration.md`
- `docs/design/relation-detector/phase-06-parser-enhancement.md`
- `docs/design/relation-detector/sql-lineage-resolver.md`
- `docs/parser-audit/grammar-migration-release-verification.md`
- `docs/parser-audit/postgres-plpgsql-version-review.md`
