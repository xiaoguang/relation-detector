# 技术选型文档

> 当前实现边界：本文件同时记录已落地技术和后续目标 profile。当前代码只使用 Java 17、Maven、
> Jackson/JDK HTTP client 生成离线 JSON KG 与 semantic extraction artifacts；没有 PostgreSQL/JSONB/
> pgvector catalog、lexicon/embedding search、在线 planner 或 SQL draft/validator。下文标为
> `Production-ready Phase 1 profile` 和 `Phase 2+` 的内容是选型方向，不是现有运行能力。

## 1. 选型原则

| 原则 | 说明 |
| --- | --- |
| 与 relation-detector 一致 | 优先复用 Java 17、Maven、多模块、parser 和 JSON 输出生态。 |
| Evidence first | LLM 只能解释 evidence，不创造数据库事实。 |
| 分阶段落地 | Prototype 可以用 JSON 文件；production-ready Phase 1 profile 推荐 PostgreSQL + JSONB + pgvector。 |
| 可替换 | LLM、embedding、存储和搜索实现都通过接口隔离。 |
| 结构化校验优先 | SQL 结构校验复用 parser / draft metadata，不用 regex 或关键字 blacklist。 |

## 2. 存储选型

| 方案 | 优点 | 缺点 | 适用场景 |
| --- | --- | --- | --- |
| JSON 文件 | 零依赖、可读、可版本化、便于原型验证 | 无事务、并发差、查询慢 | prototype / dev |
| PostgreSQL + JSONB | 事务、并发、JSON 查询、成熟生态 | 需要数据库运维 | production-ready Phase 1 profile catalog |
| PostgreSQL + JSONB + pgvector | 支持 catalog + vector search 一体化 | 需要 pgvector 扩展 | production-ready Phase 1 profile search |
| SQLite | 零运维 | 向量检索和并发写有限 | 单机替代方案 |
| Elasticsearch | 搜索能力强 | 运维重、对 Phase 1 Scope 过度设计 | 暂不采用 |

推荐路径：

```text
Prototype: JSON files
Production-ready Phase 1 profile: PostgreSQL + JSONB + pgvector
Phase 2+: 按服务边界拆分或引入消息队列
```

核心数据集应与总体设计一致：`semantic_build_run`、`semantic_object`、`semantic_evidence_ref`、`semantic_object_edge`、`semantic_review_decision`、`semantic_lexicon`、`semantic_embedding`、`semantic_question_trace`。

## 3. 模块间通信

当前已实现的离线 artifact 阶段采用同进程 Java interface + 构造函数注入：

- 离线构建：`ScanBundle -> EvidenceGraph -> SemanticKnowledgeGraph -> JSON artifacts`。
- 输出 artifact：`semantic-kg.json`、`semantic-evidence-graph.json`、`semantic-build-run.json`。
- 离线抽取：`ScanBundle -> deterministic KG + complete evidence bundle -> SemanticShardPlanner ->
  per-shard prompt/normalization -> exact-ID merge -> constrained reconciliation -> full-bundle normalization`。
- 输出 artifact：`deterministic-kg/`、`full-evidence-bundle.json`、`shards/*`、可选
  `reconciliation/`、`merged-draft.json`、`semantic-extraction-result.json` 和带 hash/token/attempt
  统计的 `run-manifest.json`。
- 当前 input token budget 是确定性字符估算与 safety margin，不是 model-specific tokenizer。
  artifact writer 在可复用output root中使用唯一staging/run目录、流式hash和原子rename；失败staging
  保留审计，成功run按`full/final-only`策略保留payload。

后续完整 Phase 1 目标链路为：

- 在线问答：`QuestionIntent -> SearchResult -> AnswerPlan -> SqlDraft -> ValidationResult -> Answer`。
- 中间状态可选择写 JSON 文件，用于调试和断点续跑。

HTTP/gRPC、消息队列和独立微服务属于 Phase 2+，只有在多团队、多进程部署或异步任务量明显增长时再引入。

## 4. LLM 与 Embedding

LLM 用于：

- 业务名、描述、同义词候选。
- entity / metric SYSTEM_PROPOSED semantic object。
- query rewrite 和问题意图解析。
- conflict / review item 的人类可读说明。

LLM 不用于：

- 创建正式 physical relationship。
- 创建正式 Data Lineage。
- 接受 metric。
- 生成最终 SQL。
- 审核自己的输出。

模型名称和价格变化很快，文档只保留能力要求，不把具体价格写成设计事实：

| 能力 | 要求 |
| --- | --- |
| LLM | 支持结构化 JSON 输出、中文业务语义、低温度可控生成、可配置模型。 |
| Embedding | 支持中文语义召回、批量索引、可配置向量维度和模型版本。 |

## 5. SQL Validator 解析选型

Phase 1 不使用 regex 提取表、字段、JOIN 条件，也不使用 keyword blacklist 判断危险操作。

推荐方案：

```text
SQL Draft Generator 输出 SqlDraft + structured elements
SQL Validator 优先校验 structured elements
可选调用 relation-detector parser 做 statement kind / syntax sanity check
```

如果 Phase 2+ 支持人工 raw SQL 校验，必须先经过结构化 parser，再做 catalog/evidence 检查。不能用 `contains("DELETE")` 或简单正则近似判断 SQL 结构。

## 6. 语言和构建

| 维度 | 选择 |
| --- | --- |
| 语言 | Java 17 |
| 构建 | Maven |
| JSON | 当前使用 Jackson；relation-detector 继续拥有自己的兼容 writer |
| HTTP client | 当前 OpenAI-compatible extraction 使用 JDK HttpClient；其他客户端仍是后续接口选择 |
| 测试 | JUnit 5 + fixture golden |
| 依赖注入 | 构造函数手动注入；Phase 2+ 可考虑 DI 框架 |

## 7. 总结

| 维度 | 当前离线 prototype / 近期目标 | Production-ready Phase 1 目标 profile | Phase 2+ / Future Capability |
| --- | --- | --- | --- |
| 存储 | JSON 文件 | PostgreSQL + JSONB + pgvector | 服务化/分库/消息队列 |
| 通信 | Java interface | Java interface + job status | HTTP/gRPC |
| 搜索 | 尚未实现；目标为 Lexicon + in-memory vector | Lexicon + pgvector | 在线学习/评测调参 |
| LLM | 已有可配置 OpenAI-compatible extraction；catalog enrichment 尚未实现 | 可配置模型 + promptVersion | 多模型评测/路由 |
| SQL 校验 | 尚未实现；目标为 Draft elements + parser sanity | relation-detector parser integration | 深层语义/成本/安全审计 |
| SQL 生成 | 尚未实现；目标为模板 draft | 模板 draft | 只允许受控润色，不让 LLM 改事实 |
