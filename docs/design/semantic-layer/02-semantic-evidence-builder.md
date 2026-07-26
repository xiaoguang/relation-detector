# Semantic Evidence Builder

## 1. 职责

`SemanticEvidenceBuilder` 将 relation-detector 的完整 `ScanBundle` 确定性物化为 `EvidenceGraph`。它负责：

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

输入是已经通过 `ScanResultReader` wire validation 的一个或多个 relation-detector 结果。合并后的
`ScanBundle` 必须满足：

- database identity 的 `type/catalog/schema` 完全一致。
- relationship、lineage、naming、derived 和 warning 数组与 summary 一致。
- fact、evidence 和 candidate ID 内容稳定且无冲突。
- endpoint 保留完整 catalog/schema/table/column 身份。
- input path 使用统一 portable label，不泄漏本机绝对路径。

Builder 只消费 typed records。数组顺序可以影响公开展示顺序，但不能影响 canonical ID。
`ScanBundle`和`EvidenceGraph`冻结外层collection，并对typed fact document、graph payload和diagnostics
执行deep-copy；公开accessor不能回写内部状态。

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
- endpoint 闭合的 `tables`。
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

Formal normalization 的默认 event ID 从已验证的 `eventCandidateRef` 派生，不处理展示前缀。

## 5. Identity 与 Evidence Closure

ID由确定性的语义输入生成，不使用数组ordinal，并在各registry中检查冲突。fact/evidence与
routine、trigger、普通SQL-write event都使用长度分隔的完整typed identity生成内容稳定ID；显示slug
不参与身份。以下标识分别注册并校验：

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
- 文档内 entity/event/metric/dimension 等 typed ref全部可解析。
- 模型不能输出 `BUSINESS_APPROVED`。
- 任一校验失败不返回部分 artifact。
- `SemanticExtractionService`和独立`normalize-extraction`共用owner-aware入口；evidence bundle必须
  携带合法`shardContext`，owned/overlap refs唯一、互斥且存在，每个模型对象必须具有当前shard拥有的grounding。
- normalizer拒绝`BUSINESS_APPROVED`；正式semantic对象缺失`reviewStatus`补
  `SYSTEM_PROPOSED`，review item补`REVIEW_NEEDED`。

## 6. 完整输入与 Typed Sharding

模型上下文由 `SemanticShardPlanner` 控制，但完整 bundle 本身不裁剪。Planner：

1. 仅使用 typed endpoint 和 fact/candidate reference 建立 table-touch graph。
2. 先按 connected component 划分。
3. 对超预算 component按 physical table owner拆分。
4. 对仍超预算的 owner按稳定 root ID分片。
5. 保证每个 root 的 dependency/evidence closure不可拆分。
6. 为每个 fact/candidate指定唯一 owner，overlap只提供只读上下文。

描述、diagnostic 文本和 arbitrary attributes不能建立 component 边。在`SemanticExtractionService`
执行链中，每个模型输出对象必须通过`ownedGroundingRefs`直接引用当前shard owned fact/candidate；
仅引用overlap或evidence不构成所有权。独立normalization命令没有该shard上下文。

Token 门限是带安全余量的确定性估算，不是模型精确 token 数。Shard request和reconciliation request都
使用同一 `maxInputTokens` 门限。超过门限在模型调用前失败，不能截断 closure。

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
- Artifact hash按文件流式计算。

## 9. 验证矩阵

实现必须覆盖：

| 类别 | 必需测试 |
| --- | --- |
| Materialization | direct/derived facts、diagnostics、event candidates完整进入图 |
| Deterministic candidates | 全量候选进入bundle；硬编码metric名称启发式只产生待审核候选；review不得被limit裁剪 |
| Identity | 输入重排ID不变；冲突ID失败 |
| Closure | 缺失fact/evidence/candidate/physical endpoint失败 |
| Event | typed mapping分类；名称、路径、detail不能改变分类 |
| Sharding | component、owner、root closure、overlap只读和唯一owner |
| Budget | shard与reconciliation低于/等于/超过门限 |
| Merge | 同物理实体合并；业务实体按grounding合并或进入review |
| Artifact | streaming写入、staging原子发布、retention、hash和单分片无重复副本 |

当前测试口径见 [Semantic Layer Test Specification](module-test-specification.md)。
