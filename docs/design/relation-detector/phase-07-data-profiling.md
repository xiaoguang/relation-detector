# Phase 7：可选数据画像详细设计

## 目标

数据画像用于回答一个问题：在已经有结构、SQL、DDL、命名或对象定义线索的前提下，真实数据是否支持或反驳这条候选关系。

本阶段重点设计三类 evidence：

- `VALUE_CONTAINMENT_HIGH`：source 列的非空取值高度包含于 target 列，强支持“source 引用 target”。
- `VALUE_OVERLAP_HIGH`：两列取值有较高重合，弱支持两列有关。
- `NEGATIVE_VALUE_MISMATCH`：对非条件的 DDL/metadata 声明 FK，live 数据明显不匹配时降低置信度；不用于普通 SQL/naming 推断。

当前代码事实：

- live DB profiling 已实现为显式 opt-in 能力：`dataProfile.enabled=true` 且有 JDBC connection 时，MySQL、PostgreSQL、Oracle、SQL Server profiler 都会对受限候选执行 exact aggregate query。
- live query 独立返回 source non-null rows、source distinct、matched distinct 和 target distinct；
  `VALUE_CONTAINMENT_HIGH`、`VALUE_OVERLAP_HIGH` 与 `NEGATIVE_VALUE_MISMATCH` 都使用真实数据库统计，
  不再用 source distinct 代填其它指标。
- 候选生成不会做全库列两两比较；只选择已有结构候选，或在 `discoverFromNamingEvidence=true` 时选择 top-level `namingEvidence` + target unique + type compatible 的命名候选。
- profiling 只支持 live JDBC exact aggregate query。未实现的离线 `INSERT` 样本配置已从 runtime、SPI v6、示例和文档中删除；YAML transport 仅保留同名拒绝哨兵，旧字段会明确报配置错误。

实现复核边界：内置四方言 profiler 通过 `DataProfileEvidenceBuilder` 执行下述负向 policy；core 的
`ProfileOutcomeContractValidator` 会在 `DataProfilePipeline` 修改 candidate 前原子校验全部外部 SPI
outcome。只有 `SUCCESS` 可携带非空 evidence 且不能携带 warning；evidence 只能是三类 data-profile evidence；
`NEGATIVE_VALUE_MISMATCH` 还必须重新通过 core-owned declared-FK policy。conditional/polymorphic
判断同时读取 candidate summary、structural evidence 和 raw evidence attributes。因此“全局只允许
非条件声明 FK 产生负向 evidence”在代码和 focused contract tests 中已经闭环。真实数据库 driver、
权限与 optimizer 组合仍属于环境性 smoke 边界，不能由 fake-JDBC 测试替代。failure outcome 不得携带
evidence；plugin warning 的 message/source/attributes 不受信任，core 只按已验证 status、adaptor id 与
candidate endpoints 重建固定脱敏 warning。全部 bounded outcomes 通过后才统一写入，违规时无部分修改。

## 设计原则

- 默认不读取业务数据，必须显式开启。
- 不做全库任意列两两比较；画像只服务候选关系或强约束候选。
- 数据画像只能产生 evidence，不能覆盖显式 FK。
- 数据画像可以增强关系，也可以反证关系，但反证不能直接删除关系。
- 负向 policy 必须由 core SPI consumer 最终执行；不能只信任内置或外部 adaptor 返回的 evidence type。
- 不输出真实业务值，也不输出可逆 hash；只输出 exact aggregate 统计量、阈值、统计规模和跳过原因。
- live 查询受候选数量、每个 source 的 target 数、超时和权限控制。
- 生产库读权限不足时降级为 skip/warning，不影响静态关系抽取。
  `DataProfiler` 返回 `ProfileOutcome`，区分 success、no evidence、invalid endpoint、permission denied、timeout 和 query failed；`DataProfilePipeline` 验证全批 outcome 后由 core 重建 warning。
- 外部 `ProfileOutcome` 的 warning 只能在 core 校验 status 对应 type/code 后，由 core 重建固定安全消息进入
  scan result；plugin message、source、attributes、SQL、URL 或业务值不会进入结果。

### 标识符渲染边界

画像查询只能由方言 renderer 渲染标识符，不能拼接裸文本或把整个 qualified name 当成一个
identifier quote。renderer 必须同时遵守产品可引用 namespace：MySQL 可用 `catalog.table`，SQL Server
可用 `[catalog].[schema].[table]`；PostgreSQL 只能引用 `schema.table`，connection database catalog
只能用于真实性校验，不能渲染成第三段；Oracle 使用 `owner.table`。因此不能由一个通用 quoter
无条件输出所有非空组件。

```text
dialectTableReference(TableId) + "." + quote(column)
```

缺失 namespace 的组件不输出，但已有组件的原始大小写和拼写不应被画像层改写。若输入
已使用本方言的 quote，renderer 保留它；其他方言 quote 会先按单个组件去壳，再用当前方言
quote，避免把点号、catalog 或 schema 包进同一对 quote。

## 输入场景

### 1. 只有 SQL / DDL / 对象定义，没有数据库连接

系统只能使用静态证据：

- DDL FK / PK / UNIQUE / INDEX。
- DDL column inventory。
- view / procedure / trigger / query 中的 JOIN / EXISTS / IN。
- top-level `namingEvidence`。

此时不产生任何 data-profile evidence。`INSERT ... VALUES` 仍可作为 SQL 资产被 parser 接受，但不进入数据画像。

### 2. 有数据库连接，但没有业务数据读取权限

如果只能读取 catalog / metadata，不能读取业务表：

- 继续产出 metadata / DDL / naming / SQL predicate evidence。
- 不产出 `VALUE_CONTAINMENT_HIGH`、`VALUE_OVERLAP_HIGH`、`NEGATIVE_VALUE_MISMATCH`。
- 输出 warning 或 info：`DATA_PROFILE_SKIPPED_NO_PERMISSION`，attributes 包含 table/column 和失败摘要，不包含 SQL 参数或敏感信息。

### 3. 有数据库连接，并且有只读数据权限

这是完整数据画像路径。

系统可以对候选关系执行受限聚合查询，但仍不能扫描任意列组合。查询只返回 count / ratio，不返回值。

### 4. 混合输入

常见情况是：

- metadata 来自 live DB。
- DDL 来自文件。
- SQL/object definitions 来自仓库。
- 数据画像来自 live DB。

只有 live JDBC profiler 产生数据画像 evidence；文件输入只提供结构、SQL、DDL、对象和命名证据。

## 配置

```yaml
sources:
  dataProfile:
    enabled: false
    timeoutSeconds: 30
    maxCandidatePairs: 1000
    maxTargetsPerSourceColumn: 3
    minContainmentRatio: 0.98
    minOverlapRatio: 0.80
    maxMismatchRatio: 0.50
    minDistinctValues: 20
    minRowsForNegative: 100
    verifyDeclaredForeignKeys: false
    discoverFromNamingEvidence: false
    skipUnindexedLargeTargets: true
```

字段说明：

- `enabled`：默认 false。关闭时不查询业务数据。
- `timeoutSeconds`：单个画像查询最大执行时间。
- `maxCandidatePairs`：本次扫描最多画像的候选关系数量。
- `maxTargetsPerSourceColumn`：同一个 source column 最多尝试几个 target。
- `minContainmentRatio`：产生 `VALUE_CONTAINMENT_HIGH` 的阈值。
- `minOverlapRatio`：产生 `VALUE_OVERLAP_HIGH` 的阈值。
- `maxMismatchRatio`：产生 `NEGATIVE_VALUE_MISMATCH` 的 missing ratio 阈值。
- `minDistinctValues`：正向强包含 evidence 的最小 distinct 样本规模。
- `minRowsForNegative`：负向 evidence 的最小 source 非空行样本规模。
- `verifyDeclaredForeignKeys`：是否验证显式 FK，默认 false。开启后即使发现 mismatch，也只降低 confidence 并输出 evidence，不删除 FK。
- `discoverFromNamingEvidence`：是否允许从 top-level namingEvidence + profile 发现新关系，默认 false。
- `skipUnindexedLargeTargets`：当 metadata 中存在 index facts 且 target column 无可见索引时跳过，避免昂贵 target lookup。

SPI v6 只暴露上述 live profiling 字段。旧的离线字段不会被忽略：YAML loader 在 transport 边界检测到它们时返回明确配置错误，且不会构造 runtime `DataProfileOptions`。

尚未实现但可后续加入的预算字段：

- `mode`：当前等价于 `CANDIDATES_ONLY`；未来可增加显式 `DISCOVERY_ASSISTED`。
- `maxCandidatesPerTable`：当前已有 `maxCandidatePairs` 和 `maxTargetsPerSourceColumn`，尚未按 table 维度限流。
- `emitSkippedCandidates`：当前 permission、timeout 和 SQL failure 已通过安全 warning 输出，
  invalid endpoint 和 no-evidence 仍不单独形成逐候选 skip item。未来可在不泄露 SQL 或业务值的
  前提下增加显式 skipped-candidate inventory。

## 候选生成：避免全库列组合扫描

核心约束：不能对每一列两两查数据。画像候选必须来自便宜、结构化、可解释的前置线索。

### 候选分层

#### A. 确定关系候选

这些候选已经是 relationship candidate，画像只是增强或反证：

- `METADATA_FOREIGN_KEY`
- `DDL_FOREIGN_KEY`
- `SQL_LOG_JOIN`
- `SQL_LOG_EXISTS`
- `SQL_LOG_SUBQUERY_IN`
- 已独立产出的 `VIEW_JOIN` / `TRIGGER_REFERENCE`，以及兼容输入中的 `PROCEDURE_JOIN`
- relationship 中引用了 top-level `NAMING_MATCH` 的候选

默认只对 A 层画像。

#### B. 强结构候选

这些候选还不一定是 relationship，但有足够便宜的结构线索，可以在 `discoverFromNamingEvidence=true` 时进入画像队列：

- source column 与 target PK/UNIQUE column 类型兼容。
- top-level `namingEvidence` 给出唯一方向，例如 `orders.customer_id -> customers.id`。
- target 端有单列 `TARGET_UNIQUE`，source 端有单列 `SOURCE_INDEX` 或同表/同 schema 业务上下文。
  组合 PK/UNIQUE/index 是列组事实：`UNIQUE(a,b)` 不能证明 `a` 或 `b` 单列唯一。
  普通组合索引 `(a,b)` 只允许物理首列 `a` 作为 `SOURCE_INDEX` / profiling lookup 支持；
  非首列 `b` 不能单独通过 gate，且首列索引也不代表单列唯一、不能单独决定 relationship 方向。
- DDL column inventory 或 metadata 显示两端都存在，且不属于临时表、pseudo rowset、参数或局部变量。

B 层不能仅靠命名生成 relationship。只有画像产生 `VALUE_CONTAINMENT_HIGH`，并且 target unique / type compatible 等结构 evidence 同时存在时，才可生成 `PROFILE_SUPPORTED_FK` 候选。

#### C. 弱候选

这些默认不画像：

- 纯表级共现。
- 只有相同列名，例如两个表都有 `status`、`type`、`tenant_id`。
- 没有 target unique / PK / naming direction 的任意同类型列。
- JSON/CLOB/TEXT/BLOB/XML 等大对象列。
- 高变动时间戳、金额、描述、备注列。

如果未来要支持 C 层，必须是显式 opt-in 审计模式，并有更低预算。

### 候选剪枝

画像前先做零成本或低成本剪枝：

- 表/列必须存在于 metadata 或 DDL column inventory。
- source 和 target 必须都是物理列 endpoint。
- 排除临时表、CTE、derived table、`NEW/OLD`、`EXCLUDED`、局部变量、参数。
- 类型族必须兼容，例如 numeric-to-numeric、string-to-string、uuid-to-uuid。
- target 优先必须是 PK/UNIQUE 或近似唯一候选。
- source 与 target 不能是同一个 endpoint。
- 单表自关联必须有 role 或列差异，例如 `manager_id -> id`。
- 如果 target 是大表且 target 列无索引，并且没有显式 FK/DDL 强证据，则跳过。

### 候选排序

当候选超过预算时，按以下优先级排序：

1. 显式 SQL predicate + target unique。
2. DDL/metadata FK 且 `verifyDeclaredForeignKeys=true`。
3. SQL predicate + `NAMING_MATCH`。
4. procedure/view/trigger 中反复出现的 predicate。
5. namingEvidence + target unique + source index。
6. 其它 B 层强结构候选。

## Live DB 画像指标

### 当前 metrics 完整性

四个 dialect renderer 都返回 `source_non_null_rows`、`source_distinct`、`matched_distinct` 和
`target_distinct`。`JdbcDataProfilerTemplate.metrics(...)` 直接写入 `DataProfileMetrics`，不代填指标。

对每个候选 `sourceTable.sourceColumn -> targetTable.targetColumn`，计算：

```text
sourceNonNullRows
sourceDistinctValues
matchedDistinctSourceValues
missingDistinctSourceValues
targetDistinctValues
containmentRatio = matchedDistinctSourceValues / sourceDistinctValues
missingRatio = missingDistinctSourceValues / sourceDistinctValues
overlapRatio = matchedDistinctSourceValues / min(sourceDistinctValues, targetDistinctValues)
```

当前没有总行数指标，因此不输出 `sourceNullRatio`。若未来需要该指标，必须新增独立
`sourceTotalRows` 测量，不能由 `sourceNonNullRows` 推导。

### `VALUE_CONTAINMENT_HIGH`

目标安全条件：

- `sourceDistinctValues >= minDistinctValues`
- `containmentRatio >= minContainmentRatio`
- source/target 类型兼容
- target 有 PK/UNIQUE，或候选本身来自 SQL/DDL/metadata 强证据
- 查询未超时，样本未被权限错误截断

输出：

```text
EvidenceType: VALUE_CONTAINMENT_HIGH
EvidenceSourceType: DATA_PROFILE
score: 0.30
attributes:
  profileMode: LIVE_DATABASE
  containmentRatio: 0.995
  matchedDistinctSourceValues: 995
  sourceDistinctValues: 1000
  sourceNonNullRows: 5000
  targetDistinctValues: 1200
  minContainmentRatio: 0.98
  minDistinctValues: 20
  targetUnique: true
  queryTimedOut: false
```

### `VALUE_OVERLAP_HIGH`

产生条件：

- `matchedDistinctSourceValues > 0`
- `overlapRatio >= minOverlapRatio`
- 没达到 `VALUE_CONTAINMENT_HIGH` 的强条件，或 target 唯一性不足

输出较弱 evidence，用于提示值域有关，但不能强定向。

### `NEGATIVE_VALUE_MISMATCH`

负向 evidence 风险更高，必须更保守。

产生条件：

- 候选必须携带 `DDL_FOREIGN_KEY` 或 `METADATA_FOREIGN_KEY` 声明证据。
- source/target 都是物理列 endpoint。
- 候选不是 conditional 或 polymorphic relationship。
- metrics 来自 `LIVE_DATABASE` exact query。
- `sourceDistinctValues >= minDistinctValues`
- `sourceNonNullRows >= minRowsForNegative`
- `missingRatio >= maxMismatchRatio`
- 对显式 FK 默认不验证；只有 `verifyDeclaredForeignKeys=true` 时才输出。

SQL JOIN、EXISTS、IN、routine/trigger、naming-only、conditional 和 polymorphic 候选仍可获得
`VALUE_CONTAINMENT_HIGH` / `VALUE_OVERLAP_HIGH`，但不会获得负向证据。这个边界避免在没有 tenant、
软删除、时间窗口、归档或权限行过滤上下文时，把普通推断关系的自然缺失解释成反证。

输出：

```text
EvidenceType: NEGATIVE_VALUE_MISMATCH
EvidenceSourceType: DATA_PROFILE
score: -0.30
attributes:
  profileMode: LIVE_DATABASE
  missingRatio: 0.72
  missingDistinctSourceValues: 720
  sourceDistinctValues: 1000
  sourceNonNullRows: 5000
  maxMismatchRatio: 0.50
  negativePolicy: DECLARED_FOREIGN_KEY_ONLY
```

## 查询策略

### 通用逻辑

所有方言都渲染为只读 exact aggregate query，返回统计量，不返回值。

伪 SQL：

```sql
SELECT
  (SELECT COUNT(*) FROM source_table WHERE source_col IS NOT NULL) AS source_non_null_rows,
  (SELECT COUNT(DISTINCT source_col) FROM source_table WHERE source_col IS NOT NULL) AS source_distinct,
  (SELECT COUNT(DISTINCT s.source_col)
     FROM source_table s JOIN target_table t ON t.target_col = s.source_col
    WHERE s.source_col IS NOT NULL) AS matched_distinct,
  (SELECT COUNT(DISTINCT target_col) FROM target_table WHERE target_col IS NOT NULL) AS target_distinct;
```

方言只在 identifier quoting、Oracle `FROM DUAL` 和 SQL Server `COUNT_BIG` 上不同；live query
不使用 `LIMIT`、`FETCH FIRST`、`TOP` 或随机采样。查询必须受 JDBC timeout 保护。

### 批处理优化

为了避免候选多时一条一条慢查询，可以做两层优化：

- 同一 source table/source column 对多个 target 的候选，可在未来显式设计 batch query；当前不缓存或
  materialize 跨候选中间结果。
- 同一 target table/target column 被多个 source 使用时，可在未来复用 target distinct；当前逐候选
  query 会重复测量该值，只复用已有 metadata/unique/index facts。

当前逐候选执行，并保留 `maxCandidatePairs` 和 timeout。

## 与 relationship / namingEvidence 的关系

数据画像不是 parser，不发现 SQL 结构，也不重新计算 naming match。

流程：

```text
parse SQL / DDL / metadata
  -> relationships + DDL_COLUMN inventory + namingEvidence
  -> DataProfileCandidateGenerator 选择少量候选
  -> DataProfiler 执行 live DB profile
  -> evidence 合并到已有 relationship 或生成 profile-supported candidate
  -> RelationshipMerger 统一评分
```

约束：

- `NAMING_MATCH` 仍由 top-level `namingEvidence` 单一来源提供。
- 数据画像可以消费 namingEvidence 来排序候选，但不能自己生成 NAMING_MATCH。
- 默认不允许纯 profile 创建 relationship。
- 如果开启 `discoverFromNamingEvidence=true`，也必须满足 naming + unique/type + high containment，不允许全列扫描发现关系。

## 与评分模型关系

画像 evidence 进入 Phase 2 的统一评分模型：

- 增强：`VALUE_CONTAINMENT_HIGH`、`VALUE_OVERLAP_HIGH`。
- 反证：`NEGATIVE_VALUE_MISMATCH`。

subtype 规则：

- `METADATA_FOREIGN_KEY` 保持 `DECLARED_FK`。
- `DDL_FOREIGN_KEY` 保持 `DDL_DECLARED_FK`。
- SQL predicate / naming / unique 候选叠加 `VALUE_CONTAINMENT_HIGH` 后可变成 `PROFILE_SUPPORTED_FK`。
- `NEGATIVE_VALUE_MISMATCH` 降低 confidence，但不删除关系，不改变 explicit FK subtype。

## 安全与权限

安全要求：

- 不输出真实值。
- 不输出 SQL 参数值。
- 不记录完整 profiling SQL，除非 debug 模式且脱敏。
- 权限错误只记录 table/column 和错误类型。
- 默认不读取业务数据。

权限策略：

- 如果连接不可用：skip。
- 如果 catalog 可读但数据不可读：skip profile，保留 metadata。
- 如果部分表可读：只 profile 可读候选；权限失败通过 `PROFILE_PERMISSION_DENIED` warning 保留。
- 如果查询超时：通过 `PROFILE_QUERY_TIMEOUT` warning 记录该候选，不影响整体 scan。
- 其它 SQL failure 通过 `PROFILE_QUERY_FAILED` warning 保留。`ProfileOutcome` 同时区分
  `SUCCESS`、`NO_EVIDENCE`、`SKIPPED_INVALID_ENDPOINT`、permission、timeout 和 query failure；
  warning attributes 只携带 endpoint、SQLState、vendor code、exception class 和 renderer source，
  不回传真实业务值、rendered SQL、driver message、连接串或参数值。
- `JdbcDataProfilerTemplate` 使用固定安全消息，不调用 `SQLException.getMessage()`。共享分类器的
  方言中立规则只识别 authorization exception 和 SQLState `28xxx`/`42501`；Oracle profiler
  由调用方额外传入 vendor code 1031，SQL Server profiler 额外传入 229/916。普通 live
  collector 也由 adaptor 的 `permissionDeniedVendorCodes()` 传入自己的 vendor policy；方言 code 不在全局默认集合。fake JDBC 单测证明契约与脱敏边界；具体 driver/version 的
  vendor code 行为仍属于环境性 live smoke，文档不得把单测写成真实数据库验证。

## 性能控制

live 模式必须有硬上限：

- `maxCandidatePairs`
- `maxTargetsPerSourceColumn`
- `timeoutSeconds`

`maxCandidatesPerTable` 仍是后续预算字段，不能在“已实现硬上限”中列为现状。

复杂度目标：

```text
O(profiledCandidatePairs)
```

而不是：

```text
O(allTables * allColumns * allTables * allColumns)
```

任何实现如果绕过候选生成器做全库列组合扫描，都应被架构测试禁止。

## 组件设计

当前组件：

- `DataProfileCandidateGenerator`
  - 输入 relationships、namingEvidence、metadata/DDL column inventory。
  - 输出受候选预算约束的 profile candidates。
- `DataProfilePipeline`
  - 在 production scan 中复用 relationship candidates、metadata、top-level naming evidence，选择预算内 live DB profile 候选。
  - 作为 SPI consumer 重验 `ProfileOutcome`：只接受三类 profile evidence，failure/skip status 不接受
    evidence，并对负向 evidence 重新执行 core-owned policy。
- `DataProfileEvidenceBuilder`
  - 根据 metrics 和阈值生成 `VALUE_CONTAINMENT_HIGH`、`VALUE_OVERLAP_HIGH`、`NEGATIVE_VALUE_MISMATCH`。
- `MySqlDataProfiler` / `PostgresDataProfiler` / `OracleDataProfiler` / `SqlServerDataProfiler`
  - 分别渲染受限聚合 SQL，只返回 count / ratio 所需指标，不返回真实业务值。

live profiling 的 namespace 已按方言闭环：MySQL 渲染 catalog/table，PostgreSQL 渲染
schema/table，Oracle 渲染 owner/table，SQL Server 渲染 catalog/schema/table。`ScanEngine` 在任何
live source（包括 profile-only）之前解析并验证 executable scope；`DataProfileNamespacePolicy` 再对
candidate endpoint 做防御性校验。PostgreSQL 异库 candidate 和 Oracle 带 catalog candidate 会在 SQL
渲染前拒绝，不会通过省略 namespace 轴误查同名表。

SPI 演进：

当前接口：

```java
ProfileOutcome profile(Connection connection, ProfileRequest request);
```

如后续确有测量证据需要 batch，可另行设计：

```java
default List<ProfileOutcome> profileBatch(Connection connection, List<ProfileRequest> requests) {
  return requests.stream().map(r -> profile(connection, r)).toList();
}
```

当前生产接口仍逐候选执行；仓库没有 batch profiling SPI，也不应把建议写成已实现能力。

`ProfileOutcome` 不是 adaptor 的任意 evidence 注入口。生产契约只允许
`VALUE_CONTAINMENT_HIGH`、`VALUE_OVERLAP_HIGH`、`NEGATIVE_VALUE_MISMATCH`；只有 `SUCCESS`
可以携带非空 evidence。core 必须在 `DataProfilePipeline` 再验证一次，避免第三方 v6 adaptor 绕过
candidate selection、conditional guard 或负向 evidence policy。当前 `ProfileOutcomeContractValidator` 在
写入 warning 或 candidate 前原子校验全部 outcome 的 status、evidence allowlist、`DATA_PROFILE` source type 和负向策略；
pre-merge conditional/polymorphic 判断同时读取 candidate、structural evidence 与 raw evidence attributes。

## 测试设计

### 单元测试

- candidate generator 不做全列组合扫描。
- namingEvidence + target unique + type compatible 能进入 B 层候选。
- 纯同名 `status/type/code` 不进入画像候选。
- containment ratio 达阈值产生 `VALUE_CONTAINMENT_HIGH`。
- overlap 达阈值但 containment 不达阈值产生 `VALUE_OVERLAP_HIGH`。
- 只有非条件声明式 FK 满足 negative gates 时产生 `NEGATIVE_VALUE_MISMATCH`。
- SQL/naming/conditional/polymorphic 候选即使缺失率高也不产生负向证据，但正向 evidence 不受影响。
- 样本太小、权限失败、超时不产生误导 evidence。

### 集成测试

- MySQL/PostgreSQL/Oracle/SQL Server live profiler 使用 fake JDBC result测试四项 exact metrics、query timeout、权限分类、
  warning脱敏与containment/overlap/mismatch结果装配；这证明renderer/JDBC contract，不替代真实数据库
  optimizer、权限和版本组合的runtime smoke。
- correctness fixture 不默认依赖 live DB；live profiler 使用独立 JDBC contract tests。

### 性能测试

- 构造 100 张表、1000 列 metadata，验证 candidate 数量受上限控制。
- 构造大量 namingEvidence，验证 `maxTargetsPerSourceColumn` 生效。
- 构造超时 profiler，验证整体 scan 不失败。

### JSON 验收

`rawEvidence` 和 `evidence` 中只出现统计指标：

```json
{
  "type": "VALUE_CONTAINMENT_HIGH",
  "sourceType": "DATA_PROFILE",
  "score": 0.30,
  "attributes": {
    "profileMode": "LIVE_DATABASE",
    "containmentRatio": 0.995,
    "matchedDistinctSourceValues": 995,
    "sourceDistinctValues": 1000,
    "sourceNonNullRows": 5000,
    "targetDistinctValues": 1200,
    "minContainmentRatio": 0.98
  }
}
```

不得出现真实业务值数组。

## 验收标准

- 默认配置不执行任何业务数据查询。
- 开启画像后只对预算内 candidates 查询。
- 不做全库列组合扫描。
- 有 live data 权限时，高值域包含率能产生 `VALUE_CONTAINMENT_HIGH`。
- 非条件声明式 FK 明显值不匹配且满足负向 gate 时能产生 `NEGATIVE_VALUE_MISMATCH`。
- 普通 SQL、naming、conditional 和 polymorphic 候选不产生负向 evidence。
- 权限不足、超时、样本不足都不会中断整体 scan；permission/timeout/query-failure 会产生
  `ProfileOutcome` warning。warning message 使用固定安全文本，Oracle profiler 传入 1031、
  SQL Server profiler 传入 229/916，并与共享 SQLState 分类一起受契约测试保护；真实
  driver/version 行为仍是环境性 runtime 验收项。
- 输出不包含真实业务值。
- correctness/golden 暂不覆盖 live DB profiling；profiler 用独立单测验收。
