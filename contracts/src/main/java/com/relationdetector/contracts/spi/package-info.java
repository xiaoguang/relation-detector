/**
 * 数据库 adaptor SPI 契约层。
 *
 * <p>CN: 本包定义 core 调用数据库 adaptor 的边界：metadata、对象定义、数据库 DDL、
 * 日志、token-event parser、full-grammer profile hooks 和数据画像。SPI 不包含
 * MySQL/PostgreSQL 实现细节；具体实现属于各 adaptor 模块。
 *
 * <p>EN: Database adaptor SPI boundary. Core calls metadata, object, database
 * DDL, log, token-event parser, full-grammer profile hooks, and profiling
 * services through these contracts. Dialect implementations belong to adaptor
 * modules, not to this package.
 */
package com.relationdetector.contracts.spi;
