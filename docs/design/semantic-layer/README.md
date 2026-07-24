# Semantic Layer 子系统设计索引

本目录包含 Evidence-Grounded Semantic Layer 中除 relation-detector 事实层以外的子系统详细设计。总体边界以 [Evidence-Grounded Semantic Layer 整体设计](../semantic-layer-overall-design.md) 为准；术语口径以 [Semantic Layer 术语表](glossary.md) 为准。

## 架构概览

### 当前已实现链路

```text
Relation Detector
  -> Scan Result Reader
  -> Semantic Evidence Builder
  -> SemanticKgBuilder
  -> semantic-kg.json / semantic-evidence-graph.json / semantic-build-run.json
```

这条当前链路吸收 Semantica 官方架构中的 `ingest -> raw documents -> parse / normalize -> extract -> conflict / dedup -> KG / provenance / reasoning` 思路，但落地边界更窄：relation-detector scan result / ScanBundle 是本项目的标准 facts/evidence records；当前代码已落地到离线 KG JSON 阶段，即 `semantic-layer/semantic-core` 可以把 scan result 构建为 evidence graph 与 `semantic-kg.json`，`semantic-layer/semantic-cli` 提供 `semantic build` 离线入口。EvidenceGraph 中的事件事实类型是 `SemanticEventCandidate`，KG 渲染为 `Event` 节点；它只来自 direct non-control write lineage，derived lineage 仅作 supporting evidence。事件 source/operation 只按 typed provenance 与 mapping kind 分类，缺失时使用 `SQL_WRITE/WRITE/SQL_WRITE_OPERATION` 中性默认值。完整 typed source identity 用于 raw contribution 去重和稳定排序；routine/trigger 最终按对象聚合，普通 SQL write 按 statement/source object 与 target table 聚合，同一 event 汇总多个 mapping kind 和不同证据位置。routine event identity 使用精确 `FUNCTION/PROCEDURE/PACKAGE/PACKAGE_BODY/EVENT` 类型与开放属性 `sourceObjectIdentity`；PostgreSQL full/live 路径使用输入参数类型签名区分 overload，compact token-event 使用 typed kind/name 与声明 statement identity，避免复制完整参数类型 grammar。当前 KG 节点范围是 `PhysicalTable`、`PhysicalColumn`、`RelationshipFact`、`LineageFact`、`NamingEvidenceFact`、`Event`、`Diagnostic`、derived fact 和从 relationship fact materialize 的 `JoinPath`；边包括 table-column、fact source/target、event input/output、supported-by evidence 和 path step。所有非 diagnostic fact/event、物理 endpoint node 和 edge 必须具有非空可解析 evidence；完全相同 ID/content 可幂等复用，冲突 ID 会使整个 build 原子失败。

`ScanResultReader` / `ScanBundle` 保留完整 `database.type + catalog + schema` 身份；多 input 合并会拒绝
任一 identity 轴不同的输入。所有 semantic artifact 使用同一个 portable path renderer：工作目录内路径
相对化，外部绝对路径只保留文件名；它用于防止本机绝对路径泄漏，不是跨仓库的持久 source identity。

当前还实现了语义抽取 artifact 链路：

```text
Relation Detector JSON
  -> Scan Result Reader
  -> deterministic-kg/ + SemanticExtractionBundleBuilder
  -> SemanticShardPlanner (连通分量 / evidence closure / 唯一 owner)
  -> per-shard SemanticExtractionPromptBuilder
  -> semantic extract
       -> codex-session: 写逐片 prompt / evidence bundle / 协调模板，不调用外部模型
       -> openai-api: 固定 gpt-5.6-sol/xhigh，顺序执行逐片调用、片内 normalization、
                      exact-ID merge 和受限 reconciliation
  -> full-bundle normalization
       -> merged draft / normalized semantic document / run manifest
  -> semantic normalize-extraction
       -> raw result + evidence bundle
       -> 严格验证 evidence/candidate refs，补齐 semanticGraph / validation
```

模型不接收也不改写 deterministic KG；KG 与 extraction bundle 是同一个 `ScanBundle` 的并列输出。
分片保留全部 fact/candidate，并由 planner 要求每项只有一个 canonical owner。超预算 table owner
会按稳定 root ID 拆成 `table#part-NNNN`，但每个 root 及其 typed closure 仍不可切分。当前 prompt 和
deterministic backfill 遵守 overlap 只读规则；`SemanticShardOutputOwnershipValidator` 还会在 backfill
前要求每个 model-authored 对象以 `ownedGroundingRefs` 直接引用当前 shard 拥有的 fact/candidate，
仅引用 overlap 或 evidence 不能建立输出所有权。token budget 使用确定性估算而不是模型 tokenizer。
任何原子 closure 超过估算门限、引用不闭合、同 ID 冲突未解决或全局 normalization 失败都不会返回
正式 extraction result。artifact writer 先写唯一 staging 目录，完整结果、manifest 和 hash 全部成功后
才以同文件系统原子 rename 发布 `run-<runId>`；失败 staging 保留为审计材料，不发布半成品 run。
该分片边界只控制模型上下文；当前 reader 仍会先在内存中完整物化单个 relation-detector JSON。
超大输入的 bounded-memory streaming / on-disk ingestion 尚未实现，不能由模型分片能力代替。

`semantic e2e` 是 deterministic 验证入口：同一次读取 scan result 后同时写 `semantic-kg/<case-name>/` 和 `semantic-extraction/<case-name>/` 的 evidence bundle / prompt artifacts，但不调用模型。当前不写 Semantic Catalog Store，不提供 lexicon、embedding、review queue 或在线问答；这些仍是后续阶段。

`semantic normalize-extraction` 强制接收 `--evidence-bundle`。openai-api 与独立 CLI 使用相同的 bundle-aware
normalizer：候选回填后建立统一 reference index，验证每个 evidence/candidate ref、文档内 entity 引用和
governance 状态。`SemanticPhysicalReferenceIndex` 同时要求正式语义对象引用的表列存在于 evidence bundle，
`SemanticOwnerIdRegistry` 保证所有 semantic section 的 owner ID 全局唯一。任一闭包失败都直接拒绝，不输出
部分 artifact。

### 当前实现差异矩阵

| ID | 状态 | 当前边界 |
| --- | --- | --- |
| `SEM-WIRE-01` | `MATCHED` | reader 校验必需结构、ISO-8601 `generatedAt`、endpoint、confidence、summary/数组计数、relation/lineage/evidence/warning 枚举及嵌套 evidence；非空 derived relationship/lineage 使用 writer 的统一 path contract。relation-detector 的 `includeWarnings=false` 同时清空根/fact warning 数组并把可见 count 置零，因此 writer 生成的 suppressed output 仍满足该契约。 |
| `SEM-REF-01` | `MATCHED` | evidence/fact/candidate ID、文档内 entity 引用和 bundle 物理表列引用均执行精确闭包校验，不降级 catalog/schema。 |
| `SEM-ID-01` | `MATCHED` | bundle typed ingestion 和 formal normalized semantic document 拒绝同 section / 跨 section owner ID 重复；`SemanticGraphAssembler` 拒绝 node 覆盖与冲突 edge。该结论不自动覆盖离线 `SemanticKgBuilder`。 |
| `SEM-KG-01` | `MATCHED` | `SemanticKgBuilder/ReferenceIndex` 要求非 diagnostic fact/event、endpoint node 与 edge 的 evidence 非空且可解析；identity registry 只允许完整内容相同的幂等重复，冲突 node/edge ID 原子失败。 |
| `SEM-EVENT-01` | `MATCHED` | event candidate只消费typed `mappingKind`、`sourceObjectType`与structured provenance，缺失时稳定降级，不读取路径、source前缀、表列名或detail推断结构。routine key/stable ID使用精确对象类型与`sourceObjectIdentity`；PostgreSQL full/live使用输入参数类型签名，compact token-event使用typed声明statement identity。formal normalization的默认event ID从已验证`eventCandidateRef`派生。 |
| `SEM-SHARD-PLAN-01` | `MATCHED` | 完整输入的 fact/candidate owner、dependency closure、evidence closure和shard coverage在模型调用前验证；超预算 table owner按稳定root拆片且root closure保持原子。 |
| `SEM-SHARD-OUTPUT-01` | `MATCHED` | 每个model-authored item必须用`ownedGroundingRefs`直接引用当前片owned fact/candidate；overlap与`evidenceRefs`只提供审计上下文，越界使整片在backfill前原子失败。 |
| `SEM-SHARD-BUDGET-01` | `MATCHED` | `targetInputTokens/maxInputTokens`明确为带margin的确定性估算门限；最终prompt再次估算并在API前fail-fast，manifest只记录estimated tokens，不宣称exact。 |
| `SEM-SHARD-GRAPH-01` | `MATCHED` | component只读取relationship/naming/lineage/event的typed endpoint字段及candidate typed refs；description、diagnostic与attributes文本不能建边。 |
| `SEM-SHARD-MERGE-01` | `MATCHED` | 物理实体按完整`physicalName`，纯业务实体按规范名称、类型和owned grounding signature确定性合并；同名不同grounding保留并生成review，冲突内容显式失败。 |
| `SEM-SHARD-ARTIFACT-01` | `MATCHED` | 可复用output root下使用唯一staging/run目录；完整成功后原子rename，失败保留FAILED staging；artifact使用流式SHA-256，支持`full/final-only`保留策略。 |
| `SEM-SHARD-CONFIG-01` | `MATCHED` | YAML root/section/unknown field/数值严格校验，相对路径按config目录解析；CLI override后再次构造统一typed config。 |
| `SEM-SHARD-STATE-01` | `MATCHED` | 公开JSON accessor返回副本、集合不可修改；同包流水线使用明确的trusted accessor，provider/writer不能通过公开引用回写已校验状态。 |

wire、reference closure、formal normalization ID、离线 KG evidence/identity gate、routine event identity
以及semantic shard的typed planning、owner output、identity merge、估算门限、artifact transaction、
strict configuration和公开状态不可变边界均已闭环。详细
证据见 [LLM Semantic Extraction](03-llm-semantic-enricher.md#42-当前实现差异矩阵)。Catalog Store、
search、planner 等目标能力统一由 [Future Capabilities Roadmap](future-capabilities-roadmap.md) 管理，
不因本矩阵状态变化而归类为当前实现。

### 目标离线构建链路

```text
Relation Detector
  -> Scan Result Reader
  -> Semantic Evidence Builder
  -> Semantic Extraction Provider
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
| 2 | Semantic Evidence Builder | [02-semantic-evidence-builder.md](02-semantic-evidence-builder.md) | 将 direct/derived relationship、lineage、naming、diagnostic 和 typed event candidate 物化为 evidence graph；metadata/comment 索引仍是后续能力。 |
| 3 | LLM Semantic Extraction | [03-llm-semantic-enricher.md](03-llm-semantic-enricher.md) | 构造 evidence-closed shards，支持 codex-session、openai-api、受限协调和 normalized result；确定性 KG 作为并列 artifact，模型不得改写。 |
| 4-13 | Future Capabilities | [future-capabilities-roadmap.md](future-capabilities-roadmap.md) | Catalog、lexicon、embedding、search、question/planner、SQL draft/validation、answer 与 review 的目标、依赖、安全边界和实施门槛。 |

未来在线问答与治理不再维护十份尚未实现的类/API 草图；统一以路线图中的 typed 输入输出、
安全边界和实施验收条件作为进入实现前的设计门槛。

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
- Semantica 架构启发已归入[整体设计](../semantic-layer-overall-design.md)及各子系统文档；不依赖仓库外的本地研究笔记作为设计契约。
- [集成设计与端到端数据流](integration-design.md)
- [技术选型文档](technology-selection.md)
- [端到端测试示例](end-to-end-examples.md)
- [语义层测试设计草案](module-test-specification.md)（行为场景 + 示例输入输出）
- [relation-detector 子模块设计](../relation-detector/README.md)
- [设计文档索引](../00-design-index.md)
