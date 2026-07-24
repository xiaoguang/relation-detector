# LLM Semantic Extraction 详细设计

## 1. 目标与定位

**职责：** 基于 relation-detector scan result 构造可追溯 evidence bundle / prompt，并在需要时调用 LLM 生成业务语义候选：实体、事件、关系解释、字段血缘解释、指标候选、维度候选、三元组和审核项。

当前代码分两条独立链路：

- `semantic build` / `semantic e2e` 的 KG 构建链路直接执行
  `SemanticEvidenceBuilder -> SemanticKgBuilder`，不创建 semantic fact，也不调用 LLM。
- `semantic extract` 的语义抽取链路已经实现：先从同一个 `ScanBundle` 写
  `deterministic-kg/`，再由 `SemanticExtractionBundleBuilder` 构造完整 evidence bundle。
  `SemanticShardPlanner` 按当前 table-touch 连通分量形成 evidence-closed shard；`codex-session`
  只写逐片 prompt / bundle / 协调模板，`openai-api` 默认使用
  `gpt-5.6-sol`、`xhigh` 调用 Responses API。逐片结果先在片内归一化，再确定性合并、
  受限协调，最后针对原始完整 bundle 做一次全局闭包校验。

当前 `semantic extract` 的 normalized result 包含：

- `entities`
- `events`
- `relations`
- `lineage`
- `metrics`
- `dimensions`
- `triplets`
- `reviewItems`
- `semanticGraph`
- `validation`

其中 event 必须引用 deterministic `eventCandidates`；lineage 仍是一等输出，不被 triplet 替代。`semantic extract` 结果目前仍是文件 artifact，不会写入 Semantic Catalog Store。

**硬边界：**

- LLM 不创建正式物理 relationship。
- LLM 不创建正式 Data Lineage。
- LLM 不把任何 metric / entity / join path 直接提升为 `BUSINESS_APPROVED`。
- LLM 输出必须引用 evidence bundle 中已有的 fact/evidence/candidate id；无法闭合的内容会使正式 normalization 失败。写入持久化 warning / review queue 属于后续 catalog/governance 阶段。
- LLM 不接收可改写的正式 KG。确定性 KG 与模型 evidence bundle 是同一 `ScanBundle`
  的并列 artifact；模型只能返回语义候选或受限 reconciliation patch。

LLM 在本模块中负责语言理解和表达，不负责数据库事实判断。数据库事实来自 relation-detector 输出的 relationship、lineage、metadata、SQL source 和注释。

## 1.1 Semantica 启发：LLM 不是 accountability layer

Semantica 官方 README 将 Semantica 定位为 LLM 旁边的 Context and Accountability Layer，而不是让 LLM 自己承担事实、治理和审计。本模块沿用同一边界：

- LLM 可以把已有 evidence 翻译成业务可读说明。
- LLM 可以归纳业务域、实体候选、指标候选、同义词候选和冲突解释。
- LLM 输出必须引用 evidenceRefs；无法引用 evidence 的内容会使当前 formal normalization 原子失败，不会产生带 validation issue 的正式 artifact。后续 catalog/governance 阶段可以在独立的候选摄取流程中转为 warning 或 review item。
- LLM 不能确认 conflict，不能合并重复对象，不能写入 `BUSINESS_APPROVED`，不能绕过 SQL Validator。

因此 LLM Enricher 的输出是 semantic candidates，不是 catalog truth。Catalog Store 和 Review Queue 负责持久化、状态保护和治理决策。

四类角色示例：

| 角色 | 输入 evidence | LLM 可以输出什么 | 边界 |
| --- | --- | --- | --- |
| 解释 | `orders.customer_id -> customers.id`，字段注释为 "下单客户" | "`orders.customer_id` 表示订单所属客户，可用于连接客户主表。" | 只能解释已有 relationship，不能新增 join。 |
| 归纳 | `customers`、`orders`、`payments` 多个表和 join path | "这些表共同支持客户交易域，`customers` 是客户主体，`orders` 是订单事实，`payments` 是支付事实。" | 归纳的是业务视角，不改变物理表关系。 |
| 扩展 | 字段名 `customer_id`，注释 "客户编号"，已有术语 "客户" | 同义词候选："用户"、"会员"、"买家"。 | 只能进入词库候选和审核队列，不能直接成为正式业务口径。 |
| 规划 | 问题："每个客户最近30天支付金额是多少？"；catalog 中有 `customers/orders/payments` | 问题改写、候选指标、候选表字段、需要的 join path 提示。 | 只生成 question plan 候选；SQL 由模板生成并由 Validator 校验。 |

## 2. 上游与下游

```text
Semantic Evidence Builder
  -> EvidenceGraph
  -> SemanticKgBuilder
  -> semantic-kg.json / semantic-evidence-graph.json
```

KG 构建链路不调用 LLM，也不会修改 evidence graph。

```text
ScanBundle
  -> deterministic-kg/ (规则构建，不交给模型改写)
  -> SemanticExtractionBundleBuilder (完整 bundle)
  -> SemanticShardPlanner (连通分量 / evidence closure / 唯一 owner)
  -> SemanticExtractionPromptBuilder (逐片)
  -> semantic extract
       -> codex-session: 写 shards/* prompt / bundle / session 与 reconciliation template
       -> openai-api: 顺序调用 Responses API，片内 normalization 后 merge/reconcile
  -> full-bundle normalization
       -> semantic-extraction-result.json / run-manifest.json
  -> semantic normalize-extraction
       -> 对已有 JSON 输出生成 normalized semantic document
```

真实 LLM 只存在于 `openai-api` provider。`codex-session` 是开发/人工测试入口，不会自动调用模型；用户或 Codex 会话可以读取 prompt 后生成 JSON，再通过 `normalize-extraction` 标准化。

输出对象默认状态：

| 对象 | 默认状态 | 说明 |
| --- | --- | --- |
| SemanticTable | `EVIDENCE_SUPPORTED` | 可由 metadata / DDL / relationship 支撑，但不是人工确认业务口径。 |
| SemanticColumn | `EVIDENCE_SUPPORTED` | 可由字段名、注释、metadata、lineage 支撑。 |
| SemanticEntity | `SYSTEM_PROPOSED` | 业务实体抽象需要审核或后续治理确认。 |
| SemanticMetric | `SYSTEM_PROPOSED` | 指标口径必须审核后才能作为正式回答口径。 |
| JoinPath Explanation | `EVIDENCE_SUPPORTED` | 只能解释已存在 relationship path，不能新增 path。 |

## 3. 接口契约

KG 构建链路没有 enrichment 扩展接口。正式 LLM 能力只通过独立 extraction provider 生成候选文档，
不得原地修改 `EvidenceGraph`。语义抽取链路使用以下 API：

```java
public final class SemanticExtractionBundleBuilder {
    ObjectNode build(ScanBundle bundle, String focus, int maxRelationships, int maxLineage, int maxNamingEvidence);
}

public final class SemanticExtractionService {
    SemanticExtractionRunPlan plan(ObjectNode fullBundle, SemanticShardingOptions options);
    SemanticExtractionRunResult execute(
        SemanticExtractionRunPlan plan,
        SemanticModelClient shardClient,
        SemanticModelClient reconciliationClient);
}

public interface SemanticModelClient {
    SemanticExtractionResult extract(SemanticExtractionPrompt prompt);
    String requestJson(SemanticExtractionPrompt prompt);
}

public final class SemanticExtractionDocumentNormalizer {
    ObjectNode normalize(JsonNode rawDocument, JsonNode evidenceBundle);
}
```

无 evidence bundle 的兼容入口只会 fail-fast，不产生正式语义结果。CLI 的
`normalize-extraction` 同样强制要求 `--evidence-bundle`。

normalizer 的 JSON 只是输入/输出边界：内部先映射为 typed `SemanticExtractionDocument` 及
`SemanticEntity/Event/Metric/Triplet/ReviewItem` DTO，然后依次交给 `SemanticCandidateBackfill`、
`SemanticSectionNormalizer`、`SemanticReviewGenerator`、`SemanticReferenceValidator` 和 graph assembler。
validator state 按每次 normalize 新建，同一 normalizer 实例可被并发复用。

`semantic-extraction-result.json` / normalized semantic document 当前必须满足：

- 每个对象至少保留一个可解析到 evidence bundle registry 的 `EvidenceRef`；缺失或未知引用会使正式归一化失败。
- event/triplet candidate ref 必须存在且类型匹配；不能用任意字符串冒充 deterministic candidate。
- 模型输出中的 `BUSINESS_APPROVED` 被立即拒绝；该状态只能由后续 Review Queue / governance workflow 写入。
- LLM 产生的 join path 字段必须命名为 explanation / candidate，不能命名为正式 physical join path。

闭包分为三层：evidence/fact/candidate id 在 evidence bundle 的统一 reference index 中校验；semantic
section 之间的 entity 引用在本次文档内部解析；`physicalName`、`physicalField`、`sourceFields` 等物理
字符串必须精确存在于 bundle 的表列 registry，不能用有效 evidenceRef 为不存在的 endpoint 背书。

normalized semantic document 的 entity、event、relation、lineage、metric、dimension、triplet 和 review item
共享一个 owner-id registry，同 section 或跨 section 重复均失败。graph assembler 也会拒绝重复 node；相同
edge ID 仅在内容完全一致时幂等去重，内容冲突则失败。

## 4. Prompt 输入约束

发送给 LLM 的 evidence bundle 是可追溯的结构；默认保留完整候选池，只有显式上限或 focus 才产生 preview / compact 视图。当前实现中的 bundle 顶层包括：

```json
{
  "database": {"type": "mysql", "catalog": "sample", "schema": ""},
  "focus": "",
  "inputFiles": ["..."],
  "sources": ["ddl", "object-files", "logs"],
  "tables": ["customers", "orders", "payments"],
  "evidence": [
    {"id": "evidence:<sha256>", "type": "SQL_LOG_JOIN", "source": "queries.sql", "detail": "..."}
  ],
  "relationships": [
    {
      "id": "relationship:<sha256>",
      "source": "orders.customer_id",
      "target": "customers.id",
      "type": "FK_LIKE",
      "confidence": 0.9,
      "evidenceRefs": ["evidence:<sha256>"]
    }
  ],
  "lineage": [
    {
      "id": "lineage:<sha256>",
      "sources": ["payments.amount"],
      "target": "sales_fact.amount",
      "flowKind": "VALUE",
      "transformType": "AGGREGATE"
    }
  ],
  "eventCandidates": [
    {
      "id": "event-candidate:routine:<sha256>",
      "sourceType": "ROUTINE",
      "lineageRefs": ["lineage:..."],
      "supportingDerivedLineageRefs": ["derivedLineage:..."],
      "relationshipRefs": ["relationship:..."]
    }
  ],
  "derivedRelationships": [],
  "derivedLineage": [],
  "namingEvidence": [],
  "diagnostics": [],
  "instructions": {
    "allOutputsMustUseEvidenceRefs": true,
    "llmCannotCreateDatabaseFacts": true
  }
}
```

完整输入身份保留 `database.type/catalog/schema`，`inputFiles` 使用统一 portable path label：工作目录内路径
相对化，外部绝对路径只保留文件名。

无 `focus` 时，bundle 默认覆盖全局完整候选池；`--max-relationships`、`--max-lineage`、`--max-naming`
的默认值是 `0`，表示不限制。只有在 `sharding.mode=off` 时才允许用户显式设置正数上限，
生成有意的 preview / compact prompt view；生产分片不能通过截断减少上下文。
有 `focus` 时只保留相关表和 evidence。所有输出引用 bundle 中内容稳定的 fact、evidence 或 candidate id；
relationship、lineage、naming、diagnostic、evidence、triplet candidate 以及 normalizer 生成的 relation/lineage/
triplet/review id 都不使用数组位置。bundle-aware normalizer 会根据 evidence bundle 补齐遗漏的 event、
triplet 和 review item 候选，并对每个引用做类型化闭包校验。

### 4.1 Evidence-closed 分片

默认配置：

```yaml
model: gpt-5.6-sol
reasoningEffort: xhigh
artifactRetention: full
sharding:
  mode: auto
  targetInputTokens: 240000
  maxInputTokens: 800000
  maxShardCount: 128
  reconcile: true
```

`AUTO` 在完整 prompt 估算不超过目标预算时只产生一个 shard；否则先按当前 table-touch 图的连通分量分组，
再按稳定顺序把多个小型、互不连通的 component 装入同一目标预算，避免“一张孤立小表一次模型调用”。
不能装入目标预算的单个连通分量再按 table owner 拆成 evidence-closed unit。若一个 table owner
加入 ownership context 后仍超过 `maxInputTokens`，planner 按稳定 root ID 将其切成
`table#part-NNNN` 子片；每个 root 重新闭合 typed dependency/evidence，单个 root 及其 closure 是
不可切分的预算原子。这样 table 仍是 canonical ownership 轴，但不会被错误等同于“一张表只能对应
一次模型请求”。`FORCE` 保留逐 component/table/part split 的诊断形态，不做小组件装箱。closure
必须包含被该片 fact/candidate 引用的全部 fact、candidate 和 evidence；`evidenceRefs` 在这里是统一
reference index 引用，不等同于只引用底层 `evidence[]`。

`OFF` 不按 `targetInputTokens` 主动分片，但仍必须满足 `maxInputTokens` 估算门限。门限针对加入
`ownedFactRefs`、`ownedCandidateRefs` 和 `overlapRefs` 后的最终 prompt 估算，而不是加入 ownership
上下文之前的中间 bundle。当前 `SemanticPromptBudgetEstimator` 使用 ASCII 字符数、非 ASCII code point
数、固定开销和 15% margin 做确定性估算；它没有调用模型 tokenizer。因此该门限是 repository estimate
gate，不是 provider 精确 token 数的数学硬上限。API 返回的 actual usage 只用于事后审计。

每个 fact 与 deterministic candidate 恰好有一个 canonical owner。其他 shard 可以携带只读 overlap
上下文，planner 不会重复授予 owner，deterministic candidate backfill 也只补 owned candidates。prompt
要求每个model-authored item使用非空`ownedGroundingRefs`直接引用当前片owned fact/candidate。
`SemanticShardOutputOwnershipValidator`在candidate backfill和formal normalization前校验整个raw输出；
direct candidate/fact ref指向其他owner、只有overlap、或只用`evidenceRefs`提供审计上下文均使整片原子
失败。一个 root 的原子closure超过估算门限、owner缺失、引用不闭合或shard数超限时，planner明确
失败，不截断、不丢事实。`maxShardCount=128` 是默认运行保护，不是能力上限；大型 derived bundle
需要调用方基于预计规模显式提高该值，manifest 仍记录实际 shard 数供审计。

这里的预算只约束模型 request context，不约束 relation-detector JSON 的读取内存。当前
`ScanResultReader` 会先把一个输入完整物化为 `ScanBundle`，随后才构建 deterministic KG 和 shard plan。
因此，无法在配置堆内完成 typed materialization 的超大输入不属于本分片机制已经解决的范围；要支持
这类输入，需要单独设计 streaming / on-disk ingestion，不能通过提高模型分片数来宣称支持。

模型 shard 顺序执行。每片输出先用该片 bundle 执行 formal normalization；全部片成功后按输出中的
canonical identity确定性合并。物理entity使用完整`physicalName`；无物理身份的业务entity使用
`normalizedName + machineType/type + ownedGroundingSignature`。相同identity幂等合并并重写typed
entity refs；同名但grounding不同的业务entity保留不同ID并生成
`POTENTIAL_SEMANTIC_DUPLICATE/REVIEW_NEEDED`。无owned grounding或同一identity结构冲突显式失败，
禁止last-write-wins。
多 shard 且启用 reconciliation 时，模型只接收 compact semantic summary、conflict variants 和 owner
信息，并只可返回：

- 已知 conflict 的 variant 选择；
- 已有对象的展示名称/说明调整；
- 使用已有 entity ID 和完整 bundle reference 的语义关系。

协调器不得创建物理 fact、新 evidence/candidate reference、物理 endpoint 或治理批准状态。
应用 patch 后，最终文档必须再次针对原始完整 bundle 归一化并重建semantic graph/reference closure；
任一 shard、merge、patch 或全局闭包失败都不会返回`SemanticExtractionRunResult`。

`run-manifest.json` 记录完整 bundle hash、每片 owner/估算 token、实际 input/output token、
transport attempts、协调状态、冲突数、最终闭包状态，以及所有 artifact 的 SHA-256 和大小。
`--output`是可复用root；每次运行写`.staging-<runId>`，完整成功后以同文件系统原子rename发布为
`run-<runId>`。失败保留带`FAILED` manifest的staging且不发布final run。manifest直接使用当前配置的
provider/model/reasoning，artifact hash通过流式读取生成。`artifactRetention=full`保留全部请求、响应和
协调payload；`final-only`在模型抽取完整成功后只保留最终结果、deterministic KG、manifest、hash和
pruned清单。`request-only`没有最终语义结果，因此始终保留其请求payload，不执行`final-only`裁剪。
deterministic KG、build-run 和 evidence graph 也直接通过 Jackson generator 流式写入文件，不能先
`writeValueAsString` 物化整个 artifact；后者会使大型 derived KG 同时受到堆大小和 JVM 单字符串长度
上限约束。

### 4.2 当前实现差异矩阵

| ID | 状态 | 已实现边界与剩余缺口 |
| --- | --- | --- |
| `SEM-SHARD-PLAN-01` | `MATCHED` | planner 对完整输入建立唯一 fact/candidate owner map，逐片补齐 dependency/evidence closure；超预算 table owner 按稳定 root 拆成 part，root closure 保持原子，并在模型调用前执行覆盖校验。 |
| `SEM-SHARD-OUTPUT-01` | `MATCHED` | 每个model-authored item通过`ownedGroundingRefs`证明当前片owner；direct ref越界、overlap-only或evidence-only输出在backfill前原子拒绝。 |
| `SEM-SHARD-BUDGET-01` | `MATCHED` | 门限应用于ownership/overlap完整渲染后的prompt保守估算；超过`maxInputTokens`在API前失败，配置、Javadoc和manifest均不把estimate称为exact token。 |
| `SEM-SHARD-GRAPH-01` | `MATCHED` | component只消费typed endpoint和fact/candidate reference字段；description、diagnostic和attributes文本不能误连物理table。 |
| `SEM-SHARD-MERGE-01` | `MATCHED` | 完整physical identity或业务name/type/owned-grounding identity确定性合并并重写refs；同名不同grounding生成review，冲突显式失败。 |
| `SEM-SHARD-ARTIFACT-01` | `MATCHED` | unique staging/run目录、完整成功后的原子rename、FAILED staging、streaming SHA-256和`full/final-only`策略均有独立测试。 |
| `SEM-SHARD-CONFIG-01` | `MATCHED` | YAML shape/unknown field/numeric value严格失败，相对路径按配置目录解析，CLI override后重新构造并校验typed config。 |
| `SEM-SHARD-STATE-01` | `MATCHED` | 构造输入deep-copy、public JSON accessor返回副本、集合不可修改；同包trusted accessor仅用于已校验内部流水线。 |

上述七项均已由typed validation、identity和artifact transaction boundary闭环；没有通过弱化
evidence closure、删除overlap或截断事实规避问题。

独立归一化命令为：

```bash
semantic normalize-extraction \
  --input semantic-extraction-result-raw.json \
  --evidence-bundle semantic-extraction-evidence-bundle.json \
  --output semantic-extraction-result.json
```

## 5. LLM 输出约束

LLM 返回 JSON semantic document，系统再做 deterministic normalization / validation：

```json
{
  "entities": [
    {
      "id": "entity:orders",
      "name": "orders",
      "physicalName": "orders",
      "machineType": "BusinessDataEntity",
      "type": "业务单据实体",
      "evidenceRefs": ["evidence:<sha256>"]
    }
  ],
  "events": [
    {
      "id": "event:sp_rebuild_sales_fact",
      "name": "重建销售事实表",
      "eventCandidateRef": "event-candidate:routine:<sha256>",
      "inputs": ["sales_orders", "payments"],
      "outputs": ["sales_fact"],
      "evidenceRefs": ["evidence:<sha256>"]
    }
  ],
  "relations": [],
  "lineage": [],
  "metrics": [],
  "dimensions": [],
  "triplets": [],
  "reviewItems": []
}
```

`SemanticExtractionDocumentNormalizer` 会补齐内容稳定 id、entity refs、`semanticGraph` 和 `validation`。
正式输出只在当前实现的 ID/内部语义引用闭包满足 `validation.isRefClosed=true` 时返回；未知 evidence、
错误 candidate 类型、孤立 entity、缺失 evidence，或无法解析到本次 semantic document entity 的物理引用
都会抛出 `SemanticExtractionValidationException`。`SemanticPhysicalReferenceIndex` 还会逐项拒绝 bundle 中不存在的
`physicalName`、lineage field、metric field 和 dimension field；有效 evidenceRef 不能替虚构物理 endpoint 背书。

## 6. 输出校验

LLM 输出进入 catalog 前必须校验。当前代码已实现的是 normalized artifact 校验，不写 catalog：

- `physicalName`、`sourceFields` 等字段按 normalized document 内的 entity / section 互引解析；所有 evidence/candidate refs 同时在 bundle reference index 中验证。
- `evidenceRefs` 必须非空且解析到 bundle 的 evidence、fact 或 deterministic candidate；整个结果原子校验。
- event 必须带 `eventCandidateRef`，不能从 derived-only lineage 单独创造 event。
- derived lineage 只能作为 eventCandidate 上的 `supportingDerivedLineageRefs` 辅助解释。
- normalizer 拒绝模型输出的 `reviewStatus=BUSINESS_APPROVED`，不把越权状态静默降级为另一个状态。
- 无 evidence 的 metric/entity 当前使 formal normalization 失败；不会返回 `validation.missingEvidenceRefs` 部分结果。转成 `NEEDS_MORE_EVIDENCE` 必须由后续 catalog/review 候选流程显式完成。
- join path explanation 只能引用已有 relationship path，不能产生新的 path step。

## 7. 流程图

<details open>
<summary>中文</summary>

```mermaid
flowchart TD
  A["完整证据包"] --> B["确定性 KG（并列 artifact）"]
  A --> C["Evidence-closed shard planner"]
  C --> D["逐片 LLM 与片内 normalization"]
  D --> E["Exact-ID merge"]
  E --> F{"多片且启用协调?"}
  F -- "是" --> G["受限 reconciliation patch"]
  F -- "否" --> H["无冲突 draft"]
  G --> I["完整 bundle 全局 normalization"]
  H --> I
  I --> J["Ref-closed semantic document"]
  I -. "失败" .-> K["原子失败，不输出部分正式结果"]
```

</details>

<details>
<summary>English</summary>

```mermaid
flowchart TD
  A["Complete evidence bundle"] --> B["Deterministic KG sibling artifact"]
  A --> C["Evidence-closed shard planner"]
  C --> D["Per-shard LLM and normalization"]
  D --> E["Exact-ID merge"]
  E --> F{"Multiple shards and reconciliation enabled?"}
  F -- "yes" --> G["Constrained reconciliation patch"]
  F -- "no" --> H["Conflict-free draft"]
  G --> I["Full-bundle global normalization"]
  H --> I
  I --> J["Ref-closed semantic document"]
  I -. "failure" .-> K["Atomic failure; no partial formal result"]
```

</details>

## 8. 测试验收

| 场景 | 预期 |
| --- | --- |
| LLM 返回 `BUSINESS_APPROVED` metric | 正式 normalization 失败；只有治理流程可写该状态 |
| LLM 返回字段但没有对应 semantic entity | 正式 normalization 失败并报告 unresolved reference |
| LLM 新建 entity 并填写 bundle 中不存在的物理表/字段 | 正式 normalization 失败，不输出部分 artifact |
| LLM 在同一或不同 section 复用 semantic object id | 正式 normalization 失败；graph node/edge 也有独立冲突防御 |
| 两个物理图连通分量 | 形成两个确定性 shard；每个 fact/candidate 只有一个 owner |
| shard 引用 bundle 外 evidence/fact/candidate | planning 或片内 normalization 失败 |
| 一个最小 evidence closure 超过配置的估算门限 | 明确失败，不截断事实；另用 provider usage 审计实际 token |
| 模型只使用 overlap ref 创建对象 | `SemanticShardOutputOwnershipValidator` 在 backfill 前原子拒绝，不产生部分结果 |
| 两片返回同 ID 不同内容 | 形成显式 conflict；没有协调或协调不完整时失败 |
| 两片返回不同 ID 的同一 physical entity | 当前不会形成 conflict，记录为 `SEM-SHARD-MERGE-01` |
| HTTP 429/5xx/transport failure | 在配置上限内重试；4xx/JSON/闭包错误不作为 transport 重试 |
| LLM 返回新 join path step | 当前不写入物理 relationship；正式拒绝/审核属于后续 catalog gate |
| evidence 完整的 table/column | 写入 `EVIDENCE_SUPPORTED` |
| 指标候选 | normalized result 保留 `SYSTEM_PROPOSED` / `REVIEW_NEEDED` 等状态；Review Queue 写入尚未实现 |

---

## 附录 A：行为设计与测试建议

本附录保留 LLM Enricher 的测试意图，但不定义已实现接口或固定调用次数。LLM 只能解释、归纳、扩展和规划，不能裁决数据库事实。

建议覆盖的行为：

- LLM 返回无法解析到本次 semantic document entity 的 `physicalRef` 时，normalizer 必须拒绝正式输出并给出 unresolved reference。
- LLM 新建 entity 并引用 bundle 中不存在的物理 endpoint 时，`SemanticPhysicalReferenceIndex` 必须拒绝正式输出；有效 evidenceRef 不能替不存在的表列背书。
- LLM 为多个 semantic object 提供相同 id 时，`SemanticOwnerIdRegistry` 必须在同 section 或跨 section 冲突处拒绝；`SemanticGraphAssembler` 继续作为 node/edge 冲突的第二道防御，不能依赖 map 覆盖或 `putIfAbsent` 选择任一项。
- LLM 返回不存在的 `evidenceFingerprint` 时，bundle reference index 必须拒绝该引用。
- LLM 返回 `BUSINESS_APPROVED` 时，normalizer 必须拒绝，不静默改写模型输出。
- LLM 生成的 metric、entity、synonym 默认是 `SYSTEM_PROPOSED`，只有治理流程可以提升为 `BUSINESS_APPROVED`。
- join path explanation 只能引用已有 relationship path，不能新增 path step。

示例：

```pseudo-json
{
  "llmOutput": {
    "metric": "customer_total_paid_amount",
    "reviewStatus": "BUSINESS_APPROVED",
    "evidenceRefs": ["VALUE:AGGREGATE:payments.amount->paid_amount_30d"]
  },
  "expectedBehavior": {"result": "REJECTED", "reason": "governance-only status"}
}
```
