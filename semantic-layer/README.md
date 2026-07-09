# Semantic Layer

Semantic Layer consumes relation-detector JSON and builds evidence-backed semantic artifacts.

`semantic build` and `semantic extract` both create deterministic event candidates from relation-detector lineage
before any LLM call. These candidates come from routines, triggers, standalone SQL writes, fact/dimension refreshes,
state changes, inventory movement, and accounting/cash reconciliation writes. LLM extraction may rename and explain
events, but it should reference `eventCandidates` instead of inventing event facts. Each event candidate now carries
deterministic readability hints: `readableNameHint`, `businessActionHint`, and `eventNameBasis`.

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
  --input relation-detector/target/sample-data-parser-cli/results/mysql-v8_0-full-derived-fresh.json \
  --output semantic-layer/target/semantic-e2e \
  --name mysql-v8_0-full-derived
```

Evidence candidate limits default to `0`, meaning unlimited. Use `--max-relationships`, `--max-lineage`, or
`--max-naming` only when intentionally creating a preview prompt view; production extraction should keep the full
candidate pool so correctness is not sacrificed for prompt compactness.

## Semantic extraction providers

`semantic extract` has two providers:

| Provider | Intended use | External API call | API key |
| --- | --- | --- | --- |
| `codex-session` | Development and manual testing inside Codex | No | Not required |
| `openai-api` | Production or automated LLM extraction | Yes | Required |

Development default is `codex-session`. It only writes:

- `semantic-extraction-evidence-bundle.json`
- `semantic-extraction-prompt.md`
- `semantic-extraction-codex-session.md`

It does not call an external model provider and does not require `OPENAI_API_KEY`.
It also does not create `semantic-extraction-result.json` by itself; the Codex session or a human must generate JSON
from the prompt, then run `semantic normalize-extraction`.

`openai-api` writes both the raw model text and the normalized semantic document:

- `semantic-extraction-result-raw.json`
- `semantic-extraction-result.json`

The normalized result is a ref-closed semantic document. It contains stable ids, entity refs, `semanticGraph`, and
`validation` so users can inspect whether entities, events, relations, lineage, metrics, dimensions, and triplets are
connected.

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

For no-API Codex-session testing, save the generated JSON and normalize it locally. This command is raw-only: it builds
stable ids, `semanticGraph`, and `validation`, but it does not have the evidence bundle available for candidate
backfill:

```bash
java -jar semantic-layer/semantic-cli/target/relation-detector-semantic-cli-0.1.0-SNAPSHOT.jar \
  semantic normalize-extraction \
  --input semantic-layer/semantic-extraction-preview/mysql-v8_0-full/semantic-extraction-result-full.json \
  --output semantic-layer/semantic-extraction-preview/mysql-v8_0-full/semantic-extraction-result-normalized.json
```

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
