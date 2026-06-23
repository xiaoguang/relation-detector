# Semantic Layer 集成设计与端到端数据流

## 1. 文档目的

本文档定义 Semantic Layer 全系统的：
- **端到端数据流**：从 relation-detector 输出到用户最终响应的完整数据形态变化
- **LLM 依赖决策**：每个模块是否使用大模型，为什么使用或不使用
- **跨模块契约**：模块间传递的精确数据形态
- **端到端验收场景**：从输入到输出的完整测试用例

## 2. LLM 依赖决策

### 2.1 决策原则

| 原则 | 说明 |
| --- | --- |
| 事实不可创造 | LLM 不能创造表、字段、关系。这些必须来自 relation-detector |
| 语义可推断 | LLM 可以从命名、注释、关系中推断业务含义 |
| 确定性操作用规则 | 数据校验、路径选择、SQL 生成必须是确定性的 |
| 非确定性操作用 LLM | 自然语言理解、业务描述生成、同义词扩展需要 LLM |

### 2.2 各模块决策

| 模块 | 使用 LLM？ | 原因 |
| --- | --- | --- |
| **Scan Result Reader** | 否 | 纯数据解析和校验。JSON 解析 + 字段校验是确定性规则操作。LLM 反而会引入错误 |
| **Semantic Evidence Builder** | 否 | 纯图构建。BFS join path 发现、注释提取、冲突检测是确定性算法。LLM 无法可靠地遍历图结构 |
| **LLM Semantic Enricher** | **是** | 核心 LLM 使用点。需要从 evidence 推断业务实体名、生成描述、扩展同义词、识别指标候选。这些是典型的 NLG 任务，规则无法覆盖 |
| **Semantic Catalog Store** | 否 | 纯 CRUD 存储。确定性读写，不需要 AI |
| **Lexicon Manager** | 否 | 规则驱动的文本归一化和索引。列名拆分、注释提取是规则操作。同义词候选来自 LLM Enricher，Manager 只做存储和检索 |
| **Embedding Indexer** | 否（但用 Embedding API） | 调用 Embedding API（如 text-embedding-3-small）生成向量。这不是 LLM 调用，不涉及文本生成。文本构造是模板化的 |
| **Semantic Search** | 否 | 纯数学计算。cosine similarity + 加权求和是确定性公式。不需要 LLM |
| **Question Understanding** | **是** | 核心 LLM 使用点。用户问题是自由形式的自然语言，必须用 LLM 提取实体、指标、时间范围、过滤条件。规则 NER 无法覆盖业务术语的多样性 |
| **Query Planner** | 否（可选辅助） | 核心逻辑是图算法（join path 选择）和规则匹配。歧义消解时可选调用 LLM 做语义判断，但 Phase 1 Scope 用规则即可 |
| **SQL Draft Generator** | **绝对不能** | 这是最关键的 LLM 禁区。SQL 生成必须使用模板，确保只引用 catalog 中存在的表字段。让 LLM 生成 SQL 会编造不存在的列名和 join 条件 |
| **SQL Validator** | 否 | 规则校验。表存在性、列存在性、join evidence 都是查 catalog 的确定性操作。可以复用 relation-detector parser |
| **Answer Composer** | 否 | 模板化响应组装。解释文本来自 catalog 中的描述字段，不需要 LLM 重新生成 |
| **Review Queue** | 否 | 状态机。CRUD 操作，不涉及 AI |

### 2.3 LLM 调用点总结

```
离线链路：
  Evidence Bundle → [LLM Enricher] → Semantic Objects
  调用次数：N 次（按表/实体分批），每批 1 次 LLM 调用

在线链路：
  User Question → [Question Understanding] → QuestionIntent
  调用次数：1 次

总 LLM 调用：离线链路 N 次 + 在线链路 1 次/问题
```

## 3. 端到端数据流

### 3.1 离线构建链路：完整数据形态变化

```
Step 1: relation-detector 输出（文件）
─────────────────────────────────────────
scan-result.json
{
  "database": {"type": "mysql", "schema": "shop"},
  "relationships": [
    {
      "source": {"table": "orders", "column": "customer_id"},
      "target": {"table": "customers", "column": "id"},
      "relationType": "FK_LIKE",
      "relationSubType": "INFERRED_JOIN_FK",
      "confidence": 0.70,
      "evidence": [
        {"type": "SQL_LOG_JOIN", "score": 0.55, "source": "mysql-slow.log", "detail": "line 10: o.customer_id = u.id"}
      ]
    }
  ],
  "dataLineage": [...],
  "warnings": [...]
}

        ↓ [Scan Result Reader: 纯规则，无 LLM]

Step 2: ScanBundle（内存对象）
─────────────────────────────────────────
ScanBundle {
  database: {type: "mysql", schema: "shop"},
  relationships: [
    NormalizedRelationship {
      id: "FK_LIKE:orders.customer_id->customers.id",
      source: Endpoint(table="orders", column="customer_id"),
      target: Endpoint(table="customers", column="id"),
      relationType: FK_LIKE,
      confidence: 0.70,
      evidence: [Evidence(type=SQL_LOG_JOIN, score=0.55, ...)]
    }
  ],
  relationshipIndex: {
    bySourceTable: {"orders": [...]},
    byTargetTable: {"customers": [...]},
    byColumnPair: {("orders.customer_id","customers.id"): [...]}
  },
  metadataIndex: {
    tables: {"orders": {columns: [id, customer_id, status, ...]}},
    primaryKeys: {"orders": ["id"], "customers": ["id"]}
  }
}

        ↓ [Semantic Evidence Builder: 纯算法，无 LLM]
        ↓ [新增: businessRole 确定性推断 + confidence 确定性计算]
        ↓ [新增: 冲突规则初筛 → candidateConflict 列表（尽量提高召回率）]
        ↓ [新增: evidenceFingerprint 统一生成]

Step 3: EvidenceGraph（内存对象）
─────────────────────────────────────────
EvidenceGraph {
  fieldEvidences: {
    "orders.customer_id": {
      physicalRef: "orders.customer_id",
      dataType: "bigint",
      nullable: false,
      evidenceRefs: [
        {type: "RELATIONSHIP", fingerprint: "FK_LIKE:orders.customer_id->customers.id:SQL_LOG_JOIN", confidence: 0.70},
        {type: "DDL_COLUMN", text: "customer_id bigint not null"},
        {type: "SQL_USAGE", text: "JOIN customers c ON o.customer_id = c.id"}
      ]
    }
  },
  joinPathEvidences: [
    {
      pathId: "path:orders->customers",
      fromTable: "orders",
      toTable: "customers",
      steps: [{source: "orders.customer_id", target: "customers.id", confidence: 0.70}],
      pathConfidence: 0.70,
      hopCount: 1
    },
    {
      pathId: "path:customers->orders->payments",
      fromTable: "customers",
      toTable: "payments",
      steps: [
        {source: "orders.customer_id", target: "customers.id", confidence: 0.70},
        {source: "payments.order_id", target: "orders.id", confidence: 0.98}
      ],
      pathConfidence: 0.686,
      hopCount: 2
    }
  ],
  expressionEvidences: {
    "expr:paid_amount_30d": {
      expression: "SUM(payments.amount)",
      sourceColumns: ["payments.amount"],
      flowKind: "VALUE",
      transformType: "AGGREGATE"
    }
  }
}

        ↓ [Compact: 截断到大模型 token 预算]
        ↓ [LLM Semantic Enricher: 调用大模型]
        ↓ [LLM 任务: 业务名/描述/同义词/实体/指标/join path 解释]
        ↓ [新增: LLM 冲突确认（CONFIRMED/FALSE_ALARM）]

Step 4: EnrichmentResult（LLM 输出）
─────────────────────────────────────────
{
  "tables": [
    {
      "id": "table:orders",
      "physicalName": "orders",
      "semanticNames": ["订单", "订单主表", "交易订单"],
      "description": "记录客户订单主数据，包含订单状态、金额和时间信息",
      "domain": "交易",
      "grain": "一行表示一个订单",
      "primaryKey": ["orders.id"],
      "evidenceRefs": ["DDL:orders", "REL:orders.customer_id->customers.id"],
      "reviewStatus": "BUSINESS_APPROVED",
      "confidence": 0.95
    }
  ],
  "columns": [
    {
      "id": "column:orders.customer_id",
      "physicalName": "orders.customer_id",
      "semanticNames": ["客户ID", "下单客户"],
      "description": "订单所属客户的唯一标识",
      "businessRole": "foreign_key",
      "entityRef": "entity:Customer",
      "synonyms": ["客户", "买家", "用户"],
      "evidenceRefs": ["REL:orders.customer_id->customers.id", "DDL_COLUMN:orders.customer_id"],
      "reviewStatus": "BUSINESS_APPROVED",
      "confidence": 0.95
    }
  ],
  "entities": [
    {
      "id": "entity:Customer",
      "names": ["客户", "用户", "会员", "买家"],
      "primaryTable": "customers",
      "keyColumns": ["customers.id"],
      "relatedTables": ["orders", "payments"],
      "description": "系统中的客户主体，可以进行下单和支付",
      "evidenceRefs": ["REL:orders.customer_id->customers.id", "REL:payments.customer_id->customers.id"]
    }
  ],
  "metrics": [
    {
      "id": "metric:customer_total_paid_amount",
      "names": ["客户总支付金额", "总消费金额"],
      "expression": "SUM(payments.amount)",
      "sourceColumns": ["payments.amount"],
      "defaultGrain": ["customers.id"],
      "defaultTimeColumn": "payments.paid_at",
      "joinPaths": ["joinpath:customers-orders-payments"],
      "reviewStatus": "SYSTEM_PROPOSED",
      "confidence": 0.80
    }
  ],
  "reviewItems": [
    {
      "reviewId": "review-001",
      "objectId": "metric:customer_total_paid_amount",
      "objectType": "METRIC",
      "status": "SYSTEM_PROPOSED",
      "priority": "MEDIUM",
      "recommendation": "新指标候选，需要确认支付金额口径"
    }
  ]
}

        ↓ [Semantic Catalog Store: 纯存储，无 LLM]
        ↓ [Embedding Indexer: Embedding API，非 LLM]
        ↓ [Lexicon Manager: 规则提取，无 LLM]

Step 5: Catalog 就绪
─────────────────────────────────────────
semantic-catalog/
  semantic-objects.json       # 所有语义对象
  semantic-lexicon.json       # 词库（术语到对象映射）
  semantic-embeddings.jsonl  # 向量索引
  semantic-review-queue.json  # 审核队列
```

### 3.2 在线问答链路：完整数据形态变化

```
Step 1: 用户输入
─────────────────────────────────────────
"每个客户最近30天的支付金额是多少？"

        ↓ [Question Understanding: 调用 LLM]

Step 2: QuestionIntent（LLM 输出）
─────────────────────────────────────────
{
  "originalQuestion": "每个客户最近30天的支付金额是多少？",
  "normalizedQuestion": "每个客户最近30天的支付金额是多少？",
  "language": "zh",
  "entities": [
    {"mention": "客户", "startChar": 2, "endChar": 4, "candidateEntityId": null, "confidence": 0.9}
  ],
  "metrics": [
    {"mention": "支付金额", "startChar": 10, "endChar": 14, "candidateMetricId": null, "aggregationHint": "SUM", "confidence": 0.85}
  ],
  "dimensions": [
    {"mention": "每个客户", "startChar": 0, "endChar": 4, "candidateColumnId": null, "dimensionType": "entity_key", "confidence": 0.9}
  ],
  "timeRange": {
    "mention": "最近30天",
    "type": "RELATIVE",
    "startExpression": "CURRENT_DATE - INTERVAL '30 days'",
    "endExpression": "CURRENT_DATE",
    "confidence": 0.95
  },
  "filters": [],
  "outputIntent": "AGGREGATE_RANK",
  "ambiguities": [],
  "overallConfidence": 0.88
}

        ↓ [Semantic Search: 纯算法，无 LLM]
        ↓ [消歧：entity "客户" → SemanticSearch → entity:Customer]

Step 3: 消歧后的 QuestionIntent
─────────────────────────────────────────
entities[0].candidateEntityId = "entity:Customer"  (score: 0.92)
metrics[0].candidateMetricId = "metric:customer_total_paid_amount"  (score: 0.88)

        ↓ [Query Planner: 纯算法，无 LLM]

Step 4: AnswerPlan
─────────────────────────────────────────
{
  "planId": "plan-20260623-001",
  "question": "每个客户最近30天的支付金额是多少？",
  "answerable": true,
  "primaryEntity": {
    "entityId": "entity:Customer",
    "entityName": "客户",
    "primaryTable": "customers",
    "keyColumns": ["customers.id"],
    "confidence": 0.92
  },
  "metrics": [
    {
      "metricId": "metric:customer_total_paid_amount",
      "metricName": "客户总支付金额",
      "expression": "SUM(payments.amount)",
      "sourceColumns": ["payments.amount"],
      "aggregationFunction": "SUM",
      "reviewStatus": "SYSTEM_PROPOSED",
      "confidence": 0.80
    }
  ],
  "dimensions": [
    {"columnId": "column:customers.id", "physicalName": "customers.id", "semanticName": "客户ID"},
    {"columnId": "column:customers.name", "physicalName": "customers.name", "semanticName": "客户名称"}
  ],
  "joinPath": {
    "pathId": "joinpath:customers-orders-payments",
    "steps": [
      {"source": "orders.customer_id", "target": "customers.id", "confidence": 0.70},
      {"source": "payments.order_id", "target": "orders.id", "confidence": 0.98}
    ],
    "pathConfidence": 0.686,
    "hopCount": 2
  },
  "timeRange": {
    "columnRef": "payments.paid_at",
    "startExpression": "CURRENT_DATE - INTERVAL '30 days'",
    "endExpression": "CURRENT_DATE",
    "confidence": 0.95
  },
  "needsAggregation": true,
  "groupByColumns": ["customers.id", "customers.name"],
  "planConfidence": 0.82
}

        ↓ [SQL Draft Generator: 模板生成，绝对不用 LLM]

Step 5: SqlDraft
─────────────────────────────────────────
{
  "draftId": "draft-20260623-001",
  "sql": "SELECT c.id, c.name, SUM(p.amount) AS paid_amount_30d\nFROM customers c\nJOIN orders o ON o.customer_id = c.id\nJOIN payments p ON p.order_id = o.id\nWHERE p.paid_at >= CURRENT_DATE - INTERVAL '30 days'\nGROUP BY c.id, c.name\nORDER BY paid_amount_30d DESC\nLIMIT 1000",
  "dialect": "mysql",
  "elements": [
    {"type": "TABLE_REF", "sqlFragment": "customers", "sourceObjectId": "table:customers", "reviewStatus": "BUSINESS_APPROVED"},
    {"type": "TABLE_REF", "sqlFragment": "orders", "sourceObjectId": "table:orders", "reviewStatus": "BUSINESS_APPROVED"},
    {"type": "TABLE_REF", "sqlFragment": "payments", "sourceObjectId": "table:payments", "reviewStatus": "BUSINESS_APPROVED"},
    {"type": "JOIN_CLAUSE", "sqlFragment": "JOIN orders o ON o.customer_id = c.id", "sourceObjectId": "REL:orders.customer_id->customers.id", "evidenceRef": "SQL_LOG_JOIN", "confidence": 0.70},
    {"type": "JOIN_CLAUSE", "sqlFragment": "JOIN payments p ON p.order_id = o.id", "sourceObjectId": "REL:payments.order_id->orders.id", "evidenceRef": "DDL_FOREIGN_KEY", "confidence": 0.98},
    {"type": "METRIC_EXPRESSION", "sqlFragment": "SUM(p.amount)", "sourceObjectId": "metric:customer_total_paid_amount", "reviewStatus": "SYSTEM_PROPOSED"}
  ],
  "warnings": [
    {"type": "SYSTEM_PROPOSED_METRIC_USED", "message": "指标 '客户总支付金额' 尚未审核"}
  ]
}

        ↓ [SQL Validator: 规则校验，无 LLM]

Step 6: ValidationResult
─────────────────────────────────────────
{
  "sqlDraft": "SELECT c.id, c.name, SUM(p.amount) AS paid_amount_30d\n...",
  "status": "PASSED_WITH_WARNINGS",
  "checks": {
    "tableExists": {"customers": true, "orders": true, "payments": true},
    "columnExists": {"customers.id": true, "customers.name": true, "payments.amount": true, "payments.paid_at": true},
    "joinEvidence": {
      "orders.customer_id->customers.id": {"evidence": "SQL_LOG_JOIN", "confidence": 0.70},
      "payments.order_id->orders.id": {"evidence": "DDL_FOREIGN_KEY", "confidence": 0.98}
    },
    "aggregationValid": true,
    "groupByComplete": true,
    "dangerousOperation": false
  },
  "errors": [],
  "warnings": [
    {"type": "SYSTEM_PROPOSED_METRIC_USED", "message": "指标 metric:customer_total_paid_amount 审核状态为 SYSTEM_PROPOSED"}
  ]
}

        ↓ [Answer Composer: 模板组装，无 LLM]

Step 7: Answer（最终输出）
─────────────────────────────────────────
{
  "answerId": "answer-20260623-001",
  "question": "每个客户最近30天的支付金额是多少？",
  "type": "SQL_DRAFT",
  "answerable": true,
  "sqlDraft": {...},
  "validation": {...},
  "explanation": {
    "summary": "查询每个客户在最近30天内的支付金额合计",
    "tablesUsed": [
      {"tableName": "customers", "semanticName": "客户", "role": "主实体", "description": "记录客户信息"},
      {"tableName": "orders", "semanticName": "订单", "role": "关联表", "description": "通过 orders.customer_id 关联客户"},
      {"tableName": "payments", "semanticName": "支付", "role": "指标来源", "description": "通过 payments.order_id 关联订单"}
    ],
    "metricsUsed": [
      {"name": "客户总支付金额", "expression": "SUM(payments.amount)", "reviewStatus": "SYSTEM_PROPOSED", "evidence": "SQL projection: paid_amount_30d"}
    ],
    "uncertainties": [
      {"item": "支付金额口径", "reason": "指标 metric:customer_total_paid_amount 尚未审核", "impact": "金额统计口径可能需要确认", "suggestion": "请确认支付金额是否包含退款金额"}
    ]
  }
}
```

## 4. 跨模块契约

### 4.1 契约定义

每个模块的输入和输出都是明确的 Java record 或 JSON schema。模块间通过以下接口契约连接：

```
[relation-detector]
    ↓ 输出: scan-result.json (JSON 文件)
[ScanResultReader]
    ↓ 输出: ScanBundle (内存对象)
[SemanticEvidenceBuilder]
    ↓ 输出: EvidenceGraph (内存对象) + CompactEvidenceBundle (给 LLM)
[LLMSemanticEnricher]
    ↓ 输出: EnrichmentResult (内存对象)
[SemanticCatalogStore]
    ↓ 输出: SemanticCatalog (持久化对象)
[EmbeddingIndexer]
    ↓ 输出: EmbeddingRecord 列表 (JSONL 文件)
[LexiconManager]
    ↓ 输出: LexiconEntry 列表 (JSON 文件)

--- 在线链路 ---

[QuestionUnderstanding]
    ↓ 输入: String (用户问题)
    ↓ 输出: QuestionIntent (内存对象)
[SemanticSearch]
    ↓ 输入: String (查询词) + QuestionIntent (消歧)
    ↓ 输出: SearchResult (候选对象列表)
[QueryPlanner]
    ↓ 输入: QuestionIntent + SearchResult
    ↓ 输出: AnswerPlan
[SqlDraftGenerator]
    ↓ 输入: AnswerPlan
    ↓ 输出: SqlDraft
[SqlValidator]
    ↓ 输入: SqlDraft
    ↓ 输出: ValidationResult
[AnswerComposer]
    ↓ 输入: AnswerPlan + SqlDraft + ValidationResult
    ↓ 输出: Answer (JSON + 人类可读文本)
```

### 4.2 契约不变性

| 契约 | 不可变规则 |
| --- | --- |
| 所有语义对象必须带 evidenceRefs | 如果 LLM Enricher 输出的对象缺少 evidenceRefs，Catalog Store 必须拒绝 |
| 物理名必须来自 catalog | SQL Generator 只能引用 catalog 中的表名和列名 |
| 指标默认 SYSTEM_PROPOSED | LLM Enricher 生成的指标 reviewStatus 必须为 SYSTEM_PROPOSED |
| SQL 必须校验 | Answer Composer 不能输出未经 Validator 校验的 SQL |

## 5. 端到端验收场景

### 5.1 场景一：简单聚合查询（Happy Path）

**输入：** "每个客户最近30天的支付金额是多少？"

**预期全链路输出：**

1. QuestionUnderstanding → QuestionIntent（entities=[客户], metrics=[支付金额], timeRange=最近30天）
2. SemanticSearch → 客户→entity:Customer, 支付金额→metric:customer_total_paid_amount
3. QueryPlanner → AnswerPlan（answerable=true, 3表join, 需要聚合）
4. SqlDraftGenerator → 完整 SQL（含 GROUP BY, ORDER BY, LIMIT）
5. SqlValidator → PASSED_WITH_WARNINGS（指标 SYSTEM_PROPOSED）
6. AnswerComposer → SQL + 解释 + 不确定项

**验收标准：**
- SQL 语法正确，可在 MySQL/PostgreSQL 执行
- 所有表名和列名来自 catalog
- join 条件有 evidence
- 不确定项包含"指标未审核"

### 5.2 场景二：歧义问题（反问）

**输入：** "找出活跃客户"

**预期全链路输出：**

1. QuestionUnderstanding → QuestionIntent（entities=[客户], filters=[活跃], ambiguities=[活跃有多个口径]）
2. SemanticSearch → 客户→entity:Customer, 活跃→多个候选（status=ACTIVE, last_login_at, 最近下单, 最近支付）
3. QueryPlanner → AnswerPlan（answerable=false, clarificationQuestions=[按什么标准判断活跃？]）
4. AnswerComposer → 澄清问题（列出 4 种可能口径，让用户选择）

**验收标准：**
- 不生成了 SQL（因为 answerable=false）
- 澄清问题列出了具体的候选口径
- 每个候选口径有对应的列名和证据

### 5.3 场景三：无法回答（缺失信息）

**输入：** "看一下商品库存风险"

**预期全链路输出：**

1. QuestionUnderstanding → QuestionIntent（entities=[商品, 库存风险], metrics=[风险], ambiguities=[库存风险口径不明确]）
2. SemanticSearch → 商品→entity:Product, 库存风险→无匹配 metric
3. QueryPlanner → AnswerPlan（answerable=false, missingInfo=[METRIC_FORMULA], 候选表=[products, inventory_snapshots]）
4. AnswerComposer → 表字段计划（列出候选表和字段，说明为什么无法生成 SQL）

**验收标准：**
- 不生成了 SQL
- 列出了候选表和字段
- 说明了缺失什么信息（"库存风险"的业务口径不明确）

### 5.4 场景四：SQL 校验失败

**输入：** 如果 SQL Generator 生成了一段引用不存在列的 SQL（模拟 bug）

**预期：**

1. SqlValidator → FAILED，errors=[COLUMN_NOT_FOUND, JOIN_NO_EVIDENCE]
2. AnswerComposer → 不输出 SQL，输出错误解释

**验收标准：**
- 用户看不到包含不存在列的 SQL
- 错误信息指明了具体哪个列不存在

### 5.5 场景五：离线构建全链路

**输入：** relation-detector 的 scan-result.json（包含 15 个表、87 个列、24 条关系、8 条 lineage）

**预期全链路输出：**

1. ScanResultReader → ScanBundle（87 个列的 metadata，24 条关系的索引）
2. SemanticEvidenceBuilder → EvidenceGraph（87 个 field evidence，8 个 expression evidence，50+ 条 join path）
3. LLMEnricher → EnrichmentResult（15 个 SemanticTable，87 个 SemanticColumn，8 个 SemanticEntity，8 个 SemanticMetric，24 个 join path explanations，8 个 ReviewItem）
4. CatalogStore → semantic-catalog/ 目录（7 个 JSON/JSONL 文件）
5. EmbeddingIndexer → 150+ 条 embedding 记录
6. LexiconManager → 200+ 条 lexicon 记录

**验收标准：**
- 所有语义对象有 evidenceRefs
- 所有指标 reviewStatus = SYSTEM_PROPOSED
- 所有表/列 reviewStatus = EVIDENCE_SUPPORTED 或 SYSTEM_PROPOSED；只有 Review Queue / governance workflow 可以写入 BUSINESS_APPROVED
- 冲突字段进入 Review Queue
- Embedding 记录数与语义对象数一致
- Lexicon 覆盖所有表和列名

## 6. 错误传播策略

```
模块内部错误 → 记录 warning/error → 不阻断下游
  - ScanResultReader: 单条关系无效 → 跳过，记录 warning
  - EvidenceBuilder: 注释解析失败 → 跳过，记录 warning
  - LLMEnricher: API 调用失败 → 重试3次，仍失败返回部分结果
  - SqlValidator: 校验失败 → FAILED，AnswerComposer 不输出 SQL

不可恢复错误 → 抛出异常 → 终止当前链路
  - ScanResultReader: 文件不存在 → 终止
  - CatalogStore: 磁盘写入失败 → 终止
  - SqlGenerator: plan 无表 → 终止
```

## 7. 性能预算

| 链路 | 阶段 | 预算 |
| --- | --- | --- |
| 离线构建 | 全链路 | < 10 分钟（100 表规模） |
| 离线构建 | LLM 调用 | < 5 分钟（取决于 API 并发） |
| 在线问答 | 端到端 | < 3 秒 |
| 在线问答 | LLM 调用（Question Understanding） | < 1 秒 |
| 在线问答 | Semantic Search | < 200ms |
| 在线问答 | SQL 生成 + 校验 | < 100ms |
