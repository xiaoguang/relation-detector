# Project Agent Instructions

This file defines repository-wide rules for human and automated contributors. A
more specific `AGENTS.md` takes precedence inside its subtree.

## Repository Boundaries

- `relation-detector/` produces physical facts from database metadata and typed
  SQL parsing: relationships, lineage, naming evidence, diagnostics, and derived
  facts.
- `semantic-layer/` consumes relation-detector JSON and builds auditable semantic
  artifacts and KG output. It must not invoke parser or adaptor implementations.
- Keep these modules separate. Do not move semantic inference into
  relation-detector, and do not make semantic-layer a second physical-fact parser.
- The root `docs/` tree is the design and audit source. Update documents when the
  implementation changes; never describe a planned behavior as already complete.

## Evidence Principles

- Every fact must be traceable to structured evidence. Preserve source file,
  statement, line, object, block, and evidence references where available.
- Exact identifier resolution is not naming inference. Preserve explicit catalog
  and schema components; do not silently equate `schema.table` and `table`.
- Naming heuristics belong only in the configured naming rule engine. A
  `NAMING_MATCH` on a relationship must reference top-level naming evidence.
- Derived facts are inference, not direct observations. Keep direct and derived
  counts, evidence, and output semantics distinct.
- An LLM may enrich labels and semantic interpretation, but it must not invent or
  rewrite physical relationship, lineage, naming, or provenance facts.

## Engineering Rules

- Read the nearest `AGENTS.md`, relevant design document, tests, and surrounding
  implementation before editing.
- Prefer existing typed models, grammar contexts, and helper APIs. Do not infer SQL
  structure with regex, scanner passes, token spans, grammar rule-name strings,
  reflection, or table/column name allowlists.
- Keep parser and scan state per invocation. Do not introduce shared mutable state.
- Treat generated files, reports, and `target/` artifacts as outputs unless a
  documented generator explicitly owns a tracked section.
- Do not modify unrelated user changes. Do not refresh golden files to hide a
  structural regression.

## Verification Discipline

- Counts are a signal, not proof of correctness. Review SQL context for every
  unexpected fact, observation, diagnostic, or golden difference.
- Start with focused tests, then the affected module/dialect, parser matrix, full
  correctness, and sample-data CLI as risk requires.
- Before claiming release-level completion, use the repository verification tools
  and inspect their manifest; a Maven exit code alone is insufficient.
- Distinguish historical evidence from current verification. Re-running tests now
  cannot prove that an earlier development process followed every planned step.
- Classify findings honestly as implemented, decided-pending-implementation,
  review-needed, or historical-process-not-provable.

## Lessons That Must Persist

- Final fact parity can hide observation loss. Compare semantic observations and
  provenance, not only endpoint sets or summary counts.
- A parser accepting an asset with zero diagnostics does not prove the SQL is
  executable by the database. Validate dialect object semantics as well as syntax.
- Multi-column PK/UNIQUE/index declarations do not prove uniqueness or indexing of
  each member column independently.
- Conditional or polymorphic relationships must preserve their discriminator; an
  unconditional derived closure over them is unsafe.
- Different SQL locations are distinct observations even when they support the
  same fact.
- Audit classifiers must explain every unmatched change. Broad labels such as
  "provenance only" require one-to-one evidence, not a count-level assumption.

## Primary References

- `docs/design/00-design-index.md`
- `docs/design/relation-detector/README.md`
- `docs/design/semantic-layer/README.md`
- `docs/parser-audit/grammar-migration-release-verification.md`
