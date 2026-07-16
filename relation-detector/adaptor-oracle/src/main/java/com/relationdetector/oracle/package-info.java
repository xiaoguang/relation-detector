/**
 * Oracle database adaptor.
 *
 * <p>CN: 本模块承载 Oracle adaptor 的 SPI 注册、Oracle token-event parser 入口
 * 以及 12c/19c/21c/26ai full-grammar profile module。core 仍负责最终
 * relationship、lineage、DDL evidence、NAMING_MATCH、confidence 和输出。
 *
 * <p>EN: Oracle adaptor module. It owns Oracle SPI registration, token-event
 * parser entry points, and versioned 12c/19c/21c/26ai full-grammar modules while
 * core owns semantic extraction, merging, scoring, and output.
 * <p>Responsibility: 装配 Oracle capabilities、collectors、parsers 与 owner rules / Assembles the Oracle adaptor.
 * <p>Inputs: ScanScope、JDBC connection and framed Oracle statements / Scope, connection, and statements.
 * <p>Outputs: SPI v5 Oracle collector/parser implementations / Oracle SPI implementations.
 * <p>Upstream/Downstream: core SPI 上游，Oracle 子包实现下游 / Connects core SPI to Oracle subpackages.
 * <p>Forbidden: 不在 root 重复 parser semantics 或 evidence merge / Must not duplicate parser semantics or merging.
 */
package com.relationdetector.oracle;
