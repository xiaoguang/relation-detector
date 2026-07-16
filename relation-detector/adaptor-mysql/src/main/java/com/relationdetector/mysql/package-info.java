/**
 * MySQL adaptor 装配层。
 *
 * <p>CN: 本包暴露 MySQL metadata、数据库 DDL、对象定义、日志、数据画像、
 * token-event parser 和 full-grammar module。根包只做装配；方言 parser 代码分别
 * 位于 tokenevent 与 fullgrammar/v8_0 子包。
 *
 * <p>EN: MySQL adaptor assembly layer. It exposes MySQL metadata, database DDL,
 * object definitions, logs, profiling, token-event parsers, and full-grammar
 * modules. The root package assembles services; dialect parser code lives in
 * tokenevent and fullgrammar/v8_0 subpackages.
 * <p>Responsibility: 装配 MySQL capabilities、collectors、parsers 与 identifier rules / Assembles the MySQL adaptor.
 * <p>Inputs: ScanScope、JDBC connection、framed MySQL statements / Scope, connection, and framed statements.
 * <p>Outputs: SPI v5 collector/parser implementations / SPI v5 collector and parser implementations.
 * <p>Upstream/Downstream: core SPI 上游，MySQL 子包实现下游 / Connects core SPI to MySQL subpackages.
 * <p>Forbidden: 不在 adaptor root 重复事实合并或 naming rules / Must not duplicate merging or naming rules.
 */
package com.relationdetector.mysql;
