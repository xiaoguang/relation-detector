# Grammar、Script Framing 与 Routine Parser 架构迁移设计

## 1. 文档目的

本文固化 relation-detector 的 grammar 所有权、client script framing、PostgreSQL
PL/pgSQL 嵌套解析、版本包命名和构建文件治理规则，作为后续实施计划的唯一需求输入。

本文同时记录目标状态和实施状态。grammar 所有权、Script Framer、SPI v4、正确 Grammar
拼写和版本 package 迁移已经完成；PL/pgSQL 过程外壳、`BEGIN ATOMIC`、缺省 `LANGUAGE`、
`FOREACH`、来源审计和 source-tree hygiene 已按本文收口。后续变更仍必须先建立架构测试，
不得通过兼容桥恢复旧结构。

## 2. 已确认决策

- 所有 ANTLR `.g4` 文件统一由 `relation-detector/grammar/` 下的 Maven module 持有。
- adaptor 只持有 parser binding、typed context adapter、visitor/collector、profile module、
  version policy 和 framing 的 Java 调用入口。
- client script grammar 只负责 statement framing，不负责 SQL 事实推断。
- PostgreSQL 的 PL/pgSQL 是独立过程语言。full-grammar profile 必须按数据库版本严格解析，
  其中的静态 SQL 必须回调同版本 PostgreSQL full grammar parser。
- PostgreSQL token-event 使用独立、version-neutral 的 compact PL/pgSQL token-event grammar；
  其中的静态 SQL 只能回调 PostgreSQL token-event parser，不能调用 versioned full grammar。
- MySQL、Oracle 和 SQL Server 的 routine body 已由各自 SQL/过程 grammar 直接覆盖时，
  不为追求目录对称而增加第二套 routine parser。
- 版本由 Maven artifact、Java package、profile 和 policy 表达；同一版本 package 内的类名
  不重复携带版本号。
- 全仓历史拼写错误必须一次性修正为 `Grammar`、`grammar`、`full-grammar`。这是破坏性迁移：
  不保留旧 Java 类型、package、目录、YAML 值、CLI 值、profile id、fixture id、脚本参数或
  deprecated alias。
- parser 行为、JSON 事实语义、confidence、NAMING_MATCH 单一来源以及 token-event/full
  grammar 相互独立等既有边界保持不变。

### 2.1 实施状态

| 范围 | 状态 | 代码门禁 |
| --- | --- | --- |
| Grammar module 所有权与 README 归位 | 已实施 | `GrammarOwnershipArchitectureTest` |
| Script Framer 与 SPI v4 | 已实施 | framing/SPI architecture tests |
| PostgreSQL PL/pgSQL 过程外壳与 current-mode SQL callback | 已实施 | 四套 `Postgres*PlPgSqlParserTest` |
| sealed routine body、缺省 LANGUAGE、BEGIN ATOMIC | 已实施 | `PostgresRoutineLanguageDispatcherTest`、outer routine tests |
| FOREACH、partial recovery、dynamic SQL diagnostic | 已实施 | 四套 PL/pgSQL contract tests |
| 正确 Grammar 拼写和 version package 命名 | 已实施 | terminology architecture tests |
| 空目录与确认死代码治理 | 已实施 | `SourceTreeHygieneTest`、reachability audit script |

这里的“已实施”表示源码和自动化门禁已经落地；每次发布仍须通过 correctness、parser matrix、
sample-data CLI 和 no-cache clean build，不能仅凭本表判断构建通过。

## 3. 目标模块结构

```text
relation-detector/
  grammar/
    common-token-event/
    common-script/
    mysql-token-event/
    mysql-script/
    mysql-v5_7/
    mysql-v8_0/
    postgres-token-event/
    postgres-plpgsql-token-event/
    postgres-script/
    postgres-v16/
    postgres-v17/
    postgres-v18/
    plpgsql-v16/
    plpgsql-v17/
    plpgsql-v18/
    oracle-token-event/
    oracle-script/
    oracle-v12c/
    oracle-v19c/
    oracle-v21c/
    oracle-v26ai/
    sqlserver-token-event/
    sqlserver-script/
    sqlserver-v2016/
    sqlserver-v2017/
    sqlserver-v2019/
    sqlserver-v2022/
    sqlserver-v2025/
  adaptor-mysql/
  adaptor-postgres/
  adaptor-oracle/
  adaptor-sqlserver/
```

每个 grammar module 持有：

- `.g4` 源文件。
- grammar 所需的 lexer/parser base class。
- 生成源所需的 ANTLR Maven 配置。
- grammar 来源、版本、派生方式、能力边界和已知 gap 的 README。

每个 adaptor 持有：

- generated parser binding。
- typed context adapter。
- statement、rowset、predicate、projection、write、DDL 和 routine collector。
- parser profile module 与 version policy。
- script framer 的 Java facade。

adaptor 不再持有 `.g4`、generated source 或只为旧 grammar 路径占位的 README。

## 4. 四十二项修改要求

### 4.1 Grammar 所有权与 README

1. **迁移全部 grammar**：将 versioned full grammar、compact token-event grammar、client
   script lexer grammar、compact PostgreSQL PL/pgSQL token-event grammar 和 versioned
   PostgreSQL PL/pgSQL grammar 全部迁入 `relation-detector/grammar/`。

2. **压缩 adaptor 职责**：adaptor 只保留 binding、typed context adapter、visitor、
   collector、profile module、version policy 和 Java framing 入口，不再拥有 grammar 源文件。

3. **清理 stale README**：将 adaptor 下描述 full grammar 的 README 移到对应 grammar
   module，删除迁移后只剩 README 的旧目录；README 中的路径必须指向真实模块。

4. **采用明确的 grammar module 矩阵**：common、四种方言的 token-event、四种方言的
   script grammar、所有 versioned full grammar、PostgreSQL compact PL/pgSQL token-event
   grammar 和三套 versioned PL/pgSQL grammar 均为独立 Maven artifact，不把不同模式或
   不同版本的生成代码合并到同一 module。

### 4.2 Client Script Framing

5. **保留 client script lexer**：它负责识别 MySQL `DELIMITER`、PostgreSQL dollar quote、
   Oracle `/` 和 SQL Server `GO`，并正确忽略字符串或注释里的伪分隔符。

6. **限制 script grammar 职责**：小型 script `.g4` 只建立 server statement 的字符区间、
   起止行和 block 边界，不生成 relationship、lineage、naming evidence 或 SQL 结构事件。

7. **按实际职责重命名**：将 script 层所有 `*Parser`、`*ParseRequest`、`*ParseResult`
   分别改为 `*Framer`、`*FrameRequest`、`*FrameResult`，避免与 SQL parser 混淆。

8. **升级 adaptor SPI**：script framing 类型或 grouped capability 发生二进制变化时提升
   SPI major version。旧插件必须被明确拒绝并提示重新编译，不保留双 SPI 或反射兼容桥。

### 4.3 PostgreSQL PL/pgSQL 架构

9. **承认 PostgreSQL 的嵌套语言边界**：outer PostgreSQL token-event/full grammar负责
   函数声明、string body 和 typed `BEGIN ATOMIC` SQL body；PL/pgSQL string body必须交给
   与当前 parser mode 对应的第二阶段 typed parser。

10. **禁止用 SQL grammar 解析整个 PL/pgSQL body**：`DECLARE`、控制流、异常、赋值和
    `RAISE` 等过程结构由 PL/pgSQL grammar 解析，不能作为 unknown SQL 静默跳过。

11. **按 `LANGUAGE` 和 parser mode typed dispatch**：`plpgsql`、`sql`、SQL-standard
    `BEGIN ATOMIC` 和其他过程语言走明确分支；每个分支还必须保持当前 token-event 或
    full-grammar mode。非 SQL/PL/pgSQL 语言不生成 SQL 事实，并输出可审计的 unsupported
    diagnostic 或交给显式插件。

12. **嵌套静态 SQL 回调同模式 parser**：PL/pgSQL parser 只解析过程外壳。token-event
    PL/pgSQL 中的静态 SQL 只能回调 PostgreSQL token-event parser；versioned PL/pgSQL 中的
    静态 SQL 只能回调与 outer profile 相同的 PostgreSQL v16、v17 或 v18 full grammar parser。

13. **三版过程能力职责一致**：v16/v17/v18 的 PL/pgSQL parser 都必须覆盖声明、条件、循环、
    异常和静态 SQL dispatch；版本差异只由官方语法能力决定，不能因实现遗漏形成差异。

14. **建立独立 PL/pgSQL artifact**：新增 version-neutral 的
    `postgres-plpgsql-token-event`，以及 `plpgsql-v16`、`plpgsql-v17` 和 `plpgsql-v18`。
    compact token-event grammar 不声称版本严格；三套 full profile grammar 不再由单一共享
    routine grammar 假装覆盖所有版本。

15. **在 grammar 层表达版本边界**：例如不同版本对 `MERGE ... RETURNING` 的支持差异必须
    落在对应 PL/pgSQL/static SQL grammar、typed adapter 或 version capability 中，不能用
    Java SQL 文本 blacklist 实现。

16. **复用当前模式的 SQL grammar**：不下载或复制第二套 PostgreSQL full SQL G4；versioned
    PL/pgSQL 内部 SQL 统一复用现有 v16/v17/v18 parser artifact。compact PL/pgSQL
    token-event 内部 SQL 复用 PostgreSQL token-event parser，不依赖任何 versioned artifact。

17. **采用官方 PostgreSQL 源码作为 PL/pgSQL source of truth**：每个版本固定官方 tag/commit
    下的 `src/pl/plpgsql/src/pl_gram.y` 和 `pl_scanner.c`，由其派生 versioned ANTLR grammar；
    compact token-event grammar 只能覆盖这些官方版本的已确认共同子集，不能自行发明宽松语法。

18. **记录可复现来源**：每个 PL/pgSQL module README 必须与 G4 位于同一 Maven module，
    并记录官方 tag、commit、源文件、派生规则、已支持 statement family、版本差异和已知 gap。

19. **禁止构建期下载 grammar**：官方来源在开发阶段审核并 vendor 到仓库；普通 Maven 构建
    必须离线可复现。

### 4.4 MySQL Routine 边界

20. **保持 MySQL 单阶段 routine 解析**：MySQL versioned grammar 直接解析
    `CREATE PROCEDURE/FUNCTION/TRIGGER/EVENT` 及其 compound statement body。

21. **覆盖 MySQL 过程语句**：`IF/ELSEIF/ELSE/END IF`、`LOOP`、`WHILE`、`REPEAT`、
    `DECLARE`、handler 和内部静态 SQL 均由同一版本 grammar 的 typed context 处理。

22. **不追求伪对称**：不为 MySQL 新增独立 routine grammar；只有语言本身存在 opaque
    nested body 时才使用第二阶段 parser。

### 4.5 Version Package 命名

23. **删除类名中的重复版本**：当版本已经出现在 Maven artifact 和 Java package 中，类名
    只描述职责，不再包含 `16`、`17`、`18`、`57`、`80`、`12c`、`26ai`、`2016` 等版本片段。

24. **统一 PostgreSQL 类名**：在 `...v16` 等 package 内使用
    `PostgresFullGrammarLexer`、`PostgresFullGrammarParser`、`PostgresParseTreeAdapter`、
    `PlPgSqlShellCollector` 和 `FullGrammarBinding`。shell collector 只消费本版本 generated
    typed context并产出静态 SQL source span，不承担 SQL 结构分析。

25. **统一 MySQL 类名**：各版本 package 内使用 `MySqlParseTreeAdapter`、
    `FullGrammarDialectModule` 等无重复版本的名称。

26. **统一 Oracle 类名**：v12c/v19c/v21c/v26ai package 内统一使用
    `OracleParseTreeAdapter` 等职责名。

27. **统一 SQL Server 类名**：v2016/v2017/v2019/v2022/v2025 package 内统一使用
    `SqlServerParseTreeAdapter`、`FullGrammarDialectModule` 等职责名。

28. **只在版本身份层保留版本**：Maven artifact id、Java package、grammar module 路径、
    profile id、version policy 和 fixture 路径必须继续保留版本。

### 4.6 历史误导命名

29. **重命名关系 extractor**：将同时消费 token-event/full grammar structured event 的
    `TokenEventRelationExtractor` 改为 `StructuredRelationshipExtractor`。

30. **重命名 relationship parser wrapper**：将可包装任意 `StructuredSqlParser` 的
    `TokenEventSqlRelationParser` 改为 `StructuredSqlRelationshipParser`。

31. **显式使用 PL/pgSQL 命名**：PostgreSQL routine grammar、parser facade、visitor 和
    context adapter 必须使用 `PlPgSql`，不能用泛化的 routine-body 名称隐藏语言类型。

32. **删除无生产用途的 sink**：确认 `FullGrammarEventSink` 无生产引用后删除，不为测试或
    历史 API 保留空壳。

33. **按职责重命名 typed sink**：承担多个 collector 编排的 typed sink 改为
    `FullGrammarEventFacade` 或经代码审计确认的等价职责名；真正的 sink 只负责事件记录。

34. **统一 binding 名称**：version package 内统一使用 `FullGrammarBinding`，不再并存
    `*VersionBinding` 与普通 binding 两套命名。

### 4.7 Grammar 拼写一次性迁移

35. **一次性修正所有内部符号**：Java class/interface/record/enum、method/field、package、
    import、文件名、目录名、Maven artifact/module、ANTLR package、generated output path 和
    test name 全部使用正确的 `Grammar` / `grammar` 拼写。

36. **一次性修正所有外部契约**：YAML、CLI 参数和值、environment/property 名、profile id、
    manifest、fixture id、warning/result name、JSON 中若存在的 parser 标识、脚本和文档全部改为
    `full-grammar` 或对应正确形式。不接受旧值，不提供迁移 alias，不输出 deprecated warning；
    使用旧值必须作为未知配置直接失败。

37. **建立零遗留门禁**：仓库级架构测试扫描错误拼写的大小写与连接符变体，生产、测试、
    fixture、脚本和文档均必须为零。迁移提交本身也不能保留旧类型 shim、旧 package forwarding
    class、旧 YAML compatibility branch 或旧路径 README。

### 4.8 Lexer Base 命名

38. **统一 Java 大小写风格**：将 lexer base 类统一为 `MySqlLexerBase`、
    `PostgresLexerBase` 等项目约定形式。

39. **同步 ANTLR superclass 配置**：重命名 lexer base 时同步修改 `.g4` 中的 `superClass`、
    package、imports、生成配置和相关测试，禁止只改 Java 文件名。

### 4.9 Maven 与本地生成文件

40. **提交项目级 Maven 配置**：保留并版本化 `.mvn/extensions.xml`、
    `.mvn/maven-build-cache-config.xml` 和 `.mvn/maven.config`，因为它们属于可复现构建契约。

41. **忽略 Maven 本地缓存**：使用 `**/.mvn/build-cache/` 排除生成缓存，仓库中不得跟踪
    cache JAR、buildinfo、临时索引或机器相关路径。

42. **隔离 IDE 私有配置**：团队未明确维护共享 VS Code 配置时，将 `.vscode/settings.json`
    加入 ignore；grammar/parser 迁移提交不得夹带个人 IDE 设置。

## 5. 目标数据流

```text
client script text
  -> dialect ScriptFramer
  -> server SqlStatementRecord + absolute source range
  -> token-event parser OR selected version full grammar parser
  -> typed StructuredSqlEvent
  -> relationship / lineage / DDL / naming evidence pipeline
```

PostgreSQL routine 的附加路径分为两条独立 parser-mode 链路：

```text
CREATE FUNCTION/PROCEDURE
  -> typed LANGUAGE + parser-mode dispatch
      |
      +-- token-event mode
      |     -> LANGUAGE plpgsql
      |     |     -> compact PL/pgSQL token-event parser
      |     |     -> embedded static SQL
      |     |           -> PostgreSQL token-event parser
      |     -> LANGUAGE sql string body
      |     |     -> PostgreSQL token-event parser
      |     -> BEGIN ATOMIC
      |     |     -> outer PostgreSQL token-event parser
      |     -> other language
      |           -> explicit unsupported diagnostic or registered plugin
      |
      +-- full-grammar mode
            -> selected version PostgreSQL full grammar
            -> LANGUAGE plpgsql
            |     -> same-version PL/pgSQL parser
            |     -> embedded static SQL
            |           -> same-version PostgreSQL full grammar parser
            -> LANGUAGE sql string body
            |     -> same-version PostgreSQL full grammar parser
            -> BEGIN ATOMIC
            |     -> outer versioned PostgreSQL grammar
            -> other language
                  -> explicit unsupported diagnostic or registered plugin
```

两条路径只能共享 typed event model、source provenance、VALUE/CONTROL 语义和无状态 helper。
compact PL/pgSQL token-event parser、versioned PL/pgSQL parser、PostgreSQL token-event parser
和 versioned PostgreSQL full grammar parser均不得相互包装或委托。

Routine body 的分发规则固定如下：string body 只有显式 `LANGUAGE plpgsql` 或
`LANGUAGE sql` 才可解析；缺少 language 时输出 `POSTGRES_ROUTINE_LANGUAGE_MISSING`。
typed `BEGIN ATOMIC` body在缺少 language时按 SQL处理，显式非 SQL language则输出
`POSTGRES_ROUTINE_BODY_LANGUAGE_MISMATCH`。atomic body的 producer必须来自 outer grammar
typed context，不能搜索 raw SQL文本。

## 6. 明确不做的事情

- 不让 script framer 推断 SQL relationship、lineage 或 naming evidence。
- 不让 PL/pgSQL grammar 复制一套 PostgreSQL full SQL grammar。
- 不让 compact PL/pgSQL token-event parser 调用 versioned full grammar，也不让 versioned
  PL/pgSQL parser 调用 PostgreSQL token-event parser。
- 不为 MySQL、Oracle、SQL Server 强行增加 PostgreSQL 风格的二阶段 routine parser。
- 不合并不同数据库版本的 generated lexer/parser。
- 不让 token-event 与 full grammar 相互 delegate。
- 不通过 regex、scanner、token span、grammar rule-name 字符串、反射 context 或名称白名单
  弥补 typed grammar gap。
- 不保留任何错误拼写的源码、配置或兼容入口。
- 不借命名迁移改变 relationship、lineage、naming evidence、confidence 或 JSON 事实语义。

## 7. 实施分组建议

为降低一次大提交的诊断难度，实施计划应拆为以下可独立验收的工作包；但第 6 组拼写迁移
必须在一个工作包中一次完成，不能跨版本长期共存。

1. Grammar ownership 与 stale README 清理。
2. Script grammar module 化、ScriptFramer 重命名和 SPI 升级。
3. PostgreSQL compact/versioned PL/pgSQL parser、按 mode 的 LANGUAGE dispatch 与同模式
   static SQL callback。
4. Version package 类名去重和 typed adapter 命名统一。
5. 历史 `TokenEvent*`、sink、binding、lexer base 名称清理。
6. 全仓正确 Grammar 拼写的原子迁移。
7. 架构门禁、full correctness、19 parser matrix 和全部 sample-data CLI 验收。

拼写迁移既可以作为第一组执行，以便后续代码全部使用正确名称；也可以作为最后一个纯机械
工作包执行，以减少与 PL/pgSQL 功能差异混合。无论选择哪种顺序，都必须在同一个逻辑工作包
中同时修改内部符号、外部配置、测试、fixture、脚本和文档。

## 8. 测试要求

### 8.1 架构测试

- adaptor 源目录下 `.g4` 文件数为 0。
- 每个 grammar module 的来源 README 与真实 `.g4` 位于同一 Maven module。
- 不存在错误 Grammar 拼写的任何变体。
- 不存在重复版本类名。
- token-event/full grammar 不互相 import 或 delegate。
- script framer 包不得依赖 relationship、lineage 或 naming extractor。
- compact PL/pgSQL token-event parser 只能把静态 SQL 交给 PostgreSQL token-event parser。
- versioned PL/pgSQL parser 只能把静态 SQL 交给同版本 PostgreSQL full grammar parser。
- token-event/full-grammar routine generated parser 之间不得 import、包装或 delegate。
- visitor、adapter、framer 和 collector 不得持有 static mutable state。

### 8.2 Script framing 测试

- MySQL arbitrary delimiter、routine 内部分号、字符串和注释。
- PostgreSQL `$$`、`$tag$` 和嵌套 SQL 字符串。
- Oracle object block 后独立 `/`。
- SQL Server 独立行 `GO`，以及字符串/注释中的 `GO`。
- 所有 statement 的字符区间和起止行准确。

### 8.3 PostgreSQL routine 测试

- token-event/v16/v17/v18 的 `DECLARE`、IF、LOOP、FOREACH、EXCEPTION、assignment 和 RAISE。
- compact token-event PL/pgSQL 对关系/血缘所需过程结构的覆盖。
- `LANGUAGE sql` 与 `LANGUAGE plpgsql` 正确分发。
- string body 缺省 `LANGUAGE` 必须一致拒绝；typed `BEGIN ATOMIC` 缺省 language必须按 SQL分发。
- token-event 内嵌 SELECT/INSERT/UPDATE/DELETE/MERGE 调用 PostgreSQL token-event parser。
- full-grammar 内嵌 SELECT/INSERT/UPDATE/DELETE/MERGE 调用同版本 full grammar。
- 版本专属 SQL 在目标版本接受、低版本拒绝或产生明确 diagnostic。
- unsupported language 不产生 SQL 事实。
- routine provenance 保留 source file、statement、line、block、object type/name。
- 两种 mode 对共同支持的 routine SQL 输出相同 semantic observation；version-only 能力只要求
  versioned full grammar 严格识别，不要求 compact token-event 模拟版本边界。

### 8.4 回归测试

- focused grammar/framer/routine tests。
- 受影响方言 correctness。
- 19 类 parser matrix smoke。
- 全部 correctness fixtures。
- 全部 sample-data direct/derived CLI 输出。
- 根 reactor `mvn test`。

## 9. 最终验收标准

1. 所有 `.g4` 均位于 `relation-detector/grammar/` 下，adaptor 下没有 grammar 源或 stale
   grammar README。
2. script 层命名和职责均为 framing，不参与事实推断。
3. PostgreSQL token-event 拥有独立 compact PL/pgSQL grammar 并回调 token-event SQL parser；
   v16/v17/v18 分别拥有可审计的 PL/pgSQL grammar，并回调同版本 full SQL parser。
4. MySQL routine 的 IF、循环、DECLARE、handler 和内部 SQL仍由 versioned MySQL grammar
   直接解析，不引入无必要的第二阶段。
5. version package 内不存在重复版本类名；版本只出现在 artifact/package/profile/policy/fixture。
6. 历史误导的 TokenEvent wrapper、routine、sink 和 binding 名称全部清理。
7. 全仓只存在正确的 Grammar 拼写；旧 Java/YAML/CLI/profile/fixture 值直接不存在且无法使用。
8. `.mvn` 只跟踪构建配置，不跟踪 build cache；个人 IDE 设置不混入迁移。
9. token-event/full grammar 的直接和 derived 事实集合无计划外变化，diagnostics 无新增静默失败。
10. 所有 correctness fixtures、19 parser categories、全部 sample-data CLI 和 Maven reactor
    测试通过。
11. core/adaptor 不保留 `src/main/antlr4` 空目录，source/test package目录不为空；
    correctness fixture目录必须包含 `manifest.yml`。
12. 死代码只能通过 reachability audit列为候选并经人工确认后删除；审计脚本不得自动改源码。

## 10. 实施计划输入要求

后续计划必须对每个工作包列出：

- 精确文件和 module 清单。
- 类、package、artifact、profile、YAML 和 fixture 的旧名到新名映射。
- 先失败的架构/行为测试。
- Maven reactor 依赖顺序。
- focused、scope、matrix-smoke、full correctness 和 CLI 命令。
- golden/hash 差异审计方法。
- 不满足验收时的回滚边界。

任何执行者不得把“编译通过”视为完成。只有第 9 节全部成立，迁移才算完成。
