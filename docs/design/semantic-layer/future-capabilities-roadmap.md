# Semantic Layer Future Capabilities Roadmap

> 状态：路线图，以下能力均未在当前生产代码中实现。本文件描述实施边界和验收门槛，不是已交付 API、
> Java 类型或存储 Schema 的承诺。历史详细草图继续保存在 Git 历史中。

## 1. 当前基线

当前 semantic-layer 已实现的边界是：

```text
relation-detector JSON
  -> ScanResultReader / ScanBundle
  -> SemanticEvidenceBuilder
  -> EvidenceGraph
  -> SemanticKgBuilder
  -> semantic-kg.json / semantic-evidence-graph.json / semantic-build-run.json
```

正式 semantic extraction 通过独立 provider 链路生成候选文档，并由 evidence bundle、typed reference index、
physical endpoint registry 和 graph assembler 做原子闭包校验。当前没有 catalog service、在线搜索、自然语言
查询规划、SQL draft、answer API 或 review workflow。

所有未来能力共同遵守以下约束：

- relation-detector 是物理 relationship、lineage、naming 和 provenance 的唯一生产者。
- semantic-layer 可以解释、组织和审核物理事实，不能发明或改写物理事实。
- LLM 输出只能是候选；`BUSINESS_APPROVED` 只能来自人工或治理流程。
- 每个可执行 join、指标、SQL 片段和最终回答必须保留可解析 evidence refs。
- raw SQL 的结构判断必须来自 parser 或结构化 draft element，不能使用 regex、关键字包含或名称白名单。
- 任一闭包校验失败都必须原子拒绝，不返回部分正式 artifact。

## 2. 依赖顺序

```text
Evidence-backed Catalog Store
  -> Lexicon Manager
  -> Embedding Indexer
  -> Semantic Search
  -> Question Understanding
  -> Query Planner
  -> SQL Draft Generator
  -> SQL Validator
  -> Answer Composer

Catalog Store / Lexicon / Planner / Validator
  -> Review Queue
```

后置能力不得绕过前置能力自行重建 catalog、identity、evidence 或 governance 状态。

## 3. 能力矩阵

### 3.1 Evidence-backed Catalog Store

- **目标**：持久化 build run、semantic objects、edges、evidence refs、review decisions 和 question trace。
- **前置依赖**：稳定的 EvidenceGraph/KG wire contract、scanRun/sourceHash 和 review status 模型。
- **输入**：已通过闭包校验的 evidence graph、KG、build metadata 和 governance decisions。
- **输出**：可按 build snapshot、object identity 和 evidence ref 查询的 catalog snapshot。
- **安全边界**：不调用 LLM、不做语义判断；历史 `BUSINESS_APPROVED/REJECTED` 状态不能被新模型输出覆盖；
  未出现对象只降级或废弃，不静默物理删除。
- **实施验收**：全量与增量 build 可重放；每个对象可回到 scan run、source hash 和 payload snapshot；
  JSON prototype 与生产存储数据集一一对应。

### 3.2 Lexicon Manager

- **目标**：管理业务词、同义词和 semantic object 的多来源映射，并发现冲突。
- **前置依赖**：Catalog Store、稳定 object IDs、review decisions。
- **输入**：对象名、DDL/SQL comment evidence、人工词条和模型提出的同义词候选。
- **输出**：精确/模糊 lookup、反向 object index、conflict candidates。
- **安全边界**：归一化、索引和排序是确定性逻辑；模型候选不能自动成为人工确认词条；同词多对象必须保留
  全部来源并进入 review。
- **实施验收**：精确、前缀和有限模糊搜索稳定；多来源优先级可解释；冲突不会被 last-write-wins 覆盖。

### 3.3 Embedding Indexer

- **目标**：为可检索 semantic objects 生成模板化文本和版本化向量索引。
- **前置依赖**：Catalog Store、对象可见性和 review 状态、可配置 embedding provider。
- **输入**：catalog snapshot、对象类型模板、provider/model/version 和输入预算。
- **输出**：object ID 到 embedding 的索引、模型元数据、截断与失败审计。
- **安全边界**：embedding 不产生事实；文本由确定性模板构造；有限重试，不能把 provider 失败伪装成空索引。
- **实施验收**：相同对象和配置生成稳定输入 hash；增量索引只处理变化对象；模型版本或维度变化触发明确重建。

### 3.4 Semantic Search

- **目标**：组合 lexicon、向量召回、对象类型/review/evidence 过滤和可解释 rerank。
- **前置依赖**：Catalog Store、Lexicon Manager、Embedding Indexer。
- **输入**：规范化 query、可见对象范围、review/evidence 阈值。
- **输出**：有界候选列表及 lexical/vector/evidence/review 分项分数。
- **安全边界**：搜索只召回候选，不确认业务含义；`REJECTED` 默认不可见；无 evidence 对象不能成为正式答案依据。
- **实施验收**：精确词优先、语义近义可召回、冲突保留 alternatives；候选排序可复现且每项可解释。

### 3.5 Question Understanding

- **目标**：把自然语言问题转成 typed `QuestionIntent`，表达实体、指标、维度、过滤、时间和歧义。
- **前置依赖**：Semantic Search、稳定 catalog snapshot 和 typed intent schema。
- **输入**：用户问题、locale/timezone、搜索候选和允许的业务域。
- **输出**：typed intent、候选引用、confidence、clarification needs。
- **安全边界**：模型只能选择或组织提供的候选，不能创建物理表列、指标或 join；原问题和候选引用必须保留。
- **实施验收**：简单查询确定性解析；时间范围、同比环比和业务日历显式建模；歧义返回澄清而非猜测。

### 3.6 Query Planner

- **目标**：把 QuestionIntent 转成 evidence-backed `AnswerPlan`，选择 grain、字段、指标和定向 join path。
- **前置依赖**：Question Understanding、Catalog Store、reviewed metrics 和 relationship graph。
- **输入**：typed intent、catalog snapshot、relationship evidence、grain/role constraints。
- **输出**：required tables/columns、metric expressions、filters、time semantics、oriented join steps 和 alternatives。
- **安全边界**：只在有界 evidence graph 上搜索；无路径或路径近似冲突时不可回答或请求澄清；未审核指标只能进入
  带 warning 的 draft。
- **实施验收**：单表不造 join；多表每一步有 evidence fingerprint；grain/role/hop/review 参与排序且结果可复现。

### 3.7 SQL Draft Generator

- **目标**：从 AnswerPlan 模板化渲染只读 SQL draft。
- **前置依赖**：answerable 的 AnswerPlan、方言 quoting/date rendering。
- **输入**：typed plan、oriented join steps、structured metric/filter expressions。
- **输出**：SQL 文本、dialect、逐片段 `SqlDraftElement` 和 warnings。
- **安全边界**：不搜索路径、不推断 join 方向、不调用 LLM；所有表列和表达式来自 plan；draft 必须进入 Validator，
  不能直接执行。
- **实施验收**：SELECT-only；每个 FROM/JOIN/filter/metric fragment 可回溯；缺 element/evidence 时拒绝生成。

### 3.8 SQL Validator

- **目标**：验证 SQL draft 仍受 catalog、evidence、review status 和 read-only contract 约束。
- **前置依赖**：SQL Draft Generator、Catalog Store、结构化 parser sanity check。
- **输入**：SqlDraft 与 SqlDraftElements、catalog snapshot、relationship evidence。
- **输出**：typed errors/warnings、validated draft 或 rejection。
- **安全边界**：不以 regex 或关键词黑名单解析 SQL；Phase 1 不冒充完整安全审计、成本估算或数据库权限检查。
- **实施验收**：不存在表列、无 evidence join、非 SELECT、未审核指标和 parser mismatch 均有稳定分类；
  任一 hard error 禁止下游输出可执行 SQL。

### 3.9 Answer Composer

- **目标**：把 plan、draft 和 validation 结果组装为 SQL draft、澄清问题或表字段计划。
- **前置依赖**：Query Planner、SQL Validator、稳定 machine-readable Answer schema。
- **输入**：AnswerPlan、可选 SqlDraft、ValidationResult。
- **输出**：`SQL_DRAFT`、`CLARIFICATION_NEEDED` 或 `TABLE_FIELD_PLAN`，同时保留 evidence 和 warnings。
- **安全边界**：模板不新增事实；未来 LLM 只能润色文本，不能修改 SQL、对象引用、warnings 或 review status。
- **实施验收**：validator failure 不输出可执行 SQL；每个数字、字段、join 和 draft 都有 explanation/evidence。

### 3.10 Review Queue

- **目标**：管理 semantic candidates、冲突和治理决策的状态机与审计日志。
- **前置依赖**：Catalog Store、稳定 object IDs、认证/授权和 decision provenance。
- **输入**：系统候选、conflicts、low-confidence/review-required items 和人工决定。
- **输出**：pending queue、decision history、reopen records 和 catalog status updates。
- **安全边界**：模型只能提供 recommendation/impact summary；不能写 `BUSINESS_APPROVED`；决定必须保留 reviewer、
  time、reason 和 previous state。
- **实施验收**：状态转换受约束、并发决定不丢失、历史可审计、拒绝对象不会重新进入默认搜索。

## 4. 跨能力实施门槛

任一能力进入实现前都必须具备：

1. 明确 owner module，避免 semantic-layer 内出现第二套物理 parser。
2. typed 输入输出 contract 和兼容策略。
3. evidence/ref closure、identity conflict 与原子失败测试。
4. LLM/provider 数据脱敏、有限重试和不可提升 governance 状态的测试。
5. 正向、负向、冲突、增量和重放测试。
6. 对现有 KG/extraction artifact 的无计划外 fingerprint 变化证明。

在这些门槛满足前，本路线图中的类名、接口名和存储选择均不应提前固化为生产 API。
