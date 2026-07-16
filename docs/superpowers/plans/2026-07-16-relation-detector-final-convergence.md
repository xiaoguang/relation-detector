# Relation Detector Final Convergence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the 20 frozen identity, evidence, public-contract, and verification IDs in the final convergence design without adding parser features or performance mechanisms.

**Architecture:** Establish one dialect-aware identity path from typed identifiers to canonical fact keys, one observation aggregation contract across fact types, and one preflight/error boundary for CLI and direct API scans. Preserve display endpoints and the public JSON schema while moving all merge, lookup, profile, and derived decisions onto validated internal keys.

**Tech Stack:** Java 17, Maven reactor, JUnit 5, ANTLR-generated typed contexts, Jackson JSON/YAML, repository shell verification tools.

## Global Constraints

- The frozen specification is `docs/superpowers/specs/2026-07-16-relation-detector-final-convergence-design.md`.
- Do not add another closure ID; attach new evidence to an existing ID or backlog it.
- Do not add performance caches, schedulers, thread pools, parser features, grammar rules, or semantic-layer changes.
- Do not infer SQL structure with regex, scanner passes, token spans, rule-name strings, reflection, or identifier allowlists.
- Keep token-event and full-grammar parsers independent.
- Do not change the public JSON schema, CLI/YAML fields, confidence formula, or parser mode.
- `NEGATIVE_VALUE_MISMATCH` remains whole-table profiling; filtered row-domain profiling is backlog.
- Write a failing test and verify the intended failure before every production behavior change.
- Do not update golden files unless a closure-ID review records SQL, old output, new output, and classification.
- Heavy correctness and CLI jobs run sequentially through existing isolation scripts.

---

### Task 1: Freeze The Contract And Baseline Proof

**Files:**
- Create: `relation-detector/core/src/test/java/com/relationdetector/core/identity/FinalIdentityContractTest.java`
- Create: `relation-detector/core/src/test/java/com/relationdetector/core/evidence/FinalEvidenceContractTest.java`
- Create: `relation-detector/core/src/test/java/com/relationdetector/core/scan/FinalScanContractTest.java`
- Modify: `docs/design/relation-detector/code-design-traceability.md`
- Modify: `docs/design/relation-detector/design-validation-report.md`

**Interfaces:**
- Consumes: the frozen 20-ID specification.
- Produces: red tests grouped by invariant, not by implementation class.

- [ ] **Step 1: Record the code baseline and closure IDs**

Add a generated-free table to both design documents listing `ID-01..05`, `EV-01..06`,
`CT-01..06`, and `TG-01..03` with initial state `OPEN`. Do not change existing technical
status to `MATCHED`.

- [ ] **Step 2: Add one representative red test per group**

The identity test must construct same-normalized-name/different-structure tables and assert
they are not the same identity. The evidence test must merge one relationship observation
with two guards and assert both survive. The scan test must provide only `ddlPaths` through
direct `ScanEngine` use and assert the DDL is processed rather than ignored.

- [ ] **Step 3: Run and verify RED**

Run:

```bash
mvn -pl relation-detector/core -am \
  -Dtest='FinalIdentityContractTest,FinalEvidenceContractTest,FinalScanContractTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: all three families execute; at least the intended identity, multi-guard, and path
tests fail for the documented reasons.

- [ ] **Step 4: Save baseline output outside Git**

Write the focused failure summary and current `git diff --stat` to
`relation-detector/target/final-convergence/`. Do not stage `target`.

### Task 2: Close Structural And Dialect Identifier Identity (ID-01, ID-02)

**Files:**
- Modify: `relation-detector/contracts/src/main/java/com/relationdetector/contracts/model/TableId.java`
- Modify: `relation-detector/contracts/src/main/java/com/relationdetector/contracts/model/Endpoint.java`
- Modify: `relation-detector/contracts/src/main/java/com/relationdetector/contracts/spi/IdentifierRules.java`
- Modify: `relation-detector/core/src/main/java/com/relationdetector/core/identity/CanonicalIdentifierResolver.java`
- Modify: `relation-detector/core/src/main/java/com/relationdetector/core/identity/CanonicalEndpointKey.java`
- Modify: four `*DatabaseAdaptor.java` identifier-rule definitions
- Test: `FinalIdentityContractTest.java`
- Test: existing `CanonicalIdentifierResolverTest`, `EndpointCatalogIdentityTest`

**Interfaces:**
- Produces: `IdentifierRules.qualifiedNameSemantics()` as a defaulted SPI method and one
  canonical component-normalization path.
- Consumers: relationship, lineage, DDL, profile, and derived tasks below.

- [ ] **Step 1: Expand RED tests**

Add parameterized assertions for:

```text
MySQL: db.orders -> catalog=db, schema=null, table=orders
PostgreSQL: "Orders" != orders
Oracle: "Orders" != ORDERS
SQL Server: [db.part].[sales.part].[orders.part] retains all three components
```

Also assert a forged `TableId` with matching `normalizedName` but different schema/table
cannot satisfy `sameIdentity()` or form a valid `Endpoint` with the other table's column.

- [ ] **Step 2: Verify RED**

Run the two identity test classes and confirm each new assertion fails at the expected old
comparison or two-part mapping.

- [ ] **Step 3: Implement minimal canonical component semantics**

Add a default SPI value that preserves existing `schema.table` behavior and override MySQL
to use `catalog.table`. Normalize quoted identifiers through the dialect rule before removing
display quotes. Make `TableId.sameIdentity()` independently compare catalog, schema,
table name, and normalized table identity; never use record equality as dialect identity.

- [ ] **Step 4: Replace direct structural equality in enumerated identity paths**

Replace `TableId.equals()`/raw normalized-name identity in relationship self-join and naming
self-reference paths with the canonical resolver. Do not replace display comparisons used
only for deterministic output ordering.

- [ ] **Step 5: Verify GREEN**

Run:

```bash
mvn -pl relation-detector/contracts,relation-detector/core,relation-detector/adaptor-mysql,relation-detector/adaptor-postgres,relation-detector/adaptor-oracle,relation-detector/adaptor-sqlserver -am \
  -Dtest='FinalIdentityContractTest,CanonicalIdentifierResolverTest,EndpointCatalogIdentityTest,*CatalogIdentity*' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

### Task 3: Propagate Source Namespace And Canonical Fact Keys (ID-03, ID-04)

**Files:**
- Modify: `relation-detector/core/src/main/java/com/relationdetector/core/scan/StatementParsePipeline.java`
- Modify: `relation-detector/core/src/main/java/com/relationdetector/core/scan/StatementExecutionService.java`
- Modify: `relation-detector/core/src/main/java/com/relationdetector/core/relation/RelationshipAliasSupport.java`
- Modify: `relation-detector/core/src/main/java/com/relationdetector/core/relation/RelationshipMerger.java`
- Modify: `relation-detector/core/src/main/java/com/relationdetector/core/lineage/DataLineageMerger.java`
- Modify: `relation-detector/core/src/main/java/com/relationdetector/core/naming/NamingEvidenceMerger.java`
- Modify: `relation-detector/core/src/main/java/com/relationdetector/core/naming/NamingEvidencePool.java`
- Modify: `relation-detector/core/src/main/java/com/relationdetector/core/provenance/SemanticObservationFingerprint.java`
- Test: `FinalIdentityContractTest.java`
- Test: existing merger, naming, projection, and database-object tests

**Interfaces:**
- Produces: a per-statement `NamespaceContext` derived from definition provenance and a
  canonical endpoint-key provider passed to mergers.

- [ ] **Step 1: Add namespace and merger RED tests**

Test a live definition from catalog/schema A containing bare `orders` while scan scope is B.
Assert relationship, lineage, DDL inventory, naming, and known-physical identity all use A.
Test qualified metadata plus bare SQL under A merges to one fact with two observations;
catalog B never merges.

- [ ] **Step 2: Verify RED**

Run the new tests plus `RelationshipMergerEvidenceAggregationTest`, `DataLineageMergerTest`,
and `NamingEvidencePoolTest`.

- [ ] **Step 3: Carry definition namespace into execution**

Create the statement namespace from `objectCatalog/objectSchema` or DDL definition fields
before extraction. Pass it to both relationship and lineage extractors and to DDL naming /
inventory assembly. Global scope is fallback only when the source has no namespace.

- [ ] **Step 4: Inject one canonical-key provider into fact consumers**

Change merger and fingerprint identity methods to accept/use `CanonicalEndpointKey` under
the active `IdentifierRules + NamespaceContext`. Keep `Endpoint.normalizedKey()` for public
display/reference compatibility only; do not use it as the merge key in the enumerated paths.

- [ ] **Step 5: Verify GREEN and reverse-search**

Run focused tests, then audit identity consumers with:

```bash
rg -n 'sameIdentity\(|\.equals\(|normalizedKey\(' \
  relation-detector/core/src/main/java/com/relationdetector/core/{relation,lineage,naming,ddl,metadata,profile,derived,provenance}
```

Classify every remaining match as display/reference or canonical identity; remove unclassified
identity bypasses.

### Task 4: Close Live, Profile, And Derived Namespace Boundaries (ID-05)

**Files:**
- Modify: `relation-detector/core/src/main/java/com/relationdetector/core/scan/ScanEngine.java`
- Modify: `relation-detector/core/src/main/java/com/relationdetector/core/scan/DataProfilePipeline.java`
- Modify: `relation-detector/core/src/main/java/com/relationdetector/core/profile/IdentifierQuoter.java`
- Modify: PostgreSQL, Oracle, and SQL Server namespace resolvers/profilers
- Modify: `relation-detector/core/src/main/java/com/relationdetector/core/derived/DerivedPathGraphBuilder.java`
- Modify: derived relationship/lineage/naming identity helpers as required by the shared key
- Test: profile-only catalog tests and `DerivedPathInferenceServiceTest`

- [ ] **Step 1: Write RED tests**

Assert PostgreSQL/SQL Server profile-only catalog mismatch fails before profile SQL; Oracle
non-empty unsupported catalog fails before a catalog query; PostgreSQL profile SQL omits the
catalog component after validating connection database; cross-catalog same-name tables never
form derived relationship, lineage, or naming paths.

- [ ] **Step 2: Verify RED**

Run only profiler, namespace resolver, and derived tests.

- [ ] **Step 3: Implement one live namespace validation point**

After opening JDBC and before any metadata/object/DDL/profile operation, ask the adaptor to
validate/resolve executable live scope once. Reuse the result for every live source. Do not
add another connection or query.

- [ ] **Step 4: Render dialect-legal profile table references**

Let the dialect renderer choose whether catalog is executable syntax. PostgreSQL renders
schema/table while using catalog only as a validated connection database. MySQL may render
catalog/table; SQL Server may render catalog/schema/table; Oracle renders owner/table.

- [ ] **Step 5: Use full canonical table keys in derived bridges**

Replace schema-only bridge keys with canonical catalog/schema/table keys and merge same-path
variants without changing valid in-catalog paths.

- [ ] **Step 6: Verify GREEN**

Run all tests named `*Catalog*`, `*DataProfiler*`, and `*DerivedPath*` in affected modules.

### Task 5: Preserve Complete Observation And Conditional Identity (EV-01, EV-02)

**Files:**
- Modify: `relation-detector/core/src/main/java/com/relationdetector/core/relation/StructuredRelationshipExtractor.java`
- Modify: `relation-detector/core/src/main/java/com/relationdetector/core/relation/RelationshipCandidateSupport.java`
- Modify: `relation-detector/core/src/main/java/com/relationdetector/core/relation/RelationshipConditionalSummarizer.java`
- Modify: `relation-detector/core/src/main/java/com/relationdetector/core/relation/RelationshipObservationPolicy.java`
- Modify: `relation-detector/core/src/main/java/com/relationdetector/core/provenance/SemanticObservationFingerprint.java`
- Test: `FinalEvidenceContractTest.java`

- [ ] **Step 1: Write multi-guard RED tests**

Create typed predicate events with two guards, two observations differing only by guard,
and guarded plus unguarded observations. Assert pre-deduplication, merged raw evidence,
summary conditions, polymorphic flag, and semantic fingerprint.

- [ ] **Step 2: Verify RED**

Confirm failure occurs before merger for the different-guard same-position case and in the
summary for the second guard.

- [ ] **Step 3: Canonicalize complete condition arrays**

Use the sorted `conditions` array in exact observation identity. Keep flattened discriminator
fields only as compatibility attributes derived from a single condition; summary logic must
read the complete array.

- [ ] **Step 4: Implement conditional summary rules**

Collect every condition from every structural observation. Preserve conditions only when all
structural observations are guarded; keep guarded raw evidence when an unguarded observation
makes the fact unconditional. Calculate polymorphism from all discriminator/value/target sets.

- [ ] **Step 5: Verify GREEN**

Run relationship extractor, merger, conditional-derived exclusion, and fingerprint tests.

### Task 6: Unify Consensus And Occurrence Semantics (EV-03, EV-04)

**Files:**
- Modify: `relation-detector/core/src/main/java/com/relationdetector/core/evidence/EvidenceObservationAggregator.java`
- Modify: `relation-detector/core/src/main/java/com/relationdetector/core/relation/RelationshipMerger.java`
- Modify: `relation-detector/core/src/main/java/com/relationdetector/core/naming/NamingEvidenceMerger.java`
- Modify: `relation-detector/core/src/main/java/com/relationdetector/core/output/JsonResultWriter.java`
- Test: `FinalEvidenceContractTest.java`
- Test: existing lineage/naming/JSON evidence tests

- [ ] **Step 1: Write RED tests for conflict and folded counts**

Provide observations with shared semantic attributes but conflicting file/line/object values.
Assert only common attributes reach grouped evidence. Fold exact duplicates with
`occurrenceCount=3` and assert every direct/derived summary reports three observations while
confidence receives one observation.

- [ ] **Step 2: Verify RED**

Run relationship, naming, lineage, and JSON writer tests.

- [ ] **Step 3: Consume aggregator consensus everywhere**

Remove first-observation attribute copies in relationship and naming summary builders. Use
the aggregator's deep consensus map and continue preserving complete raw evidence.

- [ ] **Step 4: Centralize occurrence summation**

Add one helper that reads a positive numeric `occurrenceCount`, defaulting to one. Reuse it
for relationship, lineage, naming, and all derived JSON summary counts. Do not feed the sum
into repeated-observation scoring.

- [ ] **Step 5: Verify GREEN**

Run all merger and `JsonResultWriterEvidenceOutputTest` tests.

### Task 7: Make Enhancement And Derived Aggregation Idempotent (EV-05, EV-06)

**Files:**
- Modify: `relation-detector/core/src/main/java/com/relationdetector/core/metadata/MetadataEvidenceEnhancer.java`
- Modify: `relation-detector/core/src/main/java/com/relationdetector/core/ddl/DdlEvidenceInventory.java`
- Modify: `relation-detector/core/src/main/java/com/relationdetector/core/derived/DerivedRelationshipInference.java`
- Modify: `relation-detector/core/src/main/java/com/relationdetector/core/derived/DerivedPathGraphBuilder.java`
- Test: metadata enhancement and derived path tests

- [ ] **Step 1: Write RED tests**

Assert one metadata fact attached to a relationship with three SQL observations has one
metadata occurrence; running enhancement twice is unchanged. Assert two edge variants of one
canonical derived relationship path produce one fact with both raw observations.

- [ ] **Step 2: Verify RED**

Run metadata, DDL inventory, and derived tests.

- [ ] **Step 3: Deduplicate by fact plus authoritative observation identity**

Build metadata/DDL enhancement once per final fact identity and endpoint side. Do not use the
number of SQL observations as authoritative evidence multiplicity.

- [ ] **Step 4: Merge derived relationship variants**

Collect all path observations, group by canonical kind/source/target/path, and merge evidence
variants using the same occurrence/consensus rules. Preserve conditional edge exclusion.

- [ ] **Step 5: Verify GREEN**

Run all affected tests and compare direct fact fingerprints before/after.

### Task 8: Close Direct API And Capability Preflight (CT-01, CT-02, CT-03)

**Files:**
- Create: `relation-detector/core/src/main/java/com/relationdetector/core/scan/ScanInputPathResolver.java`
- Create: `relation-detector/core/src/main/java/com/relationdetector/core/scan/AdaptorContractValidator.java`
- Modify: `relation-detector/core/src/main/java/com/relationdetector/core/scan/ResolvedScanConfig.java`
- Modify: `relation-detector/core/src/main/java/com/relationdetector/core/scan/ScanEngine.java`
- Modify: `relation-detector/core/src/main/java/com/relationdetector/core/scan/ScanCapabilityValidator.java`
- Modify: CLI config loader to reuse the core path resolver rather than duplicate it
- Test: `FinalScanContractTest.java`, direct API and fake adaptor tests

- [ ] **Step 1: Write direct API and fake-adaptor RED tests**

Parameterize DDL/object/log path plus include behavior. Add wrong SPI version, wrong database
type, wrong explicit adaptor id, live DDL without structured DDL parser, and live objects
without structured SQL parser. Assert every failure occurs before the first JDBC connection.

- [ ] **Step 2: Verify RED**

Run scan capability and path tests.

- [ ] **Step 3: Resolve paths in core**

Resolve configured paths/includes into immutable `*Files` during `ScanConfig.resolve()`, with
stable sorting, duplicate removal, and explicit unreadable-file failure. CLI passes the same
base directory and does not perform a second expansion.

- [ ] **Step 4: Validate adaptor contract once**

Move SPI version, supported database type, and explicit adaptor-id checks into a reusable
validator invoked by `ScanEngine` before JDBC. `AdaptorRegistry` reuses it for CLI selection.

- [ ] **Step 5: Complete preflight dependencies**

Live DDL requires capability, collector, and structured DDL parser; live objects require
capability, collector, and structured SQL parser; profile requires capability and profiler.

- [ ] **Step 6: Verify GREEN**

Run core scan, CLI path, adaptor registry, and capability tests.

### Task 9: Close Live Failures, Weight Adjustment, And CLI Errors (CT-04, CT-05, CT-06)

**Files:**
- Modify: `relation-detector/core/src/main/java/com/relationdetector/core/scan/ScanEngine.java`
- Modify: `relation-detector/core/src/main/java/com/relationdetector/core/scan/SourceCollectorPipeline.java`
- Modify: `relation-detector/core/src/main/java/com/relationdetector/core/scan/EvidenceEnhancementPipeline.java`
- Create: `relation-detector/core/src/main/java/com/relationdetector/core/evidence/EvidenceWeightAdjustmentService.java`
- Modify: `relation-detector/cli/src/main/java/com/relationdetector/cli/Main.java`
- Modify: `relation-detector/cli/src/main/java/com/relationdetector/cli/SingleScanRunner.java`
- Test: `FinalScanContractTest.java` and CLI E2E tests

- [ ] **Step 1: Write failure-boundary RED tests**

Assert connection failure throws a typed exception to CLI; metadata failure emits a sanitized
metadata warning and still collects live DDL/object; a custom weight adjuster is invoked once
per raw evidence; each declared `ErrorCode` has a single-scan E2E scenario.

- [ ] **Step 2: Verify RED**

Run scan diagnostics, metadata partial-success, weight adjustment, and CLI tests.

- [ ] **Step 3: Separate nonrecoverable and recoverable failures**

Propagate connection and live configuration failures. Catch metadata collector failure inside
its source boundary with the metadata operation code, then continue sibling live sources.

- [ ] **Step 4: Wire weight adjustment exactly once**

Before final merge, visit raw evidence on direct relationship/naming/lineage candidates and
apply `adaptor.profiling().evidenceWeightAdjuster()`. Reject null evidence or scores outside
the existing accepted score range. Built-in no-op adjusters must produce byte-identical output.

- [ ] **Step 5: Map CLI errors at typed boundaries**

Move argument parsing inside `MainCommand`'s error boundary. Distinguish config path/read,
config format, input file, connection, runtime scan, and output write exceptions without
printing credentials, SQL, or JDBC URLs.

- [ ] **Step 6: Verify GREEN**

Run core diagnostics and all single-scan/batch CLI tests.

### Task 10: Complete Architecture Gates And Final Verification (TG-01, TG-02, TG-03)

**Files:**
- Modify: `relation-detector/core/src/test/java/com/relationdetector/core/parser/DialectGrammarArchitectureTest.java`
- Create or modify: contracts enum round-trip test
- Modify: `docs/design/relation-detector/code-design-traceability.md`
- Modify: `docs/design/relation-detector/design-validation-report.md`
- Modify affected phase documents only where implementation behavior changed

- [ ] **Step 1: Replace text-based record detection with compiler AST**

Reuse the existing JDK compiler-tree setup to classify the top-level declaration. Add a test
fixture where a normal class contains `record TypeName(` in a comment/string and prove it is
not exempted from the size gate.

- [ ] **Step 2: Add exhaustive enum and error-contract tests**

Round-trip every public enum through Jackson and execute every `ErrorCode` path declared in
the frozen CT-06 matrix. Do not add unused enum values.

- [ ] **Step 3: Run the fixed reverse audit**

Review only the frozen paths for raw identity keys, first-observation summary copies, partial
condition identity, list-size observation counting, missing parser-half preflight, and CLI
argument parsing outside the mapping boundary. Attach each remaining legitimate match to its
closure ID; do not open unrelated work.

- [ ] **Step 4: Run focused and matrix verification**

```bash
mvn -pl relation-detector/contracts,relation-detector/core,relation-detector/adaptor-mysql,relation-detector/adaptor-postgres,relation-detector/adaptor-oracle,relation-detector/adaptor-sqlserver,relation-detector/cli -am \
  -Dtest='Final*ContractTest,*Catalog*,*Evidence*,*Capability*,*CliEndToEnd*,DialectGrammarArchitectureTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test

mvn -T 2 -Pmatrix-smoke verify
```

- [ ] **Step 5: Run full correctness without golden update**

```bash
CORRECTNESS_HEAP=6g CORRECTNESS_PARALLELISM=6 \
  bash relation-detector/scripts/run-correctness-isolated.sh
```

Inspect the newly generated run summary and verify every discovered fixture executed.

- [ ] **Step 6: Run all sample-data CLI jobs**

```bash
SAMPLE_DATA_PARSER_CLI_HEAP=6g \
SAMPLE_DATA_PARSER_CLI_CASE_PARALLELISM=4 \
SAMPLE_DATA_PARSER_CLI_SCAN_PARALLELISM=2 \
  bash relation-detector/scripts/run-sample-data-isolated.sh
```

Verify 19 parser categories, 38 direct/derived JSON outputs, `Diag=0`, observation parity,
evidence references, relative paths, source lines, and derived-cycle checks.

- [ ] **Step 7: Refresh owned reports and rerun freshness checks**

Use only `CorrectnessSummaryGeneratorTest` and `DataLineageAuditGeneratorTest` with their
documented update flags, then rerun them without update flags.

- [ ] **Step 8: Run release verification**

Run `bash relation-detector/scripts/verify-release.sh` from a clean worktree and inspect its
manifest rather than relying only on the exit code.

- [ ] **Step 9: Close the matrix**

Change an ID to `CLOSED` only when its RED, focused-green, reverse-audit, and full-green
evidence are present. Move every nonblocking new finding to backlog. End the convergence effort
when all 20 IDs are closed; do not begin another unrestricted audit.

## Plan Self-Review

- Every frozen ID maps to exactly one implementation task and Task 10 verification.
- No task adds parser syntax, performance machinery, semantic-layer behavior, or filtered
  profiling context.
- Identity tasks precede evidence and public-contract tasks that consume canonical keys.
- Evidence changes are tested before mergers or JSON summaries are modified.
- Heavy jobs run once, sequentially, only after focused and matrix tests are green.
- No placeholder, compatibility alias, automatic golden refresh, or open-ended audit remains.
