# Semantic Layer 测试契约

## 1. 范围

本文只定义当前已实现链路的测试边界：

```text
relation-detector JSON
  -> ScanResultReader / ScanBundle
  -> EvidenceGraph / deterministic KG
  -> complete extraction bundle
  -> typed sharding
  -> model output normalization / merge
  -> final evidence-closed semantic document
```

Catalog、search、question planning、SQL draft、查询/SQL validation和治理审批 workflow尚未实现，
其验收条件见 [Future Capabilities Roadmap](future-capabilities-roadmap.md)。当前 normalization、
reference closure、graph validation和模型 review candidate 已纳入本文件测试范围。

## 2. Reader 与 Evidence Graph

| 场景 | 期望 |
| --- | --- |
| 完整 database identity | `type/catalog/schema` 全部保留；多输入任一轴不同即失败 |
| relationship/lineage/naming/derived | 全部进入 graph，并保留 fact/evidence references |
| typed write lineage | 生成 deterministic event candidate；CONTROL 和 derived lineage不创建 event |
| 相同内容重排 | fact、candidate、node 和 edge ID集合不变 |
| 相同ID不同内容 | 原子失败，不使用 last-write-wins |
| 不可解析 evidence | 非 diagnostic fact/event、endpoint node或edge闭包失败 |
| reader/graph state | 外层collection不可修改；typed fact `document()`、graph fact payload与diagnostics在构造和公开accessor边界均deep-copy，修改返回`JsonNode`不得改变内部状态 |

## 3. 完整 Extraction Bundle

Bundle测试必须证明：

- 全部 direct/derived relationship、lineage和naming均保留。
- 全部 event、triplet和review candidates均保留。
- `tables`覆盖每个保留 endpoint。
- evidence inventory对所有 fact/candidate references闭合。
- 输入路径使用 portable label。
- 不存在 focus 或分片前数量裁剪。
- 旧 focus/limit CLI参数和YAML字段被明确拒绝。
- 名称驱动的`METRIC_SOURCE`候选必须不存在；deterministic candidate只能来自typed facts/events。
- review candidates必须全部保留；generator不得重新引入limit分支。

不得用“统计数量接近”替代逐引用闭包测试。

## 4. Typed Sharding

| 场景 | 期望 |
| --- | --- |
| typed component | 只由endpoint和fact/candidate refs连接 |
| 文本碰撞 | description、diagnostic和attributes中的同名文本不能连接component |
| unique owner | 每个fact/candidate恰有一个owner |
| overlap | 可重复提供只读上下文，不能触发candidate backfill或建立输出所有权 |
| oversized owner | 按稳定root拆分，单root及其dependency/evidence closure不可截断 |
| token gate | shard和reconciliation prompt低于/等于门限通过，超过门限时模型调用为0 |
| model ownership | 每个model-authored item直接引用当前片owned grounding；越界整片失败 |

## 5. Normalization 与 Merge

| 场景 | 期望 |
| --- | --- |
| 虚构物理表列 | 即使evidenceRef有效也拒绝 |
| 未知evidence/candidate ref | 原子失败，不输出部分document |
| `BUSINESS_APPROVED` | 模型输出直接拒绝 |
| 同物理实体跨片 | 按完整`physicalName`合并为一个ID |
| 同名业务实体、相同grounding | 确定性合并并重写typed refs |
| 同名业务实体、不同grounding | 保留不同ID并生成duplicate review |
| 无owned grounding业务实体 | 拒绝进入正式结果 |
| graph node/edge冲突 | 完全相同可幂等去重，内容冲突失败 |
| standalone normalization ownership | bundle必须携带有效`shardContext`，并复用自动分片的owner校验 |
| missing review status | 正式对象补`SYSTEM_PROPOSED`，review item补`REVIEW_NEEDED`；`BUSINESS_APPROVED`拒绝 |
| event/owner ID collision | deterministic event candidate与formal缺省entity/event/metric/dimension ID都使用长度分隔完整canonical identity；标点碰撞、grounding顺序、显式ID保真、review/edge碰撞及shard parity均由负向/等价测试覆盖 |

## 6. Artifact

测试必须覆盖：

- 每次run使用唯一staging和`run-<runId>`目录。
- codex-session、request-only与模型完整执行分别发布
  `AWAITING_MODEL_RESULTS`、`REQUESTS_READY`、`COMPLETE` manifest状态；不能仅凭`run-*`目录判断模型完成。
- 任何payload前必须已有可解析`IN_PROGRESS`；普通失败写`FAILED`。若终态写入失败则保留
  `IN_PROGRESS`与原异常，且不得误发布final run。
- 单分片payload只存在于`shards/shard-0001/`。
- 多分片reconciliation payload只存在于`reconciliation/`；run根层文件只保存full bundle、
  merged/final result、deterministic KG和manifest。
- 无界JSON使用Jackson直接写文件；prompt/request仅因token门限有界而允许字符串。
- `full`与`final-only` retention、pruned清单、文件大小和SHA-256可复核。
- 两个并发run不会共享目录或状态。

## 7. 架构门禁

`SemanticDocumentationArchitectureTest`要求重要生产类型具有具体中英文设计说明，覆盖职责、输入、
输出、上下游和禁止边界。`SemanticLayerArchitectureTest`还验证：

- semantic-layer不依赖parser或adaptor实现。
- event分类不使用regex、raw SQL、路径或evidence detail。
- model client只负责模型调用。
- 无界semantic artifact不通过完整字符串写盘。

门禁只能证明可机械检查的结构；设计文字是否符合真实调用链仍需代码评审。
