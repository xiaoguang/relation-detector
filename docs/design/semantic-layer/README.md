# Semantic Layer 子系统设计索引

本目录包含 Evidence-Grounded Semantic Layer 中除 relation-detector 以外的所有子系统详细设计。relation-detector 作为事实层子系统，其设计在 `../relation-detector/` 中。

## 架构概览

### 离线构建链路

```
Relation Detector → Scan Result Reader → Semantic Evidence Builder → LLM Enricher → Catalog Store
                                                                                    ↓
                                                                              Embedding Indexer
                                                                                    ↓
                                                                              Lexicon Manager
```

### 在线问答链路

```
Question → Question Understanding → Semantic Search → Query Planner → SQL Draft Generator
                                                                           ↓
                                                                     SQL Validator
                                                                           ↓
                                                                     Answer Composer → User
```

### 审核链路

```
LLM Enricher / Query Planner → Review Queue → Human Review → Catalog Store (update)
```

## 子系统设计文档

### 离线构建

| 序号 | 子系统 | 文档 | 职责 |
| --- | --- | --- | --- |
| 1 | Scan Result Reader | [01-scan-result-reader.md](01-scan-result-reader.md) | 读取 relation-detector 输出，校验并归一化为 ScanBundle |
| 2 | Semantic Evidence Builder | [02-semantic-evidence-builder.md](02-semantic-evidence-builder.md) | 将关系、血缘、元数据、注释组织成 evidence graph |
| 3 | LLM Semantic Enricher | [03-llm-semantic-enricher.md](03-llm-semantic-enricher.md) | 使用 LLM 将 evidence 转换为业务语义对象 |
| 4 | Semantic Catalog Store | [04-semantic-catalog-store.md](04-semantic-catalog-store.md) | 语义对象持久化存储，支持 CRUD、版本化、增量更新 |
| 5 | Lexicon Manager | [05-lexicon-manager.md](05-lexicon-manager.md) | 管理业务词到语义对象的映射和同义词 |
| 6 | Embedding Indexer | [06-embedding-indexer.md](06-embedding-indexer.md) | 为语义对象生成 embedding 向量索引 |

### 在线问答

| 序号 | 子系统 | 文档 | 职责 |
| --- | --- | --- | --- |
| 7 | Semantic Search | [07-semantic-search.md](07-semantic-search.md) | 结合 lexicon 精确匹配和 embedding 语义召回 |
| 8 | Question Understanding | [08-question-understanding.md](08-question-understanding.md) | 将自然语言问题解析为结构化意图 |
| 9 | Query Planner | [09-query-planner.md](09-query-planner.md) | 将意图映射为 answer plan，选择表、字段、join path |
| 10 | SQL Draft Generator | [10-sql-draft-generator.md](10-sql-draft-generator.md) | 根据 answer plan 生成 SQL 草稿 |
| 11 | SQL Validator | [11-sql-validator.md](11-sql-validator.md) | 校验 SQL 的正确性、安全性和 evidence 合规性 |
| 12 | Answer Composer | [12-answer-composer.md](12-answer-composer.md) | 组装最终用户响应 |

### 治理

| 序号 | 子系统 | 文档 | 职责 |
| --- | --- | --- | --- |
| 13 | Review Queue | [13-review-queue.md](13-review-queue.md) | 管理需要人工审核的语义候选对象 |

## 全局约束

- 每个子系统通过明确的接口（Java interface）与其他子系统交互。
- 所有语义对象必须携带 `evidenceRefs`，可追溯到 relation-detector 原始输出。
- LLM 不创造数据库事实，只能基于 evidence 生成业务语义候选。
- 指标默认 `SUGGESTED` 状态，必须人工审核才能变为 `ACCEPTED`。
- SQL draft 必须经过 SQL Validator 校验，不能直接执行。
- 不确定时优先反问用户，而不是生成看似正确但口径错误的 SQL。
- v1 存储使用 JSON 文件，v2 迁移到 PostgreSQL + JSONB + pgvector。

## 与 relation-detector 的关系

relation-detector 是事实层子系统，负责从 metadata、DDL、SQL 日志、对象定义中提取：

- `RelationshipCandidate`：表关系（FK_LIKE、CO_OCCURRENCE）及 evidence
- `DataLineageCandidate`：字段血缘（VALUE/CONTROL flow）
- `MetadataSnapshot`：表、列、主键、外键、索引、约束
- `WarningMessage`：解析和处理警告

Semantic Layer 在 relation-detector 之上构建业务语义，不修改 relation-detector 的行为或输出。

## 相关文档

- [Evidence-Grounded Semantic Layer 整体设计](../semantic-layer-overall-design.md)
- [Semantic Layer 示例附录](../semantic-layer-examples.md)
- [集成设计与端到端数据流](integration-design.md)（含 LLM 决策、跨模块契约、端到端验收）
- [技术选型文档](technology-selection.md)（存储、数据总线、大模型、框架选型与对比）
- [端到端测试示例](end-to-end-examples.md)（5 个完整场景的输入输出和验收检查点）
- [relation-detector 子模块设计](../relation-detector/README.md)
- [设计文档索引](../00-design-index.md)

## LLM 依赖总览

| 模块 | 使用 LLM？ | 原因 |
| --- | --- | --- |
| Scan Result Reader | 否 | 纯 JSON 解析和校验，确定性规则 |
| Semantic Evidence Builder | 否 | 纯图构建，BFS + 规则提取 |
| **LLM Semantic Enricher** | **是** | 核心 LLM 使用点：从 evidence 推断业务语义 |
| Semantic Catalog Store | 否 | 纯 CRUD 存储 |
| Lexicon Manager | 否 | 规则驱动的文本归一化和索引 |
| Embedding Indexer | 否（用 Embedding API） | 调用 Embedding API，非 LLM 文本生成 |
| Semantic Search | 否 | 纯数学计算：cosine similarity + 加权求和 |
| **Question Understanding** | **是** | 在线链路唯一 LLM 调用：自然语言理解 |
| Query Planner | 否 | 图算法 + 规则匹配 |
| SQL Draft Generator | **绝对不能** | 模板生成，LLM 会编造表名/列名/join 条件 |
| SQL Validator | 否 | 规则校验：查 catalog 的确定性操作 |
| Answer Composer | 否 | 模板化响应组装 |
| Review Queue | 否 | 状态机 + CRUD，人工审核 |