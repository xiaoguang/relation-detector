# Phase 2：核心模型和评分详细设计

## 目标

建立统一领域模型、证据模型、命名证据池、关系归并规则和置信度计算规则。后续 MySQL、PostgreSQL、Oracle、SQL Server 以及外部 adaptor 都必须输出或转换成这些模型。

本阶段重点是模型正确性和评分可解释性，不依赖真实数据库。

## 核心模型

### TableId

表示一个表的稳定身份：

```java
public record TableId(
    String catalog,
    String schema,
    String tableName,
    String normalizedName
) {}
```

规则：

- `catalog` 可为空。
- MySQL 中 database 映射为 `catalog`；`schema` 通常为空。不得为了沿用两段式名字而把
  database 降级写入 schema。
- PostgreSQL 中 `schema` 通常是 `public` 或用户 schema。
- `normalizedName` 由 adaptor 按数据库规则提供，core 不自行猜测大小写。
- 物理表身份必须同时比较 catalog 和 `normalizedName`：catalog 必须同时为空，或经 adaptor
  规范化后精确相同；不能只比较 `normalizedName`，也不能把缺失 catalog 与任意显式 catalog
  自动视为相同。
- `TableId.sameIdentity()` 是模型层的严格身份判断；需要解析裸 endpoint 时，必须先通过
  `CanonicalIdentifierResolver` 和明确的 `NamespaceContext` 得到 canonical key，再做精确比较。
- catalog 与 schema 是不同身份轴。即使某个数据库产品在用户语义上把 database/schema
  混称为同一概念，adaptor 也必须选择一个稳定映射，并让 metadata、SQL endpoint、profile
  和 relationship alias 使用同一映射。

### ColumnRef

表示一个列引用：

```java
public record ColumnRef(
    TableId table,
    String columnName,
    String normalizedName,
    ColumnType type,
    boolean nullable
) {}
```

列可以在输出关系中为空，表示表级关系。

### RelationshipCandidate

候选关系：

```java
public final class RelationshipCandidate {
  private Endpoint source;
  private Endpoint target;
  private RelationType relationType;
  private RelationSubType relationSubType;
  private BigDecimal confidence;
  private List<Evidence> evidence;
  private List<Evidence> rawEvidence;
  private List<WarningMessage> warnings;
}
```

`Endpoint` 包含 table 和可空 column。parser 输出的 `TableId` / `ColumnRef` 保留 SQL 中显式
写出的 catalog、schema、quote 和标识符拼写，作为 JSON 和 evidence 的可读 endpoint；不使用
默认 schema 把裸 endpoint 改写成 schema-qualified endpoint。

当前实现状态以 `code-design-traceability.md` 为准。完整身份是目标契约，但当前仍为 `PARTIAL`：
`TableId.sameIdentity()` 尚未独立校验 schema/table 字段；MySQL qualified SQL 的两段名仍可能按
`schema.table` 解释；fact merger 仍依赖 endpoint 保存的可读 `normalizedName`；derived table bridge
遗漏 catalog。MySQL live metadata scope 已统一为 `catalog=<database>, schema=null`，但 parser、merger、
profile 与 derived 仍需使用同一 dialect-aware canonical key 才能宣称闭环。

`StatementParsePipeline` 的 known-physical inventory 与 live DDL candidate qualification 必须保留
`MetadataTableFact` / `DatabaseDdlDefinition` 的 catalog，不能重新退化为只含 schema/table 的
`TableId`。

方向规则：

- 显式 FK：子表/引用列是 source，父表/被引用列是 target。
- JOIN/谓词推断：SQL 谓词先证明两列存在结构关系；方向只能由 DDL/metadata/data-profile、unique-vs-nonunique，或 top-level `namingEvidence` 证据池中可引用的唯一 `NAMING_MATCH` 方向提示推出。
- 命名方向启发式：`customer_id -> customers.id`、`user_id -> users.id`、`parent_id -> id`、self-join 中 `manager_id -> id` 这类 `_id/id` 规则先进入独立 `NamingEvidenceCandidate` 池。relationship 只能消费这条证据并写入 `evidenceRef`，不能自己重新计算或凭空创建关系。
- 数据画像：若 A 列值域大部分包含于 B，且 B 唯一或接近唯一，则 A 为 source，B 为 target。
- 明确列等值但无法可靠判断 FK-like 方向时，输出列级 `CO_OCCURRENCE`；只有没有可靠列端点或只有表共同出现时，才退化为表级 `CO_OCCURRENCE`。

### NamingEvidenceCandidate

命名证据是独立证据池，不是 relationship fact：

```java
public record NamingEvidenceCandidate(
    Endpoint source,
    Endpoint target,
    Evidence evidence,
    String rule,
    boolean directionHint,
    List<Evidence> rawEvidence
) {}
```

规则模型：

- `NamingRuleSet` 是合并后的运行时规则集；系统默认规则和客户规则都通过同一个 `NamingRuleEngine` 执行。
- 系统默认规则保存在 classpath 资源 `naming-rules/system-default.yml`，用配置表达三类 direct 规则：`TABLE_ID`、`ID_SUFFIX_TO_ID`、`SELF_ROLE_ID`。
- 客户规则通过 YAML `namingMatch.ruleFiles` 和 inline `namingMatch.rules` 注入，当前只允许输出 `USER_CONFIGURED` direct naming evidence。客户规则支持列名 equals / equalsAny / suffix / suffixAny、目标表 aliases，以及显式 source / target endpoint pair。
- `namingMatch.systemRulesEnabled=false` 时只运行客户规则；`namingMatch.enabled=false` 时不生成 direct naming evidence。
- `TRANSITIVE_NAMING_PATH` 不允许配置；它只能由 `DerivedPathInferenceService` 在 `derivedPaths.enabled=true` 时从已有 evidence path 推导。

内置 direct 规则语义：

- `TABLE_ID`：`orders.customer_id -> customers.id` 这类列名与目标表名 stem 匹配。
- `ID_SUFFIX_TO_ID`：一侧 `_id` 指向另一侧 `id`，默认只作用于已有 structural relationship candidate。
- `SELF_ROLE_ID`：self-join 中 `employees.manager_id -> employees.id` 这类角色 id，默认只作用于已有 structural relationship candidate。

稳定 id 由代码生成：

```text
naming:<source-normalized-key>-><target-normalized-key>:<rule>
```

可读 endpoint 与 canonical endpoint key 是两个层次：

- `source` / `target`、`suggestedSourceEndpoint` / `suggestedTargetEndpoint` 用于输出、审计和
  人工阅读，保持 parser 捕获的显式拼写。
- `suggestedSourceEndpointKey` / `suggestedTargetEndpointKey` 与内部
  `CanonicalEndpointKey` 用于 scan 内的精确匹配、去重和 `NamingMatchEvidenceEnhancer` lookup；
  它们不替代 display endpoint，也不属于 naming rule 的猜测结果。
- `ScanScope` namespace 只作为 canonical key 跨 metadata、DDL、SQL source 对齐时的内部解析
  上下文。它不改变输出 endpoint；bare 与 schema-qualified endpoint 不会因同名自动等价。

输出边界：

- top-level `namingEvidence` 保存完整 grouped `evidence` 和 `rawEvidence`，以及可读 endpoint；
  用于内部匹配的 canonical key 仅以 key attribute 保存，不取代 endpoint 展示。
- relationship 里的 `NAMING_MATCH` 只保存轻量摘要和 `evidenceRef`，指向 top-level naming evidence id。
- `NamingEvidenceCandidate` 可以来自 metadata columns、DDL column inventory 或已有 SQL predicate candidate；具体来源由每条 `NamingRule.appliesTo` 控制。
- name-only hint 不能单独生成 relationship；只有同端点已有 SQL / DDL / metadata / profile relationship candidate 时，才可被 `NamingMatchEvidenceEnhancer` 消费。
- 不变量：relationship 中的 `NAMING_MATCH.evidenceRef` 必须能在 top-level `namingEvidence.id` 中找到。

### SQL 谓词关系守卫

SQL 关系只来自明确的列-列结构谓词，不来自“同一 predicate 子树里恰好出现了多个字段”。该规则同时约束 token-event 与 full-grammar，因为两者最终都产出同一组 `StructuredSqlEvent` 并交给 `StructuredRelationshipExtractor`。

允许产生 relationship event 的结构：

- `JOIN ... ON left_alias.col = right_alias.col`。
- `WHERE left_alias.col = right_alias.col`，且两侧都能解析成非 ignored 的物理 rowset 字段。
- `column IN (SELECT column FROM physical_rowset ...)`，并且事件带 `verifiedColumnSubquery=true`。
- `(col1, col2) IN (SELECT col1, col2 FROM physical_rowset ...)`，左右 tuple 数量一致，且每一项都是裸列。
- `EXISTS (...)` 子查询内部明确存在 `outer.col = inner.col` 的关联 equality。

不会产生 relationship event 的结构：

- `status = 'ACTIVE'`、`is_approved = true` 等独立 literal filter。
- `channel_type IN ('WEB', 'MOBILE_APP')` 等 literal list。
- `doc_title LIKE '%foo%'`、`SIMILAR TO`、`ESCAPE`；keyword 永远不能成为 endpoint。
- 表达式 tuple，例如 `('VER_' || major_version) IN (SELECT ...)`。
- `SELECT SUM(quantity)`、`HAVING SUM(...)`、aggregate/filter 字段与外层字段的误配对。
- 参数、JSON path、局部变量、literal 到字段的绑定；这些属于未来 Parameter Binding，不进入 v1 relationship。

这些守卫不能基于特殊表名或列名实现。合法过滤依据只有语法结构、event type、作用域、rowset 是否 ignored、endpoint 是否是物理表字段，以及数据库关键字。

### DataLineageCandidate

字段血缘是独立模型，不混入 `RelationshipCandidate`：

```java
public final class DataLineageCandidate {
  private List<Endpoint> sources;
  private Endpoint target;
  private LineageFlowKind flowKind;
  private LineageTransformType transformType;
  private BigDecimal confidence;
  private List<DataLineageEvidence> evidence;
  private List<WarningMessage> warnings;
  private Map<String, Object> attributes;
}
```

设计边界：

- v1 只输出数据库内部字段血缘，即 `table.column -> table.column`。
- 不输出 parameter、JSON path、literal、局部变量到字段的绑定；这些属于后续 Parameter Binding 模型。
- `target` 必须是物理表字段；`sources` 可以有多个物理表字段。
- `flowKind=VALUE` 表示源字段值参与目标字段写入；`flowKind=CONTROL` 表示源字段控制写入结果，例如 `CASE WHEN source.col ... THEN ...`。
- `DataLineageCandidate.confidence` 只表示血缘可信度，不参与 `RelationshipCandidate.confidence` 计算。
- `RelationshipMerger` 不处理字段血缘；字段血缘由 `DataLineageMerger` 按 `sources + target + flowKind + transformType` 去重。
- `sources` 在事实身份中是集合语义，不是 parser 遍历顺序语义。归并 key 必须先按
  canonical endpoint key 排序并去重；同一组 source 仅因 visitor 发射顺序不同，不能形成两条
  Data Lineage fact。原始发射顺序如需审计，应只保留在 observation/provenance 中。

默认血缘置信度：

| TransformType | VALUE 默认分 | CONTROL 默认分 | 说明 |
| --- | ---: | ---: | --- |
| `DIRECT` | 0.90 | 0.55 | VALUE 表示 `SET a.x = b.y`；CONTROL 表示 JOIN / WHERE / correlated locator。 |
| `AGGREGATE` | 0.80 | 0.55 | VALUE 表示聚合输入；CONTROL 表示 GROUP BY 或聚合定位列。 |
| `CUMULATIVE` | 0.80 | 0.55 | running sum、running total、CDF 这类累计聚合衍生值写入目标列。 |
| `COALESCE` | 0.75 | 0.55 | 多个字段兜底选择。 |
| `ARITHMETIC` | 0.75 | 0.55 | 加减乘除等数值表达式。 |
| `CONCAT_FORMAT` | 0.70 | 0.55 | `CONCAT`、`FORMAT`、`||`、字符串聚合等格式化。 |
| `FUNCTION_CALL` | 0.65 | 0.55 | 其它函数调用，能抽到物理字段参数。 |
| `CASE_WHEN` | 0.65 | 0.55 | THEN/ELSE 字段是 VALUE；WHEN 条件字段是 CONTROL。 |
| `WINDOW_DERIVED` | 0.50 | 0.55 | VALUE 表示窗口派生值；CONTROL 表示 PARTITION / ORDER 依赖。 |
| `UNKNOWN_EXPRESSION` | 0.35 | 0.55 | 能抽到来源字段但表达式形态不可精确分类。 |

CONTROL 候选的 confidence 固定为 `0.55`；`transformType` 表达依赖角色，不改变该默认分。当前正式角色为 CASE predicate=`CASE_WHEN`、JOIN/WHERE/correlated locator=`DIRECT`、GROUP BY/aggregate locator=`AGGREGATE`、window PARTITION/ORDER=`WINDOW_DERIVED`。CONTROL 不参与 derived lineage。

例子：

```sql
UPDATE users u
SET total_spent = COALESCE(o_summary.actual_total, 0.00)
FROM (
  SELECT user_id, SUM(pay_amount) AS actual_total
  FROM orders
  GROUP BY user_id
) o_summary
WHERE u.id = o_summary.user_id;
```

输出字段血缘：

```text
VALUE:AGGREGATE:orders.pay_amount->users.total_spent
```

注意：这不会给 `orders.user_id -> users.id` 的关系置信度加分。关系置信度仍由 JOIN/EXISTS/DDL/metadata/profile 等 evidence 计算。

## 关系类型

`RelationType`：

- `FK_LIKE`
- `CO_OCCURRENCE`

`RelationSubType`：

- `DECLARED_FK`
- `DDL_DECLARED_FK`
- `INFERRED_JOIN_FK`
- `SUBQUERY_INFERRED_FK`
- `PROFILE_SUPPORTED_FK`
- `NAMING_SUPPORTED_FK`
- `COLUMN_CO_OCCURRENCE`
- `TABLE_CO_OCCURRENCE`

多证据场景下，`relationSubType` 采用主导证据原则：

1. `DECLARED_FK`
2. `DDL_DECLARED_FK`
3. `PROFILE_SUPPORTED_FK`
4. `INFERRED_JOIN_FK`（历史/外部已定向候选保留；当前 typed SQL parser 不再仅凭 JOIN 产出）
5. `SUBQUERY_INFERRED_FK`（历史/外部已定向候选保留；当前 typed SQL parser 不再仅凭 IN/EXISTS 产出）
6. `NAMING_SUPPORTED_FK`（历史/外部已定向候选保留；当前生产 parser 不用列名形态定方向）
7. `COLUMN_CO_OCCURRENCE`
8. `TABLE_CO_OCCURRENCE`

说明：

- `relationSubType` 只表达当前关系的主要可信形态。
- 所有细节差异必须保留在 evidence 列表中。
- 数据画像可以把 JOIN/命名推断提升为 `PROFILE_SUPPORTED_FK`，但不能覆盖显式 FK。

## Evidence 模型

```java
public record Evidence(
    EvidenceType type,
    BigDecimal score,
    EvidenceSource source,
    String detail,
    Map<String, Object> attributes
) {}
```

`EvidenceType` 初始集合：

- `METADATA_FOREIGN_KEY`
- `DDL_FOREIGN_KEY`
- `VIEW_JOIN` / `PROCEDURE_JOIN` / `TRIGGER_REFERENCE`（对象定义中的谓词/引用证据；不能单独证明 FK-like 方向）
- `SQL_LOG_JOIN` / `SQL_LOG_SUBQUERY_IN` / `SQL_LOG_EXISTS`（当前 typed SQL parser 对明确 SQL 谓词保留的语法 evidence；不能单独证明 FK-like 方向）
- `SQL_LOG_COLUMN_CO_OCCURRENCE`（`RESERVED_COMPATIBILITY / NOT_PRODUCED`；泛化列级共现 evidence，用于历史/外部导入或无法保留具体谓词形态的兼容场景，当前生产 typed parser 不主动产出）
- `SQL_LOG_TABLE_CO_OCCURRENCE`（`RESERVED_COMPATIBILITY / NOT_PRODUCED`；表级共现 evidence，用于历史/外部导入或显式 opt-in 审计场景，当前生产 typed parser 不主动产出）
- `NAMING_MATCH`
- `SOURCE_INDEX`
- `TARGET_UNIQUE`
- `COLUMN_TYPE_COMPATIBLE`
- `VALUE_CONTAINMENT_HIGH`
- `VALUE_OVERLAP_HIGH`
- `NEGATIVE_VALUE_MISMATCH`
- `TRANSITIVE_PATH`
- `REPEATED_OBSERVATION`

当前实现状态：

| EvidenceType | 当前状态 | 说明 |
| --- | --- | --- |
| `METADATA_FOREIGN_KEY` | 已产出 | MySQL/PostgreSQL/Oracle/SQL Server live metadata collector 会从各自 catalog 的显式外键生成 child-to-parent relationship。 |
| `DDL_FOREIGN_KEY` | 已产出 | typed DDL parser / DDL relation extraction 会从 `CREATE TABLE`、`ALTER TABLE` 等 DDL 生成。 |
| `VIEW_JOIN` | 已产出 | typed `CREATE VIEW` / `CREATE MATERIALIZED VIEW` 查询体中的物理列谓词使用该 evidence；它保留 view 对象 provenance，但不能单独证明 FK-like 方向。 |
| `PROCEDURE_JOIN` | 未独立产出 | enum、分数、merger 和 subtype 都支持；当前 procedure/function/routine body 中的谓词仍复用 SQL predicate evidence。 |
| `TRIGGER_REFERENCE` | 已产出 | typed trigger body，以及带 `routineReturnsTrigger=true` 的 PostgreSQL trigger function 中的物理列谓词使用该 evidence；`NEW/OLD` pseudo rowset 只解析回 trigger target table，不作为独立物理 endpoint。 |
| `SQL_LOG_JOIN` | 已产出 | typed SQL parser 对 `JOIN ... ON`、comma join、`JOIN USING` 等明确列级谓词生成。 |
| `SQL_LOG_SUBQUERY_IN` | 已产出 | typed SQL parser 对 scalar / tuple `IN (SELECT ...)` 且能确认两侧都是列端点时生成。 |
| `SQL_LOG_EXISTS` | 已产出 | typed SQL parser 对 correlated `EXISTS` / `NOT EXISTS` 内部明确列级关联谓词生成。 |
| `SQL_LOG_COLUMN_CO_OCCURRENCE` | `RESERVED_COMPATIBILITY / NOT_PRODUCED` | enum、分数、merger 兼容逻辑保留；当前 typed SQL path 优先保留具体 `SQL_LOG_JOIN` / `SQL_LOG_EXISTS` / `SQL_LOG_SUBQUERY_IN`，不主动产出该泛化 evidence。 |
| `SQL_LOG_TABLE_CO_OCCURRENCE` | `RESERVED_COMPATIBILITY / NOT_PRODUCED` | enum、分数、merger 兼容逻辑保留；当前 parser 不因“同一 SQL 里出现多表但没有列谓词”自动生成正式 relationship evidence。 |
| `NAMING_MATCH` | 已产出 | `NamingEvidenceExtractor` 通过 `NamingRuleEngine` 执行系统默认 YAML 与客户 YAML 合并后的 `NamingRuleSet`，生成 top-level `namingEvidence` 池；relationship 只能通过 `evidenceRef` 引用，不能本地重算或凭空创建关系。 |
| `SOURCE_INDEX` | 已产出 | typed DDL parser / metadata enhancer 可从普通索引、FK source-side index 生成。 |
| `TARGET_UNIQUE` | 已产出 | typed DDL parser / metadata enhancer 可从 PK、unique constraint、unique index 生成。 |
| `COLUMN_TYPE_COMPATIBLE` | 已产出 | metadata enhancer 在已有关系候选上按两端 column facts 增强。 |
| `VALUE_CONTAINMENT_HIGH` | 已产出（live DB opt-in） | MySQL/PostgreSQL/Oracle/SQL Server live profiler 在 `dataProfile.enabled=true` 且候选、权限、样本和阈值 gate 满足时产出；correctness fixture 默认不依赖 live DB。 |
| `VALUE_OVERLAP_HIGH` | 已产出（live DB opt-in） | 同一 live profiler 在包含率未达强条件但 overlap 达阈值时产出；只输出统计量，不输出业务值。 |
| `NEGATIVE_VALUE_MISMATCH` | 已产出（live DB opt-in） | 负向 evidence 只在 live sample 非 partial、distinct/row 数和 missing ratio gate 满足时产出；降低 confidence，不删除显式关系。 |
| `TRANSITIVE_PATH` | 已产出（opt-in derived path） | `derivedPaths.enabled=true` 时由 `DerivedPathInferenceService` 从已定向关系、`VALUE` lineage 或 namingEvidence 图推导；默认关闭，不修改直接 relationship / lineage。 |
| `REPEATED_OBSERVATION` | 已派生产出 | 只能由 `RelationshipMerger` 在同组可重复观测 evidence 的 `count > 1` 时生成，不由 parser、metadata collector 或 profiler 直接产出。 |

兼容 evidence 与当前替代关系：

- `SQL_LOG_COLUMN_CO_OCCURRENCE` 没有被“无声抛弃”。在当前 typed parser 能识别具体 SQL 结构时，它被更可审计的语法 evidence 取代：`JOIN` / comma join / `JOIN USING` 产出 `SQL_LOG_JOIN`，correlated `EXISTS` 产出 `SQL_LOG_EXISTS`，scalar / tuple `IN (SELECT ...)` 产出 `SQL_LOG_SUBQUERY_IN`。view 查询体使用 `VIEW_JOIN`，trigger / trigger function 查询体使用 `TRIGGER_REFERENCE`；procedure/function/routine body 当前仍复用具体 SQL predicate evidence，`PROCEDURE_JOIN` 保留为兼容类型。
- `SQL_LOG_COLUMN_CO_OCCURRENCE` 只在历史结果、外部 adaptor 导入、或确实无法保留具体谓词形态的兼容输入中仍可被 merger/score 理解；生产 parser / extractor 不主动降级生成它。
- `SQL_LOG_TABLE_CO_OCCURRENCE` 对“同一 SQL 出现多张表但没有列级谓词”的场景没有现役替代 evidence。当前实现选择不产出 relationship，因为这种表级共现无法证明列关系和方向，false positive 风险高。如果 SQL 中存在明确 JOIN / EXISTS / IN 谓词，则不会走表级共现，而是产出上面的具体列级 predicate evidence。

## 归并规则

同一关系的归并 key：

```text
normalizedSourceTable
normalizedSourceColumn or "*"
normalizedTargetTable
normalizedTargetColumn or "*"
relationType
```

归并规则：

- 列级关系优先于表级关系。
- 如果已有 `orders.user_id -> users.id`，再出现 `orders -> users`，表级 evidence 可并入列级关系，但不降低列级关系精度。
- 如果只有表级关系，则保留表级关系。
- A->B 和 B->A 不自动视为相同，除非 relationType 是 `CO_OCCURRENCE` 且没有列信息。
- 先用 evidence type/source type/source/detail、file/object/statement/block/line、endpoint side、rule 和
  attributes 建立 exact observation identity；只有身份完全相同的 parser 重复事件才折叠为
  `occurrenceCount`。
- 在保留 exact raw observations 之后，用 `EvidenceType + EvidenceSourceType + source + score`
  建立用于评分/展示的 summary group。该 summary 不得把不同 SQL 位置当成同一原始观测。
- `rawEvidence` 保留归并前可区分的原始证据。不同 file/object/statement/block/line 必须分别保留；
  同一语义位置的完全重复事件可以折叠为一条并记录 `occurrenceCount`，且不得因此获得重复观测加分。
- `evidence` 保留归并后的摘要证据，用于置信度计算和常规展示。聚合后的 evidence 保留原始 score 一次，并在 `attributes.count` 中记录出现次数。
- 当 `count > 1` 时，聚合 evidence 还应记录 `firstDetail`、`lastDetail`、`sampleDetails` 和 `sampleTruncated`。`sampleDetails` 默认最多保留 5 条代表性 detail，避免日志证据爆炸。
- 对可重复观测类证据，例如 `SQL_LOG_JOIN`、`SQL_LOG_SUBQUERY_IN`、`SQL_LOG_EXISTS`、`VIEW_JOIN`、`PROCEDURE_JOIN`、`TRIGGER_REFERENCE`、`SQL_LOG_COLUMN_CO_OCCURRENCE`、`SQL_LOG_TABLE_CO_OCCURRENCE`，重复出现会额外生成一条 `REPEATED_OBSERVATION` evidence。
- `REPEATED_OBSERVATION` 是小幅排序/加固证据，不替代基础证据。它的分数使用递减增益并带绝对上限：`score = 0.10 * (1 - 1 / count)`。因此重复 2 次加 0.05，重复 3 次加 0.0667，重复 100 次加 0.099；它只会接近 0.10，永远不会达到或超过 0.10。
- conditional relationship 必须保留 typed predicate group 中的全部 discriminator guard，而不是只提升
  第一项。不同 guard 的稳定排序后结果进入 `conditions`；存在无条件 structural observation 时 summary
  按无条件处理，但条件 observations 仍保留在 raw evidence。
- grouped evidence 的顶层 attributes 只能保留所有 observations 都存在且深度相等的共识属性；
  file/statement/line/block/object 或 condition 冲突时只能留在 raw evidence，不能复制首条 provenance。

当前实现状态为 `PARTIAL`：conditional summary 仍只读取第一项扁平 guard，relationship grouped
evidence 仍使用 first observation attributes，尚未完全遵守以上两条归并契约。

## 置信度计算

### 评分原则

置信度不是“SQL 解析器有多自信”，而是“这条候选关系真实存在的概率倾向”。分数设计遵循四条原则：

1. 数据库声明强于静态文本，静态文本强于运行日志；运行 SQL 里的列等值只证明共现，不证明 FK 方向。
2. 能证明方向和列级对应的 DDL/metadata/data-profile 证据，比只能证明 SQL 列共现或表共现的证据强。
3. 辅助证据只能加固已有候选，不能单独创造关系。例如索引、唯一性、类型兼容、命名匹配都不能凭空证明外键；但“明确 SQL 谓词 + 一侧唯一、一侧非唯一”或“明确 SQL 谓词 + top-level `namingEvidence` 中唯一可引用的 `NAMING_MATCH` 方向提示”可以把方向推导为 source 指向 target。
4. 数据画像可以增强也可以反证，但默认仍低于显式约束，因为抽样数据可能不完整，历史数据也可能暂时“看起来像”外键。

基础分和设计理由：

| EvidenceType | Score | 分数为什么这样定 | 典型来源和例子 |
| --- | ---: | --- | --- |
| `METADATA_FOREIGN_KEY` | 0.98 | 数据库 catalog 明确声明外键，是最强证据。仍不设为 1.00，是为了给权限异常、跨环境迁移、已失效历史结构等极少数情况留下解释空间。 | MySQL `information_schema.KEY_COLUMN_USAGE` 或 PostgreSQL `pg_constraint` 读到 `orders.user_id -> users.id`。 |
| `DDL_FOREIGN_KEY` | 0.90 | DDL 文件声明外键也很强，但文件可能不是当前线上库的真实状态，可能来自历史 migration 或未执行脚本，所以低于 live metadata。 | `ALTER TABLE orders ADD CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users(id);` |
| `VIEW_JOIN` | 0.72 | 视图定义中的列级谓词证据通常比普通日志稳定，但它仍不单独证明 FK-like 方向。 | view body 中的 `orders.user_id = users.id`。 |
| `PROCEDURE_JOIN` | 0.70 | 存储过程中的列级谓词证据来自稳定业务逻辑，但仍需 DDL/metadata/profile/unique 方向证据才能定向。 | routine body 中的 `UPDATE ... JOIN ...`。 |
| `TRIGGER_REFERENCE` | 0.65 | 触发器引用能说明写入时的表关联或列引用，但 `NEW/OLD` 不是物理 endpoint，方向仍要靠结构证据。 | trigger body 中的物理表列等值。 |
| `SQL_LOG_JOIN` | 0.55 | SQL 明确给出 JOIN / comma join 等值谓词；它证明列级谓词关系，但没有方向证据时输出 `CO_OCCURRENCE`。 | `orders.customer_id = customers.id`。 |
| `SQL_LOG_SUBQUERY_IN` | 0.58 | `IN (SELECT ...)` / tuple IN 明确表达外层列与子查询列的谓词关系；方向仍由唯一性、DDL、metadata 或画像决定。 | `o.customer_id IN (SELECT c.id FROM customers c)`。 |
| `SQL_LOG_EXISTS` | 0.58 | correlated `EXISTS` 明确表达存在性谓词；evidence 保留 EXISTS 语法来源。EXISTS 自身不定向，但可叠加 unique、metadata、profile 或 `NAMING_MATCH` 方向证据。 | `EXISTS (SELECT 1 FROM payments p WHERE p.order_id = o.id)`。 |
| `SQL_LOG_COLUMN_CO_OCCURRENCE` | 0.40 | `RESERVED_COMPATIBILITY / NOT_PRODUCED`。泛化列级共现 evidence，仅为历史/外部导入或无法保留具体谓词语法形态的兼容场景保留；当前 typed SQL path 优先保留 `SQL_LOG_JOIN` / `SQL_LOG_EXISTS` / `SQL_LOG_SUBQUERY_IN`。 | 外部 adaptor 只能告诉 core `warehouse_inventory.product_id` 与 `order_items.product_id` 有列级共现，但不能区分 JOIN / EXISTS / IN。 |
| `SQL_LOG_TABLE_CO_OCCURRENCE` | 0.25 | `RESERVED_COMPATIBILITY / NOT_PRODUCED`。表级共现只能证明同一 SQL 中出现多个表，不能证明列级关系，也不能证明方向；当前生产 parser 不主动输出，避免报表/分析 SQL 带来 false positive。 | 外部导入或显式 opt-in 审计场景报告 `orders` 与 `users` 同 SQL 出现。 |
| `NAMING_MATCH` | 0.20 | 命名方向启发式；完整证据先进入 top-level `namingEvidence` 池，relationship 只能通过 `evidenceRef` 引用它，不能单独创建关系或本地重算。若 attributes 中的 `suggestedSourceEndpoint` / `suggestedTargetEndpoint` 唯一且匹配当前端点，可参与 FK-like 方向推导。 | `customer_id` 与 `customers.id`、`manager_id` 与 self-join alias 的 `id`。 |
| `SOURCE_INDEX` | 0.10 | 子表外键列常有索引，但索引也可能只是为了过滤或排序。只能作为辅助证据。 | `CREATE INDEX idx_orders_user_id ON orders(user_id);` |
| `TARGET_UNIQUE` | 0.18 | 被引用列通常是 PK/unique；这是比普通索引更强的方向证据，但唯一列不代表一定被引用。 | `users.id` 是 primary key。 |
| `COLUMN_TYPE_COMPATIBLE` | 0.08 | 类型一致是必要条件之一，但大量无关列都可能同类型，所以只能给很小加分。 | `orders.user_id BIGINT` 与 `users.id BIGINT`。 |
| `VALUE_CONTAINMENT_HIGH` | 0.30 | 如果 source 的非空取值几乎都存在于 target，说明“引用集合”关系很强；但抽样、软删除、分区数据会影响判断，所以低于结构证据。 | 抽样显示 `orders.user_id` 的 99.5% 值存在于 `users.id`。 |
| `VALUE_OVERLAP_HIGH` | 0.20 | 值域重合能提示关系，但不如包含率强。两个状态码、租户 id、时间片等也可能高度重合。 | `invoice.account_id` 与 `account.id` 抽样重合率高。 |
| `NEGATIVE_VALUE_MISMATCH` | -0.30 | 明显值不匹配应降低置信度，但不直接清零，因为日志/DDL/metadata 仍可能代表真实关系，只是样本不完整或数据质量差。 | 抽样发现大量 `orders.user_id` 不存在于 `users.id`。 |
| `REPEATED_OBSERVATION` | 0.00-0.10 | 同一关系被重复观测到确实比只出现一次更可信，但频率本身不能把日志 JOIN 刷成显式外键。因此它只给递减的小幅加分，并设置绝对上限。 | `SQL_LOG_JOIN` 在同一日志源中出现 3 次，额外分 `0.10 * (1 - 1 / 3) = 0.0667`。 |

正向证据合并：

```text
confidence = 1 - product(1 - evidenceScore)
```

注意：公式中的 evidenceScore 是聚合后的 evidence 分数。同一类型、同一来源、同一关系重复出现多次时，基础分只计入一次，出现次数写入 `attributes.count`。如果该 evidence 属于可重复观测类，系统再追加一条 `REPEATED_OBSERVATION`，按递减公式给少量加分。这样日志频率能表达“反复出现更值得看”，但不会把普通日志 JOIN 刷成接近显式外键的置信度。

例子：

```text
orders.user_id -> users.id
SQL_LOG_JOIN appeared 3 times in app.log
```

聚合前：

```text
SQL_LOG_JOIN = 0.55
SQL_LOG_JOIN = 0.55
SQL_LOG_JOIN = 0.55
```

聚合后：

```text
SQL_LOG_JOIN = 0.55
attributes.count = 3
attributes.firstDetail = "line 10: o.user_id = u.id"
attributes.lastDetail = "line 91: o.user_id = u.id"
attributes.sampleDetails = [
  "line 10: o.user_id = u.id",
  "line 38: o.user_id = u.id",
  "line 91: o.user_id = u.id"
]
attributes.sampleTruncated = false

REPEATED_OBSERVATION = 0.10 * (1 - 1 / 3) = 0.0667
```

最终：

```text
confidence = 1 - (1 - 0.55) * (1 - 0.0667)
           = 0.5800
```

负向证据处理：

```text
confidenceAfterNegative = positiveConfidence * product(1 + negativeScore)
```

例如 `NEGATIVE_VALUE_MISMATCH = -0.30`，则最终分数乘以 `0.70`。

边界：

- `METADATA_FOREIGN_KEY` 关系最低不低于 `0.95`。
- 最终最高封顶 `0.99`。
- 最终最低不低于 `0.0`。

### 计算例子

#### 例子 1：数据库元数据中的显式外键

SQL：

```sql
CREATE TABLE users (
  id BIGINT PRIMARY KEY
);

CREATE TABLE orders (
  id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  CONSTRAINT fk_orders_user
    FOREIGN KEY (user_id) REFERENCES users(id)
);
```

候选关系：

```text
orders.user_id -> users.id
```

证据：

```text
METADATA_FOREIGN_KEY = 0.98
```

计算：

```text
confidence = 1 - (1 - 0.98)
           = 0.98
```

合理性：这是数据库当前 catalog 明确声明的外键，应直接进入高置信区间。最终 subtype 为 `DECLARED_FK`。

#### 例子 2：DDL 文件外键，同时在日志里出现 JOIN

DDL：

```sql
ALTER TABLE orders
ADD CONSTRAINT fk_orders_user
FOREIGN KEY (user_id) REFERENCES users(id);
```

日志 SQL：

```sql
SELECT o.id, u.email
FROM orders o
JOIN users u ON o.user_id = u.id
WHERE o.created_at >= '2026-01-01';
```

证据：

```text
DDL_FOREIGN_KEY = 0.90
SQL_LOG_JOIN = 0.55
```

计算：

```text
confidence = 1 - (1 - 0.90) * (1 - 0.55)
           = 1 - 0.10 * 0.45
           = 0.9550
```

合理性：DDL 已经很强，运行日志又证明业务查询确实这样使用，所以高于单独 DDL。但因为不是 live metadata，仍保留与显式 catalog FK 的差异。

#### 例子 3：只有运行日志 JOIN，但有唯一性和命名辅助

SQL：

```sql
SELECT o.id, u.name
FROM orders o
JOIN users u ON o.user_id = u.id;
```

结构辅助：

```sql
CREATE UNIQUE INDEX pk_users_id ON users(id);
```

证据：

```text
SQL_LOG_JOIN = 0.55
TARGET_UNIQUE = 0.18
NAMING_MATCH = 0.20
```

计算：

```text
confidence = 1 - (1 - 0.55) * (1 - 0.18) * (1 - 0.20)
           = 1 - 0.45 * 0.82 * 0.80
           = 0.7048
```

合理性：这已经超过“普通共现”，因为 JOIN 条件、target 唯一性、`user_id -> users.id` 命名都一致；但它仍低于 DDL/metadata 外键，因为没有显式约束。

#### 例子 4：日志 JOIN 加数据画像支持

SQL：

```sql
SELECT o.id
FROM orders o
JOIN users u ON o.user_id = u.id;
```

数据画像结果：

```text
orders.user_id 非空样本中 99.5% 能在 users.id 找到
users.id 是唯一列
```

证据：

```text
SQL_LOG_JOIN = 0.55
TARGET_UNIQUE = 0.18
NAMING_MATCH = 0.20
VALUE_CONTAINMENT_HIGH = 0.30
```

计算：

```text
confidence = 1 - 0.45 * 0.82 * 0.80 * 0.70
           = 0.7934
```

合理性：数据画像明显增强了运行日志推断，但仍没有超过 DDL FK。这样可以让系统优先展示“强烈推断关系”，同时不会把它伪装成声明式外键。

#### 例子 5：存储过程中的 JOIN，带索引、唯一性和类型兼容

SQL：

```sql
CREATE PROCEDURE rebuild_user_order_summary()
BEGIN
  INSERT INTO user_order_summary(user_id, order_count)
  SELECT u.id, COUNT(o.id)
  FROM users u
  JOIN orders o ON o.user_id = u.id
  GROUP BY u.id;
END;
```

辅助结构：

```sql
CREATE INDEX idx_orders_user_id ON orders(user_id);
-- users.id 是 BIGINT PRIMARY KEY，orders.user_id 也是 BIGINT
```

证据：

```text
SQL_LOG_JOIN = 0.55
TARGET_UNIQUE = 0.18
SOURCE_INDEX = 0.10
COLUMN_TYPE_COMPATIBLE = 0.08
```

计算：

```text
confidence = 1 - 0.45 * 0.82 * 0.90 * 0.92
           = 0.6945
```

合理性：当前实现不会仅因对象类型是 procedure/function 就把 evidence 改成
`PROCEDURE_JOIN`；routine body 的 typed predicate 继续使用 `SQL_LOG_JOIN|EXISTS|SUBQUERY_IN`，
对象 provenance 另行保留。索引、唯一性和类型兼容用于支持方向判断。

#### 例子 6：`IN` 子查询表达引用集合

SQL：

```sql
SELECT o.id
FROM orders o
WHERE o.user_id IN (
  SELECT u.id
  FROM users u
  WHERE u.status = 'ACTIVE'
);
```

当前输出：

```text
SQL_LOG_SUBQUERY_IN = 0.58
TARGET_UNIQUE = 0.18
VALUE_CONTAINMENT_HIGH = 0.30
```

计算：

```text
confidence = 1 - 0.42 * 0.82 * 0.70
           = 0.7589
```

合理性：`IN` 子查询天然表达“外层列属于内层结果集合”，方向比普通 JOIN 更清晰；加上 target 唯一和高包含率后，是中高置信推断关系。

#### 例子 7：列级弱共现，不生成 FK-like

SQL：

```sql
SELECT *
FROM warehouse_inventory wi
JOIN order_items oi ON wi.product_id = oi.product_id;
```

证据：

```text
EvidenceType = SQL_LOG_JOIN (0.55)
RelationSubType = COLUMN_CO_OCCURRENCE
```

计算：

```text
confidence = 1 - (1 - 0.55)
           = 0.55
```

合理性：SQL 给出了明确的列等值谓词，所以保留具体 predicate evidence；但两侧都是
`product_id`，没有目标 `id`、唯一性或 metadata 证据，关系 subtype 保持列级
`COLUMN_CO_OCCURRENCE`，不能推断外键方向。兼容 enum `SQL_LOG_COLUMN_CO_OCCURRENCE` 当前不主动产出。

#### 例子 8：只有表共现，不生成列级 FK-like

SQL：

```sql
SELECT o.id, u.email
FROM orders o, users u
WHERE o.status = 'PAID'
  AND u.marketing_opt_in = TRUE;
```

当前输出：不创建 relationship，也不创建 table-level co-occurrence evidence。

合理性：两张表在同一 SQL 中出现，但没有 `o.user_id = u.id`、`IN`、`EXISTS` 或可解析的
列级条件。仅凭同语句出现不足以生成物理关系；兼容 enum `SQL_LOG_TABLE_CO_OCCURRENCE` 当前不主动产出。

#### 例子 9：强推断被数据画像反证

SQL：

```sql
SELECT o.id, u.email
FROM orders o
JOIN users u ON o.user_id = u.id;
```

数据画像结果：

```text
orders.user_id 大量非空样本不存在于 users.id
```

证据：

```text
SQL_LOG_JOIN = 0.55
TARGET_UNIQUE = 0.18
NAMING_MATCH = 0.20
NEGATIVE_VALUE_MISMATCH = -0.30
```

先合并正向证据：

```text
positiveConfidence = 1 - 0.45 * 0.82 * 0.80
                   = 0.7048
```

再应用负向证据：

```text
confidence = 0.7048 * (1 - 0.30)
           = 0.4934
```

合理性：JOIN、命名、唯一性都支持关系，但真实数据明显不匹配，最终置信度应明显下降。系统仍保留 evidence，方便人工判断是数据质量问题、软删除、跨租户过滤缺失，还是误判。

#### 例子 9：视图 JOIN

SQL：

```sql
CREATE VIEW user_order_view AS
SELECT
  o.id AS order_id,
  o.user_id,
  u.email
FROM orders o
JOIN users u ON o.user_id = u.id;
```

证据：

```text
VIEW_JOIN = 0.72
TARGET_UNIQUE = 0.18
```

计算：

```text
confidence = 1 - (1 - 0.72) * (1 - 0.18)
           = 1 - 0.28 * 0.82
           = 0.7704
```

合理性：视图是稳定数据库对象，可信度高于普通日志 JOIN；但视图也可能只是报表输出，所以不能达到 DDL FK 或 metadata FK 的级别。

#### 例子 10：触发器中的引用

SQL：

```sql
CREATE TRIGGER orders_audit_after_insert
AFTER INSERT ON orders
FOR EACH ROW
BEGIN
  INSERT INTO order_audit(order_id, user_email)
  SELECT NEW.id, u.email
  FROM users u
  WHERE u.id = NEW.user_id;
END;
```

证据：

```text
TRIGGER_REFERENCE = 0.65
TARGET_UNIQUE = 0.18
```

计算：

```text
confidence = 1 - (1 - 0.65) * (1 - 0.18)
           = 1 - 0.35 * 0.82
           = 0.7130
```

合理性：触发器证明写入链路会引用 `users.id`，但触发器也常用于审计、同步和派生数据，关系语义比 view/procedure 更间接。

#### 例子 11：`EXISTS` 子查询

SQL：

```sql
SELECT o.id
FROM orders o
WHERE EXISTS (
  SELECT 1
  FROM users u
  WHERE u.id = o.user_id
    AND u.status = 'ACTIVE'
);
```

证据：

```text
SQL_LOG_EXISTS = 0.58
TARGET_UNIQUE = 0.18
NAMING_MATCH = 0.20
```

计算：

```text
confidence = 1 - (1 - 0.58) * (1 - 0.18) * (1 - 0.20)
           = 1 - 0.42 * 0.82 * 0.80
           = 0.7245
```

合理性：`EXISTS` 表达存在性校验，方向通常比普通 JOIN 更清楚；但它仍来自 SQL 文本，不是数据库声明。

#### 例子 12：值域重合而非强包含

SQL：

```sql
SELECT i.id, a.name
FROM invoices i
JOIN accounts a ON i.account_id = a.id;
```

数据画像结果：

```text
invoices.account_id 与 accounts.id 高度重合，但样本中仍有一批历史 account_id 不存在于 accounts.id
```

证据：

```text
SQL_LOG_JOIN = 0.55
TARGET_UNIQUE = 0.18
VALUE_OVERLAP_HIGH = 0.20
```

计算：

```text
confidence = 1 - (1 - 0.55) * (1 - 0.18) * (1 - 0.20)
           = 1 - 0.45 * 0.82 * 0.80
           = 0.7048
```

合理性：值域重合支持关系存在，但不如“source 几乎完全包含于 target”强。它适合给 JOIN 推断加固，而不是单独把关系推到高置信。

## 可解释性要求

最终输出不能只给分数，必须包含：

- `rawEvidence`：原始证据审计轨迹；不同语义位置分别记录，同一位置的完全重复事件可以用
  `occurrenceCount` 折叠。
- `evidence`：归并后的摘要证据，包含计数、样本 detail 和用于评分的 evidence item。
- top-level `namingEvidence`：完整命名证据池，包含稳定 `id`、grouped `evidence` 和 `rawEvidence`。
- relationship 中的 `NAMING_MATCH` evidence：只保存 `evidenceRef` 和方向摘要，不重复完整 raw observations。
- `derivedRelationships` / `derivedDataLineages`：开启 `derivedPaths` 后的推导视图，必须保存完整 path、`TRANSITIVE_PATH` grouped evidence 和 raw observations；它们不是直接物理事实。derived relationship 内部按 referenced-by 反向遍历，但输出 source/target 保持 FK-like 正向，并用 `traversalPath` 保留内部遍历顺序。
- derived `namingEvidence`：使用 `rule=TRANSITIVE_NAMING_PATH`，仍然在 top-level `namingEvidence` 池中输出，并带 `derived=true`、`path`、`pathEvidenceRefs`。JSON 另有只读轻量数组 `derivedNamingEvidence` 便于统计和阅读，但它不是第二套证据池，不包含 `rawEvidence` / grouped `evidence`；完整证据必须按 id 回查 top-level `namingEvidence`。
- 每个 evidence 的 type。
- 每个 evidence 的 score。
- evidence 来源，例如 `metadata`、`ddl-file`、`mysql-slow-log`。
- 简短 detail。
- 可选 attributes，例如出现次数、样本行数、包含率。

## 验收标准

- 多 evidence 可归并成一条关系。
- 显式 FK 不会被弱证据降级为普通推断关系。
- 命名证据不能单独生成高置信 FK。
- 负向数据画像能降低最终分数。
- 无法确定方向的 JOIN 不生成方向错误的列级 FK-like。

## 测试设计

- `METADATA_FOREIGN_KEY` 单证据输出 0.98。
- `SQL_LOG_JOIN + TARGET_UNIQUE + NAMING_MATCH` 分数高于单独 `SQL_LOG_JOIN`。
- `NEGATIVE_VALUE_MISMATCH` 降低最终分数。
- `DECLARED_FK + SQL_LOG_JOIN` 的 subtype 仍是 `DECLARED_FK`。
- `JOIN a.x = b.y` 且两侧都非 unique、命名无提示时退化为 `CO_OCCURRENCE`。
