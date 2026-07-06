# Semantic Search 详细设计

> 当前实现状态：目标设计，尚未落地。当前代码没有 Semantic Search、lexicon/embedding 混合召回或 rerank；这些模块依赖后续 Catalog Store、Lexicon Manager 和 Embedding Indexer。

## 1. 目标与定位

**职责：** 结合 lexicon 精确匹配、embedding 语义召回和 evidence-based rerank，从自然语言查询中找到最相关的语义对象。

**LLM 依赖：** 否。搜索阶段不让 LLM 重排事实对象，避免把不存在或无 evidence 的对象排到前面。LLM query rewrite 属于 `Question Understanding`，其输出仍要进入本模块的确定性检索和 rerank。

## 2. 上游与下游

```text
Lexicon Manager
Embedding Indexer
Semantic Catalog Store
  -> Semantic Search
  -> SearchResult
  -> Query Planner
```

## 3. 接口契约

```java
public interface SemanticSearch {
    SearchResult search(SearchQuery query);
    List<SearchHit> searchByType(String query, ObjectType type, int maxResults);
    List<SearchHit> searchMetricsForEntity(String entityId, String query, int maxResults);
    List<String> suggest(String prefix, int maxResults);
}
```

## 4. 搜索流程

<details open>
<summary>中文</summary>

```mermaid
flowchart TD
  A["搜索请求"] --> B["归一化查询词"]
  B --> C["词库精确 / 同义词召回"]
  B --> D["向量语义召回"]
  C --> E["合并候选对象"]
  D --> E
  E --> F["加载置信度 / 审核状态 / 证据"]
  F --> G["可配置证据重排"]
  G --> H["过滤已拒绝和低分结果"]
  H --> I["搜索结果"]
```

</details>

<details>
<summary>English</summary>

```mermaid
flowchart TD
  A["Search Query"] --> B["Normalize query terms"]
  B --> C["Lexicon exact / synonym lookup"]
  B --> D["embedding semantic recall"]
  C --> E["Merge candidates"]
  D --> E
  E --> F["Load confidence / review status / evidence"]
  F --> G["Configurable evidence rerank"]
  G --> H["Filter rejected and low-score results"]
  H --> I["Search Result"]
```

</details>

## 5. Rerank 口径

搜索评分是 **可配置初始 heuristic**，不是已经校准的固定公式。第一版可以从以下信号开始：

- lexicon exact / synonym match。
- embedding similarity。
- semantic object confidence。
- review status：`BUSINESS_APPROVED` > `EVIDENCE_SUPPORTED` > `SYSTEM_PROPOSED`。
- relationship path confidence。
- lineage support。
- recent successful question trace。

权重必须配置化，并通过以下反馈迭代：

- question trace 中用户选择的对象。
- Review Queue 的人工决策。
- 离线 benchmark question set。
- SQL Validator 成功/失败结果。

文档和实现不应硬编码某一组长期权重。示例权重只能作为默认配置，不是语义正确性的证明。

## 6. LLM 决策

本模块不使用 LLM。LLM 可以在上游生成 query rewrite，但最终命中对象必须来自 catalog / lexicon / embedding index，并保留 evidence。

## 7. 测试验收

| 场景 | 预期 |
| --- | --- |
| 精确术语搜索 | lexicon 命中优先 |
| 同义词搜索 | 通过 lexicon synonym 命中 |
| 语义搜索 | embedding 召回候选，但仍经 evidence rerank |
| `BUSINESS_APPROVED` 和 `SYSTEM_PROPOSED` 同分 | `BUSINESS_APPROVED` 优先 |
| `REJECTED` 对象 | 不参与默认结果 |
| embedding API 失败 | 降级为 lexicon-only |
| 无 evidence candidate | 不进入默认结果，或作为低置信度候选返回 |

---

## 附录 A：行为设计与测试建议

Semantic Search 的分数是可配置 heuristic，不是固定公式。测试应验证排序行为和边界，而不是锁死某组小数。

建议覆盖的行为：

- lexicon 精确命中应优先召回对应 semantic object。
- embedding 召回可以补充同义表达，但结果必须经过 evidence rerank。
- `REJECTED` 对象不进入默认结果。
- `BUSINESS_APPROVED` 与 `EVIDENCE_SUPPORTED` 对象在相关性接近时优先级高于 `SYSTEM_PROPOSED`。
- embedding 服务不可用时，可降级为 lexicon-only，并返回 degraded warning。

示例：

```pseudo-json
{
  "query": "客户消费金额",
  "expectedBehavior": [
    "召回 customer 相关实体或字段",
    "召回 payments.amount 或相关指标候选",
    "过滤 REJECTED 对象",
    "返回 scoreBreakdown，但不承诺固定权重"
  ]
}
```
