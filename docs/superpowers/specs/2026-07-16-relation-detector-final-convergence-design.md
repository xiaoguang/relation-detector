# Relation Detector Final Convergence Design

## 1. Purpose

This document freezes the final convergence boundary for `relation-detector`.
It replaces open-ended whole-repository auditing with a finite set of invariants,
explicit change-control rules, and objective completion gates.

The production-code baseline is commit `f2bd631f`. The design corrections already
present in the working tree are audit input for this closure effort; they are not
evidence that the corresponding implementation is complete.

## 2. Fixed Scope

The closure matrix contains exactly 20 IDs in four groups:

- identity and namespace: `ID-01` through `ID-05`;
- evidence and observation aggregation: `EV-01` through `EV-06`;
- public execution contracts: `CT-01` through `CT-06`;
- verification gates: `TG-01` through `TG-03`.

After this document is accepted, implementation may add evidence, tests, producers,
consumers, or bypasses to an existing ID, but it may not create another closure ID.
A newly discovered issue is handled as follows:

1. If it violates an existing invariant, attach it to that ID.
2. If it is necessary to make an existing ID pass, add it as a subtask of that ID.
3. If it is outside the fixed invariants, record it in backlog without reopening closure.
4. Only a newly confirmed data-loss, security, or destructive-execution defect may
   amend the matrix, and that requires explicit user approval.

## 3. Explicitly Out Of Scope

The following do not block this convergence:

- performance optimization or execution-model changes;
- new SQL syntax, parser coverage, grammar upgrades, or token/full feature parity;
- semantic-layer changes;
- broad refactoring unrelated to a frozen invariant;
- exhaustive real-database driver, permission, or server-version combinations;
- general Javadoc improvement outside the specific architecture-gate defect;
- filtered profiling contexts for tenant, soft-delete, archive, or time-window rules.

`NEGATIVE_VALUE_MISMATCH` remains a whole-table profiling result. Filtered row-domain
profiling is a future feature and must not be introduced implicitly during this work.

## 4. Status Model

Every closure ID moves through the same states:

| State | Meaning |
| --- | --- |
| `OPEN` | Static audit confirms a mismatch or missing proof. |
| `RED_PROVEN` | A focused positive, negative, or direct-API test fails for the intended reason. |
| `IMPLEMENTED` | Minimal production change is present. |
| `FOCUSED_GREEN` | Focused tests and affected module/dialect tests pass. |
| `REVERSE_AUDITED` | Predeclared `rg`/AST audit finds no old bypass in the enumerated paths. |
| `FULL_GREEN` | Full correctness and required CLI verification pass. |
| `CLOSED` | All preceding states are evidenced and design status is updated. |

No item may move directly from `OPEN` to `CLOSED` because a count or Maven exit code
looks plausible.

## 5. Identity And Namespace Matrix

### ID-01: Structural Table And Endpoint Identity

**Invariant:** Table identity is determined from catalog, schema, table, and column
components under dialect identifier rules. A caller-supplied `normalizedName` cannot
make structurally different values equal.

**Producers:** metadata collectors, DDL visitors, SQL relationship/lineage extractors,
live object and DDL definitions.

**Consumers:** `Endpoint`, naming self-reference checks, alias symbol tables, metadata
and profile indexes, fact mergers, derived graph construction.

**Known bypasses:** `TableId.sameIdentity()` trusts catalog plus `normalizedName`;
isolated paths still use record `equals()`.

**Required proof:** mismatched catalog/schema/table tuples are rejected; dialect-
equivalent tuples compare through a canonical key rather than record equality.

### ID-02: Dialect-Qualified Identifier Axes And Quoting

**Invariant:** Qualified names are split by typed parser context and mapped using the
owning dialect. MySQL two-part `database.table` maps database to catalog; PostgreSQL,
Oracle, and SQL Server preserve their catalog/schema rules and quoted case semantics.

**Producers:** token-event/full-grammar adapters, DDL adapters, trigger/routine scopes.

**Consumers:** relationship aliases, lineage sources, DDL inventory, naming, and
known-physical reconstruction.

**Known bypasses:** the common resolver always treats a two-part identifier as
`schema.table`; some paths unquote before dialect normalization.

**Required proof:** parameterized tests cover MySQL `db.t`, PostgreSQL/Oracle quoted
case, SQL Server bracketed components containing dots, and unqualified names under an
explicit namespace.

### ID-03: Statement And Definition Namespace Propagation

**Invariant:** Each statement is resolved with the namespace of its structured source.
Live object/DDL definition catalog and schema override a different or empty global scan
scope for that statement only.

**Producers:** file framing, `DatabaseObjectDefinition`, `DatabaseDdlDefinition`, and
metadata snapshot.

**Consumers:** relationship, lineage, DDL inventory, naming, and known-physical tests.

**Known bypasses:** live object namespace is currently retained mainly as provenance;
some naming and DDL post-processing paths reuse only global scan scope.

**Required proof:** a bare table in a definition from catalog/schema A resolves to A
even when global scope is empty or B, and cannot match B.

### ID-04: One Canonical Fact Key

**Invariant:** relationship, lineage, naming, DDL enhancement, profile budgeting,
semantic observation fingerprints, and candidate deduplication use one dialect-aware
canonical endpoint key. Display spelling remains separate from identity.

**Known bypasses:** direct `Endpoint.normalizedKey()` use in merger/dedup identity,
lowercasing in isolated direction logic, and raw keys in enhancement indexes.

**Required proof:** qualified metadata and namespace-resolved bare SQL merge once;
different catalog/schema or quoted-case identities never merge; all retained SQL
locations remain separate observations.

### ID-05: No Namespace Downgrade At Live, Profile, Or Derived Boundaries

**Invariant:** every live operation proves its executable catalog before the first
catalog/profile query; dialect profile SQL renders a legal table reference; derived
bridges include complete table identity.

**Known bypasses:** profile-only PostgreSQL/SQL Server scans bypass catalog validation;
Oracle catalog input is not rejected or proven; PostgreSQL profiler emits a three-part
table; derived table bridge omits catalog.

**Required proof:** profile-only catalog mismatch issues zero profile SQL; PostgreSQL
renders `schema.table` after validating connection database; Oracle rejects unsupported
catalog input; cross-catalog same-name tables cannot form any derived path.

## 6. Evidence And Observation Matrix

### EV-01: Complete Observation Identity

**Invariant:** exact observation identity includes fact identity, semantic evidence
type, file/object, statement/block/line, join or mapping kind, endpoint side, rule, and
the sorted complete condition set.

**Known bypass:** SQL relationship pre-deduplication omits conditions.

**Required proof:** observations differing only by a guard or SQL location both survive;
only byte-for-byte semantic duplicates fold.

### EV-02: Complete Conditional And Polymorphic Summary

**Invariant:** every typed guard is retained. A fact is conditional only when every
structural observation is guarded. An unconditional observation wins at fact summary,
while guarded raw evidence remains. Polymorphism considers all guard values and targets.

**Known bypass:** merger summary reads only the flattened first guard.

**Required proof:** multi-guard, mixed guarded/unguarded, and multi-target discriminator
tests pass through extractor, merger, JSON, and semantic fingerprint.

### EV-03: Consensus Attributes

**Invariant:** top-level candidate and grouped evidence attributes contain only values
deeply equal across all contributing observations. Conflicting provenance remains only
in raw evidence.

**Known bypasses:** relationship and some naming summaries copy the first observation.

**Required proof:** conflicting file, line, block, object, and condition attributes do
not leak into grouped summaries; common attributes remain.

### EV-04: Uniform Occurrence Semantics

**Invariant:** `occurrenceCount` represents exact multiplicity only. Summary observation
counts equal the sum of raw evidence occurrence counts for relationship, lineage,
naming, and every derived fact type. Folded duplicates never earn repetition confidence.

**Known bypass:** relationship and derived path JSON counts use list size.

### EV-05: Enhancement Idempotence

**Invariant:** one metadata, DDL, profile, or naming fact is attached once per matching
fact identity, not once per preexisting SQL observation. Re-running an enhancement is
idempotent.

**Known bypass:** metadata enhancement can add one catalog evidence occurrence for each
raw candidate observation.

### EV-06: Derived Variant Preservation

**Invariant:** derived facts use canonical endpoint-path identity. Variants of the same
path merge into one fact and preserve all distinct edge/reference observations.
Conditional direct relationship and naming edges remain excluded.

**Known bypass:** derived relationship enumeration can be first-path-wins even though
derived lineage already merges variants.

## 7. Public Contract Matrix

### CT-01: Direct Java Path Inputs

`ScanConfig.ddlPaths/objectPaths/logPaths` and include patterns must behave identically
through CLI/YAML and direct `ScanEngine` use. The shared core resolver expands them before
capability validation and collection; it must not silently return an empty scan.

### CT-02: Direct Adaptor Identity And SPI Validation

`ScanEngine.scan(config, adaptor)` validates SPI version, adaptor id when explicitly
configured, and supported database type before opening JDBC. CLI registry and direct API
use the same validator.

### CT-03: Complete Capability Preflight

Every requested source validates both producer and required consumer before JDBC:
live DDL requires collector plus structured DDL parser; live objects require collector
plus structured SQL parser; profiling requires profiler. File-only scans remain valid.

### CT-04: Live Failure Boundaries

Status: `FOCUSED_GREEN` (Task 9 focused core tests).

JDBC connection failure is non-recoverable and maps to the connection error contract.
A metadata catalog-family failure is recoverable, sanitized, and must not prevent live
DDL/object collection. Proven catalog configuration failures remain non-recoverable.

### CT-05: Evidence Weight Adjustment

Status: `FOCUSED_GREEN` (Task 9 focused core tests).

The advertised `EVIDENCE_WEIGHT_ADJUSTMENT` capability has exactly one consumer in the
scan pipeline. Each raw evidence item is adjusted once before merge; built-in no-op
adjusters preserve current output. The adjusted score is range-validated.

### CT-06: Executable CLI Error Codes

Status: `IMPLEMENTED`; focused single-scan error-code verification remains required.

Single-scan CLI maps argument, config file, config format, adaptor, input file, database
connection, runtime scan, and output write failures to their declared `ErrorCode` values.
Argument parsing is inside the mapping boundary. Batch behavior retains its separate
partial-failure contract.

## 8. Verification Gates

### TG-01: Fixed Adversarial Test Matrix

The fixed test families are:

1. cross-catalog/schema/quoted identity;
2. profile-only catalog truth and legal SQL rendering;
3. source-definition namespace propagation;
4. third-party adaptor and direct API validation;
5. direct path expansion for DDL/object/log inputs;
6. multi-guard conditional observations;
7. consensus and occurrence semantics;
8. enhancer idempotence and derived variants;
9. live failure isolation and CLI error mapping.

These families may gain cases for a frozen invariant but may not become a general parser
feature suite.

### TG-02: Architecture And Enum Contract Gates

The source-size gate identifies top-level record declarations through compiler AST, not
text search. Public enum serialization/deserialization and all CLI `ErrorCode` mappings
are exhaustively tested. Predeclared architecture scans reject raw identity bypasses in
the enumerated merger, profile, naming, DDL, and derived paths.

### TG-03: One Final Reverse Audit And Release Verification

After all behavior IDs are focused-green:

1. run the fixed reverse-audit queries once;
2. run affected modules and the 19-category matrix;
3. run isolated full correctness;
4. run isolated sample-data CLI and verify 19 categories / 38 JSON outputs;
5. run release verification and inspect its manifest;
6. update generated reports only through their generators and rerun freshness checks.

The final review is restricted to the frozen matrix. Non-blocking findings are recorded
in backlog and do not restart convergence.

## 9. Output And Golden Policy

- Structural refactoring and contract wiring must not change physical facts.
- Identity fixes may remove false merges or recover facts only when a focused negative
  test proves the old identity was invalid.
- Evidence fixes may change condition arrays, grouped attributes, raw observations, and
  observation counts without changing direct endpoint facts.
- Any correctness golden difference must include SQL, old output, new output, and its
  closure ID before update.
- Golden files are never refreshed merely to make a structural change pass.

## 10. Completion Definition

Convergence is complete only when:

- all 20 IDs are `CLOSED`;
- the fixed reverse audit reports no bypass for those IDs;
- all focused positive, negative, and direct-API tests pass;
- all discovered correctness fixtures pass without unexplained golden changes;
- all 19 parser categories and 38 direct/derived JSON outputs are generated;
- sample-data diagnostics, evidence references, source lines, path hygiene, and derived
  cycle checks pass;
- design traceability contains no `PARTIAL` or `REVIEW_NEEDED` for these 20 IDs.

At that point this closure effort ends. New parser features, performance work, runtime
environment coverage, and other non-blocking improvements stay in backlog.
