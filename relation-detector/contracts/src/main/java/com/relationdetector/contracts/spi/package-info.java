/**
 * 数据库 adaptor SPI 契约层。
 *
 * <p>CN: 本包定义 core 调用数据库 adaptor 的边界：metadata、对象定义、数据库 DDL、
 * 日志、token-event parser、full-grammar profile hooks 和数据画像。SPI 不包含
 * MySQL/PostgreSQL 实现细节；具体实现属于各 adaptor 模块。
 *
 * <p>EN: Database adaptor SPI boundary. Core calls metadata, object, database
 * DDL, log, token-event parser, full-grammar profile hooks, and profiling
 * services through these contracts. Dialect implementations belong to adaptor
 * modules, not to this package.
 * <p>Responsibility: 定义 core 与数据库 adaptor 的可执行边界 / Defines the executable core-to-adaptor boundary.
 * <p>Inputs: scan scope、JDBC connection、SQL 与 profile request / Scan scope, connections, SQL, and profile requests.
 * <p>Outputs: metadata、definitions、events、profile evidence 与 warnings / Metadata, definitions, events, and warnings.
 * <p>Upstream/Downstream: core 调用，方言 adaptor 实现 / Called by core and implemented by dialect adaptors.
 * <p>Forbidden: 不放置方言实现或扫描策略 / Must not contain dialect implementations or scan policy.
 */
package com.relationdetector.contracts.spi;
