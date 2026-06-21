# 设计一致性检查报告

## 检查目标

本报告用于检查当前设计文档是否与代码实现保持一致，重点覆盖：

- core / adaptor 职责边界。
- `token-event` 与 `full-grammer` parser 模式。
- SQL / DML relationship 抽取。
- Data Lineage v1。
- DDL relationship 抽取。
- correctness fixture 与生成报告。

## 检查结论

当前主设计已经从早期 “Simple / ANTLR primary / shadow” 迁移为：

```text
parser.mode=auto|full-grammer|token-event

token-event:
  ANTLR lexer/parser support
  -> token-event structured events
  -> relationship / lineage / DDL semantic extractor

full-grammer:
  adaptor-owned versioned grammar profile
  -> typed parse-tree visitor
  -> same structured events
  -> same semantic extractor
```

代码中的主要行为与设计一致：

- MySQL/PostgreSQL 是当前一等支持目标。
- SQL Server/Oracle 仍是 future adaptor。
- core 不直接 import MySQL/PostgreSQL full-grammer 实现；版本化 module 由 adaptor 注册。
- Relationship 与 Data Lineage 是两个独立输出模型。
- Simple SQL/DDL parser 和旧 SQL/DDL parser mode 配置不再是当前能力。
- correctness fixture 以当前 parser golden 为正式基线；full-grammer parity 测试用于保证版本化 grammar 不低于 token-event fallback。

## 需要特别说明的实现事实

### 1. fallback 只发生在 parser selection 层

当 `parser.mode=auto|full-grammer` 时，如果无法根据 database type / profile / version 选中 full-grammer profile，runner 会使用 adaptor 暴露的 token-event parser，并记录 fallback 诊断。

如果 full-grammer profile 已经选中，full-grammer parser 自己返回 structured events、partial result 和 warning；它不会在 event 层委托 token-event 补齐事件。未捕获异常由 `ScanEngine` 记录当前 statement/source 失败，不会静默混合另一条 parser 链路。

### 2. SQL relationship 与 Data Lineage 当前会各 parse 一次

`ScanEngine.safeParseStatement(...)` 当前先调用 `SqlRelationParserRunner.parseStructured(...)` 给 Data Lineage 使用，再调用 `SqlRelationParserRunner.parse(...)` 生成 relationship。两次都会经过同一 parser selection 逻辑。

这是实现事实，不是设计分歧；后续可以优化为复用同一个 `StructuredParseResult`，但不应改变输出语义。

### 3. full-grammer 与 token-event 共用语义层

full-grammer 只替换事件来源，不替换语义判断。以下逻辑仍在 Java semantic layer：

- FK-like 方向归一。
- 列级 / 表级 `CO_OCCURRENCE` 判断。
- self-join 结构性列级弱共现。
- Data Lineage transform 映射和 confidence。
- DDL index / FK 事件到 relationship 的转换。

### 4. 不允许特殊名字过滤

当前设计要求 SQL/DDL/Lineage 过滤只能基于语法结构、事件类型、作用域、endpoint 类型或数据库关键字。不能因为表名或列名包含 `tmp`、`temp`、`manager_id` 等特殊字符串而改变关系/血缘结论。

临时表只能来自明确语法结构，例如 `CREATE TEMPORARY TABLE` / `CREATE TEMP TABLE`。

## 一致性检查项

### Core 与 adaptor

结果：通过。

- core 负责 parser selection、module registry、relationship merger、lineage merger、confidence、输出模型。
- adaptor 负责数据库元数据、日志/对象采集、token-event parser、versioned full-grammer module。
- MySQL `SQL_MODE` helper 只属于 MySQL full-grammer runtime，不是系统 `parser.mode`。

### Relationship 模型

结果：通过。

- `RelationType` 仍只保留 `FK_LIKE` 和 `CO_OCCURRENCE`。
- 列级弱共现使用 `RelationSubType.COLUMN_CO_OCCURRENCE` 与 `EvidenceType.SQL_LOG_COLUMN_CO_OCCURRENCE`。
- 同表不同 alias 的 self-join 允许输出列级弱共现；同 alias 行内比较不输出关系。

### Data Lineage 模型

结果：通过。

- `ScanResult` 已有独立 `dataLineages`。
- Data Lineage confidence 不参与 relationship confidence。
- v1 只输出数据库内部 `table.column -> table.column`，不做 Parameter Binding。
- `CUMULATIVE` 已作为累计/运行聚合 transform 与普通 `AGGREGATE` 区分。

### Parser 模式

结果：通过，需注意文档用词。

- 用户可见模式名是 `full-grammer` 与 `token-event`。
- Java package 使用 `fullgrammer` / `tokenevent`，因为 Java package 不能包含横线。
- `full-grammer` 具体版本实现在 adaptor，例如 `mysql.fullgrammer.v8_0`、`postgres.fullgrammer.v16`。
- 无方言或无合理版本信息时使用 token-event。

### DDL

结果：通过。

- 当前 DDL production parser 是 token-event DDL structured parser 或被 parser selection 选中的 full-grammer DDL parser。
- 两者都输出 `DDL_FOREIGN_KEY` / `DDL_INDEX` 事件。
- `DdlRelationExtractionVisitor` 只消费 DDL events，不参与 SQL relation / lineage。

### 测试资产

结果：通过。

- `CorrectnessFixtureRunnerTest` 保护当前 parser golden。
- `CorrectnessSummaryGeneratorTest` 生成轻量索引报告。
- `DataLineageAuditGeneratorTest` 维护 lineage 审核入口。
- `FullGrammerCorrectnessShadowTest` 与 `FullGrammerDdlCorrectnessShadowTest` 保护 full-grammer 不低于 token-event。

## 后续技术债

- SQL relationship 与 Data Lineage parse result 可以复用，减少同一 SQL 双 parse。
- DDL token-event path 仍有 cursor/helper 层，后续可继续向 typed parse-tree visitor 收敛。
- full-grammer profile 当前只覆盖 MySQL 8.0 与 PostgreSQL 16；新增大版本需新增 adaptor module、fixture 和 parity test。
- SQL Server / Oracle 仍需要独立 adaptor，而不是回退到 MySQL/PostgreSQL parser。
