# 构建、测试与性能验收指南

## 目标与边界

本指南说明如何选择开发测试范围并完成发布验收。事实正确性不能通过少跑 fixture、复用 Surefire 结果或刷新 golden 获得；性能结论必须读取当前 verification session，而不是复制历史耗时。

- token-event 与 full-grammar parser 独立实现，不跨模式 delegate。
- parser、visitor 和状态按 parse 创建，不共享可变业务状态。
- `namingEvidence` 是 `NAMING_MATCH` 的唯一顶层来源。
- 结构重构不得更新 correctness golden。
- 重型 correctness 与 sample-data CLI 必须顺序运行，不能重叠 JVM。

## Grammar artifact 结构

`relation-detector/grammar/` 当前包含 28 个可构建 grammar artifact：

- 14 套 versioned full grammar：MySQL 2、PostgreSQL 3、Oracle 4、SQL Server 5。
- 4 套 PL/pgSQL shell grammar：compact token-event 1、PostgreSQL v16/v17/v18 各 1。
- 10 套 framing/token grammar：common 2，加四方言各自的 script 与 token-event 8。

`.g4` 和手写 lexer/parser base 由 grammar module 拥有；adaptor 只保留 binding、typed context adapter、visitor 和 version policy。普通 visitor 修改不应触发 ANTLR generation。架构检查的真实入口是：

```bash
bash relation-detector/scripts/tests/grammar-module-architecture-test.sh
```

## 开发测试层级

### Focused

```bash
mvn -pl <affected-module> -am \
  -Dtest='<TargetTest>' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

### Dialect scope

```bash
bash relation-detector/scripts/test-scope.sh mysql
bash relation-detector/scripts/test-scope.sh core,postgres
```

### Parser matrix

```bash
mvn -T 2 -Pmatrix-smoke verify
```

### 隔离 correctness

完整 correctness 按数据库/parser family 分 JVM 顺序执行，避免 versioned grammar 同时占满 Old Gen：

```bash
CORRECTNESS_HEAP=6g CORRECTNESS_PARALLELISM=6 \
  bash relation-detector/scripts/run-correctness-isolated.sh
```

执行后必须检查 `relation-detector/target/correctness-run-summary.json` 的 discovered、executed、passed 和 parser category，而不是依赖 Maven exit code。

### 隔离 sample-data CLI

发布验收按 parser family 分组顺序启动 JVM，Oracle root 和各 versioned profile 单独分组。发布默认 case parallelism 为 1。只有固定 heap、相同输入的 A/B 同时记录 wall time、峰值内存与 GC，并证明更高并发更快且安全时，才能显式调高：

```bash
SAMPLE_DATA_PARSER_CLI_HEAP=6g \
SAMPLE_DATA_PARSER_CLI_CASE_PARALLELISM=1 \
SAMPLE_DATA_PARSER_CLI_SCAN_PARALLELISM=2 \
  bash relation-detector/scripts/run-sample-data-isolated.sh
```

验证 `relation-detector/target/sample-data-parser-cli/batch-report.json`、`summary-with-derived.tsv` 和 `observation-parity.tsv`。

full correctness 与 sample-data isolated runner 默认共用
`target/.relation-detector-heavy-job.lock`。直接分别启动两个脚本时，后启动者必须在创建 Maven、Surefire 或 batch JVM 前失败；`RELATION_DETECTOR_HEAVY_JOB_LOCK_DIR` 只用于受控测试或隔离运行。

`verify-all.sh` 与 `verify-release.sh` 也使用同一锁：最外层 owner 从 smoke 阶段持锁到最终
manifest，内部 correctness/sample-data 只验证并借用 owner token。锁目录存在但 PID/job/token
尚不完整时按占用处理并要求人工清理，不能被 contender 误删；只有完整 owner PID 已死亡时才通过
原子 quarantine 恢复 stale lock。发布脚本退出或收到 INT/TERM 后仅由最外层 owner 释放锁。
收到 INT/TERM 时，最外层入口会先终止并等待当前 Maven 或 runner 进程组，再释放锁，避免留下仍在
工作的无 owner 子进程。

## 发布入口

```bash
bash relation-detector/scripts/verify-all.sh
```

该脚本先运行 matrix/模块验收，再调用隔离 correctness，最后复用已打包 CLI 生成 sample-data 结果并建立 verification manifest。它不是“单一 Maven reactor”，也不能以中途的 smoke summary 代替完整隔离结果。

需要排除构建缓存时，使用 release wrapper：

```bash
bash relation-detector/scripts/verify-release.sh
```

wrapper 先运行无缓存 clean smoke reactor，再调用 `verify-all.sh` 的分组 full correctness 和 sample-data。单 JVM `-Pacceptance` full profile 可用于受控诊断，不是发布验收入口。

## 本地 Maven 配置

根 `.mvn/` 是开发机本地、被 Git 忽略的可选配置，不属于仓库交付。验证脚本不能假设远端 checkout 含有 build-cache extension；构建在没有 `.mvn/` 时也必须正确运行。任何使用缓存的 correctness 命令都必须确认本次参数确实产生了新的 run summary。

## 性能证据与停止条件

性能报告写入 `relation-detector/target/verification/<session>/performance-report.json`。fixture 数量、parser category 数量和耗时均从当前 manifest/generated summary 读取，本指南不维护易漂移快照。

性能修改必须从最近通过相应完整验收的提交开始，只验证一个明确假设。新增缓存、线程池、调度器或生命周期机制必须有热点证据、前后 A/B 和停止条件；收益不明确时撤销实验并讨论，不继续叠加机制。
