# LLM Semantic Enricher 详细设计

## 1. 目标与定位

**职责：** 基于 `Semantic Evidence Builder` 产出的 evidence bundle，生成业务语义候选：表/字段中文名、描述、同义词、实体候选、指标候选、冲突说明和 join path 解释。

当前代码阶段只落地了扩展点，不调用真实 LLM：

- `LlmSemanticEnricher` 当前接口是 `EvidenceGraph enrich(EvidenceGraph graph)`。
- 默认实现 `NoopSemanticEnricher` 直接返回原始 `EvidenceGraph`，不创建任何 semantic fact。
- `semantic-cli` 当前固定使用 `NoopSemanticEnricher`，然后把 evidence graph materialize 为 `semantic-kg.json`。
- `CompactEvidenceBundle`、`EnrichmentResult`、prompt 模板、review item 生成和真实 LLM 调用仍是后续实现范围。

**硬边界：**

- LLM 不创建正式物理 relationship。
- LLM 不创建正式 Data Lineage。
- LLM 不把任何 metric / entity / join path 直接提升为 `BUSINESS_APPROVED`。
- LLM 输出必须引用已有 `evidenceRefs`，无法绑定 evidence 的内容只能进入 warning 或 review queue。

LLM 在本模块中负责语言理解和表达，不负责数据库事实判断。数据库事实来自 relation-detector 输出的 relationship、lineage、metadata、SQL source 和注释。

## 1.1 Semantica 启发：LLM 不是 accountability layer

Semantica 官方 README 将 Semantica 定位为 LLM 旁边的 Context and Accountability Layer，而不是让 LLM 自己承担事实、治理和审计。本模块沿用同一边界：

- LLM 可以把已有 evidence 翻译成业务可读说明。
- LLM 可以归纳业务域、实体候选、指标候选、同义词候选和冲突解释。
- LLM 输出必须引用 evidenceRefs；无法引用 evidence 的内容只能成为 warning 或 review item。
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

目标设计中的真实 LLM Enricher 后续会插入在 EvidenceGraph / CompactEvidenceBundle 与 Catalog Store 之间，但当前不会修改 evidence graph，也不会产生 catalog truth。

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

下面关于 `EnrichmentResult`、prompt 输入和输出校验的内容是后续真实 LLM Enricher 的目标设计，不是当前已实现 API。

`EnrichmentResult` 必须满足：

- 每个对象至少保留一个 `EvidenceRef`，除非该对象被标为 `NEEDS_MORE_EVIDENCE`。
- `reviewStatus` 只能由本模块设为 `SYSTEM_PROPOSED`、`EVIDENCE_SUPPORTED` 或 `NEEDS_MORE_EVIDENCE`。
- `BUSINESS_APPROVED` 只能由 Review Queue / governance workflow 写入。
- LLM 产生的 join path 字段必须命名为 explanation / candidate，不能命名为正式 physical join path。

## 4. Prompt 输入约束

发送给 LLM 的 evidence bundle 应是紧凑、可追溯的结构：

```json
{
  "task": "generate_semantic_candidates",
  "language": "zh",
  "evidence": {
    "fields": [
      {
        "physicalRef": "orders.customer_id",
        "dataType": "bigint",
        "topEvidences": [
          {
            "type": "SQL_LOG_JOIN",
            "confidence": 0.55,
            "detail": "JOIN customers ON o.customer_id = c.id"
          }
        ],
        "relatedTable": "customers",
        "relatedColumn": "id"
      }
    ],
    "expressions": [
      {
        "expressionId": "expr:paid_amount_30d",
        "expression": "SUM(payments.amount)",
        "sourceColumns": ["payments.amount"],
        "transformType": "AGGREGATE",
        "confidence": 0.8
      }
    ],
    "relationshipPaths": [
      {
        "pathId": "path:customers-orders-payments",
        "steps": [
          "orders.customer_id -> customers.id",
          "payments.order_id -> orders.id"
        ],
        "pathConfidence": 0.686
      }
    ]
  }
}
```

## 5. LLM 输出约束

LLM 返回 JSON semantic object proposal，系统再做 deterministic validation：

```json
{
  "tables": [
    {
      "physicalName": "orders",
      "semanticNames": ["订单", "交易订单"],
      "description": "记录客户订单主数据。",
      "reviewStatus": "EVIDENCE_SUPPORTED",
      "evidenceRefs": [
        {
          "evidenceFingerprint": "DDL:orders:schema.sql:1:10",
          "evidenceType": "DDL_TABLE"
        }
      ]
    }
  ],
  "metrics": [
    {
      "names": ["客户总支付金额", "总消费金额"],
      "description": "客户在指定时间范围内的支付金额合计。",
      "expression": "SUM(payments.amount)",
      "sourceColumns": ["payments.amount"],
      "reviewStatus": "SYSTEM_PROPOSED",
      "evidenceRefs": [
        {
          "evidenceFingerprint": "VALUE:AGGREGATE:payments.amount->paid_amount_30d",
          "evidenceType": "LINEAGE"
        }
      ]
    }
  ],
  "joinPathExplanations": [
    {
      "pathId": "path:customers-orders-payments",
      "usage": "可用于回答客户订单和客户支付金额问题。",
      "reviewStatus": "EVIDENCE_SUPPORTED"
    }
  ],
  "reviewItems": [
    {
      "objectId": "metric:customer_total_paid_amount",
      "reason": "指标过滤条件和退款处理口径需要业务确认。"
    }
  ]
}
```

## 6. 输出校验

LLM 输出进入 catalog 前必须校验：

- `physicalName`、`sourceColumns`、`relationship pathId` 必须存在于 evidence bundle。
- `evidenceRefs` 必须能解析回当前 `scanRunId` 的 evidence。
- `reviewStatus=BUSINESS_APPROVED` 一律降级为 `SYSTEM_PROPOSED` 并记录 warning。
- 无 evidence 的 metric/entity 进入 `NEEDS_MORE_EVIDENCE`，不得参与默认搜索和 SQL draft。
- join path explanation 只能引用已有 relationship path，不能产生新的 path step。

## 7. 流程图

<details open>
<summary>中文</summary>

```mermaid
flowchart TD
  A["紧凑证据包"] --> B["构造 LLM Prompt"]
  B --> C["生成语义对象建议"]
  C --> D["校验物理引用和证据引用"]
  D --> E{"证据是否有效?"}
  E -- "是" --> F["写入 SYSTEM_PROPOSED 语义对象"]
  E -- "否" --> G["生成警告 / 审核项"]
  F --> H["语义目录存储"]
  G --> I["审核队列"]
```

</details>

<details>
<summary>English</summary>

```mermaid
flowchart TD
  A["CompactEvidenceBundle"] --> B["Build LLM Prompt"]
  B --> C["Generate semantic object proposals"]
  C --> D["Validate physical refs and evidence refs"]
  D --> E{"Valid evidence?"}
  E -- "yes" --> F["Write SYSTEM_PROPOSED semantic objects"]
  E -- "no" --> G["Create warning / review item"]
  F --> H["Semantic Catalog Store"]
  G --> I["Review Queue"]
```

</details>

## 8. 测试验收

| 场景 | 预期 |
| --- | --- |
| LLM 返回 `BUSINESS_APPROVED` metric | 被降级为 `SYSTEM_PROPOSED` 并记录 warning |
| LLM 返回不存在字段 | 拒绝该 candidate，进入 `NEEDS_MORE_EVIDENCE` |
| LLM 返回新 join path step | 拒绝 step，只保留解释性 review item |
| evidence 完整的 table/column | 写入 `EVIDENCE_SUPPORTED` |
| 指标候选 | 写入 `SYSTEM_PROPOSED` 并进入 Review Queue |

---

## 附录 A：行为设计与测试建议

本附录保留 LLM Enricher 的测试意图，但不定义已实现接口或固定调用次数。LLM 只能解释、归纳、扩展和规划，不能裁决数据库事实。

建议覆盖的行为：

- LLM 返回不存在的 `physicalRef` 时，该对象被拒绝或进入 `NEEDS_MORE_EVIDENCE`。
- LLM 返回不存在的 `evidenceFingerprint` 时，该引用无效，不能进入正式 catalog。
- LLM 返回 `BUSINESS_APPROVED` 时必须降级为 `SYSTEM_PROPOSED`，并记录 warning 或 review item。
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
