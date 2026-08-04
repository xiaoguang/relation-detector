# Semantic Layer 子系统设计索引

本目录包含 Evidence-Grounded Semantic Layer 中除 relation-detector 事实层以外的子系统详细设计。总体边界以 [Evidence-Grounded Semantic Layer 整体设计](overall-design.md) 为准；术语口径以 [Semantic Layer 术语表](glossary.md) 为准。

## 架构概览

### 当前已实现链路

```text
Relation Detector JSON
  -> ScanResultReader.open / SemanticInputStore
  -> SemanticEvidenceStore
  -> global event/owner records and bounded root/shard materialization
  -> SemanticKgStore / SemanticKgArtifactWriter
  -> semantic-kg.json / semantic-evidence-graph.json / semantic-build-run.json
```

这条当前链路吸收 Semantica 官方架构中的 `ingest -> raw documents -> parse / normalize -> extract -> conflict / dedup -> KG / provenance / reasoning` 思路，但落地边界更窄：relation-detector scan result 是标准外部 facts/evidence 输入，生产态由磁盘后备 store承载，`ScanBundle`只用于明确有界的typed视图；当前代码已落地到离线 KG JSON 阶段，即 `semantic-layer/semantic-core` 可以把 scan result 构建为 evidence graph 与 `semantic-kg.json`，`semantic-layer/semantic-cli` 提供 `semantic build` 离线入口。EvidenceGraph 中的事件事实类型是 `SemanticEventCandidate`，KG 渲染为 `Event` 节点；它只来自 direct non-control write lineage，derived lineage 仅作 supporting evidence。事件 source/operation 只按 typed provenance 与 mapping kind 分类，缺失时使用 `SQL_WRITE/WRITE/SQL_WRITE_OPERATION` 中性默认值。完整 typed source identity 用于 raw contribution 去重和稳定排序；routine/trigger 最终按对象聚合，普通 SQL write 按 statement/source object 与 target table 聚合，同一 event 汇总多个 mapping kind 和不同证据位置。routine event identity 使用精确 `FUNCTION/PROCEDURE/PACKAGE/PACKAGE_BODY/EVENT` 类型与开放属性 `sourceObjectIdentity`；PostgreSQL full/live 路径使用输入参数类型签名区分 overload，compact token-event 使用 typed kind/name 与声明 statement identity，避免复制完整参数类型 grammar。当前 KG 节点范围是 `PhysicalTable`、`PhysicalColumn`、`RelationshipFact`、`LineageFact`、`NamingEvidenceFact`、`Event`、`Diagnostic`、derived fact 和从 relationship fact materialize 的 `JoinPath`；结构边包括 table-column、fact source/target、event input/output 和 path step。标准KG不再为每个evidence ref物化`SUPPORTED_BY_EVIDENCE`边；fact node与结构边继续携带完整`evidenceRefs`，证据payload由`semantic-evidence-graph.json`唯一持有并通过跨文件closure校验。外部图数据库如需反向证据索引，可在导入阶段按引用物化。所有非 diagnostic fact/event、物理 endpoint node 和结构 edge 必须具有非空可解析 evidence；完全相同 ID/content 可幂等复用，冲突 ID 会使整个 build 原子失败。

生产命令通过 `ScanResultReader.open()` 建立 `SemanticInputStore`：Jackson逐条校验并把四类
metadata inventory 与事实写入磁盘section/index，多 input 会拒绝 database identity、scope 或完整
inventory fingerprint 不一致。随后`SemanticInputWindowStore`只为原始字节受限的运输窗口构造
`ScanBundle`，`SemanticEvidenceStore`在磁盘上全局归并event并建立typed owner plan；root/shard由
path-backed planner直接从全局store物化，`ScanBundle`不承担root/shard身份。
所有semantic artifact使用同一个portable path renderer：工作目录内路径相对化，外部绝对路径只保留
文件名；它用于防止本机绝对路径泄漏，不是跨仓库的持久source identity。

当前还实现了语义抽取 artifact 链路：

```text
Relation Detector JSON
  -> Scan Result Reader
  -> bounded transport windows + SemanticEvidenceWindowProjector
  -> SemanticEvidenceStore + deterministic-kg/
  -> SemanticGlobalOwnerPlanner (全局连通分量 / root closure / 唯一 owner)
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
分片保留全部 fact/candidate，并由全局磁盘planner要求每项只有一个 canonical owner。全部typed
records先建立item/table/dependency/evidence索引，event contribution按完整identity归并，再计算
table component。超预算component按table owner拆分，owner内按稳定root拆分；root的传递closure不可
再切分，超出hard estimate时在模型调用前失败。raw-byte阈值只控制I/O window，不会改变owner plan。
当前 prompt 和
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
component、KG和shard plan主要以磁盘spool/外排索引为后备；run plan仍保留受`maxShardCount`约束的
小型shard descriptor集合。event contribution的标量、聚合值和成员分别落盘，base descriptor门限
通过后才物化一个event并生成association key，association完成后再执行组合门限。disk union-find
使用迭代查根、路径压缩和损坏链检测。standalone raw在`readTree()`前受`max-output-tokens`约束；
evidence envelope通过parser单字符串约束和有界writer累计`max-input-tokens`，选中record继续共用
该预算。workspace由不跟随符号链接的`walkFileTree`逐项清理。默认、发布及extended门禁分别为
1 MiB/96 MiB、128 MiB/96 MiB和1 GiB/512 MiB；它们证明指定输入形状的有界完成或有界拒绝，
不代表吞吐承诺。
relation-detector JSON携带四类metadata inventory及
`NOT_REQUESTED/COMPLETE/PARTIAL/UNAVAILABLE`状态；正式semantic命令只接受`COMPLETE`。未被
relationship、lineage或naming触达的表列仍进入evidence、KG与shard ownership。这里的`COMPLETE`
表示上游collector完成配置scope；semantic consumer另用共享typed closure rules验证constraint/index
成员、FK引用端、cardinality/ordinal和identity唯一性。采集scope缺少被引用对象时正式处理明确失败。
inventory同时携带`basis=LIVE_METADATA/DDL_DECLARATIONS/MERGED`；`NONE`会被拒绝。file-only输入
只有显式`inventoryCoverage=COMPLETE_SCOPE`且全部typed DDL声明无gap时才可使用
`DDL_DECLARATIONS`。这不把DDL提升为live snapshot：parser未暴露的数据类型继续保留`UNKNOWN`。

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
独立命令使用与prompt一致的保守估算：raw result文件大小先做确定性快速拒绝，严格UTF-8 reader在
Jackson解析期间再次逐码点计数，防止路径替换竞态。evidence envelope的任意单字符串先受由
门限导出的parser约束，字段通过有界writer逐码点累计后才解析成`JsonNode`；选中的evidence record
与tables继续共用同一累计预算。输出先写同级临时文件并原子替换，失败不留下部分结果。

Formal section normalization采用严格typed-ref优先：显式typed ref存在时必须解析成功，只有缺失时
才允许display fallback；event的每个display input/output分别校验。review先规范化`targetSection`，
再要求`targetRef`属于该section；自动review ID只使用section、target和type，不受reason变化影响。

### 当前实现差异矩阵

| ID | 状态 | 当前边界 |
| --- | --- | --- |
| `SEM-WIRE-01` | `MATCHED` | derived path至少包含三个endpoint，`pathLength == path.size()-1`，source/target与path两端一致；必需结构、枚举和嵌套evidence继续严格校验。 |
| `SEM-REF-01` | `MATCHED` | 显式typed ref必须解析成功，缺失时才允许display fallback；event display ref逐项校验，review target按规范化section查询owner。 |
| `SEM-ID-01` | `MATCHED` | bundle typed ingestion 和 formal normalized semantic document 拒绝同 section / 跨 section owner ID 重复；`SemanticGraphAssembler` 拒绝 node 覆盖与冲突 edge。该结论不自动覆盖离线磁盘KG链。 |
| `SEM-KG-01` | `MATCHED` | `SemanticKgStore/SemanticReferenceClosureStore` 要求非 diagnostic fact/event、endpoint node 与结构edge的 evidence refs非空且可解析；Evidence Graph唯一保存完整payload，标准KG不复制逐引用`SUPPORTED_BY_EVIDENCE`边。外排record store只允许完整内容相同的幂等重复，冲突node/edge ID原子失败。 |
| `SEM-KG-ARTIFACT-01` | `MATCHED` | KG记录、stable ID和跨文件evidence closure在隐藏staging中完整校验；FULL和DIGEST_ONLY都只在全部文件及digest成功后以目录级atomic move一次发布。晚期失败不产生正式目标，已有目标不会被覆盖。 |
| `SEM-EVENT-01` | `MATCHED` | event candidate只消费typed `mappingKind`、`sourceObjectType`与structured provenance，缺失时稳定降级，不读取路径、source前缀、表列名或detail推断结构。routine key/stable ID使用精确对象类型与`sourceObjectIdentity`；PostgreSQL full/live使用输入参数类型签名，compact token-event使用typed声明statement identity。 |
| `SEM-EVENT-ID-01` | `MATCHED` | deterministic event candidate与formal缺省event ID都使用长度分隔的完整identity；formal ID由已验证的完整`eventCandidateRef`生成，不经过display slug。 |
| `SEM-NORMALIZED-ID-01` | `MATCHED` | 全部formal semantic ID由canonical content派生；模型`id`只作section-scoped临时alias。entity refs先重写，其他section与review随后canonicalize，数组、alias和shard顺序不影响正式ID，冲突内容明确失败。 |
| `SEM-CANDIDATE-01` | `MATCHED` | 完整bundle保留全部typed deterministic candidates；名称驱动的`METRIC_SOURCE`与未使用review limit分支已删除，正式metric仍必须由证据闭合的semantic normalization产生。 |
| `SEM-SHARD-PLAN-01` | `MATCHED` | 完整磁盘store全局归并event并建立typed component、stable-root closure、唯一owner与overlap；raw-byte仅控制I/O window，不影响event、owner、shard或KG。 |
| `SEM-SHARD-OUTPUT-01` | `MATCHED` | shard model output要求owned grounding；reconciliation只接受冲突variant选择和既有对象rename，不能新增对象、relation、fact或evidence。 |
| `SEM-NORMALIZE-OWNER-01` | `MATCHED` | 独立`normalize-extraction`与自动分片共用owner-aware入口；bundle必须携带合法`shardContext`，owned/overlap refs唯一、互斥且存在，输出对象必须由当前片owned fact/candidate直接支撑。 |
| `SEM-SHARD-BUDGET-01` | `MATCHED` | shard的完整owned/overlap prompt与reconciliation的冲突闭包prompt都使用带margin的确定性估算，并在模型调用前应用同一`maxInputTokens`；无关merged summary不进入协调请求。等于门限保留，manifest记录estimated tokens且不宣称exact。 |
| `SEM-CODEX-OUTPUT-BUDGET-01` | `MATCHED` | Codex和direct model-client均以固定path的有界artifact交换request/response/output，writer独立校验大小、hash、JSON、usage和phase门限。OpenAI 2xx envelope在`1 MiB + 32 × maxOutputTokens`上限内流式落盘，错误body不物化或进入错误文本。 |
| `SEM-REQUEST-PACKAGE-01` | `MATCHED` | v1/v2重建均在物化前应用可信index/shard/count/token/path/size/hash/JSON/line/gzip门限，owner与sidecar通过磁盘索引逐记录处理；section/canonical digest及owner coverage全部通过后才原子发布。 |
| `SEM-FINAL-CLOSURE-01` | `MATCHED` | plan的full bundle、owner manifest、shard bundle和sidecar均以path/bytes/sha256绑定，首次render/model前构造不变快照。final write前重新校验evidence、owned grounding、candidate section、完整catalog/schema table/column身份及semantic/review refs闭包。 |
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
| `SEM-INGEST-MEMORY-01` | `MATCHED` | 流式reader、section spool、外排identity/offset/component/event contribution/association索引、全局owner与path-backed shard已实现。event base与association组合分别在列表物化前受预算；raw、envelope和已选evidence共用硬门限；临时树清理内存只随目录深度增长。 |
| `SEM-CATALOG-INVENTORY-01` | `MATCHED` | 四类inventory、scope、counts和状态进入direct/derived JSON；正式命令只接受COMPLETE。共享closure rules验证table/column/constraint/FK及有序typed index members，mixed physical/expression的kind、ordinal和交错顺序可完整表达。 |
| `SEM-DDL-INVENTORY-01` | `MATCHED` | 显式COMPLETE_SCOPE的typed file DDL可生成`COMPLETE/DDL_DECLARATIONS` inventory；typed gap、identity冲突及普通声明解析失败都会登记coverage gap。混合成功/失败为PARTIAL，全失败且无事实为UNAVAILABLE，非DDL查询失败不影响该状态。 |

离线KG的evidence/identity逻辑门禁、typed event candidate identity、deterministic candidate、typed sharding、
完整输入、模型输入请求预算、strict configuration、reader/graph公开状态和governance默认值已经闭环。
wire path shape、formal semantic引用、自动review identity、reconciliation边界、`final-only`晚期失败
审计、CLI错误分类、全局磁盘owner、metadata mixed-index member顺序及bounded-memory结构边界已经
按上述窄契约闭合。独立KG目录发布、Codex外部响应输出预算和DDL混合解析失败完整性均已通过定向负向测试闭合。详细
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
| 1 | Scan Result Reader | [01-scan-result-reader.md](01-scan-result-reader.md) | 流式读取 relation-detector 输出并建立`SemanticInputStore`；仅为有界调用者物化`ScanBundle`。 |
| 2 | Semantic Evidence Builder | [02-semantic-evidence-builder.md](02-semantic-evidence-builder.md) | 将 metadata inventory、direct/derived relationship、lineage、naming、diagnostic 和 typed event candidate 物化为 evidence graph；独立comment extraction与search index仍是后续能力。 |
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

- [Evidence-Grounded Semantic Layer 整体设计](overall-design.md)
- [Semantic Layer 术语表](glossary.md)
- [Semantic Layer 示例附录](examples.md)
- [参考亿问改进分析](yiyiwen-reference-improvement.md)
- Semantica 架构启发已归入[整体设计](overall-design.md)及各子系统文档；不依赖仓库外的本地研究笔记作为设计契约。
- [集成设计与端到端数据流](integration-design.md)
- [技术选型文档](technology-selection.md)
- [端到端测试示例](end-to-end-examples.md)
- [语义层测试设计草案](module-test-specification.md)（行为场景 + 示例输入输出）
- [relation-detector 子模块设计](../relation-detector/README.md)
- [设计文档索引](../00-design-index.md)
