# Phase 5：PostgreSQL Adaptor 详细设计

## 目标

实现 PostgreSQL adaptor，使工具能够从 PostgreSQL 12+ 获取关系证据，并遵循 PostgreSQL 的
catalog、schema、标识符和日志规则。当前 parser/sample-data 与 live metadata、object、database-DDL
代码契约均已落地；真实 PostgreSQL 权限、版本与 catalog 组合仍通过环境性 integration smoke 验证。

当前 PostgreSQL adaptor 不只负责采集，也负责 PostgreSQL 方言 parser 实现：token-event parser 位于 `com.relationdetector.postgres.tokenevent`，PostgreSQL full-grammar 公共抽象位于 `com.relationdetector.postgres.fullgrammar.common`，严格版本 profile 位于 `com.relationdetector.postgres.fullgrammar.v16`、`v17`、`v18`。core 只通过 runner 和 `FullGrammarDialectModule` registry 调度，不在 core 里 hard-code PostgreSQL 版本实现。

PostgreSQL 同时实现当前 SPI v5 的 `PostgresScriptFramer`。它使用 generated script lexer 的 typed dollar-tag lexeme，在匹配的 `$tag$ ... $tag$` 区间内不把 semicolon 当作 statement boundary；framing 先于 SQL/DDL grammar，避免函数体被通用 splitter 截断。

PostgreSQL full-grammar 的设计目标是“按大版本严格表达官方语法边界”：

- `postgresql/16` 只证明 PostgreSQL 16.x 语法。
- `postgresql/17` 只证明 PostgreSQL 17.x 语法。
- `postgresql/18` 只证明 PostgreSQL 18.x 语法。
- 低版本不应通过 full-grammar 接受高版本专属语法；token-event 可以作为宽松 fallback 尽量兼容更多版本。

## 支持范围

- PostgreSQL 12+。
- 单 schema 扫描，默认 `public`。
- 只读元数据权限优先。
- include/exclude 表过滤。

## 当前 live 实现边界

- `PostgresMetadataCollector` 从 `pg_catalog` 读取 table/column、PK/UNIQUE/FK 和 index inventory，
  并从显式 FK 生成 child-to-parent relationship。组合 constraint/index 保留 ordinal 和完整列组。
- `PostgresObjectCollector` 通过 `pg_get_functiondef` 返回完整 function/procedure declaration；view、
  materialized view 和 rule 返回数据库提供的 query/rule definition；non-internal trigger 通过
  `pg_trigger` / `pg_get_triggerdef` 返回完整 trigger declaration。trigger function 仍作为独立 function
  definition 采集；当前输出不额外创造 trigger-to-function 物理 relationship。mapper 在创建
  `DatabaseObjectDefinition` 前拒绝 null/blank definition，并通过统一 live diagnostic contract
  输出带 object context 的 `DEFINITION_UNAVAILABLE`，空定义不会进入 statement assembly。
- metadata、object 与 database-DDL 共用 `PostgresNamespaceResolver`：显式 catalog/schema 优先，
  否则使用 connection catalog 和默认 `public` schema。当前 collector 的 catalog SQL 在当前 JDBC
  connection database 上执行；显式 catalog 必须在任何系统表查询前与 `Connection.getCatalog()`
  按 PostgreSQL identifier rules 规范化比较。不同或无法读取 connection catalog 时扫描直接失败，
  不执行隐式跨 database 查询，也不会给当前库结果贴上另一个 catalog。
- `PostgresDatabaseDdlCollector` 输出关系解析用 structural table skeleton。组合 FK 通过
  `pg_constraint` 的 `conkey/confkey WITH ORDINALITY` 配对；骨架不承诺保留完整 type modifier、default、
  identity/generated/collation，因此不能作为数据库回放 DDL 使用。
- `PostgresDataProfiler` 已执行 exact aggregate query，独立返回 source non-null rows、source distinct、
  matched distinct 和 target distinct。

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

本节描述当前生产契约。`METADATA` capability 由 table/column、PK/UNIQUE/FK、index inventory、
显式 FK relationship 和 partial-success warning 的 contract tests 共同证明；capability/preflight 本身
不能替代这些内容测试。

主要读取：

- `pg_catalog.pg_class`
- `pg_catalog.pg_namespace`
- `pg_catalog.pg_attribute`
- `pg_catalog.pg_type`
- `pg_catalog.pg_attrdef`
- `pg_catalog.pg_index`
- `pg_catalog.pg_constraint`

`information_schema.tables/columns` 只用于 `PostgresDatabaseDdlCollector` 生成 relationship parser
消费的 structural table skeleton，不是 `PostgresMetadataCollector` 的 inventory 真源。

### 表和列

采集：

- table schema。
- table name。
- table type。
- column name。
- `pg_type.typname` 形式的 data type。
- `format_type(...)` 形式的完整 column type。
- nullable。
- default expression。
- identity/generated 标志。
- ordinal position。

当前 metadata record 不单独拆分 character maximum length、numeric precision/scale 或 UDT
字段；这些 type modifier 仅可能作为 `columnType` 文本的一部分保留。

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
- `postgres.fullgrammar.v16`、`v17`、`v18` 分别注册 PostgreSQL 16/17/18 full-grammar DDL parser，用于 `parser.mode=auto|full-grammar` 且 profile 可选中时。
- 两条 DDL parser 链路都输出 `DDL_FOREIGN_KEY` / `DDL_INDEX` / `DDL_COLUMN` 结构事件；最终 relationship
  仍由 core 的 `DdlRelationExtractionVisitor` 生成，`DDL_COLUMN` 只补充 inventory/naming evidence。

## 版本化 full-grammar 结构

`adaptor-postgres` 采用 Template Method + Strategy + Thin Bridge：

- `postgres.fullgrammar.common`
  - parser 生命周期、syntax error 处理、warning/attributes、SQL event core、DDL event core、expression analyzer 公共逻辑；
  - 不直接依赖某个版本 generated parser class；
  - 不按表名/列名做特殊过滤。
- `postgres.fullgrammar.v16|v17|v18`
  - 分别消费 `relation-detector/grammar/postgres-v16|v17|v18` 生成的 lexer/parser artifact；
    adaptor version package 只保留 binding、typed adapter、visitor 和 version policy；
  - typed visitor 只做“从该版本 grammar context 提取结构字段并交给 common core”的薄桥接；
  - 版本差异通过 version policy/hook 表达，例如 v18 temporal constraint 结构。
- `postgres.routine` 与 `postgres.plpgsql.tokenevent|v16|v17|v18`
  - outer grammar typed 提取对象名、`LANGUAGE`、string body或 `BEGIN ATOMIC` statement context与起始行；
  - `postgres.routine` 是 adaptor 内的 descriptor、dispatcher、provenance 和 shared shell support package，不是 grammar module；
  - token-event 使用独立 grammar artifact `postgres-plpgsql-token-event` 和 package `postgres.plpgsql.tokenevent`，静态 SQL 只回调 PostgreSQL token-event parser；
  - v16/v17/v18 full profile 分别使用 grammar artifact `plpgsql-v16/v17/v18` 和同版本 `postgres.plpgsql.v16|v17|v18` package，静态 SQL 只回调同版本 full parser；
  - string body缺省 `LANGUAGE` 输出 missing-language diagnostic；`BEGIN ATOMIC` 缺省 language按 SQL处理；
  - 两条路径只共享 sealed body descriptor、provenance 和无状态 helper，禁止互相 delegate；dynamic `EXECUTE` 只产生 unresolved diagnostic。

full-grammar module 由 `META-INF/services/com.relationdetector.core.fullgrammar.FullGrammarDialectModule` 注册。core 只按 profile 选择 module，不直接 import `v16`、`v17`、`v18` 类。

## PostgreSQL versioned correctness golden

PostgreSQL 有 root token-event baseline 和 v16/v17/v18 三组 strict full-grammar correctness 资产。
当前 fixture 与 fingerprint 数量只维护在
[`correctness-test-summary.md`](../../generated/correctness-test-summary.md)；本 Phase 文档不复制当前计数。
v17 额外覆盖 SQL/JSON、`JSON_TABLE` 和 MERGE 扩展，v18 额外覆盖 DML `RETURNING old/new`、
virtual generated column 与 temporal constraint。

每个版本目录都有自己的 `expected-relations.json` / `expected-lineage.json` / `expected-diagnostics.json`。版本目录不允许 silent fallback 到 token-event；profile 缺失、版本不匹配或 full-grammar hard failure 都应让对应 correctness 测试失败。

版本之间的差异由 `docs/parser-audit/postgres-version-golden-diff.md` 解释。低版本相对高版本缺失的 relation / evidence / lineage 必须被分类为 `EXPECTED_VERSION_GAP`、`GRAMMAR_GAP`、`SEMANTIC_GAP` 或 `REVIEW_NEEDED`。

当前对比结论：

- root token-event 与 v18 full-grammar 对同一 natural sample-data 产出相同 direct facts 和 semantic observations；per-fixture correctness 总量不同，是因为 versioned 目录包含独立版本边界 fixture，不能按汇总数量强行对齐。
- v16 相对 v17 少 PostgreSQL 17 专属 fixture；版本差异继续按 SQL 资产和官方语法边界审计，而不是按 parser 总数判断。
- v17 与 v18 的共同业务 fixture 输出一致；目录总量差异来自各自的 SQL/JSON、MERGE、DML RETURNING、temporal DDL 和 virtual generated-column fixture。
- PostgreSQL 18 的 `UPDATE ... RETURNING old/new` 不再阻断非平凡 self-update assignment；`sales_amount -> sales_amount` 的 `VALUE/ARITHMETIC` lineage 已进入 golden。
- token-event 与 v16/v17/v18 full parser 都从 typed `CREATE TEMP[TORARY] TABLE` 产生本地 rowset declaration，并保留后续 `INSERT ... SELECT` 与 `IN (SELECT ...)` 事件。只有唯一的 `VALUE/DIRECT` 投影链可以折叠成底层物理 relationship；最终 relationship、naming 和 derived 输出不得包含临时 endpoint。
- 当前没有新的 `REVIEW_NEEDED` 项；如果后续出现无法用版本语法边界解释的缺失，应进入 `postgres-version-golden-diff.md`。

## 对象定义采集

支持对象：

- function。
- procedure。
- view。
- materialized view。
- rule。
- non-internal trigger。
- trigger function（作为独立 function definition）。

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

rule 会重写表操作，定义中可能包含 `DO ALSO` / `DO INSTEAD` 的 SQL。系统把它映射为
`DatabaseObjectType.RULE` / `StatementSourceType.RULE` 并保留对象 provenance；当前只有 VIEW /
MATERIALIZED_VIEW 查询体使用 `VIEW_JOIN`，rule predicate 继续使用具体
`SQL_LOG_JOIN|SQL_LOG_EXISTS|SQL_LOG_SUBQUERY_IN`。

### 触发器

从 `pg_trigger` 和 `pg_proc` 获取：

- 触发器所属表。
- `pg_get_triggerdef` 返回的 trigger declaration。
- `pg_get_functiondef` 返回的独立 trigger function declaration。

PostgreSQL 触发器逻辑通常在函数中。production `PostgresObjectCollector` 同时采集 non-internal trigger
的 `pg_get_triggerdef` 和函数的 `pg_get_functiondef`，两者保留各自 object identity 与 catalog/schema。
当前不把 trigger-to-function 调用关系提升为物理 relationship；如后续需要该语义，必须由 typed
object dependency 模型表达，不能通过对象名猜测。

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

PostgreSQL 当前实现：

- 对已经筛选出的候选执行 exact aggregate query，不使用 `LIMIT` 采样替代真实分母。
- 由 JDBC query timeout 控制单候选超时。
- 独立测量 source non-null rows、source distinct、matched distinct 和 target distinct。
- `sampleRows` / `maxDistinctValues` 只保留给未来离线样本模式，不限制 live exact query。

当前实现状态为 `PARTIAL`：metadata endpoint 会保留 connection database catalog，但通用
`IdentifierQuoter` 会把它渲染成 PostgreSQL 不支持的 `catalog.schema.table` 三段引用；renderer 必须
只渲染 `schema.table`，同时单独校验 request catalog 与 connection catalog。仅启用 profile、关闭其它
live collector 时目前也会绕过 `PostgresNamespaceResolver` 的 catalog 校验。

示意：

```sql
SELECT (SELECT COUNT(*) FROM source_table s WHERE s.source_col IS NOT NULL) AS source_non_null_rows,
       (SELECT COUNT(DISTINCT s.source_col) FROM source_table s
          WHERE s.source_col IS NOT NULL) AS source_distinct,
       (SELECT COUNT(DISTINCT s.source_col) FROM source_table s
          WHERE s.source_col IS NOT NULL
            AND EXISTS (SELECT 1 FROM target_table t
                        WHERE t.target_col = s.source_col)) AS matched_distinct,
       (SELECT COUNT(DISTINCT t.target_col) FROM target_table t
          WHERE t.target_col IS NOT NULL) AS target_distinct;
```

## 权重修正

PostgreSQL adaptor 可以修正：

- `log_statement` 可信度。
- view/function 定义中的 JOIN 可信度。
- quoted identifier 解析置信度。

修正后仍由 core 统一合并。

## 验收标准

以下代码契约已由 fake-JDBC/contract tests 覆盖；真实数据库权限与版本组合仍需环境性 smoke：

- 可通过 JDBC 读取 PostgreSQL 表、列、PK、FK、unique、index。
- 可读取 function/procedure/view/materialized view/rule/trigger 定义。
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
