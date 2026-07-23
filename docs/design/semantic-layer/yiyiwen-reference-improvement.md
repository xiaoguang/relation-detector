# 参考亿问改进分析

本文档沉淀 `亿问/` 目录中关于 DataAgent、SemanticDB、NL2LF2SQL 和本体化语义层的材料，对 Evidence-Grounded Semantic Layer 的启发、可吸收点、不可照搬点和后续设计建议。

本文是外部参考分析，不是当前实现说明。文中提到的亿问能力只表示参考材料中的设计主张，不表示本项目已经实现，也不评价其真实产品实现效果。所有进入本项目设计的能力仍以 [Evidence-Grounded Semantic Layer 整体设计](../semantic-layer-overall-design.md) 和 [Semantic Layer 术语表](glossary.md) 为准。

## 1. 背景与阅读边界

`亿问/` 目录中的材料主要围绕企业 DataAgent、可信问数、NL2LF2SQL、SemanticDB、指标语义层和本体化语义层展开。它们对本项目最有价值的地方不是具体产品功能，而是几条架构判断：

- 不能让大模型直接面对物理数据库并一次性生成 SQL。
- 自然语言和 SQL 之间需要稳定的中间语义表达。
- 语义层不能只做字段字典或指标字典，还需要逐步表达对象、事件、关系、规则和权限。
- 每个数字、解释、归因和建议都需要证据链。

本项目的起点不同：relation-detector 已经能从 metadata、DDL、SQL、procedure、trigger 等来源提取 relationship、Data Lineage 和 diagnostics。也就是说，我们拥有一个更偏底层的事实采集与证据生成子系统。因此我们吸收亿问材料时，应优先吸收“语义中间层”和“可信证据链”的架构思想，而不是照搬完整 DataAgent 产品形态。

## 2. 亿问设计主线摘要

### 2.1 亿问材料主题表

| 材料 | 核心主题 | 对我们的价值 | 吸收方式 |
| --- | --- | --- | --- |
| `01-1900天跨越“信任缺陷”` | 企业 AI 数据产品的核心瓶颈是信任缺陷，不是对话形式。 | 强化“可信证据链”作为语义层主线。 | 纳入总体叙事：LLM 输出必须可追溯，不可信时应反问或进入 Review Queue。 |
| `02-亿问DataAgent产品全景` | DataAgent 需要业务语义、数据可信和分析闭环；核心查询采用 NL2LF2SQL。 | 说明自然语言问答不能停在 NL2SQL。 | 将 `QuestionPlan / AnswerPlan` 明确为本项目的 LogicForm 类中间表达。 |
| `03-NL2SQL的天花板，NL2LF2SQL是怎么突破的？` | SemanticDB 用实体和事件表达业务世界，LogicForm 承接自然语言理解结果。 | 对我们最有启发。 | 补强 [LogicForm-like intermediate representation](glossary.md#logicform-like-intermediate-representation)、`SemanticEntity`、后续 `SemanticEvent`、时间语义和复杂指标拆解。 |
| `04-5秒内、“无限上下文”...` | 通过语义检索、上下文裁剪和稳定执行控制复杂语义与性能。 | 提醒我们不能把所有 schema 直接塞给模型。 | 保持 Lexicon + Embedding + evidence rerank 的检索架构，避免直接全量 prompt。 |
| `05-NL2SQL中的本体化语义层和指标语义层` | 指标语义层解决口径统一，本体化语义层表达对象、事件、关系和规则。 | 帮助区分指标层和更大语义层。 | 在文档中把指标层写成重要组成，而不是语义层全部。 |
| `06-语义层不能只剩指标和维度` | DataAgent 需要指标语义、对象语义、事件语义、规则语义、动作语义。 | 给出语义层能力分层参考。 | Phase 1 保留事实证据和问答规划；事件/规则/动作分阶段推进。 |

### 2.2 亿问的核心链路

亿问材料中最关键的链路是：

```text
自然语言
  -> 大模型问题标准化 / 上下文补全
  -> Alisa 语义理解
  -> LogicForm
  -> SemanticDB 约束和补全
  -> SQL / API / URL / 其他执行路径
  -> 业务可读结果
```

它的核心不是“多了一层 JSON”，而是把职责拆开：

| 层次 | 亿问材料中的职责 | 对我们的映射 |
| --- | --- | --- |
| 大模型问题标准化 | 补全上下文、省略恢复、代词指代、口语表达整理和句式标准化。 | `Question Standardization`，只处理语言层，不决定表字段或业务口径。 |
| Alisa 语义理解 | 在标准化问题基础上识别对象、指标、时间、条件和分析动作，并生成 LogicForm。 | 本项目用 `Question Understanding + Semantic Search + Query Planner` 分担类似职责。 |
| LogicForm | 用结构化方式描述业务问题，不直接写 SQL。 | `QuestionPlan / AnswerPlan`，作为本项目的中间语义表达。 |
| SemanticDB | 约束业务概念、复用口径、推理关系、处理时间和方言差异。 | `Semantic Catalog Store` + `Query Planner` + `SQL Draft Generator` + `SQL Validator`。 |
| 执行层 | 根据数据源生成 SQL、API、URL 或其他动作。 | Phase 1 只生成 SQL draft；API/URL/动作执行属于 Future Capability。 |
| 结果解释 | 将原始结果还原成业务可读数据。 | `Answer Composer`，Phase 1 先解释字段、表、join path、SQL draft 和不确定点。 |

### 2.3 官方材料中可直接采信的信息

以下内容来自 `亿问/` 目录中的官方文章材料，可作为设计参考的事实来源：

| 官方材料表达 | 本项目吸收方式 |
| --- | --- |
| DataAgent 不直接把自然语言翻译成 SQL，而是走 `自然语言 -> LogicForm -> SQL`。 | 本项目不采用直接 NL2SQL，把 `QuestionPlan / AnswerPlan` 定位为 LogicForm-like 中间表达。 |
| 自然语言到 LogicForm 由上层理解模块 Alisa 完成。 | 本项目不实现 Alisa，但把 `Question Understanding + Semantic Search + Query Planner` 组合成类似职责边界。 |
| LogicForm 结构化描述“用户想查什么”，表达业务对象或事件、筛选、指标、分组、排序、限制、同比、环比等。 | `QuestionPlan / AnswerPlan` 必须包含 target objects、metrics、dimensions、filters、timeRange、sort、limit、analysis options 和 evidenceRefs。 |
| Production 方案先让大模型做问题标准化和上下文补全，再交给 Alisa 做确定性语义推理和 LogicForm 生成。 | Phase 1 中 LLM 只做问题标准化、上下文补全、query rewrite 和候选 mention；确定性规划交给 catalog/search/planner。 |
| 标准化阶段不需要业务知识，不需要加载全部表、指标、口径和实体值。 | Question Understanding 的 LLM prompt 必须保持小上下文，不能把全量 schema 和指标库塞给模型。 |
| 未知词需要单独处理，可用向量库搜索或大模型二次确认缩小候选空间。 | `unknownTerms` 进入 Semantic Search / Lexicon / Review Queue；必要时才触发二次确认。 |

### 2.4 官方材料没有展开、需要推测的部分

亿问材料没有公开 Alisa 或 LogicForm 的完整代码、完整 schema、执行算法和评测细节。下面是本项目基于材料做出的工程推测，不能写成亿问官方实现事实：

| 推测点 | 本项目采用的保守设计 |
| --- | --- |
| Alisa 的内部算法可能包含语义解析、候选空间约束、规则推理和结果空间搜索。 | 不实现“Alisa 等价物”；只把职责拆成 Question Understanding、Semantic Search、Query Planner 和 Validator。 |
| LogicForm 的真实字段和语法可能比文章示例复杂。 | 不照搬亿问 JSON；使用本项目自己的 `QuestionPlan / AnswerPlan`，但保留中间语义表达的角色。 |
| SemanticDB 可能包含实体、事件、指标、规则、权限、执行器和缓存。 | Phase 1 只做 Semantic Catalog、EvidenceRef、Search、QuestionPlan、SQL draft validator；权限、动作和多执行器进入 Future Capability。 |
| “稳定性 100%”和“毫秒级”是产品材料中的目标表达，缺少可复现实验细节。 | 不写成我们的验收承诺；本项目只承诺 evidence-grounded、可审核、可回放。 |

### 2.5 复杂问题如何落到本项目链路

以“这个财年华东地区女装销售的情况”为例，亿问材料里的 LogicForm 思路会把自然语言拆成业务对象、筛选、指标、维度和分析动作。本项目的对应链路是：

| 阶段 | 示例输入 | 示例输出 |
| --- | --- | --- |
| LLM 问题标准化 | “这个财年华东地区女装销售的情况” | `normalizedQuestion`: “查看当前财年华东销售区域女装品类的销售概览、月度趋势和核心销售指标”。 |
| Question Understanding | 标准化问题 | `QuestionIntent`: 时间=`current_fiscal_year`，区域=`华东`，品类=`女装`，主题=`sales_performance`，指标 mention=`销售情况`。 |
| Semantic Search | `QuestionIntent` | 候选对象：`sales_fact`、`category_dim`、`region_dim`、`fiscal_calendar`；候选指标：销售额、订单数、销量、客单价。 |
| Query Planner | 候选对象、reviewStatus、relationship evidence | `AnswerPlan`: grain=`fiscal_month + region + product_category`，join path，metric set，warnings。 |
| SQL Draft Generator | `AnswerPlan` | 只读聚合 SQL draft。 |
| SQL Validator | SQL draft、catalog、evidence | 校验表字段、join evidence、metric review status、read-only guard。 |

如果财年、华东、女装或销售情况的默认指标没有 `BUSINESS_APPROVED` 口径，本项目不会假装可直接回答，而是在 Query Planner 或 SQL Validator 阶段返回 clarification 或 warning。这一点是我们相对产品材料必须保持的工程边界。

## 3. 与我们现有设计对比

### 3.1 能力对比表

| 维度 | 亿问 DataAgent / SemanticDB 材料 | 我们当前设计 | 判断 |
| --- | --- | --- | --- |
| 事实来源 | 强调语义数据库和业务知识图谱，但材料中没有展开底层 SQL evidence extractor。 | relation-detector 已能输出 relationship、Data Lineage、metadata、diagnostics。 | 我们的底层 evidence 更明确，应作为差异化优势。 |
| 中间表达 | 明确提出 LogicForm。 | 已有 `QuestionPlan / AnswerPlan` 概念，并在术语表中明确为 [LogicForm-like intermediate representation](glossary.md#logicform-like-intermediate-representation)。 | 可吸收。 |
| 建模单位 | 实体、事件、关系、规则、权限、动作。 | 当前以 semantic object、table、column、entity、metric、join path 为主。 | `SemanticEvent` 和规则语义需分阶段补强。 |
| 指标语义 | 指标是重要组成，但不是全部。 | 已有 metric/review status，但需要避免文档只像指标字典。 | 可吸收。 |
| LLM 角色 | 模型理解自然语言，不直接生成最终 SQL。 | LLM 解释、归纳、扩展和规划；不能创造数据库事实。 | 高度一致。 |
| SQL 生成 | LogicForm2SQL 由语义层稳定执行。 | SQL Draft Generator 基于 AnswerPlan 模板生成 draft。 | 一致，但我们必须保持 draft 和 validator 边界。 |
| 可信证据 | 每个数字可追溯、可质询。 | `EvidenceRef`、relationship、lineage、review decision。 | 高度一致，应强化为主线。 |
| 动作闭环 | 动态报告、预警、API/URL、任务流、动作语义。 | Phase 1 不承诺动作执行。 | 只能作为 Phase 2+ 或 Future Capability。 |
| 权限治理 | 材料中作为企业级必备能力。 | 当前语义层文档仅有治理和 review，不做完整权限系统。 | 不能直接写成 Phase 1。 |

### 3.2 三条路线对比

| 路线 | 数据输入 | 大模型职责 | 中间层 | SQL 生成方式 | 主要风险 | 本项目态度 |
| --- | --- | --- | --- | --- | --- | --- |
| 直接 NL2SQL | 表结构、字段名、少量说明。 | 同时完成意图理解、找表、推关系、判断指标、写 SQL。 | 无。 | LLM 直接生成。 | 幻觉空间大；SQL 能跑但业务含义错。 | 不采用。 |
| 亿问 NL2LF2SQL | SemanticDB 中的业务对象、事件、指标、规则。 | 生成或辅助生成 LogicForm。 | LogicForm + SemanticDB。 | 语义层确定性落 SQL/API/URL。 | SemanticDB 建模成本高；材料里产品能力边界较大。 | 吸收思想，不照搬完整产品边界。 |
| Evidence-Grounded Semantic Layer | relation-detector facts、metadata、comment、review decision。 | 解释、归纳、扩展、规划，输出 SYSTEM_PROPOSED 语义对象或 QuestionPlan。 | Semantic Catalog + QuestionPlan / AnswerPlan。 | 模板生成 SQL draft，再由 SQL Validator 校验。 | Phase 1 覆盖范围不如完整 DataAgent，但证据链更可控。 | 当前主路线。 |

### 3.3 指标语义层、本体化语义层与本项目对比

| 维度 | 指标语义层 | 本体化语义层 | Evidence-Grounded Semantic Layer |
| --- | --- | --- | --- |
| 核心问题 | 这个数怎么算？ | 业务世界由哪些对象、事件、关系和规则组成？ | 哪些语义结论有事实证据支撑，如何用于问答规划？ |
| 建模对象 | 指标、维度、口径、时间范围。 | 实体、事件、状态、规则、权限、动作。 | Semantic object、EvidenceRef、Entity、Metric、JoinPath、QuestionPlan。 |
| 优势 | 高频问数稳定，口径统一快。 | 支撑归因、解释、建议和动作衔接。 | 从现有数据库事实出发，可审计、可渐进落地。 |
| 风险 | 容易停在“看数”，不擅长解释业务链路。 | 建模成本高，容易概念膨胀。 | Phase 1 需要克制，不应过早承诺动作闭环。 |
| 与本项目关系 | 是必要组成。 | 是长期方向。 | 是当前落地路线：先事实证据，再语义对象，再问答规划。 |

## 4. 可吸收的设计改进

### 4.1 把 QuestionPlan / AnswerPlan 明确为 LogicForm-like 中间表达

亿问材料最值得吸收的是 NL2LF2SQL 的职责拆分。本项目不需要照搬其 LogicForm 格式，但应明确：

- `Question Understanding` 不输出 SQL，而输出结构化业务意图。
- `Query Planner` 将结构化意图补全成 `AnswerPlan`。
- `SQL Draft Generator` 只消费 `AnswerPlan`，不重新猜测 join 方向、指标口径或表字段。
- `SQL Validator` 校验 draft 是否仍被 evidence 支撑。

建议后续在整体设计中增加一个小节：

```text
Natural Language
  -> QuestionPlan
  -> AnswerPlan
  -> SQL Draft
  -> Validated Draft Response
```

### 4.2 补强 SemanticEvent 和事件链路，但不写成 Phase 1 已实现

亿问材料反复强调“业务不是围绕指标发生，而是围绕对象和事件发生”。这提醒我们：

- 支付、退款、下单、发货、登录、补货、审批等应作为未来 `SemanticEvent` 建模对象。
- 事件是 Phase 2+ 的重要建模对象，但不是所有问答和操作的统一入口；字段查询、关系查询和指标口径查询可以直接使用 metadata、relationship、attribute 或 metric。
- Data Lineage 可以帮助发现某些事件字段来源，但不能直接证明业务事件语义。
- SQL log、procedure、trigger、comment 可以作为事件候选 evidence，但仍需要 review。

建议定位：

| 内容 | 建议能力分层 |
| --- | --- |
| 从表名、字段、comment 和 SQL usage 中提出事件候选 | Phase 2+ |
| 事件链路用于解释指标变化 | Phase 2+ |
| 事件触发动作或流程 | Future Capability |
| 自动确认业务事件定义 | 不允许，必须经治理或 review。 |

### 4.3 强化时间语义

亿问材料指出“今年、本月、同比、环比、截至目前”在企业里常常涉及数据延迟、统计周期、表粒度和业务口径。我们当前文档可以吸收为：

| 时间能力 | 建议分层 | 说明 |
| --- | --- | --- |
| 明确自然语言中的日期范围 | Phase 1 Scope | 例如最近 30 天、本月、上季度。 |
| 把时间范围写入 QuestionPlan / AnswerPlan | Phase 1 Scope | 不能只留在用户原句里。 |
| 同比、环比、滚动窗口 | Phase 2+ | 需要 metric grain 和业务口径支持。 |
| 数据延迟、结账日、业务日历 | Phase 2+ | 需要配置化 calendar / freshness policy。 |
| 自动解释经营波动原因 | Future Capability | 需要事件链路和分析策略。 |

### 4.4 把“每个数字可追溯”写成设计优势

亿问材料强调“每个数字都能回到它来的地方”。这与本项目非常契合，因为 relation-detector 已经提供事实层证据：

- relationship evidence：DDL、metadata、SQL join、EXISTS、IN、co-occurrence。
- lineage evidence：写入字段来自哪些来源字段，transform type 是什么。
- source evidence：SQL source、DDL comment、procedure、trigger、diagnostics。
- review evidence：BUSINESS_APPROVED、REJECTED、NEEDS_MORE_EVIDENCE。

建议在 Answer Composer 和 SQL Validator 中强化输出：

| 输出对象 | 必须可追溯到 |
| --- | --- |
| 使用某张表 | metadata / SQL usage / semantic object evidence |
| 使用某个字段 | metadata / comment / lineage / projection evidence |
| 使用某条 join path | relationship evidence |
| 使用某个指标 | metric definition + review status |
| 生成 SQL draft | AnswerPlan + validated structured elements |

### 4.5 保留指标语义层价值，不把它写低

亿问材料有一个很好的提醒：指标语义层不是低级方案，也不是过渡形态。它适合高频、标准化、口径稳定的问数场景。

本项目文档应保持这个口径：

- 指标层是语义层重要组成。
- 业务对象和事件建模不是替代指标，而是解释指标背后的事实和链路。
- Phase 1 可以先做好 metric candidate、review status、question plan 和 SQL draft。
- 完整本体化语义层是长期方向，不是第一版必须全量完成。

## 5. 不应直接照搬的内容

### 5.1 决策表

| 亿问材料中的能力 | 是否吸收 | 本项目定位 | 原因 |
| --- | --- | --- | --- |
| NL2LF2SQL 职责拆分 | 吸收 | Phase 1 Scope 的设计原则 | 有助于把 LLM 和 SQL 生成解耦。 |
| SemanticDB 表达实体/事件 | 部分吸收 | Entity 为 Phase 1，Event 为 Phase 2+ | 事件语义需要更多 evidence 和 review。 |
| 每个数字可追溯 | 吸收 | Phase 1 Scope | 与 EvidenceRef 完全一致。 |
| 指标语义层 + 本体化语义层分层 | 吸收 | 文档叙事和 roadmap | 有助于避免指标字典化。 |
| API / URL / 多执行路径 | 暂不吸收 | Future Capability | 当前只设计 SQL draft，不做多执行器。 |
| 动态报告 / PPT / 看板 | 暂不吸收 | Future Capability 或外部产品层 | 不是语义层核心。 |
| 主动预警 / 任务推送 | 暂不吸收 | Future Capability | 需要执行调度、阈值、通知和权限。 |
| 权限治理完整闭环 | 暂不吸收 | Phase 2+ / Future Capability | 需要组织、角色、数据权限模型。 |
| 动作语义和流程调用 | 暂不吸收 | Future Capability | 不能在 Phase 1 承诺自动动作。 |
| “经营分析会可直接使用” | 不吸收 | 不写成能力 | 属于产品营销表述，不是工程设计。 |

### 5.2 容易带偏的表达

| 容易带偏的表达 | 风险 | 建议改写 |
| --- | --- | --- |
| “系统理解业务世界” | 容易被误读成 LLM 已能确认事实。 | “系统基于 evidence 构建可审核的业务语义对象”。 |
| “自动归因” | 容易越界到分析结论自动裁决。 | “生成归因候选和可追溯分析路径”。 |
| “动作闭环” | 容易暗示系统可以自动执行业务动作。 | “Future Capability：动作语义和流程衔接”。 |
| “SemanticDB 负责一切” | 容易变成黑盒。 | 拆成 Catalog Store、Search、Planner、Validator、Review Queue。 |
| “LogicForm 翻译成 SQL/API/URL” | 超出当前执行能力。 | “Phase 1 只生成 SQL draft；API/URL 是 Future Capability”。 |

## 6. 建议进入后续设计的变更清单

| 优先级 | 建议 | 影响文档 | 能力分层 |
| --- | --- | --- | --- |
| P0 | 在整体设计中明确 `QuestionPlan / AnswerPlan` 是 LogicForm-like 中间表达。 | `semantic-layer-overall-design.md`, `future-capabilities-roadmap.md` | Phase 1 Scope |
| P0 | 在 Answer Composer 中强化“每个数字/字段/SQL draft 的 evidence explanation”。 | `future-capabilities-roadmap.md`, `semantic-layer-examples.md` | Phase 1 Scope |
| P1 | 在 SQL Validator 中补充 `AnswerPlan -> SQL Draft -> Validation Result` 的追溯规则。 | `future-capabilities-roadmap.md` | Phase 1 Scope |
| P1 | 在术语表中补 `LogicForm-like intermediate representation` 或把 `QuestionPlan / AnswerPlan` 描述得更像中间语义表达。 | `glossary.md` | Phase 1 Scope |
| P1 | 补充 `Time Semantics` 小节，区分简单时间范围、同比环比、业务日历和数据延迟。 | `future-capabilities-roadmap.md` | Phase 1 / Phase 2+ |
| P2 | 新增或扩展 `SemanticEvent` 设计，说明事件候选来自哪些 evidence，如何 review。 | `02-semantic-evidence-builder.md`, `future-capabilities-roadmap.md` | Phase 2+ |
| P2 | 把指标语义层和本体化语义层的关系写入总体设计，避免“只做指标字典”的误读。 | `semantic-layer-overall-design.md` | Phase 2+ |
| P3 | 动作语义、API/URL 执行、主动预警、动态报告作为 Future Capability 单独归档。 | `semantic-layer-examples.md` 或未来 roadmap | Future Capability |

## 7. 对现有模块的影响表

| 模块 | 亿问启发 | 建议调整 | 边界 |
| --- | --- | --- | --- |
| Scan Result Reader | 事实必须先被规范化，不能直接喂给模型。 | 保持读取 relation-detector 输出并归一化为 ScanBundle。 | 不调用 LLM，不生成业务语义。 |
| Semantic Evidence Builder | SemanticDB 需要事实和证据，而不是自由文本。 | 强化 evidence graph 和 compact evidence bundle。 | 只构建 evidence，不确认业务口径。 |
| LLM Semantic Enricher | 模型负责解释、归纳、扩展。 | 继续输出 SYSTEM_PROPOSED semantic objects、同义词、描述和冲突说明。 | 不能创造 physical relationship、lineage 或 BUSINESS_APPROVED metric。 |
| Semantic Catalog Store | SemanticDB 是语义资产中心。 | 继续以 semantic object、edge、evidenceRef、review decision 为核心。 | 不做黑盒大对象。 |
| Lexicon Manager | 业务词和多问法需要被长期维护。 | 结合 LLM rewrite、人工词库和 evidence-backed aliases。 | 不能只靠 embedding。 |
| Embedding Indexer | 复杂上下文不能全塞 prompt，需要召回。 | 保持向量召回 + evidence rerank。 | embedding 不是事实裁决器。 |
| Question Understanding | NL2LF 的第一步。 | 明确输出 QuestionPlan，而不是 SQL。 | LLM 可参与，但必须结构化输出。 |
| Semantic Search | SemanticDB 支持从业务词找到语义对象。 | 保持 lexicon + embedding + evidence rerank。 | 不让 LLM 重排出无 evidence 对象。 |
| Query Planner | LogicForm 需要被补全为可执行计划。 | 将候选对象、指标、时间、join path 补成 AnswerPlan。 | 不直接生成 SQL。 |
| SQL Draft Generator | LogicForm2SQL 的执行起点。 | 只从 AnswerPlan 模板生成 SQL draft。 | 不自行猜 join path 或指标方向。 |
| SQL Validator | 稳定执行前的约束层。 | 校验 catalog existence、join evidence、metric review status、read-only/draft guard。 | 不用 regex/keyword blacklist 判断 SQL 结构。 |
| Answer Composer | 结果要回到业务可读表达。 | 输出表、字段、SQL draft、证据解释、warning 和澄清问题。 | 不把 draft 写成自动执行结果。 |
| Review Queue | 业务口径需要治理。 | 将低置信度语义、冲突指标、同义词和复杂 join path 推入 review。 | BUSINESS_APPROVED 来自业务 owner 或治理流程。 |

## 8. 对我们当前设计的结论

亿问材料对本项目最重要的提醒是：真正可信的企业数据问答，不应该是 `自然语言 -> SQL` 的一次性生成，而应该是：

```text
自然语言
  -> 结构化意图
  -> evidence-grounded answer plan
  -> SQL draft
  -> validator
  -> 带证据解释的回答
```

本项目已有的优势是底层事实链更清楚：relation-detector 能从 SQL/DDL/metadata/procedure/trigger 中提取 relationship 和 lineage。后续语义层应该把这个优势继续放大，而不是把系统推向“LLM 直接猜业务”的方向。

因此推荐的设计原则是：

- relation-detector 是事实层子系统。
- Semantic Layer 是 evidence-grounded 中间层。
- `QuestionPlan / AnswerPlan` 是本项目的 LogicForm-like 中间表达。
- LLM 只做解释、归纳、扩展和规划。
- 指标语义层是重要组成，但不是语义层全部。
- `SemanticEvent`、时间语义、复杂指标拆解是 Phase 2+ 的重点。
- 动作闭环、主动预警、API/URL 执行和完整权限治理是 Future Capability。

这条路线既吸收了亿问材料里“NL2LF2SQL 和本体化语义层”的核心思想，也保留了本项目最重要的工程边界：所有语义结论必须可追溯、可审核、可解释。
