# Semantic Layer

Semantic Layer consumes relation-detector JSON and builds evidence-backed semantic artifacts.

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
- `semanticGraph.nodes` and `semanticGraph.edges` are built from the same ids;
- `validation.isRefClosed` is false when there are isolated entities, unresolved references, or missing `evidenceRefs`;
- `validation.isolatedEntities`, `validation.unresolvedReferences`, and `validation.missingEvidenceRefs` explain what
  still needs prompt tuning, parser evidence, or human review.

For no-API Codex-session testing, save the generated JSON and normalize it locally:

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
