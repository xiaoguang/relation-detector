# Semantic Layer 子系统设计索引

本目录包含 Evidence-Grounded Semantic Layer 中除 relation-detector 事实层以外的子系统详细设计。总体边界以 [Evidence-Grounded Semantic Layer 整体设计](../semantic-layer-overall-design.md) 为准。

## 架构概览

### 离线构建链路

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

Catalog Store 是语义资产中心。Lexicon 和 Embedding 从 catalog 并行构建索引，不是彼此的串行下游。

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
SYSTEM_PROPOSED semantic objects / conflicts / low confidence items
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
| 3 | LLM Semantic Enricher | [03-llm-semantic-enricher.md](03-llm-semantic-enricher.md) | 生成业务语义候选、描述、同义词和 review item。 |
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
- LLM 只能生成 SYSTEM_PROPOSED semantic objects、解释、同义词和 query rewrite；不能创造数据库事实。
- 指标默认 `SYSTEM_PROPOSED`，只有审核后才能成为 `BUSINESS_APPROVED` 正式口径。
- `EVIDENCE_SUPPORTED` 表示有 evidence 支撑，但不等于业务已确认。
- SQL draft 必须经过 SQL Validator；文档示例不代表自动执行能力。
- 不确定时优先反问用户，而不是生成看似完整但口径不明的 SQL。
- Prototype 可用 JSON 文件；production-ready Phase 1 profile 推荐 PostgreSQL + JSONB + pgvector。
- Phase 2+ / Future Capability 能力不得写成 Phase 1 Scope 已实现能力。

## 与 relation-detector 的关系

relation-detector 是事实层子系统，负责提取：

- `RelationshipCandidate`
- `DataLineageCandidate`
- `MetadataSnapshot`
- `WarningMessage`

Semantic Layer 在这些事实之上构建业务语义，不修改 relation-detector 的行为或输出。

## 相关文档

- [Evidence-Grounded Semantic Layer 整体设计](../semantic-layer-overall-design.md)
- [Semantic Layer 示例附录](../semantic-layer-examples.md)
- [集成设计与端到端数据流](integration-design.md)
- [技术选型文档](technology-selection.md)
- [端到端测试示例](end-to-end-examples.md)
- [语义层测试设计草案](module-test-specification.md)（行为场景 + 示例输入输出）
- [relation-detector 子模块设计](../relation-detector/README.md)
- [设计文档索引](../00-design-index.md)
