# Embedding Indexer 详细设计

## 1. 目标与定位

**职责：** 为语义对象构造 embedding 文本并写入向量索引，供 Semantic Search 做语义相似度召回。

**LLM 依赖：** 否。但使用 Embedding API（如 text-embedding-3-small）。

**为什么不是 LLM：** Embedding API 和 LLM API 是不同的能力。Embedding 是将文本转为向量，不涉及文本生成。文本构造是模板化的确定性操作。

**为什么不用 LLM 生成 embedding 文本：** 模板化文本构造可以保证一致性（同样的对象总是生成同样的 embedding 文本），LLM 生成会引入变体，导致同一对象的 embedding 在不同 build 之间漂移。

## 2. 上游与下游

```
上游: Semantic Catalog Store
  ↓ 输入: SemanticCatalog.getFullCatalog() → 所有语义对象

[Embedding Indexer]
  ↓ 调用 Embedding API: text-embedding-3-small
  ↓ 持久化: semantic-embeddings.jsonl

下游: Semantic Search
  消费: 所有 embedding 向量（内存加载或文件读取）
  消费: getEmbedding(objectId) → 单个向量
```

## 3. 接口契约

```java
public interface EmbeddingIndexer {
    /**
     * 全量索引。为所有语义对象生成 embedding。
     * 后置条件: semantic-embeddings.jsonl 包含所有对象的 embedding。
     */
    EmbeddingStats indexAll(SemanticCatalog catalog, EmbeddingConfig config);

    /**
     * 增量索引。只处理变化的对象（新增或 updatedAt > lastIndexedAt）。
     */
    EmbeddingStats indexIncremental(SemanticCatalog catalog,
                                     SemanticCatalog previousCatalog,
                                     EmbeddingConfig config);

    Optional<EmbeddingRecord> getEmbedding(String objectId);
    List<EmbeddingRecord> getEmbeddings(List<String> objectIds);
    void deleteEmbedding(String objectId);
    EmbeddingStats getStats();
}
```

## 4. 处理流程图

<details open>
<summary>中文</summary>

```mermaid
flowchart TD
    A[输入: SemanticCatalog] --> B[遍历所有语义对象]
    B --> C[判断对象类型]
    C -- TABLE --> D1[模板: 表名 + 业务名 + 描述 + 域 + 粒度]
    C -- COLUMN --> D2[模板: 列名 + 业务名 + 描述 + 角色 + 同义词]
    C -- ENTITY --> D3[模板: 实体名 + 主表 + 描述]
    C -- METRIC --> D4[模板: 指标名 + 描述 + 表达式 + 粒度]
    C -- JOIN_PATH --> D5[模板: 路径 + 用途 + 步骤]
    D1 --> E[构造 textForEmbedding]
    D2 --> E
    D3 --> E
    D4 --> E
    D5 --> E
    E --> F{文本长度 > 2048?}
    F -- 是 --> G[截断 + 记录 truncated=true]
    F -- 否 --> H[保留完整文本]
    G --> I[加入 batch]
    H --> I
    I --> J{batch 满 2048 条?}
    J -- 是 --> K[调用 Embedding API]
    J -- 否 --> L{还有更多对象?}
    L -- 是 --> B
    L -- 否 --> K
    K --> M{API 调用成功?}
    M -- 否 --> N[重试 3 次]
    N --> M
    M -- 是 --> O[组装 EmbeddingRecord]
    O --> P[持久化 semantic-embeddings.jsonl]
    P --> Q[更新 embedding-metadata.json]
```

</details>

<details>
<summary>English</summary>

```mermaid
flowchart TD
    A[Input: SemanticCatalog] --> B[Iterate all semantic objects]
    B --> C[Check object type]
    C -- TABLE --> D1[template: table name + business name + description + domain + grain]
    C -- COLUMN --> D2[template: column name + business name + description + role + synonyms]
    C -- ENTITY --> D3[template: entity name + primary table + description]
    C -- METRIC --> D4[template: metric name + description + expression + grain]
    C -- JOIN_PATH --> D5[template: path + usage + steps]
    D1 --> E[Build textForEmbedding]
    D2 --> E
    D3 --> E
    D4 --> E
    D5 --> E
    E --> F{text length > 2048?}
    F -- yes --> G[truncate + record truncated=true]
    F -- no --> H[keep full text]
    G --> I[add to batch]
    H --> I
    I --> J{batch full at 2048 items?}
    J -- yes --> K[Call Embedding API]
    J -- no --> L{More objects?}
    L -- yes --> B
    L -- no --> K
    K --> M{API call succeeded?}
    M -- no --> N[Retry 3 times]
    N --> M
    M -- yes --> O[Assemble EmbeddingRecord]
    O --> P[persist semantic-embeddings.jsonl]
    P --> Q[Update embedding-metadata.json]
```

</details>

## 5. 交互时序图

<details open>
<summary>中文</summary>

```mermaid
sequenceDiagram
    participant CS as 语义目录存储
    participant EI as 向量索引器
    participant API as Embedding API
    participant SS as 语义搜索

    CS->>EI: 语义目录
    EI->>EI: 遍历所有语义对象
    EI->>EI: 构造 embedding 文本（模板化）
    loop 每 2048 条一批
        EI->>API: embed(batch_texts)
        alt API 失败
            API-->>EI: 重试 3 次（指数退避）
        end
        API-->>EI: batch_embeddings[2048][1536]
    end
    EI->>EI: 持久化 semantic-embeddings.jsonl
    SS->>EI: getEmbeddings(allObjectIds)
    EI-->>SS: 所有向量记录
```

</details>

<details>
<summary>English</summary>

```mermaid
sequenceDiagram
    participant CS as Semantic Catalog Store
    participant EI as Embedding Indexer
    participant API as Embedding API
    participant SS as Semantic Search

    CS->>EI: Semantic Catalog
    EI->>EI: Iterate all semantic objects
    EI->>EI: build embedding text from templates
    loop batch every 2048 items
        EI->>API: embed(batch_texts)
        alt API failed
            API-->>EI: Retry 3 times with exponential backoff
        end
        API-->>EI: batch_embeddings[2048][1536]
    end
    EI->>EI: persist semantic-embeddings.jsonl
    SS->>EI: getEmbeddings(allObjectIds)
    EI-->>SS: all embedding records
```

</details>

## 6. Embedding 文本构造模板

```
SemanticTable:
  [表名] {physicalName}
  [业务名] {semanticNames.join(", ")}
  [描述] {description}
  [域] {domain}
  [粒度] {grain}

SemanticColumn:
  [列名] {physicalName}
  [业务名] {semanticNames.join(", ")}
  [描述] {description}
  [角色] {businessRole}
  [同义词] {synonyms.join(", ")}

SemanticEntity:
  [实体] {names.join(", ")}
  [主表] {primaryTable}
  [描述] {description}

SemanticMetric:
  [指标] {names.join(", ")}
  [描述] {description}
  [表达式] {expression}
  [粒度] {defaultGrain.join(", ")}
```

**为什么是模板而不是 LLM：** 模板保证同一对象在不同 build 之间生成完全一致的 embedding 文本。如果用 LLM 生成，每次可能生成不同的文本，导致 embedding 向量漂移，同一对象的搜索排名不稳定。

## 7. Embedding 质量自测（P2 新增）

在 Embedding Indexer 完成后，运行自测验证 embedding 质量：

```java
@Test
void embeddingQualitySelfTest() {
    Map<String, String> expectedMappings = Map.of(
        "客户", "entity:Customer",
        "消费金额", "metric:customer_total_paid_amount",
        "订单", "entity:Order",
        "支付金额", "column:payments.amount",
        "商品", "entity:Product"
    );

    int passed = 0, failed = 0;
    List<String> failures = new ArrayList<>();

    for (var entry : expectedMappings.entrySet()) {
        SearchResult result = semanticSearch.search(entry.getKey());
        if (result.hits().isEmpty()) {
            failures.add("查询 '" + entry.getKey() + "' 无结果，期望 " + entry.getValue());
            failed++;
        } else {
            String topHit = result.hits().get(0).objectId();
            if (topHit.equals(entry.getValue())) {
                passed++;
            } else {
                failures.add("查询 '" + entry.getKey() + "' 召回 " + topHit + "，期望 " + entry.getValue());
                failed++;
            }
        }
    }

    double passRate = (double) passed / (passed + failed);
    assertTrue(passRate >= 0.80,
        "Embedding quality too low: " + String.format("%.1f%%", passRate * 100)
        + "\nFailures:\n" + String.join("\n", failures));
}
```

**自测通过标准：** 至少 80% 的已知查询 top 1 召回正确对象。低于 80% 时检查 embedding 模板、lexicon 覆盖或模型选择。

## 5. LLM 决策

**不使用 LLM。** 使用 Embedding API（text-embedding-3-small），这是 ML 推理而非 LLM 文本生成。文本构造是模板化规则。

## 6. 测试验收

| 测试场景 | 预期 |
| --- | --- |
| 全量索引 | 所有对象都有 embedding 记录 |
| 增量索引 | 只对变化对象重新生成 embedding |
| 维度一致 | 所有 embedding 维度 = config.dimensions |
| 文本模板一致性 | 同一对象多次生成相同的 textForEmbedding |
| API 失败重试 | 重试 3 次，仍失败跳过该 batch |
