# Semantic Layer

Semantic Layer consumes relation-detector JSON and builds evidence-backed semantic artifacts.

`semantic build` and `semantic extract` both derive deterministic event candidates from relation-detector lineage
before any model call. Both commands use the same streaming, disk-backed `SemanticInputStore` and
`SemanticEvidenceStore`; `semantic extract` also writes a sibling `deterministic-kg/` artifact. The model does not
receive or rewrite that KG. It receives evidence-closed bundle shards whose stable fact, candidate, and evidence
references are validated against the original complete store.

`semantic extract` also emits deterministic `reviewItemCandidates` and `tripletCandidates`:

- `reviewItemCandidates` anchor diagnostics and uncertain facts that need a business or data owner decision.
- `tripletCandidates` provide grounded summary candidates for entity relations, event input/output, metric sources,
  dimensions, lineage transforms, and naming aliases. LLM triplets should preserve `candidateRef`; they are summaries,
  not replacements for relationship or lineage facts.

For deterministic end-to-end validation without an LLM call, use `semantic e2e`. It writes both:

- `semantic-kg/<case-name>/semantic-kg.json`
- `semantic-extraction/<case-name>/semantic-extraction-evidence-bundle.json`

Example:

```bash
java -jar semantic-layer/semantic-cli/target/relation-detector-semantic-cli-0.1.0-SNAPSHOT.jar \
  semantic e2e \
  --input relation-detector/target/sample-data-parser-cli/results/mysql-v8_0-full.json \
  --output semantic-layer/target/semantic-e2e \
  --name mysql-v8_0-full
```

Formal extraction always builds one complete, reference-closed evidence bundle. It does not expose a focus filter or
pre-sharding relationship, lineage, or naming count limits. Typed sharding and the conservative input-token estimate
are the only model-context controls; they partition the complete input without truncating facts or candidates.

## Semantic extraction providers

`semantic extract` has two providers:

| Provider | Intended use | External API call | API key |
| --- | --- | --- | --- |
| `codex-session` | Development and manual testing inside Codex | No | Not required |
| `openai-api` | Production or automated LLM extraction | Yes | Required |

Development default is `codex-session`. It writes the deterministic KG plus a reconstructable request package and one
directory per planned shard:

- `deterministic-kg/semantic-kg.json`
- `request-bundle-index.json`
- `request-bundle/evidence-records.json.gz`
- `shards/shard-NNNN/semantic-extraction-evidence-bundle.json`
- `shards/shard-NNNN/semantic-extraction-prompt.md`
- `shards/shard-NNNN/semantic-extraction-codex-session.md`
- `shards/shard-NNNN/external-audit-refs.tsv`
- `run-manifest.json`

It does not call an external model provider and does not require `OPENAI_API_KEY`. The request package can reconstruct
the complete evidence bundle after the original scan input has been removed; direct reconstruction verifies
shard-declared ownership counts, sidecar integrity, and the canonical bundle hash. Codex completion additionally
verifies the v2 owner-manifest artifact before consuming responses. Only a real model execution retains
`full-evidence-bundle.json` directly.
Single-shard and multi-shard runs use the same layout: request payloads exist only under `shards/shard-NNNN/`.
Multiple shards also receive a constrained reconciliation template. A Codex session or human supplies each result,
which is normalized against that shard's bundle before deterministic merge. Final output rechecks complete evidence,
owned grounding, candidate-section membership, full catalog/schema physical table and column identities, and final
semantic-entity/review-target closure before any artifact is published.

`openai-api` uses the approved fixed profile `gpt-5.6-sol` with `xhigh` reasoning and writes:

- each shard's request, response, raw output, and normalized document;
- optional reconciliation request/response/patch;
- `merged-draft.json`;
- final `semantic-extraction-result.json`;
- a manifest containing model settings, estimated and actual tokens, attempts, hashes, and artifact sizes.

Shards run sequentially. HTTP 429, 5xx, and transport failures use bounded retry; contract, JSON, or evidence-closure
failures do not retry as transport errors. The final document is returned only after normalization against the
original complete bundle proves reference closure.

Repository verification is split into three explicit tiers. `smoke` runs the fixed MySQL v8 derived case through
full KG/request-package reconstruction and closure; `matrix` runs the same deterministic checks for all 38
direct/derived inputs without invoking a model; `enrichment` consumes separate Codex-session responses and reuses the
production owner validator, normalizer, merger, reconciliation patch validator, and final closure. Missing responses
produce `pending-responses.json`; they never turn a request-only run into a successful semantic result.

The Codex model sees an owner-preserving projection rather than a truncated fact bundle. Deterministic triplet
candidates remain in the immutable request package and are backfilled by core. Event endpoint arrays must be empty in
model output and are rebuilt from typed event candidates. `semanticGraph` and `validation` are also returned as null
and rebuilt after normalization. The complete evidence bundle remains authoritative for ownership and closure.

The normalized document keeps human-readable fields such as `name`, `type`, `meaning`, `readable`, and `description`,
but these fields are not the contract by themselves. The stable contract is:

- every semantic item has `id` and `evidenceRefs`;
- cross-section links use ids such as `inputEntityRefs`, `fromEntityRef`, `targetEntityRef`, and `dimensionEntityRef`;
- events may include `eventCandidateRef`, pointing back to deterministic event candidates in the extraction bundle;
- triplets include `candidateRef`, pointing back to deterministic triplet candidates in the extraction bundle;
- review items use `targetRef` and `targetSection`; the normalizer auto-generates review items for `REVIEW_NEEDED`
  semantic items that do not already have one;
- `semanticGraph.nodes` and `semanticGraph.edges` are built from the same ids;
- published formal results require `validation.isRefClosed=true`; unresolved references or missing `evidenceRefs`
  fail normalization atomically, while evidence-backed isolated entities are reported separately and do not break closure;
- `validation.generatedReviewItemCount` reports the unique generated review items retained in the final review
  section after normalizer and cross-shard canonicalization; model-authored review items are not counted;
- `validation.isolatedEntities` reports evidence-backed isolated entities; published formal results keep
  `unresolvedReferences` and `missingEvidenceRefs` empty because either condition fails normalization atomically.

For a no-API single-shard Codex-session test, use the exact evidence bundle in the published `run-<runId>`
directory:

```bash
java -jar semantic-layer/semantic-cli/target/relation-detector-semantic-cli-0.1.0-SNAPSHOT.jar \
  semantic normalize-extraction \
  --input <run-dir>/shards/shard-0001/semantic-extraction-result.json \
  --evidence-bundle <run-dir>/shards/shard-0001/semantic-extraction-evidence-bundle.json \
  --output semantic-layer/semantic-extraction-preview/mysql-v8_0-full/semantic-extraction-result-normalized.json
```

Automatic sharding forms connected table-touch components first, then deterministically packs small disconnected
components into a bounded shard so one small component does not cause one model call. An oversized component is split
into evidence-closed table-owned units. If one table owner still exceeds the hard estimate gate, stable root IDs form
`table#part-NNNN` subshards; each root and its typed dependency/evidence closure remains indivisible. A table is
therefore the canonical ownership axis, not necessarily one model request. Each fact and deterministic candidate has
exactly one canonical owner; overlap is read-only context. Component edges come only from typed endpoint and
fact/candidate reference fields; descriptions, diagnostics, and arbitrary attributes cannot connect components.
In the `SemanticRunArtifactWriter`/`openai-api` shard flow, every model-authored item must carry
`ownedGroundingRefs` from the current shard. A raw owner validator rejects overlap-only or cross-owner output before
backfill and formal normalization; `evidenceRefs` remain audit context and do not establish ownership. The standalone
`normalize-extraction` command uses the same owner-aware normalizer and therefore requires a validated `shardContext`
whose owned and overlap refs are unique, disjoint and present in the supplied evidence bundle.

Cross-shard entity identity is deterministic. A complete `physicalName` identifies one physical entity. A pure
business entity uses normalized name, machine type, and its owned grounding signature. Equal signatures merge and
rewrite typed references; equal names with different grounding remain distinct and receive
`POTENTIAL_SEMANTIC_DUPLICATE` review items.

`force` preserves one diagnostic unit per component/table split, while `off` ignores the preferred
`targetInputTokens` threshold but still applies the configured `maxInputTokens` estimate gate. Both values are checked
against a deterministic character-based estimate with a safety margin, not a model-specific tokenizer. They therefore
bound the repository's estimate, not the provider's exact token count. If one final atomic closure exceeds the
configured estimate gate, planning fails explicitly rather than truncating facts. The default `maxShardCount=128` is
a run-safety cap; callers processing very large derived bundles must raise it explicitly and audit the resulting
manifest rather than treating the default as a capability limit.

Sharding bounds model-request context; source ingestion is a separate disk-backed concern. `ScanResultReader.open()`
streams relation-detector JSON into `SemanticInputStore`, and `SemanticProcessingSession` builds the global evidence
store and owner plan on disk without materializing one whole scan result. Raw-byte transport windows do not truncate
facts or alter ownership; every owned/overlap prompt still passes the configured hard estimate gate before use.

`--output` is a reusable run root. Each invocation writes `.staging-<runId>` and atomically publishes a
mode-specific deliverable as `run-<runId>`: codex-session publishes `AWAITING_MODEL_RESULTS`, request-only publishes
`REQUESTS_READY`, and a model execution publishes `COMPLETE` only after shard execution, merge, full-bundle
normalization, graph/reference closure, artifact hashing, and manifest creation all succeed. A published run directory
therefore is not by itself proof of completed model extraction; callers must inspect the manifest status. Failed
executions never publish a final run. If staging exists, the writer makes a best-effort attempt to write a `FAILED`
manifest, but a second I/O failure can prevent that manifest from landing. Hashes are streamed.
`artifactRetention=full|final-only` controls successful-run payload retention. Deterministic KG, build-run, and
evidence-graph files are streamed directly through Jackson instead of being materialized as one unbounded Java String.
Full/shard evidence bundles, merged drafts, final normalized results, and standalone normalized outputs use the same
direct-to-file rule. Prompt and transport request strings remain bounded by the configured estimate gate.
`final-only` pruning applies after a complete model result exists; request-only runs retain their request payload
because that payload is their deliverable. YAML is strictly shaped, rejects unknown or invalid values, resolves
relative paths from the config directory, and is validated again after CLI overrides.

Production should use `openai-api`, either by command-line flags or config:

```bash
java -jar semantic-layer/semantic-cli/target/relation-detector-semantic-cli-0.1.0-SNAPSHOT.jar \
  semantic extract \
  --config semantic-layer/examples/semantic-extraction-openai-api.yml
```

The API provider calls an OpenAI-compatible Responses API and reads the key from `apiKeyEnv`, defaulting to
`OPENAI_API_KEY`. Use `--request-only` with `provider: openai-api` to generate the request JSON without calling the
provider.

## Config examples

Development/Codex session:

```bash
java -jar semantic-layer/semantic-cli/target/relation-detector-semantic-cli-0.1.0-SNAPSHOT.jar \
  semantic extract \
  --config semantic-layer/examples/semantic-extraction-codex-session.yml
```

Production/API:

```bash
java -jar semantic-layer/semantic-cli/target/relation-detector-semantic-cli-0.1.0-SNAPSHOT.jar \
  semantic extract \
  --config semantic-layer/examples/semantic-extraction-openai-api.yml
```

Generated semantic candidates carry `SYSTEM_PROPOSED` until a later review step. The normalizer rejects
model-authored `BUSINESS_APPROVED`, fills missing formal-object `reviewStatus` with `SYSTEM_PROPOSED`, and fills
missing review-item status with `REVIEW_NEEDED`. Consumers must not interpret any non-approved state as business
approval.
