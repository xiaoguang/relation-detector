# Semantic Layer

Semantic Layer consumes relation-detector JSON and builds evidence-backed semantic artifacts.

`semantic build` and `semantic extract` both derive deterministic event candidates from relation-detector lineage
before any model call. `semantic extract` also writes a sibling `deterministic-kg/` artifact from the same
`ScanBundle`; the model does not receive or rewrite that KG. It receives evidence-closed bundle shards whose stable
fact, candidate, and evidence references can be validated against the original complete bundle.

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

Evidence candidate limits default to `0`, meaning unlimited. Positive `--max-relationships`, `--max-lineage`, or
`--max-naming` values are preview-only and therefore require `--shard-mode off`. Production extraction keeps the
complete candidate pool and partitions it deterministically instead of truncating it.

## Semantic extraction providers

`semantic extract` has two providers:

| Provider | Intended use | External API call | API key |
| --- | --- | --- | --- |
| `codex-session` | Development and manual testing inside Codex | No | Not required |
| `openai-api` | Production or automated LLM extraction | Yes | Required |

Development default is `codex-session`. It writes the deterministic KG plus one directory per planned shard:

- `deterministic-kg/semantic-kg.json`
- `full-evidence-bundle.json`
- `shards/shard-NNNN/semantic-extraction-evidence-bundle.json`
- `shards/shard-NNNN/semantic-extraction-prompt.md`
- `shards/shard-NNNN/semantic-extraction-codex-session.md`
- `run-manifest.json`

It does not call an external model provider and does not require `OPENAI_API_KEY`.
For a single shard it also preserves the historical root-level prompt artifacts. For multiple shards it writes a
constrained reconciliation template. A Codex session or human supplies each result, which must be normalized against
that shard's bundle before deterministic merge and final full-bundle normalization.

`openai-api` uses the approved fixed profile `gpt-5.6-sol` with `xhigh` reasoning and writes:

- each shard's request, response, raw output, and normalized document;
- optional reconciliation request/response/patch;
- `merged-draft.json`;
- final `semantic-extraction-result.json`;
- a manifest containing model settings, estimated and actual tokens, attempts, hashes, and artifact sizes.

Shards run sequentially. HTTP 429, 5xx, and transport failures use bounded retry; contract, JSON, or evidence-closure
failures do not retry as transport errors. The final document is returned only after normalization against the
original complete bundle proves reference closure.

The normalized document keeps human-readable fields such as `name`, `type`, `meaning`, `readable`, and `description`,
but these fields are not the contract by themselves. The stable contract is:

- every semantic item has `id` and `evidenceRefs`;
- cross-section links use ids such as `inputEntityRefs`, `fromEntityRef`, `targetEntityRef`, and `dimensionEntityRef`;
- events may include `eventCandidateRef`, pointing back to deterministic event candidates in the extraction bundle;
- triplets include `candidateRef`, pointing back to deterministic triplet candidates in the extraction bundle;
- review items use `targetRef` and `targetSection`; the normalizer auto-generates review items for `REVIEW_NEEDED`
  semantic items that do not already have one;
- `semanticGraph.nodes` and `semanticGraph.edges` are built from the same ids;
- `validation.isRefClosed` is false when there are isolated entities, unresolved references, or missing `evidenceRefs`;
- `validation.generatedReviewItemCount` reports review items created by the deterministic normalizer;
- `validation.isolatedEntities`, `validation.unresolvedReferences`, and `validation.missingEvidenceRefs` explain what
  still needs prompt tuning, parser evidence, or human review.

For a no-API single-shard Codex-session test, use the exact evidence bundle in the published `run-<runId>`
directory:

```bash
java -jar semantic-layer/semantic-cli/target/relation-detector-semantic-cli-0.1.0-SNAPSHOT.jar \
  semantic normalize-extraction \
  --input <run-dir>/semantic-extraction-result-full.json \
  --evidence-bundle <run-dir>/semantic-extraction-evidence-bundle.json \
  --output semantic-layer/semantic-extraction-preview/mysql-v8_0-full/semantic-extraction-result-normalized.json
```

Automatic sharding forms connected table-touch components first, then deterministically packs small disconnected
components into a bounded shard so one small component does not cause one model call. An oversized component is split
into evidence-closed table-owned units. If one table owner still exceeds the hard estimate gate, stable root IDs form
`table#part-NNNN` subshards; each root and its typed dependency/evidence closure remains indivisible. A table is
therefore the canonical ownership axis, not necessarily one model request. Each fact and deterministic candidate has
exactly one canonical owner; overlap is read-only context. Component edges come only from typed endpoint and
fact/candidate reference fields; descriptions, diagnostics, and arbitrary attributes cannot connect components.
Every model-authored item must carry `ownedGroundingRefs` from the current shard. A raw owner validator rejects
overlap-only or cross-owner output before backfill and formal normalization; `evidenceRefs` remain audit context and
do not establish ownership.

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

Sharding bounds model-request context; it does not stream the source JSON. The current `ScanResultReader` materializes
one complete relation-detector result before building the deterministic KG and shard plan. Inputs that cannot fit that
typed in-memory representation require a separate streaming or on-disk ingestion design and must not be treated as
supported merely because the resulting model prompts could be split.

`--output` is a reusable run root. Each invocation writes `.staging-<runId>` and publishes it as `run-<runId>` only
after shard execution, merge, full-bundle normalization, graph/reference closure, artifact hashing, and manifest
creation all succeed. Failed runs remain as FAILED staging directories and never appear as completed runs. Hashes are
streamed. `artifactRetention=full|final-only` controls successful-run payload retention without weakening failure
auditability. Deterministic KG, build-run, and evidence-graph files are streamed directly through Jackson instead of
being materialized as one unbounded Java String. `final-only` pruning applies after a complete model result exists;
request-only runs retain their request payload because that payload is their deliverable. YAML is strictly shaped,
rejects unknown or invalid values, resolves relative paths from the config directory, and is validated again after
CLI overrides.

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

All generated semantic candidates remain `SYSTEM_PROPOSED` unless a later governance/review step approves them.
