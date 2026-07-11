# LLM Semantic Enricher 详细设计

## 1. 目标与定位

**职责：** 基于 relation-detector scan result 构造可追溯 evidence bundle / prompt，并在需要时调用 LLM 生成业务语义候选：实体、事件、关系解释、字段血缘解释、指标候选、维度候选、三元组和审核项。

当前代码分两条链路：

- `semantic build` / `semantic e2e` 的 KG 构建链路仍使用 `NoopSemanticEnricher`，它直接返回原始 `EvidenceGraph`，不创建 semantic fact，不调用 LLM。
- `semantic extract` 的语义抽取链路已经实现：`SemanticExtractionBundleBuilder` 构造 evidence bundle（默认全量候选池，显式设置 `--max-*` 或 `focus` 时才裁剪），`SemanticExtractionPromptBuilder` 生成 prompt，`codex-session` provider 只写 prompt / bundle / 会话说明，`openai-api` provider 调用 OpenAI-compatible Responses API，并通过 bundle-aware normalizer 写 normalized semantic document。

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
- LLM 输出必须引用已有 `evidenceRefs`；当前 normalizer 会把无法绑定 evidence 的内容暴露在 `validation.missingEvidenceRefs` / `validation.unresolvedReferences` 中。写入正式 warning / review queue 属于后续 catalog/governance 阶段。

LLM 在本模块中负责语言理解和表达，不负责数据库事实判断。数据库事实来自 relation-detector 输出的 relationship、lineage、metadata、SQL source 和注释。

## 1.1 Semantica 启发：LLM 不是 accountability layer

Semantica 官方 README 将 Semantica 定位为 LLM 旁边的 Context and Accountability Layer，而不是让 LLM 自己承担事实、治理和审计。本模块沿用同一边界：

- LLM 可以把已有 evidence 翻译成业务可读说明。
- LLM 可以归纳业务域、实体候选、指标候选、同义词候选和冲突解释。
- LLM 输出必须引用 evidenceRefs；无法引用 evidence 的内容在当前 normalized artifact 中成为 validation issue，后续 catalog/governance 阶段再转为 warning 或 review item。
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
  -> NoopSemanticEnricher (当前默认)
  -> SemanticKgBuilder
  -> semantic-kg.json / semantic-evidence-graph.json
```

KG 构建链路不调用 LLM，也不会修改 evidence graph。

```text
ScanBundle
  -> SemanticExtractionBundleBuilder
  -> SemanticExtractionPromptBuilder
  -> semantic extract
       -> codex-session: 只写 semantic-extraction-evidence-bundle.json /
                         semantic-extraction-prompt.md /
                         semantic-extraction-codex-session.md
       -> openai-api: 调用 Responses API，写 semantic-extraction-result-raw.json /
                      semantic-extraction-result.json
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

```java
public interface LlmSemanticEnricher {
    EvidenceGraph enrich(EvidenceGraph graph);
}
```

`LlmSemanticEnricher` 仍是 KG 构建链路的扩展点；当前 `semantic build` 使用 `NoopSemanticEnricher`。语义抽取链路使用独立的 extraction API：

```java
public final class SemanticExtractionBundleBuilder {
    ObjectNode build(ScanBundle bundle, String focus, int maxRelationships, int maxLineage, int maxNamingEvidence);
}

public final class SemanticExtractionPromptBuilder {
    SemanticExtractionPrompt build(ScanBundle bundle, String focus, int maxRelationships, int maxLineage, int maxNamingEvidence);
}

public final class OpenAiResponsesSemanticExtractor {
    SemanticExtractionResult extract(SemanticExtractionPrompt prompt);
    String requestJson(SemanticExtractionPrompt prompt);
}

public final class SemanticExtractionDocumentNormalizer {
    ObjectNode normalize(JsonNode rawDocument);
    ObjectNode normalize(JsonNode rawDocument, JsonNode evidenceBundle);
}
```

normalizer 的 JSON 只是输入/输出边界：内部先映射为 typed `SemanticExtractionDocument` 及
`SemanticEntity/Event/Metric/Triplet/ReviewItem` DTO，然后依次交给 `SemanticCandidateBackfill`、
`SemanticSectionNormalizer`、`SemanticReviewGenerator`、`SemanticReferenceValidator` 和 graph assembler。
validator state 按每次 normalize 新建，同一 normalizer 实例可被并发复用。

`semantic-extraction-result.json` / normalized semantic document 必须满足：

- 每个对象至少保留一个 `EvidenceRef`；缺失时当前 normalizer 不会自动降级状态，而是在 `validation.missingEvidenceRefs` 中记录。
- `reviewStatus` 当前由模型输出或 deterministic candidate 保留；只有 Review Queue / governance workflow 才能把对象提升为 `BUSINESS_APPROVED` 是治理规则，当前 normalizer 尚未自动降级模型输出的 `BUSINESS_APPROVED`。
- `BUSINESS_APPROVED` 只能由 Review Queue / governance workflow 写入；在当前 artifact 阶段，这条规则主要由 prompt、后续 catalog gate 和测试约束保证。
- LLM 产生的 join path 字段必须命名为 explanation / candidate，不能命名为正式 physical join path。

## 4. Prompt 输入约束

发送给 LLM 的 evidence bundle 是可追溯的结构；默认保留完整候选池，只有显式上限或 focus 才产生 preview / compact 视图。当前实现中的 bundle 顶层包括：

```json
{
  "database": {"type": "mysql", "schema": "sample"},
  "focus": "",
  "inputFiles": ["..."],
  "sources": ["ddl", "object-files", "logs"],
  "tables": ["customers", "orders", "payments"],
  "relationships": [
    {
      "id": "relationship:orders.customer_id->customers.id:FK_LIKE:0",
      "source": "orders.customer_id",
      "target": "customers.id",
      "type": "FK_LIKE",
      "confidence": 0.9,
      "evidenceRefs": [{"source": "...", "type": "SQL_LOG_JOIN", "detail": "..."}]
    }
  ],
  "lineage": [
    {
      "id": "lineage:payments.amount->sales_fact.amount:VALUE:AGGREGATE:0",
      "sources": ["payments.amount"],
      "target": "sales_fact.amount",
      "flowKind": "VALUE",
      "transformType": "AGGREGATE"
    }
  ],
  "eventCandidates": [
    {
      "id": "event-candidate:routine:sp_rebuild_sales_fact",
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

无 `focus` 时，bundle 默认覆盖全局完整候选池；`--max-relationships`、`--max-lineage`、`--max-naming`
的默认值是 `0`，表示不限制。只有用户显式设置正数上限时，才生成有意的 preview / compact prompt view。
有 `focus` 时只保留相关表和 evidence。目标契约要求所有输出引用 bundle 中稳定 fact/candidate id；event 必须来自
`eventCandidates`，triplet 必须来自 `tripletCandidates`。当前 bundle 的 relationship/lineage/naming `id` 含输入数组 index，且它们的 `evidenceRefs` 是 `{source,type,detail}` snapshot，不是 EvidenceGraph 的 evidence id；只有 event/triplet 等 candidate refs 是字符串 id。因此当前实现满足“同一有序 bundle 内可定位”，尚未满足跨重排、跨 scan 的稳定引用。bundle-aware normalizer（例如 `openai-api`
写出结果时使用的路径）会根据 evidence bundle 补齐遗漏的 event / triplet / review item 候选，避免为压缩牺牲候选完整性。
当前 `semantic normalize-extraction` CLI 只接收 raw result，不接收 evidence bundle，因此它只能做 raw-only normalization，
不能执行候选 backfill 或 bundle id 全量解析。

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
      "evidenceRefs": ["relationship:orders.customer_id->customers.id:FK_LIKE:0"]
    }
  ],
  "events": [
    {
      "id": "event:sp_rebuild_sales_fact",
      "name": "重建销售事实表",
      "eventCandidateRef": "event-candidate:routine:sp_rebuild_sales_fact",
      "inputs": ["sales_orders", "payments"],
      "outputs": ["sales_fact"],
      "evidenceRefs": ["lineage:payments.amount->sales_fact.amount:VALUE:AGGREGATE:0"]
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

`SemanticExtractionDocumentNormalizer` 会补齐 document-local id、entity refs、`semanticGraph` 和 `validation`。`validation.isRefClosed=false` 时，`isolatedEntities`、`unresolvedReferences`、`missingEvidenceRefs` 会说明问题。这里的 `isRefClosed` 只表示 normalized document 内部轻量引用检查通过：当前 validation 检查 evidence 数组非空、event/triplet ref 字段存在和语义对象互引；它不会验证 event/triplet candidate ref 或每个 `evidenceRef` 确实存在于输入 bundle，也不会验证 object-form evidence snapshot。不能把 `isRefClosed=true` 解读为完整 provenance closure。

## 6. 输出校验

LLM 输出进入 catalog 前必须校验。当前代码已实现的是 normalized artifact 校验，不写 catalog：

- `physicalName`、`sourceFields`、`relationship pathId` 等字段当前按 normalized document 内的 entity / section 互引做解析；逐条解析回 evidence bundle fact id 是后续增强项。
- `evidenceRefs` 当前要求非空，并保留给人工/工具回溯；严格 bundle fact/candidate id 解析尚未在 normalizer 中实现。bundle-aware overload 只负责候选 backfill，不会因此自动获得严格 ref validation。
- event 必须带 `eventCandidateRef`，不能从 derived-only lineage 单独创造 event。
- derived lineage 只能作为 eventCandidate 上的 `supportingDerivedLineageRefs` 辅助解释。
- 当前 normalizer 不会自动把 `reviewStatus=BUSINESS_APPROVED` 降级；“LLM 不能写 BUSINESS_APPROVED”由 prompt、后续 catalog/review gate 和测试约束共同保证，自动降级是后续治理增强项。
- 无 evidence 的 metric/entity 当前进入 `validation.missingEvidenceRefs`；自动改写为 `NEEDS_MORE_EVIDENCE` 是后续 catalog/review 阶段增强项。
- join path explanation 只能引用已有 relationship path，不能产生新的 path step。

## 7. 流程图

<details open>
<summary>中文</summary>

```mermaid
flowchart TD
  A["证据包（默认全量候选池）"] --> B["构造 LLM Prompt"]
  B --> C["生成语义对象建议"]
  C --> D["校验物理引用和证据引用"]
  D --> E{"证据是否有效?"}
  E -- "是" --> F["写入 SYSTEM_PROPOSED 语义对象"]
  E -- "否" --> G["生成警告 / 审核项"]
  F --> H["normalized semantic document"]
  G --> I["validation / review item candidates"]
```

</details>

<details>
<summary>English</summary>

```mermaid
flowchart TD
  A["Semantic Extraction Evidence Bundle"] --> B["Build LLM Prompt"]
  B --> C["Generate semantic object proposals"]
  C --> D["Validate physical refs and evidence refs"]
  D --> E{"Valid evidence?"}
  E -- "yes" --> F["Write SYSTEM_PROPOSED semantic objects"]
  E -- "no" --> G["Create warning / review item"]
  F --> H["Normalized semantic document"]
  G --> I["Validation / review item candidates"]
```

</details>

## 8. 测试验收

| 场景 | 预期 |
| --- | --- |
| LLM 返回 `BUSINESS_APPROVED` metric | 当前应由 prompt / review gate 禁止；自动降级仍是后续增强 |
| LLM 返回不存在字段 | 当前在 `validation.unresolvedReferences` / `missingEvidenceRefs` 中暴露；拒绝或降级属于后续 catalog gate |
| LLM 返回新 join path step | 当前不写入物理 relationship；正式拒绝/审核属于后续 catalog gate |
| evidence 完整的 table/column | 写入 `EVIDENCE_SUPPORTED` |
| 指标候选 | normalized result 保留 `SYSTEM_PROPOSED` / `REVIEW_NEEDED` 等状态；Review Queue 写入尚未实现 |

---

## 附录 A：行为设计与测试建议

本附录保留 LLM Enricher 的测试意图，但不定义已实现接口或固定调用次数。LLM 只能解释、归纳、扩展和规划，不能裁决数据库事实。

建议覆盖的行为：

- LLM 返回不存在的 `physicalRef` 时，当前 normalizer 应在 validation 中暴露；拒绝或进入 `NEEDS_MORE_EVIDENCE` 是后续 catalog gate 行为。
- LLM 返回不存在的 `evidenceFingerprint` 时，该引用应被 validation / catalog gate 标为无效；当前 normalizer 尚未做 bundle fact id 全量解析。
- LLM 返回 `BUSINESS_APPROVED` 时，当前 prompt 和治理约束禁止采纳；自动降级为 `SYSTEM_PROPOSED` 是后续增强。
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
  "expectedBehavior": {
    "reviewStatus": "SYSTEM_PROPOSED",
    "warning": "LLM cannot approve business metrics"
  }
}
```
