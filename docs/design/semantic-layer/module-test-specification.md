# 语义层测试设计草案

## 1. 定位

本文档记录语义层后续实现时建议覆盖的测试场景。它不是已实现 JUnit 测试清单，不定义固定 Java API，也不把某一组评分权重或 SQL 文本写成 contract。

测试数据以 `customers`、`orders`、`payments`、`products`、`order_items` 这类电商 schema 作为 Example，用于说明行为边界。

## 2. Evidence Catalog 构建

建议测试场景：

| 场景 | 示例输入 | 期望行为 |
| --- | --- | --- |
| 关系证据进入 evidence graph | `orders.customer_id -> customers.id` | 生成 source / target 字段证据，并保留 relationship evidenceRef。 |
| 血缘证据进入 expression evidence | `SUM(payments.amount) -> paid_amount_30d` | 生成 expression evidence，并标记来源字段和 transform。 |
| 注释只增强语义 | DDL comment: `客户编号` | 生成 comment evidence；不能单独创造 physical relationship 或 lineage。 |
| 冲突候选 | 同一字段在 procedure 和 DDL 中出现不同口径 | 生成 `SYSTEM_PROPOSED` review item，不自动确认正式指标。 |
| compact bundle | 大量字段和 evidence | 截断低优先级 evidence，但保留可审计 fingerprint。 |

## 3. LLM Semantic Enricher

建议测试场景：

| 场景 | 示例输入 | 期望行为 |
| --- | --- | --- |
| LLM 编造字段 | `customers.full_name` 不存在 | `SemanticPhysicalReferenceIndex` 拒绝正式 normalization，不输出部分 artifact。 |
| LLM 编造 evidenceRef | fingerprint 无法解析 | `SemanticReferenceIndex` 逐条解析 bundle fact/evidence/candidate ID；任一引用无法闭包时拒绝。 |
| LLM 返回 `BUSINESS_APPROVED` | metric reviewStatus 被 LLM 设置为 `BUSINESS_APPROVED` | 正式 normalization 直接拒绝；不能静默降级或把治理状态交给模型决定。 |
| LLM 解释 join path | 引用已有 `orders.customer_id -> customers.id` | 可生成解释文本；不能新增 path step。 |
| 同义词扩展 | `customer`、`客户编号` | 生成 SYSTEM_PROPOSED lexicon entry，等待审核或后续证据支持。 |

## 4. Semantic Search

建议测试场景：

| 场景 | 示例输入 | 期望行为 |
| --- | --- | --- |
| 精确业务词 | `客户` | 优先召回明确 lexicon 映射对象。 |
| 多问法 | `买东西最多的人` | embedding 召回相关实体、指标或字段，再由 evidence rerank。 |
| 已拒绝对象 | `REJECTED` semantic object | 不进入默认搜索结果。 |
| 服务降级 | embedding API 不可用 | 返回 lexicon-only 结果，并标记 degraded warning。 |
| 审核状态影响排序 | 相关性接近的多个对象 | `BUSINESS_APPROVED` / `EVIDENCE_SUPPORTED` 优先级高于 `SYSTEM_PROPOSED`。 |

## 5. Query Planner

建议测试场景：

| 场景 | 示例输入 | 期望行为 |
| --- | --- | --- |
| 可回答问题 | `每个客户最近30天支付金额是多少？` | 产生 answer plan，包含候选实体、指标、字段和 evidence-backed join path。 |
| 需要澄清 | `找出活跃客户` | 返回澄清问题，不强行选择 status、登录、下单或支付口径。 |
| 多路径歧义 | 多条 join path 置信度接近 | 返回澄清或进入 Review Queue，不由 LLM 拍板。 |
| 未审核指标 | `SYSTEM_PROPOSED` metric | 可以进入 draft plan，但必须携带 warning。 |

## 6. SQL Draft Generator

建议测试场景：

| 场景 | 示例输入 | 期望行为 |
| --- | --- | --- |
| 正常生成 SQL draft | answer plan 有实体、指标、join path | 渲染 SELECT draft，并保留 TABLE_REF / JOIN_CLAUSE / METRIC_EXPRESSION 元素。 |
| 不可回答 | `answerable=false` | 不生成 SQL draft。 |
| 未审核指标 | metric reviewStatus 为 `SYSTEM_PROPOSED` | SQL draft 附带 warning，不作为正式口径。 |
| join 回溯 | `PlanJoinStep` 有 evidenceRef | JOIN fragment 能回溯到 relationship evidence。 |

## 7. SQL Validator

建议测试场景：

| 场景 | 示例输入 | 期望行为 |
| --- | --- | --- |
| 表不存在 | TABLE_REF 指向未知表 | 返回 table-not-found error。 |
| 字段不存在 | METRIC_EXPRESSION 引用未知字段 | 返回 column-not-found error。 |
| join 无证据 | JOIN_CLAUSE 无 relationship evidence | 返回 join-evidence error。 |
| 非只读草稿 | statement kind 非 SELECT | 返回 read-only guard error。 |
| raw SQL 文本 | 只有 SQL 字符串，没有 structured draft element | 只能做 parser sanity check；不能用 regex 或关键字扫描替代结构化校验。 |

## 8. 端到端验收思路

端到端测试应验证行为链路，而不是固定小数或固定 SQL 字符串：

1. relation-detector scan result 被读取成 evidence bundle。
2. LLM Enricher 只产生 `SYSTEM_PROPOSED` / `EVIDENCE_SUPPORTED` 语义对象。
3. Search 能通过 lexicon 与 embedding 找到候选对象。
4. Planner 能产出 answer plan 或澄清问题。
5. SQL Draft Generator 只在 answerable 时生成 SELECT draft。
6. Validator 只基于 structured draft elements、catalog 和 evidence 做 Phase 1 校验。

## 9. 当前代码结构门禁

`SemanticDocumentationArchitectureTest` 对所有手写 public/protected 顶层类型以及名称以
`Engine/Pipeline/Service/Collector/Extractor/Resolver/Merger/Framer/Analyzer/Visitor/Writer/Validator/Registry/Builder/Assembler/Index/Facade/Handler`
结尾的编排类型执行双语设计说明检查。说明必须交代职责、输入、输出、上下游和禁止边界；generated Java、
record accessor、getter 与显而易见的小方法排除。该门禁能证明结构和禁用模板，但不能代替调用链内容评审。
