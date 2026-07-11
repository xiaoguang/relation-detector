# 构建、测试与性能验收指南

## 目标与不可破坏的边界

本指南用于后续开发者和自动化 LLM 选择最小测试范围，并在逻辑批次结束后完成全量验收。性能优化不得通过少跑 fixture、缓存测试结果或刷新 golden 获得。

- token-event 与 full-grammer 互不 delegate。
- parser/visitor/state 每次 parse 独立创建，不共享可变状态。
- `namingEvidence` 是 `NAMING_MATCH` 唯一来源。
- 结构重构不更新 correctness golden。
- Maven Build Cache 只复用 generated/compiled artifact，Surefire/Failsafe 永远运行。

## 已实现的性能结构

### Grammar artifact 隔离

`relation-detector/grammar/` 包含 14 套 versioned full grammar 和 1 套 PostgreSQL routine grammar。`.g4`、generated lexer/parser/base visitor 和 parser base 在各自 artifact 中编译；adaptor 仅保留 binding、profile/version policy、typed adapter 和 visitor。

修改普通 visitor 时 ANTLR generation 应为 0；修改某个 `.g4` 时只能重建该 grammar artifact 及其下游。架构检查入口是：

```bash
bash relation-detector/scripts/grammar-module-architecture-test.sh
```

### Correctness asset catalog

`TestAssetCatalog` 在测试 JVM 中只扫描一次 manifest，并按 content hash 缓存 input/expected JSON。SQL splitter 消费已读取文本，不重新读文件。`updateCorrectnessGold=true` 时必须单线程并禁用该缓存。

### Batch CLI

`relation-detector batch --manifest batch.yml` 在一个 JVM 中执行多个 scan job。sample-data runner 默认 case parallelism 4、scan parallelism 2、总 worker budget 8；`verify-all.sh` 使用更保守的 case parallelism 3。线程预算由 batch 统一管理，不能在每个 statement 中再无限扩张。

38 份 direct/derived JSON 生成后，`validate-sample-data-results.py` 校验 19 个 category、summary/数组计数、naming reference、absolute path 和 warnings。

### Maven 本地缓存

`.mvn/extensions.xml` 锁定 Maven Build Cache Extension 1.2.3，`.mvn/maven-build-cache-config.xml` 只启用本地缓存。缓存输入包含 grammar、parser base、ANTLR/plugin version、Java release 和 effective POM；report、sample output、timestamp 和临时文件不参与缓存 key。

## 开发测试协议

### 1. Focused

先写一个能精确失败的最小测试，然后只跑目标测试类：

```bash
mvn -T 2 -pl <affected-module> -am \
  -Dtest='<TargetTest>' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

### 2. Scope

用一次 reactor 同时跑受影响模块单测与方言 correctness：

```bash
bash relation-detector/scripts/test-scope.sh mysql
bash relation-detector/scripts/test-scope.sh core,postgres
```

支持 `core|mysql|postgres|oracle|sqlserver|assets`，可逗号组合。

### 3. Matrix smoke

```bash
mvn -T 2 -Pmatrix-smoke verify
```

smoke manifest 必须为 19 个 parser category 各选一个代表 fixture，不能回退到只验证四个根方言。

### 4. Acceptance

每个逻辑批次结束执行：

```bash
mvn -T 2 -Pacceptance verify
```

该命令必须发现并执行全部 1198 fixtures，并验收 generated reports。任何 fixture 数减少都是失败，不是提速。

### 5. 最终 parser CLI 验收

```bash
bash relation-detector/scripts/verify-all.sh
```

脚本只允许一次 Maven acceptance reactor，之后复用已打包 CLI 运行 batch，生成并校验全部 38 份 JSON。不得在脚本尾部再运行第二次 `mvn test/package`。

### 6. 无缓存参考

```bash
mvn -T 2 -Pacceptance -Dmaven.build.cache.enabled=false clean verify
```

该命令用于排除本地缓存掩盖的依赖/生成问题，必须在最终完成声明前运行。

## 性能测量

```bash
bash relation-detector/scripts/benchmark-build.sh all
```

`report.json` 必须包含 Maven module timing、ANTLR generation timing、测试 Top 20、fixture Top 20、CLI case timing、1198 fixture 运行摘要和 38 份 canonical hash；`fingerprints.tsv` 同时保留适合 shell diff 的忽略时间字段 hash。报告只允许读取当前 session 新产生的 Surefire XML，防止旧报告污染结论。`verify-all.sh` 会在 `relation-detector/target/verification/<session>/` 同时留存 Maven log、`performance-report.json` 和 `fingerprints.tsv`。

当前已测 warm acceptance 在 2 分 43 秒至 2 分 59 秒之间（原基线约 4 分 25 秒）；2 分 30 秒仍是优化目标，不应写成已达成。最新一次完整 Maven acceptance + 38 份 CLI JSON + validation/fingerprint 约 4 分 51 秒，低于 5 分 30 秒目标。每次性能结论仍应以本次 benchmark report 为准。

## LLM 执行规则

1. 先读实现与相关 golden，不凭文件名推测调用链。
2. 先写最小红测，确认失败正是目标行为。
3. 一次只修当前职责，不顺手改无关 parser/golden。
4. 依次运行 focused、scope、matrix-smoke、acceptance。
5. golden 差异必须列出 SQL、expected、actual 和语义判断；结构重构差异必须修代码。
6. 最终执行 `verify-all.sh` 和 no-cache reference，并如实报告未达成的性能目标。
