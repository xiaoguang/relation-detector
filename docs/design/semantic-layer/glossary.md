# Semantic Layer 术语表

本文档定义 Evidence-Grounded Semantic Layer 设计中的核心术语。每个术语都有稳定标题，可作为 Markdown anchor 被其他设计文档链接。

## 能力分层

### Phase 1 Scope

第一版明确要实现的能力范围。

Phase 1 Scope 重点是 evidence-grounded semantic catalog、lexicon/embedding search、question plan 和 SQL draft validator 的基础闭环。它不承诺复杂 BI 指标平台、跨系统 fuzzy match、方言 SQL 自动改写、成本估计或 SQL 自动执行。

### Phase 2+

Phase 1 Scope 稳定后可以继续推进的工程增强。

典型例子包括更完整的 Review workflow、指标版本管理、离线评测集、用户反馈调权、catalog 增量构建和更丰富的 planner 策略。

### Future Capability

未来版本才考虑的能力。

Future Capability 不是当前实现目标，也不是 Phase 1 Scope 的验收项。文档中如果出现跨系统 fuzzy match、方言自动改写、SQL 执行频率统计、成本估计、复杂安全审计、自动执行 SQL 等内容，默认都属于 Future Capability，除非对应章节明确标注为 Phase 1 Scope。

### Example

说明设计用的例子。

Example 不是 schema 字段、不是验收项、不是当前系统已实现能力。复杂 SQL、复杂指标、跨系统关联和方言提示如果被标为 Example，只用于帮助读者理解系统边界。

## 审核状态

### SYSTEM_PROPOSED

系统提出的候选语义对象。

SYSTEM_PROPOSED 可以来自 LLM、SQL comment、DDL comment、SQL usage、用户问题或规则提取。它可以用于搜索、推荐、Review Queue 和 question planning 的候选输入，但不能作为正式业务口径。

### EVIDENCE_SUPPORTED

已有证据支撑的语义对象。

EVIDENCE_SUPPORTED 表示对象能追溯到 relation-detector 输出、metadata、relationship、lineage、SQL source、comment 或 review evidence。它强于 SYSTEM_PROPOSED，但仍不等于业务 owner 已确认的正式口径。

### BUSINESS_APPROVED

业务 owner、数据治理人员或治理流程确认过的正式口径。

BUSINESS_APPROVED 不能由 LLM 或 relation-detector 直接产生。正式指标、默认问答口径和无需 warning 的 SQL draft metric 应优先使用 BUSINESS_APPROVED 对象。

### REJECTED

被业务审核或治理流程明确否定的语义对象。

REJECTED 对象不能继续作为默认搜索结果、指标口径或 SQL draft 依据，除非后续重新打开并进入新的 review workflow。

### NEEDS_MORE_EVIDENCE

证据不足，需要补充事实或人工判断的语义对象。

NEEDS_MORE_EVIDENCE 常用于关系路径冲突、指标来源不完整、同义词映射不稳、跨系统关联缺少足够 evidence 的情况。

## 事实层与证据

### relation-detector

事实采集与证据生成子系统。

relation-detector 负责从 metadata、DDL、SQL log、procedure、trigger 和 object definition 中提取 relationship、Data Lineage、diagnostics 和 evidence。它不负责确认业务指标口径，也不输出 BUSINESS_APPROVED 语义对象。

### Relationship

数据库表字段之间的关系候选。

Relationship 可以来自 DDL FK、metadata FK、SQL JOIN、EXISTS、IN、CO_OCCURRENCE 等 evidence。它说明字段之间存在结构或使用关系，但不等于业务实体关系已经被确认。

### Data Lineage

字段写入来源关系。

Data Lineage 描述目标字段的值来自哪些来源字段，以及 transform type，例如 DIRECT、ARITHMETIC、AGGREGATE、CUMULATIVE、CONCAT_FORMAT、CASE_WHEN 等。它独立于 Relationship，不参与 relationship confidence。

### Evidence

支持某个关系、血缘或语义对象的原始证据。

Evidence 可以来自 metadata、DDL、SQL、procedure、trigger、comment、scan result 或 review decision。语义层中的每个重要对象都应能回溯到 evidenceRefs。

### EvidenceRef

指向 Evidence 的可审计引用。

EvidenceRef 建议包含 scanRunId、scanVersion、parserMode、grammarProfile、sourceHash、detectorVersion、payloadSnapshot、reviewDecisionId 等字段，用于复现语义对象的来源。

### Semantic Evidence Graph

由事实层输出组织出的语义证据图。

它把表、字段、relationship、lineage、SQL usage、comment、procedure、trigger 和 review decision 串成可搜索、可解释、可审核的 evidence graph。

## 语义对象

### Semantic Catalog

语义对象的持久化目录。

Semantic Catalog 存储 SemanticTable、SemanticColumn、SemanticEntity、SemanticMetric、SemanticJoinPath、lexicon、embedding、review decision 和 question trace。它是离线构建链路和在线问答链路之间的中间资产。

### Semantic Object

语义层中可被搜索、规划或审核的对象。

常见 Semantic Object 包括 SemanticTable、SemanticColumn、SemanticEntity、SemanticMetric 和 SemanticJoinPath。所有对象都应带 reviewStatus 和 evidenceRefs。

### Semantic Table

物理表在语义层中的表示。

Semantic Table 可能包含业务名称、描述、同义词、主题域、字段列表和 evidenceRefs。它通常由 metadata 和 SQL usage evidence 支撑。

### Semantic Column

物理字段在语义层中的表示。

Semantic Column 可以保存字段名、业务别名、描述、数据类型、来源 evidence、常见表达式 usage、同义词和 reviewStatus。

### Semantic Entity

业务实体候选。

Semantic Entity 是对一组表、字段和关系的业务抽象，例如 "客户"、"订单"、"商品"。它通常先由系统提出 SYSTEM_PROPOSED，之后通过 evidence 和治理流程提升。

### Semantic Metric

业务指标候选或正式指标。

Semantic Metric 包含名称、表达式、grain、filter、time window、来源字段、evidenceRefs 和 reviewStatus。只有 BUSINESS_APPROVED metric 才能作为默认正式口径；SYSTEM_PROPOSED metric 只能进入 draft 并携带 warning。

### Semantic Event

业务事件候选。

Semantic Event 表达业务世界里“发生了什么”：谁，在什么时间，对什么对象，发生了什么行为，造成了什么结果。例如 PaymentEvent、RefundEvent、OrderCreatedEvent、InventoryChangeEvent。

Semantic Event 不是所有问答的必经入口。字段查询、表关系查询和指标口径查询可以直接使用 metadata、relationship、attribute 或 metric；当问题涉及行为、时间链路、状态变化、归因解释或复杂指标来源时，事件才成为有价值的建模单元。

Semantic Event 可以由 relation-detector facts 支撑，例如 relationship、Data Lineage、metadata、SQL usage、procedure、trigger 和 comment。它通常先是 SYSTEM_PROPOSED 或 EVIDENCE_SUPPORTED；正式业务事件定义需要 review 或治理流程确认，不能由 LLM 或 relation-detector 直接产生 BUSINESS_APPROVED。

Semantic Event 与 Semantic Metric 的关系是：指标通常是事件的聚合或派生结果。例如销售额可能来自支付事件或订单事件，退款率可能来自退款事件和支付事件，复购率可能来自多次购买事件。

Semantic Event 与 Action 的区别是：事件描述已经发生或被记录的业务事实；Action 描述系统未来可以触发的动作，例如发预警、生成报告、创建任务或调用 API。Action 属于 Future Capability，不是 Phase 1 Scope 的自动执行能力。

### Semantic Join Path

由 evidence 支撑的表连接路径。

Semantic Join Path 不由 LLM 凭空创造。它应来自 relationship、metadata、SQL usage 或人工 review evidence。Query Planner 可以在多个 join path 之间做 graph search 和 rerank。

### Lexicon

业务词与语义对象的映射。

Lexicon 用于处理精确业务词、同义词、别名和常见问法。例如 "客户"、"会员"、"买家" 可能映射到同一 SemanticEntity，但映射本身也需要 evidence 和 reviewStatus。

### Embedding

语义对象或问题文本的向量表示。

Embedding 用于模糊召回，不能单独作为事实依据。召回结果必须经过 lexicon、evidence、reviewStatus 和 planner 约束 rerank。

## LLM 角色

### 解释

LLM 把已有 evidence 翻译成可读说明。

例子：把 `orders.customer_id -> customers.id` 解释为 "订单通过 customer_id 关联客户主键"。LLM 不能因此新增不存在的 relationship。

### 归纳

LLM 根据多条 evidence 总结可能的业务含义。

例子：字段 `paid_amount`、`payment_amount`、SQL alias `total_paid` 多次出现时，LLM 可以归纳出 "支付金额" 这个 SYSTEM_PROPOSED metric 候选。

### 扩展

LLM 生成同义词、别名和常见问法候选。

例子：围绕 "客户" 扩展 "会员"、"买家"、"用户"。这些扩展进入 Lexicon candidate 或 Review Queue，不能直接成为 BUSINESS_APPROVED。

### 规划

LLM 帮助把自然语言问题拆解为查询意图。

例子：把 "最近 30 天每个客户支付金额" 拆成实体 "客户"、指标 "支付金额"、时间过滤 "最近 30 天" 和候选 join path。最终表字段、join 和 metric 仍要由 Semantic Catalog、evidence 和 SQL Validator 约束。

## 问答与 SQL 草稿

### Question Standardization

问题标准化。

Question Standardization 是自然语言进入语义规划前的轻量 LLM 步骤。它负责把原始用户问题整理成更完整、更明确、更规范的问题表达，例如补全省略、恢复代词指代、整理口语表达、保留未知业务词并输出 query rewrites。

它不需要全量 schema、指标库、业务口径库或实体值字典，也不能决定表字段、join path、metric reviewStatus 或 SQL。未知词应进入 Semantic Search、Lexicon 或 Review Queue，而不是由标准化步骤直接裁决。

### Question Plan

自然语言问题的结构化规划结果。

Question Plan 通常包含候选实体、字段、指标、时间范围、过滤条件、join path、grain、不确定项和是否需要反问。

### LogicForm-like Intermediate Representation

类 LogicForm 中间表达。

本项目不照搬亿问材料中的 LogicForm 格式，但吸收其职责：在自然语言和 SQL draft 之间放置一个结构化、可验证、可追溯的中间语义表达。`QuestionPlan / AnswerPlan` 共同承担这个角色。

它应该表达“用户想查什么”，包括业务对象、指标、维度、时间范围、过滤条件、排序、limit、grain、join path、analysis options、warning 和 evidenceRefs。它不是 SQL，也不是业务人员手写格式；它的价值是把 LLM 的自然语言理解结果交给 catalog、planner 和 validator 约束。

### Answer Plan

可用于生成回答或 SQL draft 的计划。

Answer Plan 是 Query Planner 的输出，包含已选表、字段、metric、join step、filter、grouping、warning 和 evidenceRefs。

### SQL Draft

基于 Answer Plan 生成的 SQL 草稿。

SQL Draft 不是自动执行 SQL。Phase 1 Scope 中它必须经过 SQL Validator，并保留元素级回溯信息和 warning。

### SQL Validator

校验 SQL draft 是否符合 catalog、evidence 和治理规则的模块。

Phase 1 Scope 只承诺 structured draft element 校验：表字段存在性、join evidence、metric reviewStatus、read-only/draft guard 和基础 parser sanity check。复杂聚合合法性、方言改写、成本估计和安全审计属于 Future Capability。

### Review Queue

需要人工或治理流程确认的队列。

Review Queue 管理 SYSTEM_PROPOSED object、低置信度对象、冲突 join path、未审核 metric、同义词冲突等。LLM 可以提供解释和建议，但不能最终批准。

## 业务分析术语

### RFM

RFM 是一种客户价值分析方法，由三个维度组成：

- Recency：最近一次消费距离现在多久。
- Frequency：一段时间内消费频次。
- Monetary：一段时间内消费金额。

在语义层设计里，RFM 属于复杂指标示例。它通常需要明确时间窗口、订单/支付事实表、客户粒度、金额口径和业务审核，因此默认是 Example 或 Phase 2+，不是 relation-detector 直接输出的事实。

### Grain

指标或查询结果的统计粒度。

例子：按客户、按订单、按商品、按天、按客户加月份。Query Planner 必须让 metric、join path 和 grouping 与 grain 一致。

### Join Path

连接多个表所需的路径。

Join Path 可以由一个或多个 relationship 组成。语义层选择 join path 时需要考虑 evidence、方向、粒度、角色和歧义，而不是只选最短路径。

### Metric Grain

指标定义中的默认粒度。

例子："客户最近 30 天支付金额" 的 grain 通常是 customer；"商品库存风险" 的 grain 可能是 product 或 product + warehouse。

### Draft Warning

SQL draft 或 Answer Plan 中的非阻断警告。

例子：使用了 SYSTEM_PROPOSED metric、存在多个可选 join path、缺少 BUSINESS_APPROVED 指标口径、存在需要用户确认的时间窗口。
