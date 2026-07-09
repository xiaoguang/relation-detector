# Semantic Layer 子系统设计索引

本目录包含 Evidence-Grounded Semantic Layer 中除 relation-detector 事实层以外的子系统详细设计。总体边界以 [Evidence-Grounded Semantic Layer 整体设计](../semantic-layer-overall-design.md) 为准；术语口径以 [Semantic Layer 术语表](glossary.md) 为准。

## 架构概览

### 当前已实现链路

```text
Relation Detector
  -> Scan Result Reader
  -> Semantic Evidence Builder
  -> NoopSemanticEnricher
  -> SemanticKgBuilder
  -> semantic-kg.json / semantic-evidence-graph.json / semantic-build-run.json
```

这条当前链路吸收 Semantica 官方架构中的 `ingest -> raw documents -> parse / normalize -> extract -> conflict / dedup -> KG / provenance / reasoning` 思路，但落地边界更窄：relation-detector scan result / ScanBundle 是本项目的标准 facts/evidence records；当前代码已落地到离线 KG JSON 阶段，即 `semantic-layer/semantic-core` 可以把 scan result 构建为 evidence graph 与可审计 `semantic-kg.json`，`semantic-layer/semantic-cli` 提供 `semantic build` 离线入口。当前 KG 节点范围是 `PhysicalTable`、`PhysicalColumn`、`RelationshipFact`、`LineageFact`、`NamingEvidenceFact`、`EventFact`、`Diagnostic`、derived fact 和从 relationship fact materialize 的 `JoinPath`；边包括 table-column、fact source/target、event input/output、supported-by evidence 和 path step。

当前还实现了语义抽取 artifact 链路：

```text
Relation Detector JSON
  -> Scan Result Reader
  -> SemanticExtractionBundleBuilder
  -> SemanticExtractionPromptBuilder
  -> semantic extract
       -> codex-session: 写 prompt / evidence bundle / 会话说明，不调用外部模型
       -> openai-api: 调用 OpenAI-compatible Responses API，通过 bundle-aware normalizer 写 raw response 与 normalized semantic document
  -> semantic normalize-extraction
       -> raw-only 规范化 JSON semantic extraction output，补齐 semanticGraph / validation
```

`semantic e2e` 是 deterministic 验证入口：同一次读取 scan result 后同时写 `semantic-kg/<case-name>/` 和 `semantic-extraction/<case-name>/` 的 evidence bundle / prompt artifacts，但不调用模型。当前不写 Semantic Catalog Store，不提供 lexicon、embedding、review queue 或在线问答；这些仍是后续阶段。

`semantic normalize-extraction` 当前不接收 evidence bundle，因此不能补齐 LLM 漏掉的全部 event / triplet / review 候选，也不能逐条验证 `evidenceRefs` 是否解析回 bundle fact id；这类 bundle-aware backfill 当前只在 `openai-api` 写出结果的代码路径中执行。

### 目标离线构建链路

```text
Relation Detector
  -> Scan Result Reader
  -> Semantic Evidence Builder
  -> LLM Semantic Enricher
  -> Semantic Catalog Store
       -> Lexicon Manager
       -> Embedding Indexer
       -> Review Queue
```

Catalog Store 是后续语义资产中心。Lexicon 和 Embedding 从 catalog 并行构建索引，不是彼此的串行下游。后续 Semantic Catalog Store、Lexicon、Embedding、Review Queue 和在线问答仍是设计/后续实现范围，不宣称完整 Context Graph、ontology reasoning 或自动问答已完成。

### 在线问答链路

```text
Question
  -> Question Understanding
  -> Semantic Search
  -> Query Planner
  -> SQL Draft Generator
  -> SQL Validator
  -> Answer Composer
  -> User
```

### 审核链路

```text
[SYSTEM_PROPOSED](glossary.md#system_proposed) semantic objects / conflicts / low confidence items
  -> Review Queue
  -> Human or governance workflow
  -> Semantic Catalog Store
```

## 子系统设计文档

### 离线构建

| 序号 | 子系统 | 文档 | 职责 |
| --- | --- | --- | --- |
| 1 | Scan Result Reader | [01-scan-result-reader.md](01-scan-result-reader.md) | 读取 relation-detector 输出，归一化为 ScanBundle。 |
| 2 | Semantic Evidence Builder | [02-semantic-evidence-builder.md](02-semantic-evidence-builder.md) | 组织 relationship、lineage、metadata、注释 evidence graph。 |
| 3 | LLM Semantic Enricher / Semantic Extraction | [03-llm-semantic-enricher.md](03-llm-semantic-enricher.md) | 构造 evidence bundle / prompt，支持 codex-session、openai-api 和 normalized extraction result。 |
| 4 | Semantic Catalog Store | [04-semantic-catalog-store.md](04-semantic-catalog-store.md) | 持久化 semantic objects、edges、evidence refs、review decisions。 |
| 5 | Lexicon Manager | [05-lexicon-manager.md](05-lexicon-manager.md) | 管理业务词、同义词和对象映射。 |
| 6 | Embedding Indexer | [06-embedding-indexer.md](06-embedding-indexer.md) | 为语义对象生成向量索引。 |

### 在线问答

| 序号 | 子系统 | 文档 | 职责 |
| --- | --- | --- | --- |
| 7 | Semantic Search | [07-semantic-search.md](07-semantic-search.md) | 结合 lexicon、embedding 和 evidence rerank。 |
| 8 | Question Understanding | [08-question-understanding.md](08-question-understanding.md) | 把自然语言问题解析为结构化意图。 |
| 9 | Query Planner | [09-query-planner.md](09-query-planner.md) | 选择表、字段、指标、grain 和 evidence-backed join path。 |
| 10 | SQL Draft Generator | [10-sql-draft-generator.md](10-sql-draft-generator.md) | 根据 AnswerPlan 模板生成 SQL draft。 |
| 11 | SQL Validator | [11-sql-validator.md](11-sql-validator.md) | 校验 draft 是否符合 catalog、evidence 和 governance。 |
| 12 | Answer Composer | [12-answer-composer.md](12-answer-composer.md) | 组装 SQL draft、澄清问题或表字段计划响应。 |

### 治理

| 序号 | 子系统 | 文档 | 职责 |
| --- | --- | --- | --- |
| 13 | Review Queue | [13-review-queue.md](13-review-queue.md) | 管理需要人工或治理流程确认的候选对象。 |

## 全局约束

- 所有语义对象必须携带 `evidenceRefs`，可追溯到 relation-detector 原始输出。
- provenance / auditability 是主线能力，不是输出展示层附属信息；AnswerPlan、SQL draft element 和 review decision 也必须能回溯 evidence。
- LLM 只能生成 [SYSTEM_PROPOSED](glossary.md#system_proposed) semantic objects、解释、同义词和 query rewrite；不能创造数据库事实。
- 指标默认 `SYSTEM_PROPOSED`，只有审核后才能成为 [BUSINESS_APPROVED](glossary.md#business_approved) 正式口径。
- [EVIDENCE_SUPPORTED](glossary.md#evidence_supported) 表示有 evidence 支撑，但不等于业务已确认。
- SQL draft 必须经过 SQL Validator；文档示例不代表自动执行能力。
- 不确定时优先反问用户，而不是生成看似完整但口径不明的 SQL。
- 冲突和去重分两层：系统规则负责发现候选冲突和重复；最终业务确认必须进入 Review Queue / governance workflow。
- Prototype 可用 JSON 文件；production-ready [Phase 1 Scope](glossary.md#phase-1-scope) profile 推荐 PostgreSQL + JSONB + pgvector。
- [Phase 2+](glossary.md#phase-2) / [Future Capability](glossary.md#future-capability) 能力不得写成 Phase 1 Scope 已实现能力。

## 与 relation-detector 的关系

relation-detector 是事实层子系统，负责提取：

- `RelationshipCandidate`
- `DataLineageCandidate`
- `MetadataSnapshot`
- `WarningMessage`

Semantic Layer 在这些事实之上构建业务语义，不修改 relation-detector 的行为或输出。

## 相关文档

- [Evidence-Grounded Semantic Layer 整体设计](../semantic-layer-overall-design.md)
- [Semantic Layer 术语表](glossary.md)
- [Semantic Layer 示例附录](../semantic-layer-examples.md)
- [参考亿问改进分析](yiyiwen-reference-improvement.md)
- [Semantica：开源的本体自动化构建平台](../../../Semantica：开源的本体自动化构建平台.md)
- [集成设计与端到端数据流](integration-design.md)
- [技术选型文档](technology-selection.md)
- [端到端测试示例](end-to-end-examples.md)
- [语义层测试设计草案](module-test-specification.md)（行为场景 + 示例输入输出）
- [relation-detector 子模块设计](../relation-detector/README.md)
- [设计文档索引](../00-design-index.md)
