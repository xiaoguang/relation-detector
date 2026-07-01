/**
 * Oracle database adaptor.
 *
 * <p>CN: 本模块承载 Oracle adaptor 的 SPI 注册、Oracle token-event parser 入口
 * 以及 12c/19c/21c/26ai full-grammer profile module。core 仍负责最终
 * relationship、lineage、DDL evidence、NAMING_MATCH、confidence 和输出。
 *
 * <p>EN: Oracle adaptor module. It owns Oracle SPI registration, token-event
 * parser entry points, and versioned 12c/19c/21c/26ai full-grammer modules while
 * core owns semantic extraction, merging, scoring, and output.
 */
package com.relationdetector.oracle;
