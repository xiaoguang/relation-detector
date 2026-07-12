# Phase 5：PostgreSQL Adaptor 详细设计

## 目标

实现 PostgreSQL adaptor，使工具能够从 PostgreSQL 12+ 获取关系证据。该 adaptor 与 MySQL adaptor 具备同等能力，但遵循 PostgreSQL 的 catalog、schema、标识符和日志规则。

当前 PostgreSQL adaptor 不只负责采集，也负责 PostgreSQL 方言 parser 实现：token-event parser 位于 `com.relationdetector.postgres.tokenevent`，PostgreSQL full-grammer 公共抽象位于 `com.relationdetector.postgres.fullgrammer.common`，严格版本 profile 位于 `com.relationdetector.postgres.fullgrammer.v16`、`v17`、`v18`。core 只通过 runner 和 `FullGrammerDialectModule` registry 调度，不在 core 里 hard-code PostgreSQL 版本实现。

PostgreSQL 同时实现 SPI v3 的 `PostgresScriptParser`。它使用 generated script lexer 的 typed dollar-tag lexeme，在匹配的 `$tag$ ... $tag$` 区间内不把 semicolon 当作 statement boundary；framing 先于 SQL/DDL grammar，避免函数体被通用 splitter 截断。

PostgreSQL full-grammer 的设计目标是“按大版本严格表达官方语法边界”：

- `postgresql/16` 只证明 PostgreSQL 16.x 语法。
- `postgresql/17` 只证明 PostgreSQL 17.x 语法。
- `postgresql/18` 只证明 PostgreSQL 18.x 语法。
- 低版本不应通过 full-grammer 接受高版本专属语法；token-event 可以作为宽松 fallback 尽量兼容更多版本。

## 支持范围

- PostgreSQL 12+。
- 单 schema 扫描，默认 `public`。
- 只读元数据权限优先。
- include/exclude 表过滤。

## Identifier 规则

PostgreSQL 特点：

- 未加双引号的标识符会折叠为小写。
- 加双引号的标识符保留大小写。
- database 与 schema 是不同概念。

设计：

- adaptor 负责解析和规范化双引号标识符。
- 未引用标识符 normalized 为小写。
- SQL 中 schema 缺省时，输出 endpoint 仍保持裸表名；配置 schema 或可唯一确定的
  `search_path` 仅作为 scan 内部 `CanonicalEndpointKey` 的 namespace context，用于跨
  SQL、DDL 与 metadata 精确对齐，不回写或改名可读 endpoint。
- core 不自行处理 PostgreSQL 大小写规则。

## 元数据采集

主要读取：

- `information_schema.tables`
- `information_schema.columns`
- `information_schema.table_constraints`
- `information_schema.key_column_usage`
- `information_schema.constraint_column_usage`
- `pg_catalog.pg_class`
- `pg_catalog.pg_namespace`
- `pg_catalog.pg_attribute`
- `pg_catalog.pg_index`
- `pg_catalog.pg_constraint`

### 表和列

采集：

- table schema。
- table name。
- table type。
- column name。
- data type。
- nullable。
- character maximum length。
- numeric precision/scale。
- udt name。

### 主键和唯一约束

优先从 `pg_catalog.pg_constraint` 和 `pg_index` 获取：

- primary key。
- unique constraint。
- unique index。

用途：

- 推断 JOIN 方向。
- 生成 `TARGET_UNIQUE` evidence。

### 外键

从 `pg_constraint` 获取：

- `contype = 'f'`
- source table。
- source columns。
- referenced table。
- referenced columns。
- constraint name。

输出 evidence：

```text
METADATA_FOREIGN_KEY
relationType: FK_LIKE
relationSubType: DECLARED_FK
score: 0.98
```

## DDL 解析补丁

PostgreSQL DDL 特点：

- 双引号标识符。
- `SERIAL`、`BIGSERIAL`。
- `GENERATED ... AS IDENTITY`。
- `CREATE INDEX ... USING btree/gin/gist`。
- `ALTER TABLE ONLY ... ADD CONSTRAINT ...`。

PostgreSQL adaptor 负责：

- `postgres.tokenevent.PostgresTokenEventStructuredDdlParser` 暴露 PostgreSQL token-event DDL parser。
- `PostgresRelationSql.g4` 与 `PostgresTokenEventParseTreeVisitor` 处理 PostgreSQL DDL 方言差异，例如 `ONLY`、`NOT VALID`、`CONCURRENTLY`、`INCLUDE`、partial/expression index、opclass/collation/access method。
- `postgres.fullgrammer.v16`、`v17`、`v18` 分别注册 PostgreSQL 16/17/18 full-grammer DDL parser，用于 `parser.mode=auto|full-grammer` 且 profile 可选中时。
- 两条 DDL parser 链路都只输出 `DDL_FOREIGN_KEY` / `DDL_INDEX` 结构事件；最终 relationship 仍由 core 的 `DdlRelationExtractionVisitor` 生成。

## 版本化 full-grammer 结构

`adaptor-postgres` 采用 Template Method + Strategy + Thin Bridge：

- `postgres.fullgrammer.common`
  - parser 生命周期、syntax error 处理、warning/attributes、SQL event core、DDL event core、expression analyzer 公共逻辑；
  - 不直接依赖某个版本 generated parser class；
  - 不按表名/列名做特殊过滤。
- `postgres.fullgrammer.v16|v17|v18`
  - 独立 `.g4`、generated lexer/parser package、version binding、dialect module；
  - typed visitor 只做“从该版本 grammar context 提取结构字段并交给 common core”的薄桥接；
  - 版本差异通过 version policy/hook 表达，例如 v18 temporal constraint 结构。
- `postgres.routine`
  - 解析已确认的 PL/pgSQL dollar-quoted routine body；
  - 使用方言级 `PostgresRoutineBodySql.g4` 与 typed visitor；
  - token-event 与 full-grammer 都可调用该 routine 层；它不 import、不 new、不调用 `postgres.tokenevent` parser，避免 full-grammer 和 token-event 事件来源混用。

full-grammer module 由 `META-INF/services/com.relationdetector.core.fullgrammer.FullGrammerDialectModule` 注册。core 只按 profile 选择 module，不直接 import `v16`、`v17`、`v18` 类。

## PostgreSQL versioned correctness golden

PostgreSQL 目前有四组 correctness 资产。当前统计以 `fingerprints` 字段为准；`Rel NAMING_MATCH` 是 relationship evidence 引用数，`Top-level namingEvidence` 是独立命名证据池数量。

| 路径 | Fixture | Parser/profile | Fingerprints | 说明 |
| --- | ---: | --- | ---: | --- |
| `test-fixtures/correctness/postgres` | 111 | token-event baseline | relation 1402 / lineage 332 / Rel NAMING_MATCH 353 / top-level namingEvidence 353 | 历史兼容基线，不移动到某个大版本目录。 |
| `test-fixtures/correctness/postgres/v16` | 111 | `parserMode: full-grammer`, `grammarProfile: postgresql/16` | relation 1474 / lineage 351 / Rel NAMING_MATCH 374 / top-level namingEvidence 447 | PostgreSQL 16.x 严格语法 golden。 |
| `test-fixtures/correctness/postgres/v17` | 113 | `parserMode: full-grammer`, `grammarProfile: postgresql/17` | relation 1478 / lineage 364 / Rel NAMING_MATCH 375 / top-level namingEvidence 448 | 加入 PostgreSQL 17 专属 SQL/JSON、`JSON_TABLE`、MERGE 扩展 fixture。 |
| `test-fixtures/correctness/postgres/v18` | 114 | `parserMode: full-grammer`, `grammarProfile: postgresql/18` | relation 1477 / lineage 362 / Rel NAMING_MATCH 374 / top-level namingEvidence 447 | 加入 PostgreSQL 18 `RETURNING old/new`、virtual generated column、temporal constraint fixture。 |

每个版本目录都有自己的 `expected-relations.json` / `expected-lineage.json` / `expected-diagnostics.json`。版本目录不允许 silent fallback 到 token-event；profile 缺失、版本不匹配或 full-grammer hard failure 都应让对应 correctness 测试失败。

版本之间的差异由 `docs/parser-audit/postgres-version-golden-diff.md` 解释。低版本相对高版本缺失的 relation / evidence / lineage 必须被分类为 `EXPECTED_VERSION_GAP`、`GRAMMAR_GAP`、`SEMANTIC_GAP` 或 `REVIEW_NEEDED`。

当前对比结论：

- root token-event 与 v16/v17/v18 的共同 fixture 中，versioned full-grammer 会多识别若干 CTE / EXISTS / IN / sample-data DDL evidence 关系；这些是语法结构能解释的增强，不是名字过滤。
- v16 相对 v17 少 PostgreSQL 17 专属 fixture，并且在 `pg15` / `pg17` 一类 MERGE lineage 上少高版本增强血缘；这按当前 version golden 归为版本/visitor 能力差异。
- v17 与 v18 的共同 fixture 输出一致；差异主要来自 v17 专属 SQL/JSON/MERGE fixture 与 v18 专属 DML RETURNING / temporal DDL / virtual generated column fixture。
- 当前没有新的 `REVIEW_NEEDED` 项；如果后续出现无法用版本语法边界解释的缺失，应进入 `postgres-version-golden-diff.md`。

## 对象定义采集

支持对象：

- function。
- procedure。
- view。
- materialized view。
- rule。
- trigger function。

### 函数和过程

从 `pg_proc`、`pg_namespace` 读取：

- schema。
- name。
- kind。
- definition，可通过 `pg_get_functiondef(oid)` 获取。

权限不足：

- 记录 warning。
- 允许用户通过本地 SQL 文件补充。

### 视图

从 `pg_views` 或 `pg_get_viewdef` 读取：

- schema。
- view name。
- definition。

### 物化视图

从 `pg_matviews` 读取：

- schema。
- materialized view name。
- definition。

物化视图的定义 SQL 与普通 view 一样可能包含稳定 JOIN、CTE 和子查询，因此解析后 JOIN evidence 使用 `VIEW_JOIN`。对象类型仍保持 `MATERIALIZED_VIEW`，避免运维误以为它只是普通 view。

### Rule

从 `pg_rules` 读取：

- schema。
- table name。
- rule name。
- definition。

rule 会重写表操作，定义中可能包含 `DO ALSO` / `DO INSTEAD` 的 SQL。系统把它映射为 `DatabaseObjectType.RULE` / `StatementSourceType.RULE`，JOIN evidence 默认按 view 类定义处理。

### 触发器

从 `pg_trigger` 和 `pg_proc` 获取：

- 触发器所属表。
- 触发器函数定义。
- 触发事件。

PostgreSQL 触发器逻辑通常在函数中，因此需要把 trigger 与 trigger function 关联。

## 日志提取

支持：

- PostgreSQL `log_statement` 文本日志。
- 常见 `duration: ... statement:` 格式。
- 清洗后的纯 SQL 文本。

提取规则：

- 识别 `statement:` 后的 SQL。
- 识别 `execute <name>:` 后的 SQL。
- 跳过 connection/auth/checkpoint 等非 SQL 日志。
- 多行 SQL 按日志前缀或语句级分号归并；字符串字面量中的分号不拆分，例如 `STRING_AGG(category, '; ')` 必须保留在同一条 SQL 中。

## 数据画像查询

默认关闭，只对已有候选关系运行。

PostgreSQL 实现：

- 使用 `LIMIT` 控制采样。
- 可选 `statement_timeout` 控制超时。
- 对候选 source distinct 值与 target 列做匹配。
- 利用 `pg_stats` 作为轻量辅助，但不能只依赖统计信息得出值域包含结论。

示意：

```sql
SET LOCAL statement_timeout = '30s';

SELECT COUNT(*) matched
FROM (
  SELECT DISTINCT source_col
  FROM source_table
  WHERE source_col IS NOT NULL
  LIMIT $1
) s
JOIN target_table t ON s.source_col = t.target_col;
```

## 权重修正

PostgreSQL adaptor 可以修正：

- `log_statement` 可信度。
- view/function 定义中的 JOIN 可信度。
- quoted identifier 解析置信度。

修正后仍由 core 统一合并。

## 验收标准

- 可通过 JDBC 读取 PostgreSQL 表、列、PK、FK、unique、index。
- 可读取 function/procedure/view/materialized view/rule/trigger function 定义。
- 可从 PostgreSQL statement log 提取 SQL。
- 能正确处理 schema 和双引号标识符。
- 权限不足时产生 warning，不导致整个扫描失败。

## 测试设计

- Testcontainers PostgreSQL 12+ 集成测试。
- 显式 FK 元数据采集测试。
- unique/index 采集测试。
- quoted identifier 测试。
- view definition 采集测试。
- materialized view definition 采集测试。
- rule definition 采集测试。
- trigger function 关联测试。
- statement log 提取测试。
- 权限不足 warning 测试。
- include/exclude 过滤测试。
