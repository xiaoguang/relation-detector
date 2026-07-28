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

生产命令通过 `ScanResultReader.open()` 建立 `SemanticInputStore`：Jackson逐条校验并把四类
metadata inventory 与事实写入磁盘section/index，多 input 会拒绝 database identity、scope 或完整
inventory fingerprint 不一致。`ScanBundle` 保留为明确有界调用者及单个component的typed模型。
所有semantic artifact使用同一个portable path renderer：工作目录内路径相对化，外部绝对路径只保留
文件名；它用于防止本机绝对路径泄漏，不是跨仓库的持久source identity。

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

模型不接收也不改写 deterministic KG；KG 与 extraction bundle 来自同一个磁盘后备 evidence store。
分片保留全部 fact/candidate，并由 planner 要求每项只有一个 canonical owner。超预算 table owner
会按稳定 root ID 拆成 `table#part-NNNN`，但每个 root 及其 typed closure 仍不可切分。当前 prompt 和
deterministic backfill 遵守 overlap 只读规则；`SemanticShardOutputOwnershipValidator` 还会在 backfill
前要求每个 model-authored 对象以 `ownedGroundingRefs` 直接引用当前 shard 拥有的 fact/candidate，
仅引用 overlap 或 evidence 不能建立输出所有权。token budget 使用确定性估算而不是模型 tokenizer。
任何原子 closure 超过估算门限、引用不闭合、同 ID 冲突未解决或全局 normalization 失败都不会返回
正式 extraction result。artifact writer先写唯一staging目录，并在当前模式的交付物完整后原子发布：
codex-session为`AWAITING_MODEL_RESULTS`，request-only为`REQUESTS_READY`，模型完整执行才是`COMPLETE`。
因此存在`run-<runId>`不等于模型抽取完成，消费者必须读取manifest status。失败不发布半成品run。
staging已创建时writer会尽力
写入`FAILED` manifest并保留目录，但二次I/O失败时不能保证该manifest落盘。
真实API运行在同一staging事务内顺序执行：完整bundle和deterministic artifact先落盘；每个归一化
成功的shard及解析成功的reconciliation分别以隐藏临时目录写完后原子改名。后续失败时，前序成功片
继续留在failure staging并在manifest中标为`COMPLETE`，未完成片为`PENDING`；失败不发布`run-*`。
`final-only`不修改完整staging，而是从中构建同级隐藏发布候选，只复制最终保留项和终态manifest。
copy、manifest或publish晚期失败时，完整staging保留并标记`FAILED`；成功发布后才尽力清理staging。
无界 evidence bundle、merged/final result和KG JSON直接写文件；prompt/request因token门限有界可保留
字符串表示。所有分片payload统一位于`shards/shard-NNNN/`，单分片不生成root兼容副本。输入、evidence、
component、KG和shard plan均以磁盘spool/外排索引为后备，内存边界为“最大单条record + 固定外排buffer +
单个component/shard/prompt/response”。relation-detector JSON携带四类metadata inventory及
`NOT_REQUESTED/COMPLETE/PARTIAL/UNAVAILABLE`状态；正式semantic命令只接受`COMPLETE`。未被
relationship、lineage或naming触达的表列仍进入evidence、KG与shard ownership。

`semantic e2e` 是 deterministic 验证入口：同一次读取 scan result 后同时写 `semantic-kg/<case-name>/` 和 `semantic-extraction/<case-name>/` 的 evidence bundle / prompt artifacts，但不调用模型。当前不写 Semantic Catalog Store，不提供 lexicon、embedding、review queue 或在线问答；这些仍是后续阶段。

`semantic normalize-extraction` 强制接收 `--evidence-bundle`。openai-api 与独立 CLI 使用相同的 bundle-aware
normalizer：候选回填后建立统一 reference index，验证每个 evidence/candidate ref、文档内 entity 引用和
governance 状态。`SemanticPhysicalReferenceIndex` 同时要求正式语义对象引用的表列存在于 evidence bundle，
`SemanticOwnerIdRegistry` 保证所有 semantic section 的 owner ID 全局唯一。任一闭包失败都直接拒绝，不输出
部分 artifact。独立命令要求 evidence bundle 携带`COMPLETE` inventory描述和合法`shardContext`，
并与自动执行链共用
`normalizeOwnedShard`：在 backfill 前验证 owned/overlap 集合及每个 model-authored object 的
`ownedGroundingRefs`。它不需要接收完整 shard plan，但调用方必须提供 planner 生成或同契约构造的
owner context；缺失、伪造或越界 context 会被拒绝。

Formal section normalization采用严格typed-ref优先：显式typed ref存在时必须解析成功，只有缺失时
才允许display fallback；event的每个display input/output分别校验。review先规范化`targetSection`，
再要求`targetRef`属于该section；自动review ID只使用section、target和type，不受reason变化影响。

### 当前实现差异矩阵

| ID | 状态 | 当前边界 |
| --- | --- | --- |
| `SEM-WIRE-01` | `MATCHED` | derived path至少包含三个endpoint，`pathLength == path.size()-1`，source/target与path两端一致；必需结构、枚举和嵌套evidence继续严格校验。 |
| `SEM-REF-01` | `MATCHED` | 显式typed ref必须解析成功，缺失时才允许display fallback；event display ref逐项校验，review target按规范化section查询owner。 |
| `SEM-ID-01` | `MATCHED` | bundle typed ingestion 和 formal normalized semantic document 拒绝同 section / 跨 section owner ID 重复；`SemanticGraphAssembler` 拒绝 node 覆盖与冲突 edge。该结论不自动覆盖离线 `SemanticKgBuilder`。 |
| `SEM-KG-01` | `MATCHED` | `SemanticKgBuilder/ReferenceIndex` 要求非 diagnostic fact/event、endpoint node 与 edge 的 evidence 非空且可解析；identity registry 只允许完整内容相同的幂等重复，冲突 node/edge ID 原子失败。 |
| `SEM-EVENT-01` | `MATCHED` | event candidate只消费typed `mappingKind`、`sourceObjectType`与structured provenance，缺失时稳定降级，不读取路径、source前缀、表列名或detail推断结构。routine key/stable ID使用精确对象类型与`sourceObjectIdentity`；PostgreSQL full/live使用输入参数类型签名，compact token-event使用typed声明statement identity。 |
| `SEM-EVENT-ID-01` | `MATCHED` | deterministic event candidate与formal缺省event ID都使用长度分隔的完整identity；formal ID由已验证的完整`eventCandidateRef`生成，不经过display slug。 |
| `SEM-NORMALIZED-ID-01` | `MATCHED` | entity/event/metric/dimension、graph edge和自动review都使用长度分隔canonical identity；review先规范化section，ID使用`targetSection + targetRef + type`且不包含可变reason。 |
| `SEM-CANDIDATE-01` | `MATCHED` | 完整bundle保留全部typed deterministic candidates；名称驱动的`METRIC_SOURCE`与未使用review limit分支已删除，正式metric仍必须由证据闭合的semantic normalization产生。 |
| `SEM-SHARD-PLAN-01` | `MATCHED` | 完整输入的 fact/candidate owner、dependency closure、evidence closure和shard coverage在模型调用前验证；超预算 table owner按稳定root拆片且root closure保持原子。 |
| `SEM-SHARD-OUTPUT-01` | `MATCHED` | shard model output要求owned grounding；reconciliation只接受冲突variant选择和既有对象rename，不能新增对象、relation、fact或evidence。 |
| `SEM-NORMALIZE-OWNER-01` | `MATCHED` | 独立`normalize-extraction`与自动分片共用owner-aware入口；bundle必须携带合法`shardContext`，owned/overlap refs唯一、互斥且存在，输出对象必须由当前片owned fact/candidate直接支撑。 |
| `SEM-SHARD-BUDGET-01` | `MATCHED` | shard与reconciliation的完整prompt都使用带margin的确定性估算，并在模型调用前应用同一`maxInputTokens`；等于门限保留，manifest记录estimated tokens且不宣称exact。 |
| `SEM-SHARD-GRAPH-01` | `MATCHED` | component只读取relationship/naming/lineage/event的typed endpoint字段及candidate typed refs；description、diagnostic与attributes文本不能建边。 |
| `SEM-SHARD-MERGE-01` | `MATCHED` | 物理实体按完整`physicalName`，纯业务实体按规范名称、类型和owned grounding signature确定性合并；同名不同grounding保留并生成review，冲突内容显式失败。 |
| `SEM-SHARD-ARTIFACT-01` | `MATCHED` | staging在任何payload前原子写`IN_PROGRESS`；模式成功后原子替换为`AWAITING_MODEL_RESULTS/REQUESTS_READY/COMPLETE`并发布，普通失败写`FAILED`。若终态写入自身失败则保留最后一个可解析`IN_PROGRESS`，半成品永不发布。 |
| `SEM-SHARD-FAILURE-AUDIT-01` | `MATCHED` | 逐片和reconciliation目录原子提交；final-only从完整staging构建独立发布候选，晚期失败保留全部已完成片材料且不发布半成品。 |
| `SEM-SHARD-CONFIG-01` | `MATCHED` | YAML root/section/unknown field/数值严格校验，相对路径按config目录解析；CLI override后再次构造统一typed config。 |
| `SEM-SHARD-STATE-01` | `MATCHED` | 公开JSON accessor返回副本、集合不可修改；同包流水线使用明确的trusted accessor，provider/writer不能通过公开引用回写已校验状态。 |
| `SEM-COMPLETE-INPUT-01` | `MATCHED` | 正式抽取始终使用完整、闭合bundle，不存在focus或分片前事实数量裁剪；旧CLI参数和YAML字段明确拒绝，typed sharding是唯一上下文规模控制机制。 |
| `SEM-READER-STATE-01` | `MATCHED` | `ScanBundle`/`EvidenceGraph`外层集合不可修改；typed fact document、graph payload与diagnostics在构造和公开accessor边界deep-copy，调用方不能回写内部状态。 |
| `SEM-GOVERNANCE-01` | `MATCHED` | normalizer拒绝模型写入`BUSINESS_APPROVED`；正式semantic对象缺失状态补`SYSTEM_PROPOSED`，review item补`REVIEW_NEEDED`，backfill后不保留空状态。 |
| `SEM-CLI-ERROR-01` | `MATCHED` | CLI使用固定脱敏文案；参数、配置和API key缺失通过usage异常映射为exit 2，wire、sharding、normalization、模型调用和artifact I/O失败映射为runtime exit 1。 |
| `SEM-INGEST-MEMORY-01` | `MATCHED` | 正式build/extract/e2e/normalize链路使用流式reader、section spool、外排identity/component索引与path-backed shard；只物化单条record和受预算约束的单component/shard。96 MiB/128 MiB与512 MiB/1 GiB子JVM门禁覆盖输入、build与request-only路径。 |
| `SEM-CATALOG-INVENTORY-01` | `MATCHED` | relation-detector direct/derived JSON输出同一四类metadata inventory、scope、counts与完整性状态；正式semantic命令只接受COMPLETE，相同多输入要求inventory fingerprint一致，未被关系事实触达的表列仍进入evidence/KG/shard ownership。 |

离线 KG evidence/identity gate、typed event candidate identity、deterministic candidate、typed sharding、
完整输入、统一模型请求预算、strict configuration、reader/graph公开状态和governance默认值已经闭环。
wire path shape、formal semantic引用、自动review identity、reconciliation边界、`final-only`晚期失败
审计及CLI错误分类、磁盘后备reader与完整metadata inventory已经按上述窄契约闭合。详细
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
- LLM只能生成待治理semantic objects、解释、同义词和query rewrite，不能创造数据库事实或写入
  `BUSINESS_APPROVED`。当前normalizer为正式semantic对象缺失状态补
  [SYSTEM_PROPOSED](glossary.md#system_proposed)，为review item补`REVIEW_NEEDED`。
- 指标只有经过治理流程才能成为[BUSINESS_APPROVED](glossary.md#business_approved)正式口径；空状态不能
  被解释为已审核。
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
