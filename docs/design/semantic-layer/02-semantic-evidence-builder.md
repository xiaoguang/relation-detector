# Semantic Evidence Builder

## 1. 职责

`SemanticEvidenceBuilder` 将一个已验证且有界的 `ScanBundle` 确定性物化为 `EvidenceGraph`。生产
磁盘链路先由`SemanticEvidenceStore`全局归并并外排记录，再逐个root/shard调用该内存builder；它负责：

- 保留 database、source、fact、observation 和 evidence reference。
- 将 relationship、lineage、naming、derived fact 和 diagnostic 转成可审计图元素。
- 将 typed write lineage 转成 `SemanticEventCandidate`。
- 为离线 KG 和 LLM extraction bundle 提供同一批稳定事实标识。

它不负责：

- 解析 SQL 或补造物理 relationship、lineage、naming。
- 根据名称、路径、raw SQL 或 evidence detail 推断语法结构。
- 调用 LLM、确认业务口径或写入 `BUSINESS_APPROVED`。
- 执行 catalog search、BFS path discovery、在线问答或 SQL 生成。

尚未实现的能力统一记录在
[Future Capabilities Roadmap](future-capabilities-roadmap.md)，不在本文件保留伪 API 或伪实现。

## 2. 输入契约

输入是已经通过 `ScanResultReader` wire validation 的relation-detector结果。兼容入口可合并为完整
`ScanBundle`，生产入口则从`SemanticInputStore`逐个物化有界component/root；二者必须满足：

- database identity 的 `type/catalog/schema` 完全一致。
- relationship、lineage、naming、derived 和 warning 数组与 summary 一致。
- fact、evidence 和 candidate ID 内容稳定且无冲突。
- endpoint 保留完整 catalog/schema/table/column 身份。
- input path 使用统一 portable label，不泄漏本机绝对路径。

Builder 只消费 typed records。数组顺序可以影响公开展示顺序，但不能影响 canonical ID。
`ScanBundle`和`EvidenceGraph`冻结外层collection，并对typed fact document、graph payload和diagnostics
执行deep-copy；公开accessor不能回写内部状态。

relation-detector JSON携带scope内table、column、constraint和index inventory及采集状态。
正式semantic链路只接受`COMPLETE`；四类catalog事实连同relationship、lineage、naming及derived
endpoint共同进入`tables` registry。没有被关系事实引用的表列仍可成为grounded evidence/KG节点，
Builder不得按名称或source文本补造inventory。`COMPLETE`不替代consumer侧成员引用校验；内存与磁盘
reader共用typed closure rules，验证constraint source/reference、FK两端、index member的kind、
连续ordinal、column/expression/prefix shape及identity唯一性。`members`是mixed physical/expression
顺序的权威表示，旧字段仅为兼容投影；scope采集成功但缺少引用对象的inventory仍会被正式semantic入口拒绝。

## 3. 输出结构

### 3.1 EvidenceGraph

`EvidenceGraph` 包含：

| 输入 | 图中表示 |
| --- | --- |
| Relationship | relationship fact、source/target endpoint、supporting evidence |
| Data lineage | lineage fact、source/target endpoint、flow/transform、supporting evidence |
| Naming evidence | naming fact、source/target endpoint、rule、raw observations |
| Derived fact | 独立 derived fact 与 canonical path |
| Diagnostic | diagnostic fact 与 source location |
| Typed write lineage | `SemanticEventCandidate`、input/output endpoint 和 fact refs |

`SemanticEvidenceBuilder` 只负责把输入事实及其 evidence refs 物化为图元素，不在这一层重复实现
闭包策略。正式 KG 的非 diagnostic fact/event、物理 endpoint node 和 edge 的非空、可解析
evidence约束由 `SemanticKgBuilder` 和 `ReferenceIndex` 原子校验。完全相同 ID/content 可以幂等
复用；同一 ID 对应不同内容时整个 KG build 原子失败。

### 3.2 Extraction Bundle

`SemanticExtractionBundleBuilder` 从同一个 `ScanBundle` 生成完整 extraction bundle，顶层包含：

- database identity 和 portable input files。
- `COMPLETE` inventory与全部事实endpoint共同闭合得到的`tables`。
- inventory状态、scope、counts和fingerprint，以及四类typed metadata facts。
- 全部 direct/derived relationship、lineage 和 naming。
- evidence inventory。
- deterministic event、triplet 和 review candidates。
- diagnostics 与只读 extraction instructions。

正式 extraction 不提供 focus 或事实数量裁剪。Bundle 必须覆盖全部输入事实，且每个 fact/candidate
引用都能由统一 reference index 解析。

`TripletCandidateBuilder`只从typed relationship、event、lineage和naming生成候选，不按英文列名推断
metric；`ReviewItemCandidateGenerator`一对一保留全部diagnostic review candidates，不含limit分支。
正式metric只能由后续模型解释并通过evidence closure和governance校验后产生。

### 3.3 Deterministic KG

`SemanticKgBuilder` 从 `EvidenceGraph` 构建离线 KG。模型不接收也不修改该 KG。KG closure 与 formal
semantic normalization 是两个独立边界：

- `SemanticKgBuilder/ReferenceIndex` 校验离线 KG 的 node、edge 和 evidence。
- `SemanticExtractionDocumentNormalizer/SemanticReferenceIndex` 校验模型输出与完整 extraction bundle。

`semantic-evidence-graph.json`是完整evidence payload与diagnostics的唯一持有者。KG fact node和结构edge
保留完整`evidenceRefs`，但标准wire不再为每个引用生成`SUPPORTED_BY_EVIDENCE`边；跨文件closure
直接验证引用存在。需要反向“证据支持哪些事实”查询的图数据库可在导入时建立派生索引。

一条链路通过不能替另一条链路背书。

## 4. Typed Event Candidate

Event candidate 只来自 direct、non-control write lineage。Derived lineage只能作为 supporting evidence。

分类输入仅包括：

- typed `mappingKind`
- typed `sourceObjectType`
- `sourceObjectIdentity`
- structured source/object/statement provenance

缺少 typed 分类时使用中性默认值 `SQL_WRITE/WRITE/SQL_WRITE_OPERATION`。不得读取 source 前缀、文件路径、
endpoint 名称或 evidence detail 判断 event structure。

聚合规则：

- routine/trigger 按精确对象 identity 聚合。
- 普通 SQL write 按 source statement/object 与 target table 聚合。
- 一个 event 可汇总多个 mapping kind 和多个 evidence location。
- PostgreSQL full/live routine identity包含输入参数类型。
- compact token-event 使用 typed declaration statement identity，不复制完整参数类型 grammar。

Formal normalization 的默认event ID从已验证的完整`eventCandidateRef`派生，不处理旧`ROUTINE:`
展示前缀，也不压缩为display slug；长度分隔的`StableSemanticId`保留完整identity边界。

## 5. Identity 与 Evidence Closure

ID不使用数组ordinal，并在各registry中检查冲突。fact/evidence、deterministic candidate和formal
normalized owner都使用长度分隔的完整canonical identity生成内容稳定ID。显式输入ID保持不变；
缺省entity ID按物理身份或业务名称/类型/owned grounding生成，event/metric/dimension分别使用其完整
typed owner字段。以下标识分别注册并校验：

- evidence ID
- relationship、lineage、naming、derived fact ID
- event/triplet/review candidate ID
- normalized semantic owner ID
- KG node/edge ID

同一内容在不同输入顺序下必须得到相同 ID。重复 ID 的处理固定为：

- 完整内容相同：幂等去重。
- 类型、endpoint、confidence、evidence 或 attributes不同：显式失败。

Formal model output还必须满足：

- 每个对象至少引用一个可解析 evidence/fact/candidate。
- 物理 table/column 必须存在于 bundle registry。
- 文档内 entity/event/metric/dimension/review 等 typed ref逐项可解析。
- 只有 typed ref 缺失时才允许使用兼容的展示名称回填；已提供但无效的 typed ref 必须失败。
- 模型不能输出 `BUSINESS_APPROVED`。
- 任一校验失败不返回部分 artifact。
- `SemanticExtractionService`和独立`normalize-extraction`共用owner-aware入口；evidence bundle必须
  携带合法`shardContext`，owned/overlap refs唯一、互斥且存在，每个模型对象必须具有当前shard拥有的grounding。
- normalizer拒绝`BUSINESS_APPROVED`；正式semantic对象缺失`reviewStatus`补
  `SYSTEM_PROPOSED`，review item补`REVIEW_NEEDED`。

## 6. 完整输入与 Typed Sharding

模型上下文由 `SemanticShardPlanner` 控制，但完整 bundle 本身不裁剪。内存内planner的目标契约是：

1. 仅使用 typed endpoint 和 fact/candidate reference 建立 table-touch graph。
2. 先按 connected component 划分。
3. 对超预算 component按 physical table owner拆分。
4. 对仍超预算的 owner按稳定 root ID分片。
5. 保证每个 root 的 dependency/evidence closure不可拆分。
6. 为每个 fact/candidate指定唯一 owner，overlap只提供只读上下文。

描述、diagnostic 文本和 arbitrary attributes不能建立 component 边。在`SemanticExtractionService`
执行链中，每个模型输出对象必须通过`ownedGroundingRefs`直接引用当前shard owned fact/candidate；
仅引用overlap或evidence不构成所有权。独立normalization命令同样要求 evidence bundle 携带
planner生成或按同一契约构造的`shardContext`；缺失、伪造或越界context必须失败。

Token 门限是带安全余量的确定性估算，不是模型精确 token 数。Shard request和reconciliation request都
使用同一 `maxInputTokens` 门限。超过门限在模型调用前失败，不能截断 closure。

生产磁盘链路先全局归并typed event contribution并建立`item -> table/dependency/evidence`外排索引，
再计算typed table connected component和唯一owner。raw-byte阈值只控制外排buffer/window，不影响
event、component、owner或shard。超预算component按table owner拆分，owner内按stable root拆分；
root及其传递dependency/evidence closure不可再切分，单root超出hard estimate时在模型调用前失败。
外部校验要求每个正式fact/candidate恰好owned一次，其他相关shard只能把它作为只读overlap。

Evidence store不区分live与file DDL的物理事实权限，但会保留inventory basis。`DDL_DECLARATIONS`
必须来自上游显式COMPLETE_SCOPE和typed catalog事件；它可为没有relationship的声明表列建立catalog、
KG节点与owner，不得补猜未声明对象或把`UNKNOWN`类型改写为具体数据库类型。

同一event的typed direct contributions按标量、计数和operation/input/output/lineage成员分别落盘，
经外排排序去重后先形成只含数量和保守序列化大小的descriptor。base门限通过后才物化一个有界event
并生成association key；association完成后再以event base和引用估算执行组合门限。跨窗口输出顺序、
加权confidence、candidate、triplet和KG fingerprint保持稳定。

详细模型执行、合并与 artifact 事务见
[LLM Semantic Extraction](03-llm-semantic-enricher.md)。

## 7. Artifact 边界

无界 JSON artifact使用 Jackson 直接写文件，不先构造完整字符串：

- full evidence bundle
- shard evidence bundle
- merged draft
- final semantic result
- deterministic KG、evidence graph 和 build run

Prompt 和 transport request受 token 门限约束，可以保留字符串表示。
高扇出fact/candidate的四类审计引用数组在shard prompt中投影为稳定count和SHA-256，精确引用保存在
同一shard的`external-audit-refs.tsv`。owner校验必须同时验证sidecar内容、digest和reference closure；
该投影只缩小模型上下文，不改变完整bundle或最终审计证据。

一次 extraction run 的模型中间材料写在：

```text
shards/shard-NNNN/
reconciliation/
```

逐片 prompt/request/response/raw/normalized payload 只位于 `shards/`；多片协调 payload 位于
`reconciliation/`。run 根层文件只包括完整 bundle、merged/final result、deterministic KG 和
manifest。单 shard不再复制一套 root-level prompt/request/response/raw artifacts。

## 8. 失败与安全边界

- Reader、builder、shard planner、normalizer 和 KG builder均采用全批校验后提交。
- 外部模型响应不能修改 physical facts。
- 不可解析 evidence、跨 owner输出、identity冲突和超预算 closure均显式失败。
- 失败 run不发布 `run-<runId>`。staging创建后、任何payload前先原子写`IN_PROGRESS` manifest；
  普通失败原子替换为`FAILED`。若终态写入本身失败，保留最后一个可解析`IN_PROGRESS`与原异常。
- API模型执行和artifact writer位于同一staging事务。完整bundle与deterministic artifact在首次模型调用
  前写入；每个归一化成功的shard及解析成功的reconciliation分别先写隐藏临时目录，再原子改名为正式
  审计目录。后续失败时保留这些已完成材料，manifest把已完成shard标记为`COMPLETE`、其余为
  `PENDING`并记录已落盘artifact大小和SHA-256；失败run始终不发布。
- 普通失败会保留前序成功片。`final-only`从完整staging构建同级隐藏publish candidate，只复制最终
  保留项和终态manifest；copy、manifest或publish失败时完整staging写为`FAILED`并保留，成功发布后才
  尽力清理原staging。
- Artifact hash按文件流式计算。

## 9. 验证矩阵

实现必须覆盖：

| 类别 | 必需测试 |
| --- | --- |
| Materialization | direct/derived facts、diagnostics、event candidates完整进入图 |
| Deterministic candidates | 全量typed候选进入bundle；名称驱动的`METRIC_SOURCE`不存在；review不得被limit裁剪 |
| Identity | 输入重排ID不变；冲突ID失败 |
| Closure | 缺失fact/evidence/candidate/physical endpoint失败 |
| Event | typed mapping分类；名称、路径、detail不能改变分类 |
| Sharding | component、owner、root closure、overlap只读和唯一owner；不同外排window大小产生相同event、owner map、shard与KG fingerprint |
| Budget | shard与reconciliation低于/等于/超过门限 |
| Merge | 同物理实体合并；业务实体按grounding合并或进入review |
| Artifact | streaming写入、逐片原子审计目录、失败保留、staging原子发布、retention、hash和单分片无重复副本 |

当前测试口径见 [Semantic Layer Test Specification](module-test-specification.md)。
